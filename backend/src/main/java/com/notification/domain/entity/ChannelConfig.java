package com.notification.domain.entity;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Сущность конфигурации канала.
 */
public record ChannelConfig(
    Integer configId,
    String channelName,
    String providerName,
    byte[] credentials,
    Map<String, Object> settings,
    Boolean isEnabled,
    Integer priority,
    Integer dailyLimit,
    Integer dailySentCount,
    LocalDateTime lastHealthCheck,
    String healthStatus,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static final String STATUS_HEALTHY = "HEALTHY";
    public static final String STATUS_UNHEALTHY = "UNHEALTHY";
    public static final String STATUS_UNKNOWN = "UNKNOWN";
    
    public boolean isHealthy() {
        return STATUS_HEALTHY.equals(healthStatus);
    }
    
    public boolean hasReachedDailyLimit() {
        return dailyLimit != null && dailySentCount >= dailyLimit;
    }
}
