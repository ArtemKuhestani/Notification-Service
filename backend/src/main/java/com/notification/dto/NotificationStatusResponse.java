package com.notification.dto;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

/**
 * DTO для ответа со статусом уведомления.
 */
public record NotificationStatusResponse(
    UUID notificationId,
    String status,
    String channel,
    String recipient,
    LocalDateTime createdAt,
    LocalDateTime sentAt,
    Integer retryCount,
    String errorMessage
) {
}
