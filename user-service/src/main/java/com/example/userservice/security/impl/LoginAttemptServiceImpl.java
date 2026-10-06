package com.example.userservice.security.impl;

import com.example.userservice.security.LoginAttemptService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * ==============================================================================
 * TRIỂN KHAI DỊCH VỤ CHỐNG TẤN CÔNG BRUTE-FORCE VỚI REDIS
 * ------------------------------------------------------------------------------
 * Lưu số lần đăng nhập thất bại theo từng email vào Redis với TTL là 15 phút.
 * Tự động chịu lỗi (Graceful degradation) nếu Redis tạm thời mất kết nối.
 * ==============================================================================
 */
@Service
public class LoginAttemptServiceImpl implements LoginAttemptService {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptServiceImpl.class);

    public static final int MAX_ATTEMPTS = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "login:attempts:";

    private final org.springframework.data.redis.core.RedisOperations<String, String> redisOperations;

    public LoginAttemptServiceImpl(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisOperations = redisTemplateProvider.getIfAvailable();
    }

    public LoginAttemptServiceImpl(org.springframework.data.redis.core.RedisOperations<String, String> redisOperations) {
        this.redisOperations = redisOperations;
    }

    @Override
    public boolean isBlocked(String email) {
        if (redisOperations == null || email == null) {
            return false;
        }
        try {
            String key = buildKey(email);
            String value = redisOperations.opsForValue().get(key);
            if (value != null) {
                int attempts = Integer.parseInt(value);
                return attempts >= MAX_ATTEMPTS;
            }
        } catch (Exception ex) {
            log.warn("Không thể kiểm tra brute-force từ Redis cho email [{}]: {}", email, ex.getMessage());
        }
        return false;
    }

    @Override
    public void recordFailedAttempt(String email) {
        if (redisOperations == null || email == null) {
            return;
        }
        try {
            String key = buildKey(email);
            Long attempts = redisOperations.opsForValue().increment(key);
            if (attempts != null && attempts == 1) {
                redisOperations.expire(key, LOCK_DURATION);
            }
            log.info("Email [{}] đăng nhập thất bại lần thứ: {}", email, attempts);
        } catch (Exception ex) {
            log.warn("Không thể ghi nhận lần đăng nhập sai vào Redis cho email [{}]: {}", email, ex.getMessage());
        }
    }

    @Override
    public void resetAttempts(String email) {
        if (redisOperations == null || email == null) {
            return;
        }
        try {
            String key = buildKey(email);
            redisOperations.delete(key);
        } catch (Exception ex) {
            log.warn("Không thể xóa số lần nhập sai từ Redis cho email [{}]: {}", email, ex.getMessage());
        }
    }

    private String buildKey(String email) {
        return KEY_PREFIX + email.toLowerCase().trim();
    }
}
