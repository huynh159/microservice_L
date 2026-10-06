package com.example.userservice.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test cho LoginAttemptService (Kiểm soát brute-force trên Redis)
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptServiceTest {

    @Mock
    private RedisOperations<String, String> redisOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private LoginAttemptService loginAttemptService;

    @BeforeEach
    void setUp() {
        lenient().when(redisOperations.opsForValue()).thenReturn(valueOperations);
        loginAttemptService = new com.example.userservice.security.impl.LoginAttemptServiceImpl(redisOperations);
    }

    @Test
    @DisplayName("isBlocked trả về false khi số lần nhập sai < 5 hoặc chưa có lịch sử")
    void isBlocked_LessThan5Attempts_ReturnsFalse() {
        when(valueOperations.get("login:attempts:user@example.com")).thenReturn("3");
        assertThat(loginAttemptService.isBlocked("user@example.com")).isFalse();

        when(valueOperations.get("login:attempts:user@example.com")).thenReturn(null);
        assertThat(loginAttemptService.isBlocked("user@example.com")).isFalse();
    }

    @Test
    @DisplayName("isBlocked trả về true khi số lần nhập sai >= 5")
    void isBlocked_5OrMoreAttempts_ReturnsTrue() {
        when(valueOperations.get("login:attempts:user@example.com")).thenReturn("5");
        assertThat(loginAttemptService.isBlocked("user@example.com")).isTrue();

        when(valueOperations.get("login:attempts:user@example.com")).thenReturn("7");
        assertThat(loginAttemptService.isBlocked("user@example.com")).isTrue();
    }

    @Test
    @DisplayName("recordFailedAttempt: Lần đầu tiên tăng đếm và đặt thời gian hết hạn 15 phút")
    void recordFailedAttempt_FirstAttempt_SetsExpiration() {
        when(valueOperations.increment("login:attempts:user@example.com")).thenReturn(1L);

        loginAttemptService.recordFailedAttempt("user@example.com");

        verify(valueOperations).increment("login:attempts:user@example.com");
        verify(redisOperations).expire("login:attempts:user@example.com", Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("recordFailedAttempt: Các lần tiếp theo chỉ tăng đếm không reset TTL")
    void recordFailedAttempt_SubsequentAttempts_OnlyIncrements() {
        when(valueOperations.increment("login:attempts:user@example.com")).thenReturn(2L);

        loginAttemptService.recordFailedAttempt("user@example.com");

        verify(valueOperations).increment("login:attempts:user@example.com");
        verify(redisOperations, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("resetAttempts: Xóa khóa khi đăng nhập thành công")
    void resetAttempts_DeletesRedisKey() {
        loginAttemptService.resetAttempts("user@example.com");
        verify(redisOperations).delete("login:attempts:user@example.com");
    }

    @Test
    @DisplayName("Chịu lỗi (Graceful degradation): Nếu Redis gặp sự cố không ném exception làm sập app")
    void redisException_GracefullyHandled() {
        when(valueOperations.get(anyString())).thenThrow(new RedisConnectionFailureException("Redis down"));

        boolean blocked = loginAttemptService.isBlocked("user@example.com");
        assertThat(blocked).isFalse(); // Không chặn người dùng nếu Redis tạm sập
    }
}
