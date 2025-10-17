package com.example.demo.shared.service.impl;

import com.example.demo.shared.service.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Override
    @Async("taskExecutor") // Chạy phương thức này trên một thread riêng biệt
    public void sendEmail(String to, String subject, String body) {
        try {
            log.info("Bắt đầu gửi email tới: {}", to);
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, "utf-8");

            helper.setFrom(fromEmail);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, true); // true để cho phép nội dung HTML

            mailSender.send(mimeMessage);
            log.info("Đã gửi email thành công tới: {}", to);

        } catch (MessagingException e) {
            log.error("Gửi email thất bại tới {}: {}", to, e.getMessage());
            // Có thể thêm logic để thử gửi lại hoặc thông báo cho admin
        }
    }
}