package com.notification.dto;

import java.time.LocalDateTime;

/**
 * DTO для API-клиента.
 */
public record ApiClientDto(
    Integer id,
    String name,
    String description,
    String apiKeyPrefix,
    boolean active,
    Integer rateLimit,
    LocalDateTime createdAt,
    LocalDateTime lastUsedAt
) {}
