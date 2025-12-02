package com.notification.dto;

/**
 * DTO ответа авторизации.
 */
public record AuthResponse(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    AdminInfo admin
) {}
