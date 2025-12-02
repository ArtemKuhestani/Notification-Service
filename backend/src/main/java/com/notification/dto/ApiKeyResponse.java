package com.notification.dto;

/**
 * DTO для ответа с API-ключом (возвращается только при создании или регенерации).
 */
public record ApiKeyResponse(
    Integer id,
    String name,
    String apiKey,
    String message
) {}
