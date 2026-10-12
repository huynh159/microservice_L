package com.example.notificationservice.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * ==============================================================================
 * KAFKA CONSUMER CONFIGURATION - CẤU HÌNH BỘ LẮNG NGHE & XỬ LÝ LỖI KAFKA
 * ==============================================================================
 * File này đóng vai trò "Trạm điều phối" tiếp nhận tin nhắn từ Kafka:
 * 1. Cấu hình giải mã JSON (ObjectMapper).
 * 2. Cấu hình cơ chế Tự phục hồi khi có lỗi (Retry với Exponential Backoff).
 * 3. Cấu hình Dead Letter Topic (DLT) - Nơi lưu trữ các tin nhắn lỗi không xử lý được.
 * 4. Cấu hình ContainerFactory - Nơi cấp phát các Thread chạy ngầm để đọc tin nhắn.
 * ==============================================================================
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    // Tên của topic DLT (Mặc định là "user-events.DLT").
    // Nếu tin nhắn bị lỗi quá số lần thử lại, nó sẽ bị chuyển vào topic này.
    @Value("${kafka.topics.user-events-dlt:user-events.DLT}")
    private String dltTopicName;

    /**
     * BEAN 1: ObjectMapper - Bộ chuyển đổi JSON <-> Java Object
     * - JavaTimeModule: Giúp xử lý các kiểu dữ liệu thời gian hiện đại như Instant, LocalDateTime.
     * - FAIL_ON_UNKNOWN_PROPERTIES = false: Khi Producer thêm trường mới mà Consumer chưa có,
     *   Consumer vẫn đọc bình thường, không bị crash (Tương thích xuôi / Backward Compatibility).
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }

    /**
     * BEAN 2: CommonErrorHandler - "Bác sĩ cấp cứu & Phân loại bệnh" cho Kafka
     * 
     * LUỒNG HOẠT ĐỘNG:
     * - Khi code trong Consumer gặp ngoại lệ (Exception):
     *   1. Nếu là lỗi cú pháp (Fatal): Chuyển thẳng sang DLT ngay lập tức, không thử lại.
     *   2. Nếu là lỗi tạm thời (Transient): Tự động thử lại 3 lần theo thời gian giãn cách (500ms -> 1000ms -> 2000ms).
     *   3. Sau 3 lần thử lại mà vẫn lỗi: Chuyển sang DLT để tránh làm tắc nghẽn hàng đợi!
     */
    @Bean
    public CommonErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations) {
        
        // BƯỚC A: Tạo Recoverer (Người cứu hộ).
        // Khi tin nhắn thất bại hoàn toàn, recoverer sẽ nhặt tin nhắn và gửi vào topic DLT ("user-events.DLT").
        // Tham số -1 nghĩa là để Kafka tự động chọn Partition phù hợp trong topic DLT.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, ex) -> new TopicPartition(dltTopicName, -1)
        );

        // BƯỚC B: Cấu hình Retry Backoff (Thời gian chờ thử lại cấp số nhân).
        // - maxRetries = 3: Thử lại tối đa 3 lần.
        // - initialInterval = 500ms: Lần thử đầu tiên chờ 0.5 giây.
        // - multiplier = 2.0: Mỗi lần sau nhân đôi thời gian chờ (Lần 1: 500ms, Lần 2: 1000ms, Lần 3: 2000ms).
        // - maxInterval = 3000ms: Thời gian chờ tối đa không vượt quá 3 giây.
        // Mục đích: Cho máy chủ SMTP / Mạng có thời gian tự hồi phục, tránh "dội bom" request liên tục!
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(3);
        backOff.setInitialInterval(500L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(3000L);

        // BƯỚC C: Khởi tạo DefaultErrorHandler kết hợp Recoverer và BackOff
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // BƯỚC D: Danh sách các lỗi KHÔNG ĐƯỢC THỬ LẠI (Fatal Errors / Non-retryable).
        // Ví dụ: JSON sai cú pháp, thiếu ngoặc {...}, sai kiểu dữ liệu -> Thử lại 100 lần vẫn sẽ sai!
        // Do đó, ném vào DLT ngay để giải phóng hàng đợi, không lãng phí thời gian retry.
        errorHandler.addNotRetryableExceptions(
                com.fasterxml.jackson.core.JsonParseException.class,
                com.fasterxml.jackson.databind.JsonMappingException.class,
                IllegalArgumentException.class
        );

        return errorHandler;
    }

    /**
     * BEAN 3: ConcurrentKafkaListenerContainerFactory - "Nhà máy cấp phát luồng đọc Kafka"
     * 
     * - Tạo các Background Thread chạy ngầm liên tục lắng nghe Topic từ Kafka Broker.
     * - Gắn kèm kafkaErrorHandler (ở Bean 2) vào từng luồng: Hễ luồng nào gặp lỗi,
     *   ErrorHandler sẽ can thiệp để Retry hoặc đẩy sang DLT như đã cấu hình ở trên.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
