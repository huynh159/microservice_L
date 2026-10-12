package com.example.chatservice.service.impl;

import com.example.common.exception.BadRequestException;
import com.example.common.exception.ForbiddenException;
import com.example.common.exception.ResourceNotFoundException;
import com.example.chatservice.dto.request.SendMessageRequest;
import com.example.chatservice.dto.response.ChatMessageResponse;
import com.example.chatservice.entity.ChatMessage;
import com.example.chatservice.entity.ChatRoom;
import com.example.chatservice.entity.RoomStatus;
import com.example.chatservice.entity.SenderRole;
import com.example.chatservice.repository.ChatMessageRepository;
import com.example.chatservice.repository.ChatRoomRepository;
import com.example.chatservice.service.ChatMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * ==============================================================================
 * TRIỂN KHAI NGHIỆP VỤ LƯU TRỮ VÀ PHÁT TIN NHẮN REALTIME
 * ------------------------------------------------------------------------------
 * LUỒNG XỬ LÝ (FLOW):
 * 1. Nhận tin nhắn từ Controller.
 * 2. Xác thực quyền: Người dùng chỉ được nhắn vào phòng của mình; Admin nhắn được mọi phòng.
 * 3. Lưu tin nhắn vào PostgreSQL (bảng chat_messages).
 * 4. Sử dụng SimpMessageSendingOperations đẩy tin nhắn ngay lập tức tới:
 *    "/topic/rooms.{roomId}" -> Cả 2 bên mở khung chat này đều nhận được tức thì!
 * ==============================================================================
 */
@Service
@Transactional
public class ChatMessageServiceImpl implements ChatMessageService {

    private static final Logger log = LoggerFactory.getLogger(ChatMessageServiceImpl.class);

    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    public ChatMessageServiceImpl(
            ChatRoomRepository chatRoomRepository,
            ChatMessageRepository chatMessageRepository,
            SimpMessageSendingOperations messagingTemplate
    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public ChatMessageResponse sendMessage(UUID senderId, SenderRole senderRole, SendMessageRequest request) {
        if (request.getRoomId() == null) {
            throw new BadRequestException("ROOM_ID_REQUIRED", "Cần cung cấp roomId để gửi tin nhắn");
        }

        ChatRoom room = chatRoomRepository.findById(request.getRoomId())
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng chat: " + request.getRoomId()));

        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new BadRequestException("ROOM_CLOSED", "Phòng chat đã kết thúc, không thể gửi thêm tin nhắn");
        }

        // BẢO MẬT: Nếu là Khách hàng (ROLE_USER), chỉ được phép gửi vào phòng của chính mình
        if (senderRole == SenderRole.ROLE_USER && !room.getUserId().equals(senderId)) {
            throw new ForbiddenException("FORBIDDEN", "Bạn không có quyền gửi tin nhắn vào phòng chat của người khác");
        }

        // 1. Tạo Entity và lưu vào Database
        ChatMessage message = new ChatMessage(room, senderId, senderRole, request.getContent().trim());
        ChatMessage saved = chatMessageRepository.save(message);

        // 2. Cập nhật thời gian hoạt động mới nhất của phòng
        room.setUpdatedAt(Instant.now());
        chatRoomRepository.save(room);

        // 3. Đóng gói DTO phản hồi
        ChatMessageResponse response = toMessageResponse(saved);

        // 4. BẮN TIN NHẮN REALTIME QUA WEBSOCKET STOMP TỚI PHÒNG CHAT
        String destination = "/topic/rooms." + room.getId();
        log.info("Phát tin nhắn mới từ [{}] ({}) tới kênh [{}]", senderId, senderRole, destination);
        messagingTemplate.convertAndSend(destination, response);

        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessagesByRoomId(UUID roomId, UUID currentUserId, boolean isAdmin) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "Không tìm thấy phòng chat: " + roomId));

        // BẢO MẬT: Khách hàng chỉ xem được tin nhắn của chính phòng mình
        if (!isAdmin && !room.getUserId().equals(currentUserId)) {
            throw new ForbiddenException("FORBIDDEN", "Bạn không có quyền xem lịch sử phòng chat của người khác");
        }

        return chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId)
                .stream()
                .map(this::toMessageResponse)
                .toList();
    }

    private ChatMessageResponse toMessageResponse(ChatMessage msg) {
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .roomId(msg.getRoom().getId())
                .senderId(msg.getSenderId())
                .senderRole(msg.getSenderRole())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .isRead(msg.isRead())
                .build();
    }
}
