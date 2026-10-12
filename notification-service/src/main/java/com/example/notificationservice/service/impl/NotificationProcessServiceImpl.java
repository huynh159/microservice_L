package com.example.notificationservice.service.impl;

import com.example.common.dto.event.UserCreatedEvent;
import com.example.notificationservice.entity.NotificationLog;
import com.example.notificationservice.entity.NotificationStatus;
import com.example.notificationservice.entity.NotificationType;
import com.example.notificationservice.entity.ProcessedEvent;
import com.example.notificationservice.repository.NotificationLogRepository;
import com.example.notificationservice.repository.ProcessedEventRepository;
import com.example.notificationservice.service.EmailService;
import com.example.notificationservice.service.NotificationProcessService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationProcessServiceImpl implements NotificationProcessService {

    private static final Logger log = LoggerFactory.getLogger(NotificationProcessServiceImpl.class);

    private final ProcessedEventRepository processedEventRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final EmailService emailService;

    public NotificationProcessServiceImpl(
            ProcessedEventRepository processedEventRepository,
            NotificationLogRepository notificationLogRepository,
            EmailService emailService
    ) {
        this.processedEventRepository = processedEventRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.emailService = emailService;
    }

    /**
     * XỬ LÝ NGHIỆP VỤ THÔNG BÁO CHO SỰ KIỆN TẠO NGƯỜI DÙNG (USER CREATED)
     * 
     * TẠI SAO CÓ @Transactional?
     * - Để đảm bảo toàn vẹn dữ liệu (ACID): Bảng notification_log và bảng processed_events
     *   phải cùng THÀNH CÔNG hoặc cùng THẤT BẠI.
     * - Nếu việc gửi email thất bại: Toàn bộ thao tác trong Database sẽ được ROLLBACK.
     *   Nhờ đó, lần retry tiếp theo của Kafka sẽ thấy eventId này chưa được xử lý và chạy lại từ đầu!
     */
    @Override
    @Transactional
    public boolean processUserCreatedEvent(String eventId, UserCreatedEvent event) {
        
        // ==============================================================================
        // BƯỚC 1: KIỂM TRA CHỐNG TRÙNG LẶP (IDEMPOTENCY CHECK)
        // ==============================================================================
        // Trong hệ thống phân tán, Kafka có thể gửi lại cùng 1 tin nhắn do mạng chập chờn.
        // Ta dùng bảng `processed_events` để ghi nhớ các eventId đã từng được xử lý.
        if (processedEventRepository.existsById(eventId)) {
            log.info("Sự kiện eventId [{}] đã được xử lý thành công trước đó. Bỏ qua chống trùng lặp (Idempotent).", eventId);
            return false; // Trả về false và kết thúc, KHÔNG gửi email lần 2 cho khách hàng!
        }

        // ==============================================================================
        // BƯỚC 2: GHI LOG THÔNG BÁO Ở TRẠNG THÁI PENDING (ĐANG XỬ LÝ)
        // ==============================================================================
        NotificationLog logEntry = NotificationLog.builder()
                .id(UUID.randomUUID())
                .eventId(eventId)
                .recipient(event.getEmail())
                .type(NotificationType.WELCOME_EMAIL)
                .status(NotificationStatus.PENDING)
                .createdAt(Instant.now())
                .build();
        notificationLogRepository.save(logEntry);

        try {
            // ==============================================================================
            // BƯỚC 3: SOẠN THẢO VÀ GỬI EMAIL THẬT QUA GIAO THỨC SMTP
            // ==============================================================================
            // Gọi EmailService để render giao diện Thymeleaf (welcome.html) và gửi email qua Mailpit
            emailService.sendWelcomeEmail(event.getEmail(), event.getFullName());

            // ==============================================================================
            // BƯỚC 4: CẬP NHẬT TRẠNG THÁI THÀNH CÔNG (SENT) & ĐÁNH DẤU ĐÃ XỬ LÝ
            // ==============================================================================
            // 4a. Đổi trạng thái log sang SENT
            logEntry.setStatus(NotificationStatus.SENT);
            notificationLogRepository.save(logEntry);

            // 4b. Lưu eventId vào bảng processed_events để các lần gọi sau bị chặn lại ở Bước 1
            processedEventRepository.save(new ProcessedEvent(eventId, Instant.now()));
            log.info("Xử lý thành công UserCreatedEvent: eventId [{}], email [{}]", eventId, event.getEmail());
            return true;

        } catch (Exception e) {
            // ==============================================================================
            // KHI GẶP LỖI (VÍ DỤ: MÁY CHỦ EMAIL MẤT KẾT NỐI):
            // ==============================================================================
            // Khi ném ngoại lệ ra khỏi phương thức @Transactional, Spring sẽ tự động ROLLBACK:
            // - Bản ghi PENDING ở Bước 2 sẽ bị xóa khỏi Database.
            // - eventId chưa được lưu vào processed_events.
            // Ngoại lệ này văng về KafkaConsumer -> Kích hoạt cơ chế Retry Backoff của Kafka!
            log.error("Gửi email thất bại cho eventId [{}] tới email [{}]. Transaction sẽ rollback để Kafka retry: {}",
                    eventId, event.getEmail(), e.getMessage());
            throw e;
        }
    }
}
