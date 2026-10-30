package com.example.chatservice.controller;

@Controller
public class ChatWebSocketController {
    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);
    private final CHatMessageService chatMessageService;
    public ChatWebSocketController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;

    }
    // nhana tin nhắn chat ưtf client qua giao thức stomp
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        log.warn("từ chối tin nhắn: người dùng chưa được xác thực trong phiên websocket" );
        return;
    }
    // lấy thông tin định danh người gửi từ Authentication context
    Authentication auth = (Authentication) principal;
    
    UserPricipal userPrincipal = (UserPrincipal) auth.getPrincipal();

    SenderRole senderRole = userPrincipal.isAdmin() ? SenderRole.ROLE_ADMIN : SenderRole.ROLE_USER;
    log.info("Nhận tin nhắn qua websocket từ [{}] ({}) gửi vào phòng [{}]", 
    userPrincipal.getEmail()senderRole, request.getRoomId());

    // gọi service lưu db và tự động phát tin nhắn quá websocket broker
    chatMessageService.sendMessage(userPricipal.getId(), senderRole, request);
}