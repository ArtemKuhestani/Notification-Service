package com.notification.domain.entity;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Сущность записи аудита.
 */
public record AuditLog(
    Long logId,
    Integer adminId,
    String actionType,
    String entityType,
    String entityId,
    Map<String, Object> oldValue,
    Map<String, Object> newValue,
    String ipAddress,
    String userAgent,
    LocalDateTime createdAt
) {
    // Типы действий
    public static final String ACTION_LOGIN = "LOGIN";
    public static final String ACTION_LOGOUT = "LOGOUT";
    public static final String ACTION_CREATE = "CREATE";
    public static final String ACTION_UPDATE = "UPDATE";
    public static final String ACTION_DELETE = "DELETE";
    public static final String ACTION_REGENERATE_KEY = "REGENERATE_KEY";
    public static final String ACTION_RETRY_NOTIFICATION = "RETRY_NOTIFICATION";
    public static final String ACTION_ENABLE_CHANNEL = "ENABLE_CHANNEL";
    public static final String ACTION_DISABLE_CHANNEL = "DISABLE_CHANNEL";
    public static final String ACTION_SEND_NOTIFICATION = "SEND_NOTIFICATION";
    
    // Типы сущностей
    public static final String ENTITY_ADMIN = "admin";
    public static final String ENTITY_CLIENT = "client";
    public static final String ENTITY_CHANNEL = "channel";
    public static final String ENTITY_TEMPLATE = "template";
    public static final String ENTITY_NOTIFICATION = "notification";
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private Long logId;
        private Integer adminId;
        private String actionType;
        private String entityType;
        private String entityId;
        private Map<String, Object> oldValue;
        private Map<String, Object> newValue;
        private String ipAddress;
        private String userAgent;
        private LocalDateTime createdAt;
        
        public Builder logId(Long logId) { this.logId = logId; return this; }
        public Builder adminId(Integer adminId) { this.adminId = adminId; return this; }
        public Builder actionType(String actionType) { this.actionType = actionType; return this; }
        public Builder entityType(String entityType) { this.entityType = entityType; return this; }
        public Builder entityId(String entityId) { this.entityId = entityId; return this; }
        public Builder oldValue(Map<String, Object> oldValue) { this.oldValue = oldValue; return this; }
        public Builder newValue(Map<String, Object> newValue) { this.newValue = newValue; return this; }
        public Builder ipAddress(String ipAddress) { this.ipAddress = ipAddress; return this; }
        public Builder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
        public Builder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        
        public AuditLog build() {
            return new AuditLog(logId, adminId, actionType, entityType, entityId, 
                oldValue, newValue, ipAddress, userAgent, createdAt);
        }
    }
}
