package com.example.chatservice.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * ==============================================================================
 * BỘ CHẶN XÁC THỰC KẾT NỐI WEBSOCKET (STOMP CHANNEL INTERCEPTOR)
 * ------------------------------------------------------------------------------
 * KIẾN THỨC BẢO MẬT WEBSOCKET DOANH NGHIỆP:
 * 1. Khác với REST API (mỗi request gửi 1 header HTTP), WebSocket là kết nối lâu dài (Persistent TCP).
 * 2. Ngay khi Client gửi Frame STOMP đầu tiên là CONNECT:
 *    - Client phải gửi kèm Header: "Authorization: Bearer <token>"
 * 3. ChannelInterceptor sẽ chặn tại hàm preSend():
 *    - Rút trích JWT Bearer Token.
 *    - Xác thực chữ ký số bằng JwtService.
 *    - Tạo UserPrincipal (chứa userId, email, danh sách quyền ROLE_USER, ROLE_ADMIN).
 *    - Gán danh tính vào phiên: accessor.setUser(authentication).
 * 4. Kể từ lúc này, mọi tin nhắn gửi đi trong phiên WebSocket đó đều được Spring bảo vệ
 *    và xác định được chính xác ai là người gửi (thông qua Authentication context).
 * ==============================================================================
 */
@Component
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final Logger log = LoggerFactory.getLogger(WebSocketAuthChannelInterceptor.class);

    private final JwtService jwtService;

    public WebSocketAuthChannelInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            log.info("Nhận frame STOMP CONNECT. Bắt đầu kiểm tra xác thực JWT...");

            // Lấy token từ header "Authorization" hoặc "X-Authorization"
            String authHeader = getHeader(accessor, "Authorization");
            if (authHeader == null) {
                authHeader = getHeader(accessor, "X-Authorization");
            }

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("STOMP CONNECT bị từ chối: Thiếu Header Authorization Bearer token");
                throw new AuthenticationCredentialsNotFoundException("Thiếu Authorization Bearer token trong frame CONNECT");
            }

            String token = authHeader.substring(7).trim();

            if (!jwtService.isTokenValid(token)) {
                log.warn("STOMP CONNECT bị từ chối: JWT Token không hợp lệ hoặc đã hết hạn");
                throw new AuthenticationCredentialsNotFoundException("JWT Token không hợp lệ hoặc đã hết hạn");
            }

            // Giải mã danh tính từ Token
            UUID userId = jwtService.extractUserId(token);
            String email = jwtService.extractEmail(token);
            Set<String> roles = jwtService.extractRoles(token);

            UserPrincipal userPrincipal = UserPrincipal.create(userId, email, roles);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(userPrincipal, null, userPrincipal.getAuthorities());

            // GÁN DANH TÍNH VÀO PHIÊN STOMP WEBSOCKET
            accessor.setUser(authentication);
            log.info("Xác thực STOMP thành công cho user [{}] ({}), roles: {}", userId, email, roles);
        }

        return message;
    }

    private String getHeader(StompHeaderAccessor accessor, String headerName) {
        List<String> nativeHeaders = accessor.getNativeHeader(headerName);
        if (nativeHeaders != null && !nativeHeaders.isEmpty()) {
            return nativeHeaders.get(0);
        }
        return null;
    }
}
