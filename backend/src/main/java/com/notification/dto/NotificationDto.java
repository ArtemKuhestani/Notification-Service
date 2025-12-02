package com.notification.dto;

import java.time.LocalDateTime;

/**
 * DTO для отображения уведомления в админке.
 */
public record NotificationDto(
    String id,
    Integer clientId,
    String channel,
    String recipient,
    String subject,
    String status,
    String priority,
    Integer retryCount,
    String errorMessage,
    String errorCode,
    String providerMessageId,
    LocalDateTime createdAt,
    LocalDateTime sentAt
) {}
