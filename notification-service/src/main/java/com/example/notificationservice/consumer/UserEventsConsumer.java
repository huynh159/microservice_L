package com.example.notificationservice.consumer;

import com.example.common.dto.event.UserCreatedEvent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * ==============================================================================
 * UserEventsConsumer - Lắng nghe topic `user-events` (BƯỚC 4.1)
 * ==============================================================================
 * Nhiệm vụ:
 * 1. Lắng nghe và tiêu thụ message từ topic `user-events` với group-id cố định.
 * 2. Phân tích envelope của sự kiện (eventId, eventType, payload).
 * 3. Chỉ lọc và xử lý `UserCreatedEvent` (để gửi email chào mừng ở bước 4.2).
 * 4. Bỏ qua các sự kiện khác (UserUpdatedEvent, UserDeletedEvent, v.v.) có log rõ ràng.
 * 5. Khi gặp message sai định dạng (malformed JSON hoặc thiếu envelope), ném ngoại lệ
 *    để DefaultErrorHandler tự động chuyển sang Dead Letter Topic `user-events.DLT`.
 * ==============================================================================
 */
@Component
public class UserEventsConsumer {

    private static final Logger log = LoggerFactory.getLogger(UserEventsConsumer.class);

    private final ObjectMapper objectMapper;

    public UserEventsConsumer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
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
        // Móc nối logic xử lý (ở bước 4.2 sẽ gọi EmailService và lưu notification_log)
        log.debug("Sẵn sàng gửi email chào mừng cho email [{}] với eventId [{}]", event.getEmail(), eventId);
    }
}
