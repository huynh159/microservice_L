package com.example.notificationservice.consumer;

import com.example.common.dto.event.UserCreatedEvent;
import com.example.notificationservice.entity.NotificationLog;
import com.example.notificationservice.entity.NotificationStatus;
import com.example.notificationservice.entity.NotificationType;
import com.example.notificationservice.repository.NotificationLogRepository;
import com.example.notificationservice.service.EmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * ==============================================================================
 * UserEventsConsumer - Lắng nghe topic `user-events` (BƯỚC 4.1 & BƯỚC 4.2)
 * ==============================================================================
 * 1. Lắng nghe topic `user-events` (group `notification-service-group`).
 * 2. Phân tích envelope của message (eventId, eventType, payload).
 * 3. Khi nhận `UserCreatedEvent`:
 *    - Ghi nhận `notification_log` trạng thái PENDING.
 *    - Gửi email chào mừng qua EmailService (Thymeleaf welcome.html + SMTP Mailpit).
 *    - Cập nhật trạng thái `notification_log` thành SENT (hoặc FAILED nếu lỗi).
 * 4. Bỏ qua các sự kiện khác (UserUpdatedEvent, UserDeletedEvent) có log đầy đủ.
 * 5. Khi gặp message lỗi cú pháp / schema: ném exception để Kafka ErrorHandler
 *    tự động chuyển hướng sang Dead Letter Topic `user-events.DLT`.
 * ==============================================================================
 */
@Component
public class UserEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventsConsumer.class);

    private final ObjectMapper objectMapper;
    private final EmailService emailService;
    private final NotificationLogRepository notificationLogRepository;

    public UserEventsConsumer(ObjectMapper objectMapper) {
        this(objectMapper, null, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public UserEventsConsumer(
            ObjectMapper objectMapper,
            EmailService emailService,
            NotificationLogRepository notificationLogRepository
    ) {
        this.objectMapper = objectMapper;
        this.emailService = emailService;
        this.notificationLogRepository = notificationLogRepository;
    }

    @KafkaListener(
            topics = "${kafka.topics.user-events:user-events}",
            groupId = "${spring.kafka.consumer.group-id:notification-service-group}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeUserEvent(
            @Payload String rawMessage,
            @Header(value = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(value = KafkaHeaders.RECEIVED_TOPIC, required = false) String topic,
            @Header(value = KafkaHeaders.RECEIVED_PARTITION, required = false) Integer partition,
            @Header(value = KafkaHeaders.OFFSET, required = false) Long offset
    ) {
        log.debug("Nhận raw message từ topic [{}], partition [{}], offset [{}], key [{}]: {}",
                topic, partition, offset, key, rawMessage);

        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            log.error("Nhận được message rỗng. Đẩy sang DLT.");
            throw new IllegalArgumentException("Kafka message body không được rỗng");
        }

        try {
            JsonNode rootNode = objectMapper.readTree(rawMessage);

            if (rootNode == null || !rootNode.hasNonNull("eventId") || !rootNode.hasNonNull("eventType")) {
                log.error("Message thiếu trường bắt buộc trong envelope (eventId hoặc eventType): {}", rawMessage);
                throw new IllegalArgumentException("Sự kiện thiếu thông tin envelope bắt buộc: " + rawMessage);
            }

            String eventId = rootNode.get("eventId").asText();
            String eventType = rootNode.get("eventType").asText();

            if ("UserCreatedEvent".equals(eventType)) {
                JsonNode payloadNode = rootNode.get("payload");
                if (payloadNode == null || payloadNode.isNull()) {
                    log.error("Sự kiện UserCreatedEvent thiếu payload: eventId [{}]", eventId);
                    throw new IllegalArgumentException("UserCreatedEvent thiếu payload: " + eventId);
                }

                UserCreatedEvent userCreatedEvent = objectMapper.treeToValue(payloadNode, UserCreatedEvent.class);
                if (userCreatedEvent == null || userCreatedEvent.getUserId() == null || userCreatedEvent.getEmail() == null) {
                    log.error("Payload của UserCreatedEvent không hợp lệ (thiếu userId hoặc email): eventId [{}]", eventId);
                    throw new IllegalArgumentException("Payload UserCreatedEvent thiếu userId hoặc email: " + eventId);
                }

                log.info("Nhận sự kiện UserCreatedEvent thành công: eventId [{}], userId [{}], email [{}], fullName [{}]",
                        eventId, userCreatedEvent.getUserId(), userCreatedEvent.getEmail(), userCreatedEvent.getFullName());

                handleUserCreated(eventId, userCreatedEvent);
            } else {
                log.info("Bỏ qua sự kiện không cần gửi thông báo: eventType [{}], eventId [{}]", eventType, eventId);
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Không thể phân tích cú pháp message JSON: {}. Chi tiết lỗi: {}", rawMessage, e.getMessage());
            throw new IllegalArgumentException("Lỗi phân tích cú pháp JSON message: " + e.getMessage(), e);
        }
    }

    protected void handleUserCreated(String eventId, UserCreatedEvent event) {
        if (emailService == null || notificationLogRepository == null) {
            log.debug("EmailService hoặc NotificationLogRepository chưa được thiết lập. Bỏ qua ghi log & gửi mail.");
            return;
        }

        NotificationLog logEntry = NotificationLog.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .recipient(event.getEmail())
                .type(NotificationType.WELCOME_EMAIL)
                .status(NotificationStatus.PENDING)
                .createdAt(Instant.now())
                .build();

        notificationLogRepository.save(logEntry);

        try {
            emailService.sendWelcomeEmail(event.getEmail(), event.getFullName());
            logEntry.setStatus(NotificationStatus.SENT);
            notificationLogRepository.save(logEntry);
            log.info("Đã cập nhật notification_log thành công trạng thái SENT cho eventId [{}]", eventId);
        } catch (Exception e) {
            logEntry.setStatus(NotificationStatus.FAILED);
            logEntry.setError(e.getMessage());
            notificationLogRepository.save(logEntry);
            log.error("Gửi email thất bại cho eventId [{}]. Đã lưu trạng thái FAILED vào notification_log: {}",
                    eventId, e.getMessage());
            throw e;
        }
    }
}
