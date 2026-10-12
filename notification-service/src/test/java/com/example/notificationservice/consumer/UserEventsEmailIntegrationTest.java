package com.example.notificationservice.consumer;

import com.example.notificationservice.entity.NotificationLog;
import com.example.notificationservice.entity.NotificationStatus;
import com.example.notificationservice.entity.NotificationType;
import com.example.notificationservice.repository.NotificationLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@EmbeddedKafka(
        partitions = 1,
        topics = {"user-events", "user-events.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class UserEventsEmailIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Test
    @DisplayName("Gửi UserCreatedEvent qua Kafka -> Consumer gửi email chào mừng và ghi notification_log trạng thái SENT")
    void testUserCreatedEvent_SendsEmailAndLogsSent() throws Exception {
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String testEmail = "welcome-" + UUID.randomUUID() + "@example.com";
        String testName = "Lê Văn <Developer> & Tester";

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
                    "fullName": "%s"
                  }
                }
                """, eventId, userId, testEmail, testName);

        // Gửi event lên Kafka topic user-events
        kafkaTemplate.send("user-events", userId.toString(), json).get();

        // Đợi tối đa 5 giây để consumer xử lý và gửi mail
        long deadline = System.currentTimeMillis() + 5000;
        List<NotificationLog> logs = List.of();
        while (System.currentTimeMillis() < deadline) {
            logs = notificationLogRepository.findByRecipient(testEmail);
            if (!logs.isEmpty() && logs.get(0).getStatus() == NotificationStatus.SENT) {
                break;
            }
            Thread.sleep(300);
        }

        assertThat(logs).isNotEmpty();
        NotificationLog logEntry = logs.get(0);
        assertThat(logEntry.getEventId()).isEqualTo(eventId);
        assertThat(logEntry.getRecipient()).isEqualTo(testEmail);
        assertThat(logEntry.getType()).isEqualTo(NotificationType.WELCOME_EMAIL);
        assertThat(logEntry.getStatus()).isEqualTo(NotificationStatus.SENT);
        assertThat(logEntry.getError()).isNull();
    }
}
