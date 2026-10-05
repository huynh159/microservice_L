package com.example.common.dto.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EventEnvelopeTest {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    void testUserCreatedEventEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UserCreatedEvent payload = UserCreatedEvent.builder()
                .userId(userId)
                .email("test@example.com")
                .fullName("Nguyen Van A")
                .build();

        EventEnvelope<UserCreatedEvent> envelope = EventEnvelope.<UserCreatedEvent>builder()
                .eventId(UUID.randomUUID().toString())
                .eventType("UserCreatedEvent")
                .version(1)
                .occurredAt(Instant.now())
                .source("user-service")
                .payload(payload)
                .build();

        assertThat(envelope.getEventType()).isEqualTo("UserCreatedEvent");
        assertThat(envelope.getSource()).isEqualTo("user-service");
        assertThat(envelope.getPayload().getEmail()).isEqualTo("test@example.com");
        assertThat(envelope.getPayload().getUserId()).isEqualTo(userId);

        String json = objectMapper.writeValueAsString(envelope);
        assertThat(json).contains("\"eventType\":\"UserCreatedEvent\"");
        assertThat(json).contains("\"email\":\"test@example.com\"");
        assertThat(json).doesNotContain("password");
    }
}
