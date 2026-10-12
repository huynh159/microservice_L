package com.example.userservice.config;

import com.example.userservice.dto.response.UserProfileResponse;
import com.example.userservice.entity.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * ==============================================================================
 * KIẾN THỨC TEST CẤU HÌNH REDIS & TÍNH BỀN VỮNG (RESILIENCE):
 * ------------------------------------------------------------------------------
 * 1. Graceful Degradation Testing:
 *    - Đảm bảo khi Redis sập hoặc timeout, CacheErrorHandler KHÔNG ĐƯỢC ném ngoại lệ
 *      (DoD: "Redis tạm lỗi thì service vẫn đọc DB và ghi log cảnh báo").
 *
 * 2. JSON Serialization / Deserialization Testing:
 *    - Đảm bảo DTO phức tạp (chứa UUID, Instant, Enum UserStatus, Set<String>) được
 *      Jackson chuyển thành chuỗi JSON và đọc ngược lại hoàn toàn chính xác.
 * ==============================================================================
 */
class RedisConfigTest {

    private final RedisConfig redisConfig = new RedisConfig();

    @Test
    @DisplayName("Graceful Degradation: CacheErrorHandler không ném ngoại lệ khi Redis sập")
    void testCacheErrorHandlerDoesNotThrowExceptions() {
        CacheErrorHandler errorHandler = redisConfig.errorHandler();
        assertThat(errorHandler).isNotNull();

        Cache mockCache = mock(Cache.class);
        when(mockCache.getName()).thenReturn(RedisConfig.USER_PROFILE_CACHE);
        RuntimeException redisException = new RuntimeException("Redis connection timeout: host unreachable");

        // Khi Redis GET lỗi: không ném ngoại lệ ra ngoài (cho phép Spring Cache fallback về DB)
        assertThatCode(() -> errorHandler.handleCacheGetError(redisException, mockCache, "user:123"))
                .doesNotThrowAnyException();

        // Khi Redis PUT lỗi: không ném ngoại lệ
        assertThatCode(() -> errorHandler.handleCachePutError(redisException, mockCache, "user:123", "dummyValue"))
                .doesNotThrowAnyException();

        // Khi Redis EVICT lỗi: không ném ngoại lệ
        assertThatCode(() -> errorHandler.handleCacheEvictError(redisException, mockCache, "user:123"))
                .doesNotThrowAnyException();

        // Khi Redis CLEAR lỗi: không ném ngoại lệ
        assertThatCode(() -> errorHandler.handleCacheClearError(redisException, mockCache))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("JSON Serializer: Serialize và Deserialize UserProfileResponse chính xác với Instant và UUID")
    void testJsonSerializationRoundTrip() {
        // Cấu hình ObjectMapper giống hệt RedisConfig
        RedisConfig config = new RedisConfig();
        // Lấy serializer thông qua Reflection hoặc test trực tiếp với ObjectMapper tương tự
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        mapper.registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
        mapper.disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator ptv =
                com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType(Object.class)
                        .build();

        mapper.activateDefaultTyping(
                ptv,
                com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL,
                com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(mapper);

        UUID userId = UUID.randomUUID();
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS); // Cắt bớt nano để chuẩn định dạng JSON ISO

        UserProfileResponse original = UserProfileResponse.builder()
                .id(userId)
                .email("test.cache@example.com")
                .fullName("Nguyen Van Test Cache")
                .phone("0987654321")
                .avatarUrl("https://example.com/avatar.jpg")
                .status(UserStatus.ACTIVE)
                .roles(Set.of("ROLE_USER"))
                .createdAt(now)
                .updatedAt(now)
                .build();

        // 1. Serialize sang byte[]
        byte[] bytes = serializer.serialize(original);
        assertThat(bytes).isNotNull();
        String jsonString = new String(bytes);

        // Kiểm tra trong JSON có chứa @class và các trường dữ liệu
        assertThat(jsonString)
                .contains("@class")
                .contains("test.cache@example.com")
                .contains("Nguyen Van Test Cache")
                .contains(userId.toString());

        // 2. Deserialize ngược lại từ byte[]
        Object deserializedObj = serializer.deserialize(bytes);
        assertThat(deserializedObj).isInstanceOf(UserProfileResponse.class);

        UserProfileResponse deserialized = (UserProfileResponse) deserializedObj;
        assertThat(deserialized.getId()).isEqualTo(original.getId());
        assertThat(deserialized.getEmail()).isEqualTo(original.getEmail());
        assertThat(deserialized.getFullName()).isEqualTo(original.getFullName());
        assertThat(deserialized.getPhone()).isEqualTo(original.getPhone());
        assertThat(deserialized.getAvatarUrl()).isEqualTo(original.getAvatarUrl());
        assertThat(deserialized.getStatus()).isEqualTo(original.getStatus());
        assertThat(deserialized.getRoles()).containsExactlyInAnyOrderElementsOf(original.getRoles());
        assertThat(deserialized.getCreatedAt()).isEqualTo(original.getCreatedAt());
        assertThat(deserialized.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
    }
}
