package com.example.userservice.service;

import com.example.common.dto.event.UserCreatedEvent;
import com.example.userservice.entity.OutboxEvent;
import com.example.userservice.repository.OutboxEventRepository;
import com.example.userservice.service.impl.OutboxServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxServiceImplTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    private ObjectMapper objectMapper;
    private OutboxServiceImpl outboxService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        outboxService = new OutboxServiceImpl(outboxEventRepository, objectMapper);
    }

    @Test
    @DisplayName("Lưu Outbox Event: Tạo đúng EventEnvelope chuẩn Mục 3.5 và payload không chứa thông tin nhạy cảm")
    void saveEvent_Success_CreatesStandardEventEnvelope() throws Exception {
        UUID userId = UUID.randomUUID();
        UserCreatedEvent payload = new UserCreatedEvent(userId, "test@example.com", "Nguyen Van A");

        when(outboxEventRepository.save(any(OutboxEvent.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OutboxEvent saved = outboxService.saveEvent("USER", userId.toString(), "UserCreatedEvent", payload);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getAggregateType()).isEqualTo("USER");
        assertThat(saved.getAggregateId()).isEqualTo(userId.toString());
        assertThat(saved.getEventType()).isEqualTo("UserCreatedEvent");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getPublishedAt()).isNull();

        // Kiểm tra cấu trúc JSON của EventEnvelope trong payload
        JsonNode jsonNode = objectMapper.readTree(saved.getPayload());
        assertThat(jsonNode.get("eventId").asText()).isEqualTo(saved.getId().toString());
        assertThat(jsonNode.get("eventType").asText()).isEqualTo("UserCreatedEvent");
        assertThat(jsonNode.get("version").asInt()).isEqualTo(1);
        assertThat(jsonNode.get("source").asText()).isEqualTo("user-service");
        assertThat(jsonNode.get("occurredAt")).isNotNull();

        // Kiểm tra payload bên trong envelope
        JsonNode payloadNode = jsonNode.get("payload");
        assertThat(payloadNode.get("userId").asText()).isEqualTo(userId.toString());
        assertThat(payloadNode.get("email").asText()).isEqualTo("test@example.com");
        assertThat(payloadNode.get("fullName").asText()).isEqualTo("Nguyen Van A");

        // BẢO MẬT: Đảm bảo tuyệt đối không có mật khẩu hay token nhạy cảm
        assertThat(payloadNode.has("password")).isFalse();
        assertThat(payloadNode.has("passwordHash")).isFalse();
        assertThat(payloadNode.has("token")).isFalse();

        ArgumentCaptor<OutboxEvent> captor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository).save(captor.capture());
        assertThat(captor.getValue().getAggregateId()).isEqualTo(userId.toString());
    }
}
