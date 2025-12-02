package com.notification.domain.entity;

import java.time.LocalDateTime;

/**
 * Сущность администратора системы.
 */
public record Admin(
    Integer adminId,
    String email,
    String passwordHash,
    String fullName,
    String role,
    Boolean isActive,
    Integer failedLoginAttempts,
    LocalDateTime lockedUntil,
    LocalDateTime lastLoginAt,
    String lastLoginIp,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {
    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_VIEWER = "VIEWER";
    
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }
    
    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }
}
