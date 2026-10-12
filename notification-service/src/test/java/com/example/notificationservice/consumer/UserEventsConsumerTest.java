package com.example.notificationservice.consumer;

import com.example.common.dto.event.UserCreatedEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserEventsConsumerTest {

    private ObjectMapper objectMapper;
    private UserEventsConsumer consumer;
    private AtomicBoolean handlerInvoked;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        handlerInvoked = new AtomicBoolean(false);

        consumer = new UserEventsConsumer(objectMapper) {
            @Override
            protected void handleUserCreated(String eventId, UserCreatedEvent event) {
                handlerInvoked.set(true);
            }
        };
    }

    @Test
    @DisplayName("Nghiệm thu sự kiện UserCreatedEvent hợp lệ -> Parse thành công và gọi handler")
    void testConsumeValidUserCreatedEvent_Success() {
        String eventId = UUID.randomUUID().toString();
        UUID userId = UUID.randomUUID();
        String json = String.format("""
                {
                  "eventId": "%s",
                  "eventType": "UserCreatedEvent",
                  "version": 1,
                  "occurredAt": "2026-10-12T04:00:00Z",
                  "source": "user-service",
                  "payload": {
                    "userId": "%s",
                    "email": "user@example.com",
                    "fullName": "Nguyễn Văn Test"
                  }
                }
                """, eventId, userId);

        consumer.consumeUserEvent(json, userId.toString(), "user-events", 0, 1L);

        assertThat(handlerInvoked.get()).isTrue();
    }

    @Test
    @DisplayName("Nghiệm thu các loại sự kiện khác (UserUpdatedEvent, UserDeletedEvent) -> Bỏ qua an toàn, không ném lỗi")
    void testConsumeOtherEventTypes_IgnoredSafely() {
        String updateJson = """
                {
                  "eventId": "event-update-123",
                  "eventType": "UserUpdatedEvent",
                  "version": 1,
                  "occurredAt": "2026-10-12T04:00:00Z",
                  "source": "user-service",
                  "payload": { "userId": "123e4567-e89b-12d3-a456-426614174000", "fullName": "Updated Name" }
                }
                """;

        consumer.consumeUserEvent(updateJson, "123e4567-e89b-12d3-a456-426614174000", "user-events", 0, 2L);

        assertThat(handlerInvoked.get()).isFalse();

        String deleteJson = """
                {
                  "eventId": "event-delete-123",
                  "eventType": "UserDeletedEvent",
                  "version": 1,
                  "occurredAt": "2026-10-12T04:00:00Z",
                  "source": "user-service",
                  "payload": { "userId": "123e4567-e89b-12d3-a456-426614174000" }
                }
                """;

        consumer.consumeUserEvent(deleteJson, "123e4567-e89b-12d3-a456-426614174000", "user-events", 0, 3L);

        assertThat(handlerInvoked.get()).isFalse();
    }

    @Test
    @DisplayName("Nghiệm thu sự kiện JSON sai định dạng -> Ném ngoại lệ để ErrorHandler đẩy sang DLT")
    void testConsumeMalformedJson_ThrowsExceptionForDLT() {
        String invalidJson = "{malformed-json-here";

        assertThatThrownBy(() -> consumer.consumeUserEvent(invalidJson, "key", "user-events", 0, 4L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Lỗi phân tích cú pháp JSON");

        assertThat(handlerInvoked.get()).isFalse();
    }

    @Test
    @DisplayName("Nghiệm thu message rỗng hoặc null -> Ném ngoại lệ để ErrorHandler đẩy sang DLT")
    void testConsumeBlankOrNullMessage_ThrowsExceptionForDLT() {
        assertThatThrownBy(() -> consumer.consumeUserEvent("", "key", "user-events", 0, 5L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kafka message body không được rỗng");

        assertThatThrownBy(() -> consumer.consumeUserEvent("   ", "key", "user-events", 0, 6L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Kafka message body không được rỗng");

        assertThatThrownBy(() -> consumer.consumeUserEvent(null, "key", "user-events", 0, 7L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Nghiệm thu envelope thiếu trường bắt buộc -> Ném ngoại lệ để ErrorHandler đẩy sang DLT")
    void testConsumeMissingEnvelopeFields_ThrowsExceptionForDLT() {
        String missingEventId = """
                {
                  "eventType": "UserCreatedEvent",
                  "payload": { "userId": "123e4567-e89b-12d3-a456-426614174000", "email": "a@b.com" }
                }
                """;

        assertThatThrownBy(() -> consumer.consumeUserEvent(missingEventId, "key", "user-events", 0, 8L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sự kiện thiếu thông tin envelope bắt buộc");

        String missingEventType = """
                {
                  "eventId": "event-123",
                  "payload": { "userId": "123e4567-e89b-12d3-a456-426614174000", "email": "a@b.com" }
                }
                """;

        assertThatThrownBy(() -> consumer.consumeUserEvent(missingEventType, "key", "user-events", 0, 9L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sự kiện thiếu thông tin envelope bắt buộc");
    }

    @Test
    @DisplayName("Nghiệm thu UserCreatedEvent thiếu payload -> Ném ngoại lệ để ErrorHandler đẩy sang DLT")
    void testConsumeUserCreatedEventMissingPayload_ThrowsExceptionForDLT() {
        String missingPayload = """
                {
                  "eventId": "event-123",
                  "eventType": "UserCreatedEvent"
                }
                """;

        assertThatThrownBy(() -> consumer.consumeUserEvent(missingPayload, "key", "user-events", 0, 10L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("UserCreatedEvent thiếu payload");
    }

    @Test
    @DisplayName("Nghiệm thu UserCreatedEvent payload thiếu email hoặc userId -> Ném ngoại lệ")
    void testConsumeUserCreatedEventInvalidPayloadFields_ThrowsExceptionForDLT() {
        String missingEmail = """
                {
                  "eventId": "event-123",
                  "eventType": "UserCreatedEvent",
                  "payload": { "userId": "123e4567-e89b-12d3-a456-426614174000", "fullName": "Nguyen Van A" }
                }
                """;

        assertThatThrownBy(() -> consumer.consumeUserEvent(missingEmail, "key", "user-events", 0, 11L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Payload UserCreatedEvent thiếu userId hoặc email");
    }
}
