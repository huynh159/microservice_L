package com.example.userservice.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

/**
 * ==============================================================================
 * KIẾN THỨC CỐT LÕI: CẤU HÌNH REDIS CACHE & TÍNH CHỊU LỖI (RESILIENCE)
 * ------------------------------------------------------------------------------
 * 1. Spring Cache Abstraction:
 *    - Sử dụng các annotation chuẩn (@Cacheable, @CacheEvict, @CachePut) giúp
 *      tách rời hoàn toàn nghiệp vụ khỏi việc gọi lệnh Redis thủ công.
 *
 * 2. Cache-Aside Pattern (Lazy Loading):
 *    - Đọc: Kiểm tra Redis trước -> Trúng (Hit) thì trả về ngay; Trượt (Miss) thì
 *      đọc từ Database -> Ghi vào Redis với hạn sử dụng TTL 10 phút.
 *    - Ghi: Khi cập nhật hoặc xóa dữ liệu, xóa (Evict) cache tương ứng trong Redis
 *      để tránh trường hợp client đọc phải dữ liệu cũ (Stale Data).
 *
 * 3. JSON Serializer (GenericJackson2JsonRedisSerializer):
 *    - Mặc định Spring dùng JdkSerializationRedisSerializer (lưu dạng nhị phân hex,
 *      không thể đọc được bằng redis-cli / Redis Insight, dễ lỗi ClassCastException).
 *    - Chúng ta chuyển sang JSON có kèm thông tin kiểu dữ liệu (@class), vừa dễ debug,
 *      vừa tương thích đa ngôn ngữ, hỗ trợ Java 8 Date/Time (Instant, LocalDateTime).
 *
 * 4. Graceful Degradation (Chống sập hệ thống khi Redis gặp sự cố):
 *    - Mặc định nếu Redis timeout hoặc sập, Spring Cache sẽ ném RuntimeException làm
 *      chết toàn bộ HTTP request (HTTP 500).
 *    - Bằng cách override errorHandler(), ta bắt mọi lỗi kết nối Redis, ghi log cảnh
 *      báo (log.warn), và Spring Cache sẽ tự động fallback sang đọc trực tiếp Database!
 * ==============================================================================
 */
@Configuration
@EnableCaching
public class RedisConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(RedisConfig.class);

    /**
     * Tên Cache vùng chứa thông tin hồ sơ người dùng (User Profile)
     */
    public static final String USER_PROFILE_CACHE = "user_profiles";

    /**
     * Thời gian sống mặc định của Cache Profile: 10 phút (theo đặc tả Bước 3.6)
     */
    public static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(10);

    /**
     * Tạo ObjectMapper riêng biệt cho Redis Serializer:
     * - Đăng ký JavaTimeModule: Xử lý các trường thời gian hiện đại như Instant, LocalDateTime.
     * - Tắt ghi ngày tháng dưới dạng timestamp số: Lưu thành chuỗi ISO-8601 chuẩn quốc tế.
     * - Kích hoạt Default Typing: Lưu metadata @class vào JSON để khi đọc ra có thể
     *   chuyển đổi (deserialize) chính xác về đúng DTO class ban đầu.
     */
    private ObjectMapper createRedisObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        objectMapper.activateDefaultTyping(
                ptv,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        return objectMapper;
    }

    /**
     * Cấu hình RedisCacheManager quản lý vòng đời và định dạng dữ liệu của Spring Cache:
     * - Key serializer: Chuỗi String thông thường (ví dụ: "user_profiles::0c8e...").
     * - Value serializer: Định dạng JSON UTF-8 dễ đọc, hỗ trợ Instant/UUID.
     * - TTL: 10 phút (hết 10 phút Redis tự động thu hồi bộ nhớ).
     * - disableCachingNullValues: Không lưu giá trị null vào cache để tránh lãng phí RAM.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(createRedisObjectMapper());

        RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_CACHE_TTL)
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(cacheConfig)
                .build();
    }

    /**
     * RedisTemplate dùng chung cho các tác vụ ghi/đọc thủ công với Value dạng JSON
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(createRedisObjectMapper());

        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();
        return template;
    }

    /**
     * BỘ XỬ LÝ LỖI CACHE TÙY BIẾN (Custom Cache Error Handler):
     * Cung cấp cơ chế tự phục hồi (Graceful Degradation):
     * - Khi Redis sập hoặc gián đoạn mạng, không để exception bắn lên Controller làm người dùng bị lỗi 500.
     * - Bắt lỗi, ghi log cảnh báo warn, cho phép luồng tiếp tục gọi thẳng vào Database.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis GET thất bại cho cache '{}', key '{}': {}. Bỏ qua cache và truy vấn database.",
                        cache != null ? cache.getName() : "unknown", key, exception.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Redis PUT thất bại cho cache '{}', key '{}': {}. Dữ liệu vẫn được lưu DB an toàn.",
                        cache != null ? cache.getName() : "unknown", key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Redis EVICT thất bại cho cache '{}', key '{}': {}.",
                        cache != null ? cache.getName() : "unknown", key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Redis CLEAR thất bại cho cache '{}': {}.",
                        cache != null ? cache.getName() : "unknown", exception.getMessage());
            }
        };
    }
}
