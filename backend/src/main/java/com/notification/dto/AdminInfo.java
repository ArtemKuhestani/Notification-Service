package com.notification.dto;

/**
 * DTO информации об администраторе.
 */
public record AdminInfo(
    Integer id,
    String email,
    String fullName,
    String role
) {}
