package com.notification.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * DTO для обновления токена.
 */
public record RefreshTokenRequest(
    @NotBlank(message = "Refresh токен обязателен")
    String refreshToken
) {}
