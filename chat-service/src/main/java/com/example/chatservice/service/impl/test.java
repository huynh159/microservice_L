package com.example.chatservice.service.impl;

import com.example.chatservice.dto.response.ChatMessageResponse;

@Service
@Transacional

public class ChatMessageServiceImpl implements ChatMessageService {
    private static final Logger log = LoggerFactory.getLogger(ChatMessageServiceImpl.class);
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessageSendingOperations messagingTemplate;

    public ChatMessageServiceImpl(
            ChatRoomRepository chatRepository,
            ChatMessageRepository chatMessageRepository,
            SimpMessageSendingOperations messagingTemplate

    ) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override 
    public ChatMessageResponse sendMessage(UUID senderId, SenderRole senderRole, SendMessageRequest){
        if (request.getRoomId() == null) {
            throw new BadRequestException("ROOM_ID_REQUIRED", "Cần cung cấp roomId để nhận tin nhắn");

        }
        ChatRoom room = chatRoomRepository.findById(request.getRoomId())
            .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", "không tìm thấy phòng chat: "+ request.getRoomId()));
        if (room.getStatus() == RoomStatus.CLOSED) {
            throw new BadRequestException("ROOM_CLOSED", "Phòng chat đã kết thúc, không thể gửi tin nhắn ");
        
        }
        // bảo mật: nếu là khách hàng role_user chỉ được phép gửi vào phòng của chinh mình
        if (senderRole ==SenderRole.ROLE_USER && !room.getUserId().equal(senderId)) {
            throw new ForbiddenException("FORBIDDEN","bạn không  có quyền gửi tin nhắn vào phòng chat của người khác");


        }
        // 1. tạo entity và lưu vào database 
        ChatMessage message = new ChatMessage(room,senderId, senderRole, request.getContent().trim());
        ChatMessage saved = chatMessageRepository.save(message);
        // 2. cập nhật thời gian và hoạt động mới nhất của phòng 
        room.setUpdatedAt(Instant.now());
        chatRoomRepository.save(room);
        // 3. đóng gói DTO phản hồi 
        CHatMessageResponse response = toMessageResponse(saved);
        //4. bắn tin  hắn realtime qua websocket stomp tới phòng chat
        String destination = "/topic/rooms." + room.getId();
        log.info("Phát tin nhắn mới từ [{}] ({}) tới kênh [{}]",senderId,senderRole, destination);
        messagingTemplate.converAndSend(destination, response); 
        return response;
    }

    @Ovderride
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> getMessagesByRoomId(UUID roomId, UUID curentUserId, boolean isAdmin){
        ChatRoom room = chatRoomRepository.findById(roomId)
             .orElseThrow(() -> new ResourceNotFoundException("ROOM_NOT_FOUND", " không tìm thấy phòng chat" + roomId));
        // bảo mật: khách hanghf chỉ xem được tin nhắn của chính phòng mình 
        if (!isAdmin && !room.getUserOd().equals(currentUserId)) {
            throw new ForbiddenException("FORBIDDEN", bạn không có quyền xem lịch sử phòng chat của người khác");
        }
        return chatMessageRepository.findByRoomIdOrderByCreatedAtAsc(roomId)
                .stream()
                .map(this::toMessageResponse)
                .toList(); 

    }

    private ChatMessageResponse toMessageResponse(ChatMessage msg) {
        return ChatMessageResponse.builder()
                .id(msg.getId())
                .roomid(msg.getRoom().getId())
                .senderId(msg.getSenderId())
                .senderRole(msg.gếtnderRole())
                .content(msg.getContent())
                .createdAt(msg.getCreatedAt())
                .isRead(msg.isRead())
                .build();
    }
}