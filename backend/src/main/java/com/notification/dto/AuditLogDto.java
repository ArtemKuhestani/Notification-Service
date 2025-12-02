package com.notification.dto;

import java.time.LocalDateTime;

/**
 * DTO для записи журнала аудита.
 */
public record AuditLogDto(
    Long id,
    String action,
    String entityType,
    String entityId,
    Integer adminId,
    String adminEmail,
    String oldValue,
    String newValue,
    String ipAddress,
    LocalDateTime createdAt
) {}
