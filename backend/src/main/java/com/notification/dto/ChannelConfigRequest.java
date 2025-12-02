package com.notification.dto;

import java.util.Map;

/**
 * DTO для создания/обновления конфигурации канала.
 */
public class ChannelConfigRequest {
    private String channelType;
    private Map<String, Object> settings;
    private String credentials;
    private boolean enabled = true;
    private Integer priority;

    public String getChannelType() { return channelType; }
    public void setChannelType(String channelType) { this.channelType = channelType; }

    public Map<String, Object> getSettings() { return settings; }
    public void setSettings(Map<String, Object> settings) { this.settings = settings; }

    public String getCredentials() { return credentials; }
    public void setCredentials(String credentials) { this.credentials = credentials; }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
}
