package com.notification.entity;

import java.time.LocalDateTime;

/**
 * Сущность для конфигурации канала отправки уведомлений.
 * Используется в Admin API контроллерах.
 */
public class ChannelConfig {
    private Integer id;
    private String channelType;
    private String settings;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public String getSettings() { return settings; }
    public void setSettings(String settings) { this.settings = settings; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
