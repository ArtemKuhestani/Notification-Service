package com.notification.service;

import com.notification.domain.entity.AuditLog;
import com.notification.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Сервис для логирования действий в журнал аудита.
 */
@Service
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Асинхронно сохраняет запись аудита.
     */
    @Async
    public void logAction(Integer adminId, String actionType, String entityType, 
                          String entityId, Map<String, Object> oldValue, 
                          Map<String, Object> newValue, String ipAddress, String userAgent) {
        try {
            AuditLog auditLog = AuditLog.builder()
                .adminId(adminId)
                .actionType(actionType)
                .entityType(entityType)
                .entityId(entityId)
                .oldValue(oldValue)
                .newValue(newValue)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

            auditLogRepository.save(auditLog);
            log.debug("Audit log saved: action={}, entity={}, entityId={}", 
                actionType, entityType, entityId);
        } catch (Exception e) {
            log.error("Failed to save audit log: action={}, entity={}", 
                actionType, entityType, e);
        }
    }

    /**
     * Логирует успешный вход.
     */
    public void logLogin(Integer adminId, String ipAddress, String userAgent) {
        logAction(adminId, AuditLog.ACTION_LOGIN, AuditLog.ENTITY_ADMIN, 
            String.valueOf(adminId), null, null, ipAddress, userAgent);
    }

    /**
     * Логирует выход.
     */
    public void logLogout(Integer adminId, String ipAddress, String userAgent) {
        logAction(adminId, AuditLog.ACTION_LOGOUT, AuditLog.ENTITY_ADMIN, 
            String.valueOf(adminId), null, null, ipAddress, userAgent);
    }

    /**
     * Логирует отправку уведомления.
     */
    public void logNotificationSent(String notificationId, String channel, 
                                     String recipient, String ipAddress) {
        Map<String, Object> details = Map.of(
            "channel", channel,
            "recipient", maskRecipient(recipient)
        );
        logAction(null, AuditLog.ACTION_SEND_NOTIFICATION, AuditLog.ENTITY_NOTIFICATION, 
            notificationId, null, details, ipAddress, null);
    }

    /**
     * Логирует создание сущности.
     */
    public void logCreate(Integer adminId, String entityType, String entityId, 
                          Map<String, Object> newValue, String ipAddress, String userAgent) {
        logAction(adminId, AuditLog.ACTION_CREATE, entityType, entityId, 
            null, newValue, ipAddress, userAgent);
    }

    /**
     * Логирует обновление сущности.
     */
    public void logUpdate(Integer adminId, String entityType, String entityId, 
                          Map<String, Object> oldValue, Map<String, Object> newValue, 
                          String ipAddress, String userAgent) {
        logAction(adminId, AuditLog.ACTION_UPDATE, entityType, entityId, 
            oldValue, newValue, ipAddress, userAgent);
    }

    /**
     * Логирует удаление сущности.
     */
    public void logDelete(Integer adminId, String entityType, String entityId, 
                          Map<String, Object> oldValue, String ipAddress, String userAgent) {
        logAction(adminId, AuditLog.ACTION_DELETE, entityType, entityId, 
            oldValue, null, ipAddress, userAgent);
    }

    /**
     * Маскирует получателя для логирования.
     */
    private String maskRecipient(String recipient) {
        if (recipient == null || recipient.length() < 4) {
            return "***";
        }
        if (recipient.contains("@")) {
            int atIndex = recipient.indexOf("@");
            if (atIndex > 2) {
                return recipient.substring(0, 2) + "***" + recipient.substring(atIndex);
            }
        }
        return recipient.substring(0, 2) + "***" + recipient.substring(recipient.length() - 2);
    }
}
