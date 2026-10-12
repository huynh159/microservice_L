package com.example.userservice.scheduler;

import com.example.userservice.entity.OutboxEvent;
import com.example.userservice.repository.OutboxEventRepository;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxEventProcessorTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaOperations<String, String> kafkaTemplate;

    private OutboxEventProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new OutboxEventProcessor(outboxEventRepository, kafkaTemplate);
        ReflectionTestUtils.setField(processor, "topicName", "user-events");
        ReflectionTestUtils.setField(processor, "batchSize", 50);
    }

    @Test
    @DisplayName("Gửi Kafka thành công: Đánh dấu published_at và lưu lại DB")
    void processOutboxEvents_Success_MarksAsPublished() {
        UUID eventId = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();
        OutboxEvent event = new OutboxEvent("USER", userId, "UserCreatedEvent", "{\"test\":\"payload\"}");
        event.setId(eventId);

        when(outboxEventRepository.findUnpublishedEventsForUpdate(50))
                .thenReturn(List.of(event));

        RecordMetadata metadata = new RecordMetadata(new TopicPartition("user-events", 0), 0, 0, 0, 0, 0);
        SendResult<String, String> sendResult = new SendResult<>(null, metadata);
        CompletableFuture<SendResult<String, String>> future = CompletableFuture.completedFuture(sendResult);

        when(kafkaTemplate.send(eq("user-events"), eq(userId), eq("{\"test\":\"payload\"}")))
                .thenReturn(future);

        processor.processOutboxEvents();

        assertThat(event.getPublishedAt()).isNotNull();
        verify(kafkaTemplate).send("user-events", userId, "{\"test\":\"payload\"}");
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Kafka tạm thời sập: Giữ nguyên published_at = null để quét lại sau, không làm mất sự kiện")
    void processOutboxEvents_KafkaFails_KeepsEventUnpublished() {
        UUID eventId = UUID.randomUUID();
        String userId = UUID.randomUUID().toString();
        OutboxEvent event = new OutboxEvent("USER", userId, "UserCreatedEvent", "{\"test\":\"payload\"}");
        event.setId(eventId);

        when(outboxEventRepository.findUnpublishedEventsForUpdate(50))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Kafka Broker unavailable connection refused"));

        when(kafkaTemplate.send(eq("user-events"), eq(userId), eq("{\"test\":\"payload\"}")))
                .thenReturn(failedFuture);

        processor.processOutboxEvents();

        // published_at vẫn là null để lần quét tiếp theo được phát lại
        assertThat(event.getPublishedAt()).isNull();
        verify(outboxEventRepository, never()).save(event);
    }

    @Test
    @DisplayName("Không có sự kiện chưa phát: Không gọi Kafka và không cập nhật gì")
    void processOutboxEvents_NoEvents_DoesNothing() {
        when(outboxEventRepository.findUnpublishedEventsForUpdate(50))
                .thenReturn(Collections.emptyList());

        processor.processOutboxEvents();

        verifyNoInteractions(kafkaTemplate);
        verify(outboxEventRepository, never()).save(any());
    }
}
