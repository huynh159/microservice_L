package com.example.userservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * ==============================================================================
 * CẤU HÌNH TỰ ĐỘNG THEO DÕI THỜI GIAN THỰC THỂ (JPA AUDITING CONFIGURATION)
 * ------------------------------------------------------------------------------
 * Kích hoạt tính năng JPA Auditing của Spring Data JPA.
 * Giúp các trường đánh dấu @CreatedDate và @LastModifiedDate trong các Entity
 * (như User.createdAt, User.updatedAt) tự động được gán giá trị thời gian hiện tại
 * khi INSERT hoặc UPDATE mà lập trình viên không cần set thủ công trong code.
 * ==============================================================================
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}

