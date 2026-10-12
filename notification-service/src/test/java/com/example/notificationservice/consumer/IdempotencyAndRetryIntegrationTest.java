package com.example.notificationservice.consumer;

import com.example.notificationservice.entity.NotificationLog;
import com.example.notificationservice.entity.NotificationStatus;
import com.example.notificationservice.entity.ProcessedEvent;
import com.example.notificationservice.repository.NotificationLogRepository;
import com.example.notificationservice.repository.ProcessedEventRepository;
import com.example.notificationservice.service.EmailService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mail.MailSendException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("dev")
@EmbeddedKafka(
        partitions = 1,
        topics = {"user-events", "user-events.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class IdempotencyAndRetryIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @SpyBean
    private EmailService emailService;

    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "dlt-verifier-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        DefaultKafkaConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(
                consumerProps,
                new StringDeserializer(),
                new StringDeserializer()
        );
        dltConsumer = cf.createConsumer();
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(dltConsumer, "user-events.DLT");
    }

    @AfterEach
    void tearDown() {
        if (dltConsumer != null) {
            dltConsumer.close();
        }
    }

    @Test
    @DisplayName("Nghiệm thu Idempotency: Gửi 2 message cùng eventId -> Chỉ gửi 1 email và lưu 1 log SENT")
    void testDuplicateEventId_ProcessedOnlyOnce() throws Exception {
        String duplicateEventId = "event-dup-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String testEmail = "idempotent-" + UUID.randomUUID() + "@example.com";

        String json = String.format("""
                {
                  "eventId": "%s",
                  "eventType": "UserCreatedEvent",
                  "version": 1,
                  "occurredAt": "2026-10-12T05:00:00Z",
                  "source": "user-service",
                  "payload": {
                    "userId": "%s",
                    "email": "%s",
                    "fullName": "Idempotent User"
                  }
                }
                """, duplicateEventId, userId, testEmail);

        // Gửi lần 1
        kafkaTemplate.send("user-events", userId.toString(), json).get();
        Thread.sleep(2000);

        // Gửi lần 2 với cùng eventId
        kafkaTemplate.send("user-events", userId.toString(), json).get();
        Thread.sleep(2000);

        // 1. Kiểm tra bảng processed_events chỉ có đúng 1 bản ghi
        Optional<ProcessedEvent> processedEvent = processedEventRepository.findById(duplicateEventId);
        assertThat(processedEvent).isPresent();

        // 2. Kiểm tra bảng notification_log chỉ có đúng 1 log với trạng thái SENT
        List<NotificationLog> logs = notificationLogRepository.findByEventId(duplicateEventId);
        assertThat(logs).hasSize(1);
        assertThat(logs.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);

        // 3. EmailService chỉ được gọi duy nhất 1 lần
        verify(emailService, times(1)).sendWelcomeEmail(testEmail, "Idempotent User");
    }

    @Test
    @DisplayName("Nghiệm thu Retry: Lỗi SMTP tạm thời lần 1, thành công lần 2 -> Consumer tự retry thành công")
    void testTemporarySmtpError_RetriesAndSucceeds() throws Exception {
        String eventId = "event-retry-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String testEmail = "retry-" + UUID.randomUUID() + "@example.com";

        // Cấu hình EmailService: Lần 1 ném ngoại lệ tạm thời MailSendException, lần 2 gọi method thật
        doThrow(new MailSendException("SMTP tạm thời nghẽn mạng"))
                .doCallRealMethod()
                .when(emailService).sendWelcomeEmail(eq(testEmail), anyString());

        String json = String.format("""
                {
                  "eventId": "%s",
                  "eventType": "UserCreatedEvent",
                  "version": 1,
                  "occurredAt": "2026-10-12T05:00:00Z",
                  "source": "user-service",
                  "payload": {
                    "userId": "%s",
                    "email": "%s",
                    "fullName": "Retry User"
                  }
                }
                """, eventId, userId, testEmail);

        kafkaTemplate.send("user-events", userId.toString(), json).get();

        // Đợi consumer retry (initial interval 500ms + xử lý)
        Thread.sleep(3000);

        // Xác nhận EmailService đã được gọi 2 lần (1 lần lỗi, 1 lần retry thành công)
        verify(emailService, times(2)).sendWelcomeEmail(eq(testEmail), anyString());

        // Kiểm tra processed_events đã được lưu sau khi retry thành công
        assertThat(processedEventRepository.findById(eventId)).isPresent();

        // Kiểm tra notification_log cập nhật SENT
        List<NotificationLog> logs = notificationLogRepository.findByEventId(eventId);
        assertThat(logs).isNotEmpty();
        assertThat(logs.get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    @DisplayName("Nghiệm thu DLT: Lỗi vượt quá số lần retry -> Message được đẩy sang user-events.DLT")
    void testExceededRetries_PushedToDLT() throws Exception {
        String eventId = "event-fail-" + UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String testEmail = "fail-" + UUID.randomUUID() + "@example.com";

        // EmailService liên tục gặp lỗi SMTP không hồi phục
        doThrow(new MailSendException("SMTP chết hoàn toàn"))
                .when(emailService).sendWelcomeEmail(eq(testEmail), anyString());

        String json = String.format("""
                {
                  "eventId": "%s",
                  "eventType": "UserCreatedEvent",
                  "version": 1,
                  "occurredAt": "2026-10-12T05:00:00Z",
                  "source": "user-service",
                  "payload": {
                    "userId": "%s",
                    "email": "%s",
                    "fullName": "Failed User"
                  }
                }
                """, eventId, userId, testEmail);

        kafkaTemplate.send("user-events", userId.toString(), json).get();

        // Chờ các lần retry vượt ngưỡng và đẩy sang DLT
        ConsumerRecord<String, String> dltRecord = KafkaTestUtils.getSingleRecord(dltConsumer, "user-events.DLT", Duration.ofSeconds(10));

        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.value()).isEqualTo(json);

        // processed_events KHÔNG được lưu vì xử lý thất bại
        assertThat(processedEventRepository.findById(eventId)).isEmpty();
    }
}
