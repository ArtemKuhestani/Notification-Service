package com.notification.service;

import com.notification.domain.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.util.concurrent.CompletableFuture;

/**
 * Сервис для отправки Email-уведомлений.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${notification.email.from}")
    private String fromEmail;

    @Value("${notification.email.from-name}")
    private String fromName;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Отправляет email синхронно.
     * 
     * @param to       адрес получателя
     * @param subject  тема письма
     * @param body     текст письма
     * @param isHtml   true если тело письма в формате HTML
     * @return результат отправки
     */
    public EmailResult send(String to, String subject, String body, boolean isHtml) {
        log.info("Sending email to: {}, subject: {}", maskEmail(to), subject);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom(fromEmail, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, isHtml);

            mailSender.send(message);

            log.info("Email sent successfully to: {}", maskEmail(to));
            return new EmailResult(true, null, null);

        } catch (MessagingException e) {
            log.error("Failed to create email message: {}", e.getMessage(), e);
            return new EmailResult(false, "MESSAGING_ERROR", e.getMessage());

        } catch (MailException e) {
            log.error("Failed to send email: {}", e.getMessage(), e);
            return new EmailResult(false, "MAIL_ERROR", e.getMessage());

        } catch (Exception e) {
            log.error("Unexpected error sending email: {}", e.getMessage(), e);
            return new EmailResult(false, "UNKNOWN_ERROR", e.getMessage());
        }
    }

    /**
     * Отправляет email на основе уведомления.
     */
    public EmailResult send(Notification notification) {
        return send(
            notification.recipient(),
            notification.subject() != null ? notification.subject() : "Уведомление",
            notification.messageBody(),
            isHtmlContent(notification.messageBody())
        );
    }

    /**
     * Асинхронная отправка email.
     */
    @Async
    public CompletableFuture<EmailResult> sendAsync(String to, String subject, String body, boolean isHtml) {
        EmailResult result = send(to, subject, body, isHtml);
        return CompletableFuture.completedFuture(result);
    }

    /**
     * Проверяет, является ли контент HTML.
     */
    private boolean isHtmlContent(String content) {
        if (content == null) return false;
        String lowerContent = content.toLowerCase().trim();
        return lowerContent.startsWith("<!doctype") || 
               lowerContent.startsWith("<html") ||
               lowerContent.contains("<br>") ||
               lowerContent.contains("<p>") ||
               lowerContent.contains("<div>");
    }

    /**
     * Маскирует email для логирования.
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        if (atIndex > 2) {
            return email.substring(0, 2) + "***" + email.substring(atIndex);
        }
        return "***" + email.substring(atIndex);
    }

    /**
     * Результат отправки email.
     */
    public record EmailResult(
        boolean success,
        String errorCode,
        String errorMessage
    ) {
        public boolean isSuccess() {
            return success;
        }
    }
}
