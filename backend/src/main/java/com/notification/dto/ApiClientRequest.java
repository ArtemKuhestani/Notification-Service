package com.notification.dto;

import java.util.List;

/**
 * DTO для создания/обновления API-клиента.
 */
public class ApiClientRequest {
    private String name;
    private String description;
    private boolean active = true;
    private Integer rateLimit;
    private List<String> allowedIps;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    public Integer getRateLimit() { return rateLimit; }
    public void setRateLimit(Integer rateLimit) { this.rateLimit = rateLimit; }

    public List<String> getAllowedIps() { return allowedIps; }
    public void setAllowedIps(List<String> allowedIps) { this.allowedIps = allowedIps; }
}
