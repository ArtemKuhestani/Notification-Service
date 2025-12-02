package com.notification.domain.entity;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * Сущность уведомления.
 */
public record Notification(
    UUID notificationId,
    Integer clientId,
    ChannelType channelType,
    String recipient,
    String subject,
    String messageBody,
    NotificationStatus status,
    Priority priority,
    Integer retryCount,
    Integer maxRetries,
    LocalDateTime nextRetryAt,
    String errorMessage,
    String errorCode,
    String providerMessageId,
    String idempotencyKey,
    String callbackUrl,
    Map<String, Object> metadata,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime sentAt,
    LocalDateTime expiresAt
) {
    public enum ChannelType {
        EMAIL, TELEGRAM, WHATSAPP, SMS
    }
    
    public enum NotificationStatus {
        PENDING, SENDING, SENT, DELIVERED, FAILED, EXPIRED
    }
    
    public enum Priority {
        HIGH, NORMAL, LOW
    }
    
    public boolean canRetry() {
        return status == NotificationStatus.PENDING 
            && retryCount < maxRetries 
            && (expiresAt == null || expiresAt.isAfter(LocalDateTime.now()));
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private UUID notificationId;
        private Integer clientId;
        private ChannelType channelType;
        private String recipient;
        private String subject;
        private String messageBody;
        private NotificationStatus status = NotificationStatus.PENDING;
        private Priority priority = Priority.NORMAL;
        private Integer retryCount = 0;
        private Integer maxRetries = 5;
        private LocalDateTime nextRetryAt;
        private String errorMessage;
        private String errorCode;
        private String providerMessageId;
        private String idempotencyKey;
        private String callbackUrl;
        private Map<String, Object> metadata;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private LocalDateTime sentAt;
        private LocalDateTime expiresAt;
        
        public Builder notificationId(UUID notificationId) { this.notificationId = notificationId; return this; }
        public Builder clientId(Integer clientId) { this.clientId = clientId; return this; }
        public Builder channelType(ChannelType channelType) { this.channelType = channelType; return this; }
        public Builder recipient(String recipient) { this.recipient = recipient; return this; }
        public Builder subject(String subject) { this.subject = subject; return this; }
        public Builder messageBody(String messageBody) { this.messageBody = messageBody; return this; }
        public Builder status(NotificationStatus status) { this.status = status; return this; }
        public Builder priority(Priority priority) { this.priority = priority; return this; }
        public Builder retryCount(Integer retryCount) { this.retryCount = retryCount; return this; }
        public Builder maxRetries(Integer maxRetries) { this.maxRetries = maxRetries; return this; }
        public Builder nextRetryAt(LocalDateTime nextRetryAt) { this.nextRetryAt = nextRetryAt; return this; }
        public Builder errorMessage(String errorMessage) { this.errorMessage = errorMessage; return this; }
        public Builder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
        public Builder providerMessageId(String providerMessageId) { this.providerMessageId = providerMessageId; return this; }
        public Builder idempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; return this; }
        public Builder callbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public Builder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public Builder sentAt(LocalDateTime sentAt) { this.sentAt = sentAt; return this; }
        public Builder expiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; return this; }
        
        public Notification build() {
            return new Notification(
                notificationId, clientId, channelType, recipient, subject, messageBody,
                status, priority, retryCount, maxRetries, nextRetryAt, errorMessage,
                errorCode, providerMessageId, idempotencyKey, callbackUrl, metadata,
                createdAt, updatedAt, sentAt, expiresAt
            );
        }
    }
}
