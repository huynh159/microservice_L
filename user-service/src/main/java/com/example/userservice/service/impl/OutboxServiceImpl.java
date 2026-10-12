package com.example.userservice.service.impl;

import com.example.common.dto.event.EventEnvelope;
import com.example.userservice.entity.OutboxEvent;
import com.example.userservice.repository.OutboxEventRepository;
import com.example.userservice.service.OutboxService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * ==============================================================================
 * TRIỂN KHAI GHI OUTBOX EVENT TRONG CÙNG TRANSACTION (BƯỚC 3.7)
 * ------------------------------------------------------------------------------
 * KIẾN THỨC CỐT LÕI:
 * 1. Event Envelope chuẩn (Mục 3.5 của Kế hoạch):
 *    - Bao bọc mọi sự kiện phát ra từ user-service bằng cấu trúc thống nhất:
 *      { eventId, eventType, version: 1, occurredAt, source: "user-service", payload }
 *    - Giúp consumer (notification-service) dễ dàng phân tích metadata, theo dõi trace,
 *      và chống xử lý trùng (Idempotent Consumer) dựa trên `eventId`.
 * 2. Bảo mật dữ liệu phát tán (Zero Leakage):
 *    - Payload truyền vào đây chỉ chứa các thông tin công khai/cần thiết (userId, email, fullName).
 *    - Tuyệt đối không truyền password_hash, refreshToken hay mã bí mật vào payload!
 * ==============================================================================
 */
@Service
public class OutboxServiceImpl implements OutboxService {

    private static final Logger log = LoggerFactory.getLogger(OutboxServiceImpl.class);
    private static final String SOURCE_SERVICE = "user-service";
    private static final int EVENT_VERSION = 1;

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public OutboxServiceImpl(OutboxEventRepository outboxEventRepository, ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional // Kế thừa Transaction hiện tại của UserService
    public <T> OutboxEvent saveEvent(String aggregateType, String aggregateId, String eventType, T eventPayload) {
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        String traceId = org.slf4j.MDC.get("traceId");
        if (traceId == null || traceId.trim().isEmpty()) {
            traceId = UUID.randomUUID().toString();
        }

        // 1. Đóng gói vào Envelope chuẩn theo đặc tả Mục 3.5 kèm traceId
        EventEnvelope<T> envelope = EventEnvelope.<T>builder()
                .eventId(eventId.toString())
                .eventType(eventType)
                .version(EVENT_VERSION)
                .occurredAt(now)
                .source(SOURCE_SERVICE)
                .traceId(traceId)
                .payload(eventPayload)
                .build();

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            log.error("Lỗi chuyển đổi EventEnvelope sang JSON cho event [{}], aggregate [{}]", eventType, aggregateId, e);
            throw new IllegalStateException("Không thể serialize event payload sang JSON: " + eventType, e);
        }

        // 2. Khởi tạo OutboxEvent Entity
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setId(eventId);
        outboxEvent.setAggregateType(aggregateType);
        outboxEvent.setAggregateId(aggregateId);
        outboxEvent.setEventType(eventType);
        outboxEvent.setPayload(payloadJson);
        outboxEvent.setCreatedAt(now);
        outboxEvent.setPublishedAt(null); // Chưa phát lên Kafka

        OutboxEvent saved = outboxEventRepository.save(outboxEvent);
        log.info("Đã ghi Outbox Event thành công: id [{}], type [{}], aggregate [{}]",
                saved.getId(), eventType, aggregateId);

        return saved;
    }
}
