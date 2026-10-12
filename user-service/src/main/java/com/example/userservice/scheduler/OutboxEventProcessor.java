package com.example.userservice.scheduler;

import com.example.userservice.entity.OutboxEvent;
import com.example.userservice.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * ==============================================================================
 * BỘ QUÉT VÀ PHÁT SỰ KIỆN OUTBOX LÊN KAFKA (OUTBOX PROCESSOR) - BƯỚC 3.7
 * ------------------------------------------------------------------------------
 * KIẾN THỨC CỐT LÕI:
 * 1. Định kỳ quét bảng outbox_events theo chu kỳ cấu hình (mặc định 2 giây).
 * 2. An toàn phân tán (Distributed Concurrency Safety):
 *    - Sử dụng `FOR UPDATE SKIP LOCKED` qua query native repository.
 *    - Dù có bao nhiêu instance chạy cùng lúc thì các bản ghi cũng không bị khóa chéo hay phát trùng.
 * 3. Message Key Partitioning:
 *    - Key = aggregateId (userId) giúp Kafka điều hướng mọi sự kiện của CÙNG MỘT USER
 *      vào CÙNG MỘT PARTITION, đảm bảo thứ tự thời gian chuẩn xác 100%!
 * 4. Khả năng chịu lỗi (Fault Tolerance & At-least-once Delivery):
 *    - Nếu Kafka Broker bị treo hoặc tạm thời mất kết nối, event không bị đánh dấu `published_at`
 *      và sẽ được thử lại trong chu kỳ quét kế tiếp. Dữ liệu KHÔNG BAO GIỜ bị thất lạc!
 * ==============================================================================
 */
@Component
@ConditionalOnProperty(name = "outbox.polling.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxEventProcessor {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventProcessor.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaOperations<String, String> kafkaTemplate;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${kafka.topics.user-events:user-events}")
    private String topicName;

    @Value("${outbox.polling.batch-size:50}")
    private int batchSize;

    public OutboxEventProcessor(
            OutboxEventRepository outboxEventRepository,
            KafkaOperations<String, String> kafkaTemplate
    ) {
        this(outboxEventRepository, kafkaTemplate, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    @Autowired
    public OutboxEventProcessor(
            OutboxEventRepository outboxEventRepository,
            KafkaOperations<String, String> kafkaTemplate,
            com.fasterxml.jackson.databind.ObjectMapper objectMapper
    ) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.polling.interval-ms:2000}")
    @Transactional
    public void processOutboxEvents() {
        List<OutboxEvent> unpublishedEvents = outboxEventRepository.findUnpublishedEventsForUpdate(batchSize);

        if (unpublishedEvents.isEmpty()) {
            return;
        }

        log.debug("Tìm thấy {} sự kiện Outbox chưa phát. Bắt đầu đẩy lên Kafka topic [{}]...",
                unpublishedEvents.size(), topicName);

        for (OutboxEvent event : unpublishedEvents) {
            String traceId = null;
            try {
                com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(event.getPayload());
                if (rootNode != null && rootNode.hasNonNull("traceId")) {
                    traceId = rootNode.get("traceId").asText();
                }
            } catch (Exception ignored) {
            }

            if (traceId != null && !traceId.isEmpty()) {
                org.slf4j.MDC.put("traceId", traceId);
            }

            try {
                kafkaTemplate.send(topicName, event.getAggregateId(), event.getPayload())
                        .get(5, TimeUnit.SECONDS);

                // Đánh dấu đã phát thành công
                event.markAsPublished();
                outboxEventRepository.save(event);

                log.info("Phát sự kiện Outbox lên Kafka thành công: id [{}], type [{}], key [{}], traceId [{}]",
                        event.getId(), event.getEventType(), event.getAggregateId(), traceId);
            } catch (Exception e) {
                log.error("Lỗi khi phát sự kiện Outbox id [{}], type [{}] lên Kafka. Giữ nguyên để thử lại sau. Chi tiết: {}",
                        event.getId(), event.getEventType(), e.getMessage());
                // Dừng vòng lặp của batch này nếu Kafka có vấn đề để tránh timeout liên tiếp
                break;
            } finally {
                org.slf4j.MDC.remove("traceId");
            }
        }
    }
}
