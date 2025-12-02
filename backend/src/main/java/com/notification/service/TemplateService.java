package com.notification.service;

import com.notification.entity.MessageTemplate;
import com.notification.repository.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Сервис для работы с шаблонами сообщений.
 */
@Service
public class TemplateService {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(\\w+)}}");

    private final TemplateRepository templateRepository;

    public TemplateService(TemplateRepository templateRepository) {
        this.templateRepository = templateRepository;
    }

    /**
     * Найти активный шаблон по коду и каналу.
     */
    public Optional<MessageTemplate> findTemplate(String templateCode, String channel) {
        return templateRepository.findActiveByCodeAndChannel(templateCode, channel.toUpperCase());
    }

    /**
     * Применить шаблон с подстановкой переменных.
     */
    public RenderedTemplate render(MessageTemplate template, Map<String, String> variables) {
        String subject = renderText(template.getSubjectTemplate(), variables);
        String body = renderText(template.getBodyTemplate(), variables);
        
        return new RenderedTemplate(subject, body);
    }

    /**
     * Применить шаблон по коду с подстановкой переменных.
     */
    public Optional<RenderedTemplate> renderByCode(String templateCode, String channel, 
                                                    Map<String, String> variables) {
        return findTemplate(templateCode, channel)
            .map(template -> render(template, variables));
    }

    /**
     * Подставить переменные в текст.
     */
    private String renderText(String template, Map<String, String> variables) {
        if (template == null || template.isEmpty()) {
            return template;
        }

        if (variables == null || variables.isEmpty()) {
            return template;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String variableName = matcher.group(1);
            String replacement = variables.getOrDefault(variableName, "{{" + variableName + "}}");
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Проверить, все ли переменные из шаблона есть в переданных данных.
     */
    public ValidationResult validateVariables(MessageTemplate template, Map<String, String> variables) {
        if (template.getVariables() == null || template.getVariables().isEmpty()) {
            return new ValidationResult(true, null);
        }

        // Парсим список переменных из template.variables (JSON array)
        String varsJson = template.getVariables();
        // Простой парсинг: ["var1", "var2", "var3"]
        String[] requiredVars = varsJson
            .replaceAll("[\\[\\]\"\\s]", "")
            .split(",");

        for (String requiredVar : requiredVars) {
            if (requiredVar.isEmpty()) continue;
            if (variables == null || !variables.containsKey(requiredVar)) {
                return new ValidationResult(false, 
                    "Отсутствует обязательная переменная: " + requiredVar);
            }
        }

        return new ValidationResult(true, null);
    }

    /**
     * Результат рендеринга шаблона.
     */
    public record RenderedTemplate(String subject, String body) {}

    /**
     * Результат валидации переменных.
     */
    public record ValidationResult(boolean valid, String errorMessage) {}
}
