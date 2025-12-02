package com.notification.dto;

/**
 * DTO для создания/обновления шаблона сообщения.
 */
public class TemplateRequest {
    private String code;
    private String name;
    private String channel;
    private String subjectTemplate;
    private String bodyTemplate;
    private String variables;
    private boolean active = true;

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getChannel() { return channel; }
    public void setChannel(String channel) { this.channel = channel; }

    public String getSubjectTemplate() { return subjectTemplate; }
    public void setSubjectTemplate(String subjectTemplate) { this.subjectTemplate = subjectTemplate; }

    public String getBodyTemplate() { return bodyTemplate; }
    public void setBodyTemplate(String bodyTemplate) { this.bodyTemplate = bodyTemplate; }

    public String getVariables() { return variables; }
    public void setVariables(String variables) { this.variables = variables; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
