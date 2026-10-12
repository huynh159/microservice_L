package com.example.chatservice.service.impl;

import com.example.common.exception.ForbiddenException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.chatservice.dto.response.ChatRoomResponse;
import com.example.chatservice.entity.ChatMessage;
import com.example.chatservice.entity.ChatRoom;
import com.example.chatservice.entity.RoomStatus;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.service.ChatRoomService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * ==============================================================================
 * TRIỂN KHAI NGHIỆP VỤ QUẢN LÝ PHÒNG CHAT
 * ------------------------------------------------------------------------------
 * KIẾN THỨC WEBSOCKET REALTIME:
 * - Khi có phòng chat mới được tạo ra, hệ thống tự động bắn sự kiện qua
 *   SimpMessageSendingOperations tới kênh broadcast: "/topic/admin/inbox".
 * - Tất cả các Admin đang mở trang quản trị sẽ nghe thấy chuông thông báo và danh sách
 *   cập nhật phòng chat mới xuất hiện ngay lập tức trên màn hình của họ!
 * ==============================================================================
 */
@Service
@Transactional
public class ChatRoomServiceImpl implements ChatRoomService {

    private static final Logger log = LoggerFactory.getLogger(ChatRoomServiceImpl.class);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    public ChatRoomServiceImpl(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            SimpMessageSendingOperations messagingTemplate
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public ChatRoomResponse getOrCreateRoomForUser(UUID userId, String email) {
        // 1. Tìm xem khách hàng này có phòng chat nào đang OPEN hay không
        Optional<ChatRoom> existingRoom = chatRoomRepository
                .findFirstByUserIdAndStatusOrderByCreatedAtDesc(userId, RoomStatus.OPEN);

        if (existingRoom.isPresent()) {
            return toRoomResponse(existingRoom.get());
        }

        // 2. Nếu chưa có hoặc phòng cũ đã đóng -> Tạo phòng chat mới
        ChatRoom newRoom = new ChatRoom(userId, email);
        ChatRoom savedRoom = chatRoomRepository.save(newRoom);
        log.info("Đã tạo phòng chat mới [{}] cho khách hàng [{}] ({})", savedRoom.getId(), userId, email);

        ChatRoomResponse response = toRoomResponse(savedRoom);

        // 3. THÔNG BÁO REALTIME CHO ADMIN: Bắn qua kênh /topic/admin/inbox
        try {
            messagingTemplate.convertAndSend("/topic/admin/inbox", response);
        } catch (Exception ex) {
            log.warn("Lỗi gửi thông báo phòng mới đến admin: {}", ex.getMessage());
        }

        return response;
    }

    @Override
    public ChatRoomResponse assignAdminToRoom(UUID roomId, UUID adminId) {
        ChatRoom room = findRoomByIdOrThrow(roomId);
        room.setAdminId(adminId);
        ChatRoom updated = chatRoomRepository.save(room);
        log.info("Admin [{}] đã tiếp nhận phòng chat [{}]", adminId, roomId);

        ChatRoomResponse response = toRoomResponse(updated);

        // Thông báo cho cả phòng biết Admin đã tham gia
        messagingTemplate.convertAndSend("/topic/rooms." + roomId, response);
        return response;
    }

    @Override
    public ChatRoomResponse updateRoomStatus(UUID roomId, RoomStatus status, UUID currentUserId, boolean isAdmin) {
        ChatRoom room = findRoomByIdOrThrow(roomId);

        // Kiểm tra quyền: Chỉ Admin hoặc chính chủ nhân phòng chat mới được đóng phòng
        if (!isAdmin && !room.getUserId().equals(currentUserId)) {
            throw new ForbiddenException("FORBIDDEN", "Bạn không có quyền thay đổi trạng thái phòng chat này");
        }

        room.setStatus(status);
        ChatRoom updated = chatRoomRepository.save(room);
        log.info("Phòng chat [{}] đã chuyển sang trạng thái [{}] bởi user [{}]", roomId, status, currentUserId);

        ChatRoomResponse response = toRoomResponse(updated);
        messagingTemplate.convertAndSend("/topic/rooms." + roomId, response);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getMyRooms(UUID userId) {
        return chatRoomRepository.findByUserIdOrderByCreatedAtDesc(userId)
                .stream()
                .map(this::toRoomResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatRoomResponse> getAllRoomsForAdmin() {
        return chatRoomRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toRoomResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public ChatRoomResponse getRoomById(UUID roomId) {
        ChatRoom room = findRoomByIdOrThrow(roomId);
        return toRoomResponse(room);
    }

    private ChatRoom findRoomByIdOrThrow(UUID roomId) {
        return chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng chat với ID: " + roomId));
    }

    private ChatRoomResponse toRoomResponse(ChatRoom room) {
        // Tìm tin nhắn cuối cùng để hiển thị dòng tóm tắt trên danh sách phòng
        Optional<ChatMessage> lastMsg = chatMessageRepository.findFirstByRoomIdOrderByCreatedAtDesc(room.getId());

        String lastContent = lastMsg.map(ChatMessage::getContent).orElse("Chưa có tin nhắn nào");
        Instant lastTime = lastMsg.map(ChatMessage::getCreatedAt).orElse(room.getCreatedAt());

        return ChatRoomResponse.builder()
                .id(room.getId())
                .userId(room.getUserId())
                .userEmail(room.getUserEmail())
                .adminId(room.getAdminId())
                .status(room.getStatus())
                .createdAt(room.getCreatedAt())
                .updatedAt(room.getUpdatedAt())
                .lastMessage(lastContent)
                .lastMessageTime(lastTime)
                .build();
    }
}
