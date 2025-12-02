package com.notification.service.channel;

import com.notification.repository.ChannelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

/**
 * Адаптер для отправки уведомлений по Email через SMTP.
 * 
 * <p>Использует Spring {@link JavaMailSender} для отправки писем.
 * Поддерживает как текстовые, так и HTML-сообщения.</p>
 * 
 * <h3>Конфигурация:</h3>
 * <pre>
 * spring.mail.host=smtp.gmail.com
 * spring.mail.port=587
 * spring.mail.username=your-email@gmail.com
 * spring.mail.password=your-app-password
 * spring.mail.properties.mail.smtp.starttls.enable=true
 * </pre>
 * 
 * <h3>Формат получателя:</h3>
 * <p>Стандартный email-адрес: user@example.com</p>
 * 
 * @author Notification Service Team
 * @see ChannelAdapter
 * @see JavaMailSender
 */
@Component
public class EmailChannelAdapter implements ChannelAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(EmailChannelAdapter.class);
    private static final String CHANNEL_NAME = "EMAIL";
    
    private final JavaMailSender mailSender;
    private final ChannelConfigRepository channelConfigRepository;
    
    public EmailChannelAdapter(
            JavaMailSender mailSender,
            ChannelConfigRepository channelConfigRepository
    ) {
        this.mailSender = mailSender;
        this.channelConfigRepository = channelConfigRepository;
    }
    
    /**
     * Отправляет email-сообщение.
     * 
     * @param recipient email получателя
     * @param subject   тема письма (по умолчанию "Уведомление")
     * @param message   текст сообщения (HTML или plain text)
     * @return ID сообщения (timestamp-based)
     * @throws ChannelException при ошибке SMTP
     */
    @Override
    public String send(String recipient, String subject, String message) throws ChannelException {
        log.info("Sending email to: {}", maskEmail(recipient));
        
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            
            helper.setTo(recipient);
            helper.setSubject(subject != null ? subject : "Уведомление");
            helper.setText(message, isHtml(message));
            
            mailSender.send(mimeMessage);
            
            log.info("Email sent successfully to: {}", maskEmail(recipient));
            return "email-" + System.currentTimeMillis();
            
        } catch (MailException e) {
            log.error("Failed to send email to {}: {}", maskEmail(recipient), e.getMessage());
            
            boolean retryable = isRetryableError(e);
            throw new ChannelException("Email sending failed: " + e.getMessage(), "SMTP_ERROR", retryable, e);
            
        } catch (MessagingException e) {
            log.error("Failed to create email message: {}", e.getMessage());
            throw new ChannelException("Email creation failed: " + e.getMessage(), "MESSAGE_ERROR", false, e);
        }
    }
    
    @Override
    public boolean healthCheck() {
        try {
            // Простая проверка - можем создать сообщение
            mailSender.createMimeMessage();
            return true;
        } catch (Exception e) {
            log.warn("Email health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }
    
    @Override
    public boolean isEnabled() {
        return channelConfigRepository.findByChannelName(CHANNEL_NAME)
            .map(config -> config.isEnabled())
            .orElse(false);
    }
    
    /**
     * Определяет, является ли сообщение HTML.
     */
    private boolean isHtml(String message) {
        return message != null && (
            message.contains("<html") || 
            message.contains("<body") || 
            message.contains("<p>") ||
            message.contains("<div") ||
            message.contains("<br")
        );
    }
    
    /**
     * Определяет, стоит ли повторять отправку при данной ошибке.
     * 
     * <p>Ошибки типа "неверный адрес" повторять бессмысленно.</p>
     */
    private boolean isRetryableError(MailException e) {
        String message = e.getMessage();
        if (message == null) return true;
        
        // Ошибки, которые не стоит повторять
        return !message.contains("Invalid address") 
            && !message.contains("Invalid recipient")
            && !message.contains("User unknown");
    }
    
    /**
     * Маскирует email для безопасного логирования.
     */
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return "***";
        int atIndex = email.indexOf("@");
        if (atIndex <= 2) return "***" + email.substring(atIndex);
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}
