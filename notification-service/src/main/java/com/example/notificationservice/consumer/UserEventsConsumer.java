package com.example.notificationservice.consumer;

import com.example.common.dto.event.UserCreatedEvent;
import com.example.notificationservice.service.NotificationProcessService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ==============================================================================
 * UserEventsConsumer - Lắng nghe topic `user-events` (BƯỚC 4.1, 4.2 & 4.3)
 * ==============================================================================
 * 1. Lắng nghe topic `user-events` (group `notification-service-group`).
 * 2. Phân tích envelope của message (eventId, eventType, payload).
 * 3. Khi gặp message lỗi cú pháp / schema: ném IllegalArgumentException (fatal error)
 *    để Kafka ErrorHandler tự động chuyển hướng ngay sang Dead Letter Topic `user-events.DLT`.
 * 4. Khi nhận `UserCreatedEvent` hợp lệ:
 *    - Gọi NotificationProcessService xử lý Idempotent (chống trùng lặp).
 *    - Gửi email chào mừng qua EmailService & lưu notification_log.
 *    - Lưu processed_events để đảm bảo cùng 1 eventId chỉ tạo 1 email duy nhất.
 *    - Nếu gặp lỗi mạng / SMTP tạm thời: ném exception để ErrorHandler tự động
 *      retry có backoff. Nếu vượt ngưỡng số lần thử lại -> chuyển sang DLT.
 * 5. Bỏ qua các sự kiện khác (UserUpdatedEvent, UserDeletedEvent) có log đầy đủ.
 * ==============================================================================
 */
@Component
public class UserEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventsConsumer.class);

    private final ObjectMapper objectMapper;
    private final NotificationProcessService notificationProcessService;

    public UserEventsConsumer(ObjectMapper objectMapper) {
        this(objectMapper, null);
    }

    @Autowired
    public UserEventsConsumer(
            ObjectMapper objectMapper,
            NotificationProcessService notificationProcessService
    ) {
        this.objectMapper = objectMapper;
        this.notificationProcessService = notificationProcessService;
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

        String eventId;
        String eventType;
        UserCreatedEvent userCreatedEvent = null;

        // Phân tách pha 1: Parse JSON & Envelope (Lỗi ở đây là Fatal -> Ném IllegalArgumentException -> Vào DLT ngay)
        try {
            JsonNode rootNode = objectMapper.readTree(rawMessage);

            if (rootNode == null || !rootNode.hasNonNull("eventId") || !rootNode.hasNonNull("eventType")) {
                log.error("Message thiếu trường bắt buộc trong envelope (eventId hoặc eventType): {}", rawMessage);
                throw new IllegalArgumentException("Sự kiện thiếu thông tin envelope bắt buộc: " + rawMessage);
            }

            eventId = rootNode.get("eventId").asText();
            eventType = rootNode.get("eventType").asText();

            if ("UserCreatedEvent".equals(eventType)) {
                JsonNode payloadNode = rootNode.get("payload");
                if (payloadNode == null || payloadNode.isNull()) {
                    log.error("Sự kiện UserCreatedEvent thiếu payload: eventId [{}]", eventId);
                    throw new IllegalArgumentException("UserCreatedEvent thiếu payload: " + eventId);
                }

                userCreatedEvent = objectMapper.treeToValue(payloadNode, UserCreatedEvent.class);
                if (userCreatedEvent == null || userCreatedEvent.getUserId() == null || userCreatedEvent.getEmail() == null) {
                    log.error("Payload của UserCreatedEvent không hợp lệ (thiếu userId hoặc email): eventId [{}]", eventId);
                    throw new IllegalArgumentException("Payload UserCreatedEvent thiếu userId hoặc email: " + eventId);
                }
            } else {
                log.info("Bỏ qua sự kiện không cần gửi thông báo: eventType [{}], eventId [{}]", eventType, eventId);
                return;
            }

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            log.error("Không thể phân tích cú pháp message JSON: {}. Chi tiết lỗi: {}", rawMessage, e.getMessage());
            throw new IllegalArgumentException("Lỗi phân tích cú pháp JSON message: " + e.getMessage(), e);
        }

        // Pha 2: Xử lý nghiệp vụ sự kiện (Nếu có lỗi tạm thời như SMTP, ngoại lệ được giữ nguyên để Kafka retry)
        log.info("Nhận sự kiện UserCreatedEvent thành công: eventId [{}], userId [{}], email [{}], fullName [{}]",
                eventId, userCreatedEvent.getUserId(), userCreatedEvent.getEmail(), userCreatedEvent.getFullName());

        handleUserCreated(eventId, userCreatedEvent);
    }

    protected void handleUserCreated(String eventId, UserCreatedEvent event) {
        if (notificationProcessService != null) {
            notificationProcessService.processUserCreatedEvent(eventId, event);
        } else {
            log.debug("NotificationProcessService chưa được thiết lập. Bỏ qua gửi thông báo.");
        }
    }
}
