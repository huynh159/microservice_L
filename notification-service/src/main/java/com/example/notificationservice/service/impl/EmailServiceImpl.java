package com.example.notificationservice.service.impl;

import com.example.notificationservice.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.nio.charset.StandardCharsets;

@Service
public class EmailServiceImpl implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailServiceImpl.class);

    private final JavaMailSender mailSender;
    private final TemplateEngine templateEngine;

    @Value("${notification.mail.from:no-reply@example.com}")
    private String fromEmail = "no-reply@example.com";

    public EmailServiceImpl(JavaMailSender mailSender, TemplateEngine templateEngine) {
        this.mailSender = mailSender;
        this.templateEngine = templateEngine;
    }

    @Override
    public void sendWelcomeEmail(String recipient, String fullName) {
        try {
            log.info("Bắt đầu chuẩn bị gửi email chào mừng tới recipient [{}], fullName [{}]", recipient, fullName);

            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            Context context = new Context();
            context.setVariable("fullName", fullName != null ? fullName : "");
            context.setVariable("email", recipient != null ? recipient : "");

            // Render template welcome.html với context đã truyền
            String htmlContent = templateEngine.process("welcome", context);

            helper.setFrom(fromEmail);
            helper.setTo(recipient);
            helper.setSubject("Chào mừng bạn gia nhập hệ thống!");
            helper.setText(htmlContent, true); // true = HTML format

            mailSender.send(mimeMessage);
            log.info("Gửi email chào mừng thành công tới [{}]", recipient);

        } catch (MessagingException e) {
            log.error("Lỗi khi tạo MIME email cho recipient [{}]: {}", recipient, e.getMessage(), e);
            throw new RuntimeException("Lỗi tạo email MIME: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Lỗi khi gửi email qua SMTP cho recipient [{}]: {}", recipient, e.getMessage(), e);
            throw new RuntimeException("Lỗi gửi email qua SMTP: " + e.getMessage(), e);
        }
    }
}
