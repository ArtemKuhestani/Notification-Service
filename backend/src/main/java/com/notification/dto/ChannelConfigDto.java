package com.notification.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO для конфигурации канала отправки.
 */
public record ChannelConfigDto(
    Integer id,
    String channelType,
    Map<String, Object> settings,
    boolean enabled,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
