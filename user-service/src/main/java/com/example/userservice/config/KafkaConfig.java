package com.example.userservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ==============================================================================
 * CẤU HÌNH KAFKA PRODUCER & SCHEDULING (BƯỚC 3.7)
 * ------------------------------------------------------------------------------
 * KIẾN THỨC CỐT LÕI:
 * 1. @EnableScheduling: Kích hoạt bộ lập lịch nền (Task Scheduler) để chạy job quét Outbox định kỳ.
 * 2. NewTopic Bean: Spring Kafka tự động khai báo và tạo topic "user-events" trên broker nếu chưa có.
 *    - Partitions = 3: Cho phép mở rộng song song tối đa 3 consumers trong cùng group.
 *    - Replicas = 1: Phù hợp môi trường phát triển (Production tăng lên 3 để chịu lỗi).
 * ==============================================================================
 */
@Configuration
@EnableScheduling
public class KafkaConfig {

    @Value("${kafka.topics.user-events:user-events}")
    private String userEventsTopic;

    @Bean
    public NewTopic userEventsTopic() {
        return TopicBuilder.name(userEventsTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
