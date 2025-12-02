package com.notification.domain.entity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сущность шаблона сообщения.
 */
public record MessageTemplate(
    Integer templateId,
    String templateCode,
    String templateName,
    String channelType,
    String subjectTemplate,
    String bodyTemplate,
    List<String> variables,
    Boolean isActive,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    Integer createdBy
) {
    /**
     * Применяет шаблон с подстановкой переменных.
     */
    public String applyTemplate(java.util.Map<String, String> values) {
        String result = bodyTemplate;
        for (var entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
    
    /**
     * Применяет шаблон темы с подстановкой переменных.
     */
    public String applySubjectTemplate(java.util.Map<String, String> values) {
        if (subjectTemplate == null) return null;
        String result = subjectTemplate;
        for (var entry : values.entrySet()) {
            result = result.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return result;
    }
}
