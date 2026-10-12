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

    /**
     * PHƯƠNG THỨC LẮNG NGHE CHÍNH CỦA KAFKA (CONSUMER)
     * 
     * LUỒNG HOẠT ĐỘNG:
     * 1. Spring Kafka chạy một luồng ngầm liên tục hỏi (poll) Kafka Broker: "Có tin mới ở topic user-events không?".
     * 2. Ngay khi có tin nhắn mới, Spring tự động gọi vào hàm consumeUserEvent() này và truyền dữ liệu vào.
     * 
     * CÁC THAM SỐ:
     * - @Payload rawMessage: Nội dung tin nhắn dạng chuỗi JSON nguyên bản.
     * - @Header(KafkaHeaders.RECEIVED_KEY): Khóa tin nhắn (ở đây là userId, giúp định tuyến phân vùng).
     * - @Header(KafkaHeaders.RECEIVED_TOPIC): Tên Topic mà tin nhắn đến.
     * - @Header(KafkaHeaders.RECEIVED_PARTITION): Số hiệu Partition chứa tin nhắn này.
     * - @Header(KafkaHeaders.OFFSET): Số thứ tự (vị trí trang sách) của tin nhắn trong Partition.
     */
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

        // BƯỚC 1: Kiểm tra tính hợp lệ cơ bản (Message không được rỗng)
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            log.error("Nhận được message rỗng. Ném IllegalArgumentException để đẩy sang DLT.");
            throw new IllegalArgumentException("Kafka message body không được rỗng");
        }

        String eventId;
        String eventType;
        UserCreatedEvent userCreatedEvent = null;

        // BƯỚC 2: PHÂN TÍCH CÚ PHÁP (JSON PARSING & SCHEMA VALIDATION)
        // ----------------------------------------------------------------------
        // NGUYÊN TẮC QUAN TRỌNG:
        // Nếu JSON sai cú pháp hoặc thiếu trường bắt buộc (eventId, eventType), đây là LỖI VĨNH VIỄN (Fatal).
        // Ta cố tình ném ra `IllegalArgumentException` để DefaultErrorHandler bắt được và ĐẨY THẲNG SANG DLT,
        // TUYỆT ĐỐI KHÔNG RETRY để tránh làm treo hoặc nghẽn hàng đợi Kafka!
        try {
            JsonNode rootNode = objectMapper.readTree(rawMessage);

            // Kiểm tra phong bì (Envelope) có đủ eventId và eventType không
            if (rootNode == null || !rootNode.hasNonNull("eventId") || !rootNode.hasNonNull("eventType")) {
                log.error("Message thiếu trường bắt buộc trong envelope (eventId hoặc eventType): {}", rawMessage);
                throw new IllegalArgumentException("Sự kiện thiếu thông tin envelope bắt buộc: " + rawMessage);
            }

            eventId = rootNode.get("eventId").asText();
            eventType = rootNode.get("eventType").asText();

            // Chỉ quan tâm sự kiện tạo người dùng mới để gửi email chào mừng
            if ("UserCreatedEvent".equals(eventType)) {
                JsonNode payloadNode = rootNode.get("payload");
                if (payloadNode == null || payloadNode.isNull()) {
                    log.error("Sự kiện UserCreatedEvent thiếu payload: eventId [{}]", eventId);
                    throw new IllegalArgumentException("UserCreatedEvent thiếu payload: " + eventId);
                }

                // Chuyển đổi payload JSON sang Object Java UserCreatedEvent (chứa userId, email, fullName)
                userCreatedEvent = objectMapper.treeToValue(payloadNode, UserCreatedEvent.class);
                if (userCreatedEvent == null || userCreatedEvent.getUserId() == null || userCreatedEvent.getEmail() == null) {
                    log.error("Payload của UserCreatedEvent không hợp lệ (thiếu userId hoặc email): eventId [{}]", eventId);
                    throw new IllegalArgumentException("Payload UserCreatedEvent thiếu userId hoặc email: " + eventId);
                }
            } else {
                // Các sự kiện khác (UserUpdatedEvent, UserDeletedEvent...) hiện tại chưa cần gửi thông báo
                log.info("Bỏ qua sự kiện không cần gửi thông báo: eventType [{}], eventId [{}]", eventType, eventId);
                return;
            }

        } catch (IllegalArgumentException e) {
            // Ném thẳng để ErrorHandler bắt và chuyển sang DLT
            throw e;
        } catch (Exception e) {
            log.error("Không thể phân tích cú pháp message JSON: {}. Chi tiết lỗi: {}", rawMessage, e.getMessage());
            throw new IllegalArgumentException("Lỗi phân tích cú pháp JSON message: " + e.getMessage(), e);
        }

        // BƯỚC 3: XỬ LÝ NGHIỆP VỤ (GỬI EMAIL & LƯU DATABASE)
        // ----------------------------------------------------------------------
        // NGUYÊN TẮC:
        // Đặt ngoài khối try-catch JSON ở trên để nếu có lỗi tạm thời (Transient) như:
        // - Mạng chập chờn
        // - Máy chủ gửi email (SMTP) khởi động lại
        // Thì ngoại lệ sẽ được bung ra ngoài cho Spring Kafka ErrorHandler kích hoạt
        // cơ chế Exponential Backoff RETRY (thử lại sau 500ms -> 1000ms -> 2000ms)!
        log.info("Nhận sự kiện UserCreatedEvent thành công: eventId [{}], userId [{}], email [{}], fullName [{}]",
                eventId, userCreatedEvent.getUserId(), userCreatedEvent.getEmail(), userCreatedEvent.getFullName());

        handleUserCreated(eventId, userCreatedEvent);
    }

    /**
     * Hàm gọi sang NotificationProcessService để thực hiện nghiệp vụ:
     * - Kiểm tra Idempotent (chống gửi trùng).
     * - Soạn thảo và gửi email qua SMTP.
     */
    protected void handleUserCreated(String eventId, UserCreatedEvent event) {
        if (notificationProcessService != null) {
            notificationProcessService.processUserCreatedEvent(eventId, event);
        } else {
            log.debug("NotificationProcessService chưa được thiết lập. Bỏ qua gửi thông báo.");
        }
    }
}
