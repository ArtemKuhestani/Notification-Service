package com.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO для ответа на запрос отправки уведомления.
 */
public record SendNotificationResponse(
    UUID notificationId,
    String status,
    LocalDateTime createdAt
) {
}
