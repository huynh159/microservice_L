package com.example.chatservice.controller;

import com.example.chatservice.dto.request.SendMessageRequest;
import com.example.chatservice.entity.SenderRole;
import com.example.chatservice.security.UserPrincipal;
import com.example.chatservice.service.ChatMessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * ==============================================================================
 * WEBSOCKET STOMP CONTROLLER
 * ------------------------------------------------------------------------------
 * KIẾN THỨC LẬP TRÌNH REALTIME:
 * 1. @Controller (không dùng @RestController vì đây là socket, không phải HTTP REST).
 * 2. @MessageMapping("/chat.sendMessage"):
 *    - Lắng nghe tin nhắn gửi đến đích: /app/chat.sendMessage
 * 3. Principal:
 *    - Được tự động tiêm vào từ Spring Security context mà WebSocketAuthChannelInterceptor
 *      đã gắn vào ở frame CONNECT trước đó.
 *    - Giúp Server biết chắc chắn 100% ai là người gửi mà Client không thể giả mạo ID!
 * ==============================================================================
 */
@Controller
public class ChatWebSocketController {

    private static final Logger log = LoggerFactory.getLogger(ChatWebSocketController.class);

    private final ChatMessageService chatMessageService;

    public ChatWebSocketController(ChatMessageService chatMessageService) {
        this.chatMessageService = chatMessageService;
    }

    /**
     * Nhận tin nhắn chat từ Client qua giao thức STOMP
     */
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Payload SendMessageRequest request, Principal principal) {
        if (principal == null) {
            log.warn("Từ chối tin nhắn: Người dùng chưa được xác thực trong phiên WebSocket");
            return;
        }

        // Lấy thông tin định danh người gửi từ Authentication Context
        Authentication auth = (Authentication) principal;
        UserPrincipal userPrincipal = (UserPrincipal) auth.getPrincipal();

        SenderRole senderRole = userPrincipal.isAdmin() ? SenderRole.ROLE_ADMIN : SenderRole.ROLE_USER;

        log.info("Nhận tin nhắn qua WebSocket từ [{}] ({}) gửi vào phòng [{}]",
                userPrincipal.getEmail(), senderRole, request.getRoomId());

        // Gọi service lưu DB và tự động phát tin nhắn qua WebSocket Broker
        chatMessageService.sendMessage(userPrincipal.getId(), senderRole, request);
    }
}
