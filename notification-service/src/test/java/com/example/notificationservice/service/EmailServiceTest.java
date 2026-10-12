package com.example.notificationservice.service;

import com.example.notificationservice.service.impl.EmailServiceImpl;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    private TemplateEngine templateEngine;
    private EmailServiceImpl emailService;

    @BeforeEach
    void setUp() {
        // Thiết lập TemplateEngine thực tế của Thymeleaf để test template render & HTML escaping
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCharacterEncoding("UTF-8");

        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        this.templateEngine = engine;

        this.emailService = new EmailServiceImpl(mailSender, templateEngine);
    }

    @Test
    @DisplayName("Nghiệm thu gửi email chào mừng: Render template thành công và escape ký tự nhạy cảm < và &")
    void testSendWelcomeEmail_EscapesHtmlProperly() throws Exception {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        String recipient = "test.escape@example.com";
        String dangerousName = "Trần <script>alert('hack')</script> & \"VIP\"";

        emailService.sendWelcomeEmail(recipient, dangerousName);

        ArgumentCaptor<MimeMessage> messageCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender, times(1)).send(messageCaptor.capture());

        MimeMessage sentMessage = messageCaptor.getValue();
        assertThat(sentMessage.getAllRecipients()[0].toString()).isEqualTo(recipient);
        assertThat(sentMessage.getSubject()).isEqualTo("Chào mừng bạn gia nhập hệ thống!");

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        sentMessage.writeTo(outputStream);
        String rawEmailContent = outputStream.toString("UTF-8");

        // Kiểm tra nội dung HTML đã được Thymeleaf escape an toàn:
        // "<script>" biến thành "&lt;script&gt;"
        // "'" biến thành "&#39;"
        // "&" biến thành "&amp;"
        // """ biến thành "&quot;"
        assertThat(rawEmailContent).contains("&lt;script&gt;alert(&#39;hack&#39;)&lt;/script&gt;");
        assertThat(rawEmailContent).contains("&amp;");
        assertThat(rawEmailContent).contains("&quot;VIP&quot;");
        assertThat(rawEmailContent).doesNotContain("<script>");
    }

    @Test
    @DisplayName("Nghiệm thu lỗi SMTP: JavaMailSender gặp lỗi ném ngoại lệ RuntimeException")
    void testSendWelcomeEmail_SmtpFailure_ThrowsException() {
        Session session = Session.getInstance(new Properties());
        MimeMessage mimeMessage = new MimeMessage(session);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new MailSendException("SMTP server connection timeout")).when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> emailService.sendWelcomeEmail("fail@example.com", "User Fail"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Lỗi gửi email qua SMTP");
    }
}
