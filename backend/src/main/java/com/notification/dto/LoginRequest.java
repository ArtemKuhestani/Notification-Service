package com.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO для запроса авторизации.
 */
public record LoginRequest(
    @NotBlank(message = "Email обязателен")
    @Email(message = "Некорректный формат email")
    String email,
    
    @NotBlank(message = "Пароль обязателен")
    @Size(min = 8, message = "Пароль должен содержать минимум 8 символов")
    String password
) {}
