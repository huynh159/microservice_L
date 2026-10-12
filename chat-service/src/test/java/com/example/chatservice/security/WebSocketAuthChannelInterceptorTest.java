package com.example.chatservice.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ==============================================================================
 * KIỂM THỬ BỘ CHẶN XÁC THỰC WEBSOCKET STOMP (CHANNEL INTERCEPTOR TEST)
 * ------------------------------------------------------------------------------
 * Kiểm tra 3 trường hợp thực tế khi Client bắt tay qua WebSocket:
 * 1. Token hợp lệ -> Gán đúng UserPrincipal vào phiên STOMP.
 * 2. Thiếu Token trong CONNECT frame -> Ném AuthenticationCredentialsNotFoundException.
 * 3. Token giả mạo hoặc hết hạn -> Từ chối kết nối ngay lập tức.
 * ==============================================================================
 */
class WebSocketAuthChannelInterceptorTest {

    private static final String TEST_SECRET = "super_secret_jwt_key_that_is_at_least_256_bits_long_for_security_reasons";
    private final SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));

    private JwtService jwtService;
    private WebSocketAuthChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(TEST_SECRET);
        interceptor = new WebSocketAuthChannelInterceptor(jwtService);
    }

    private String createToken(UUID userId, String email, List<String> roles, long expirationMs) {
        return Jwts.builder()
                .subject(userId.toString())
                .claim("email", email)
                .claim("roles", roles)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    @Test
    @DisplayName("STOMP CONNECT với Token hợp lệ -> Xác thực thành công và gán UserPrincipal")
    void testConnectWithValidToken() {
        UUID userId = UUID.randomUUID();
        String email = "customer@example.com";
        String token = createToken(userId, email, List.of("ROLE_USER"), 600000);

        // Giả lập Frame STOMP CONNECT có kèm Header Authorization Bearer
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.setLeaveMutable(true);
        accessor.addNativeHeader("Authorization", "Bearer " + token);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        // When: Đi qua Interceptor
        Message<?> result = interceptor.preSend(message, (MessageChannel) null);

        // Then: Message không bị chặn và accessor đã có UserPrincipal
        assertThat(result).isNotNull();
        StompHeaderAccessor resultAccessor = StompHeaderAccessor.wrap(result);
        Authentication auth = (Authentication) resultAccessor.getUser();
        assertThat(auth).isNotNull();

        UserPrincipal principal = (UserPrincipal) auth.getPrincipal();
        assertThat(principal.getId()).isEqualTo(userId);
        assertThat(principal.getEmail()).isEqualTo(email);
        assertThat(principal.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("STOMP CONNECT thiếu Header Authorization -> Từ chối kết nối")
    void testConnectMissingTokenThrowsException() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, (MessageChannel) null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("Thiếu Authorization Bearer token");
    }

    @Test
    @DisplayName("STOMP CONNECT với Token hết hạn -> Từ chối kết nối")
    void testConnectExpiredTokenThrowsException() {
        // Tạo token đã hết hạn cách đây 10 giây
        String expiredToken = createToken(UUID.randomUUID(), "expired@example.com", List.of("ROLE_USER"), -10000);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer " + expiredToken);
        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, (MessageChannel) null))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class)
                .hasMessageContaining("JWT Token không hợp lệ");
    }
}
