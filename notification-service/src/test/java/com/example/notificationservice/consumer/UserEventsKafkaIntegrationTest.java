package com.example.notificationservice.consumer;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("dev")
@EmbeddedKafka(
        partitions = 1,
        topics = {"user-events", "user-events.DLT"},
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserEventsKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    private Consumer<String, String> dltConsumer;

    @BeforeEach
    void setUp() {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "dlt-test-verifier-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
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
    @DisplayName("Gửi sự kiện UserCreatedEvent thật lên Kafka -> Consumer nhận thành công không phát sinh lỗi")
    void testSendRealUserCreatedEvent_ConsumedSuccessfully() throws Exception {
        UUID userId = UUID.randomUUID();
        String eventId = UUID.randomUUID().toString();
        String validEventJson = String.format("""
                {
                  "eventId": "%s",
                  "eventType": "UserCreatedEvent",
                  "version": 1,
                  "occurredAt": "2026-10-12T04:00:00Z",
                  "source": "user-service",
                  "payload": {
                    "userId": "%s",
                    "email": "test-kafka@example.com",
                    "fullName": "Kafka Integration User"
                  }
                }
                """, eventId, userId);

        kafkaTemplate.send("user-events", userId.toString(), validEventJson).get();

        // Đợi 2 giây cho consumer xử lý
        Thread.sleep(2000);

        // Đảm bảo không có message nào bị đẩy sang DLT
        ConsumerRecords<String, String> records = KafkaTestUtils.getRecords(dltConsumer, Duration.ofSeconds(1));
        assertThat(records.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("Gửi sự kiện sai định dạng lên Kafka -> ErrorHandler đẩy sang DLT và Consumer không bị chết")
    void testSendMalformedEvent_RoutedToDLT() throws Exception {
        String malformedJson = "{not-a-valid-json}";

        kafkaTemplate.send("user-events", "invalid-key", malformedJson).get();

        // Kiểm tra DLT nhận được message lỗi
        ConsumerRecord<String, String> dltRecord = KafkaTestUtils.getSingleRecord(dltConsumer, "user-events.DLT", Duration.ofSeconds(5));

        assertThat(dltRecord).isNotNull();
        assertThat(dltRecord.value()).isEqualTo(malformedJson);
    }
}
