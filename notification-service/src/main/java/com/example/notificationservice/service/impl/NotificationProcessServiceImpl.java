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

    @Override
    @Transactional
    public boolean processUserCreatedEvent(String eventId, UserCreatedEvent event) {
        // 1. Kiểm tra Idempotency chống xử lý trùng lặp
        if (processedEventRepository.existsById(eventId)) {
            log.info("Sự kiện eventId [{}] đã được xử lý thành công trước đó. Bỏ qua chống trùng lặp (Idempotent).", eventId);
            return false;
        }

        // 2. Ghi nhận log thông báo PENDING
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
            // 3. Gửi email qua SMTP
            emailService.sendWelcomeEmail(event.getEmail(), event.getFullName());

            // 4. Cập nhật log SENT và lưu processed_events trong cùng transaction
            logEntry.setStatus(NotificationStatus.SENT);
            notificationLogRepository.save(logEntry);

            processedEventRepository.save(new ProcessedEvent(eventId, Instant.now()));
            log.info("Xử lý thành công UserCreatedEvent: eventId [{}], email [{}]", eventId, event.getEmail());
            return true;
        } catch (Exception e) {
            log.error("Gửi email thất bại cho eventId [{}] tới email [{}]. Transaction sẽ rollback để Kafka retry: {}",
                    eventId, event.getEmail(), e.getMessage());
            throw e;
        }
    }
}
