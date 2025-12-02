package com.notification.dto;

import java.time.LocalDateTime;

/**
 * DTO для шаблона сообщения.
 */
public record TemplateDto(
    Integer id,
    String code,
    String name,
    String channel,
    String subjectTemplate,
    String bodyTemplate,
    String variables,
    boolean active,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
