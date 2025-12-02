package com.notification.dto;

import java.time.LocalDateTime;

/**
 * Стандартный ответ об ошибке API.
 */
public record ApiErrorResponse(
    LocalDateTime timestamp,
    int status,
    String error,
    String message,
    String path
) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(LocalDateTime.now(), status, error, message, path);
    }
}
