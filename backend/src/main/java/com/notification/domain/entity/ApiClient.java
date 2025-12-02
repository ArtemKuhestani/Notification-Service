package com.notification.domain.entity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сущность API-клиента (внешней системы).
 */
public record ApiClient(
    Integer clientId,
    String clientName,
    String clientDescription,
    String apiKeyHash,
    String apiKeyPrefix,
    Boolean isActive,
    Integer rateLimit,
    List<String> allowedChannels,
    List<String> allowedIps,
    String callbackUrlDefault,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Integer createdBy,
    LocalDateTime lastUsedAt
) {
    public boolean canUseChannel(String channel) {
        if (allowedChannels == null || allowedChannels.isEmpty()) {
            return true; // Все каналы разрешены
        }
        return allowedChannels.contains(channel);
    }
}
