package com.notification.controller;

import com.notification.dto.PagedResponse;
import com.notification.dto.TemplateDto;
import com.notification.dto.TemplateRequest;
import com.notification.entity.MessageTemplate;
import com.notification.repository.TemplateRepository;
import com.notification.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Контроллер для управления шаблонами сообщений.
 */
@RestController
@RequestMapping("/api/v1/admin/templates")
public class AdminTemplateController {

    private final TemplateRepository templateRepository;
    private final AuditService auditService;

    public AdminTemplateController(TemplateRepository templateRepository,
                                   AuditService auditService) {
        this.templateRepository = templateRepository;
        this.auditService = auditService;
    }

    /**
     * Получить список всех шаблонов с пагинацией.
     */
    @GetMapping
    public PagedResponse<TemplateDto> getAllTemplates(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String channel) {
        
        List<MessageTemplate> templates;
        int total;
        
        if (channel != null && !channel.isEmpty()) {
            templates = templateRepository.findByChannel(channel.toUpperCase(), page * size, size);
            total = templateRepository.countByChannel(channel.toUpperCase());
        } else {
            templates = templateRepository.findAll(page * size, size);
            total = templateRepository.count();
        }

        List<TemplateDto> content = templates.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        int totalPages = (int) Math.ceil((double) total / size);
        return new PagedResponse<>(content, page, size, total, totalPages);
    }

    /**
     * Получить шаблон по ID.
     */
    @GetMapping("/{id}")
    public TemplateDto getTemplate(@PathVariable Integer id) {
        MessageTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Шаблон не найден"));
        return toDto(template);
    }

    /**
     * Получить шаблон по коду.
     */
    @GetMapping("/code/{code}")
    public TemplateDto getTemplateByCode(@PathVariable String code) {
        MessageTemplate template = templateRepository.findByCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Шаблон с кодом " + code + " не найден"));
        return toDto(template);
    }

    /**
     * Создать новый шаблон.
     */
    @PostMapping
    public ResponseEntity<TemplateDto> createTemplate(@RequestBody TemplateRequest request) {
        // Проверяем уникальность кода
        if (templateRepository.findByCode(request.getCode()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "Шаблон с кодом " + request.getCode() + " уже существует");
        }

        MessageTemplate template = new MessageTemplate();
        template.setCode(request.getCode());
        template.setName(request.getName());
        template.setChannel(request.getChannel().toUpperCase());
        template.setSubjectTemplate(request.getSubjectTemplate());
        template.setBodyTemplate(request.getBodyTemplate());
        template.setVariables(request.getVariables());
        template.setActive(request.isActive());

        Integer id = templateRepository.save(template);

        auditService.logAction("TEMPLATE_CREATE", 
            "Создан шаблон: " + request.getCode(), 
            "channel=" + request.getChannel());

        template.setId(id);
        return ResponseEntity.status(HttpStatus.CREATED).body(toDto(template));
    }

    /**
     * Обновить шаблон.
     */
    @PutMapping("/{id}")
    public TemplateDto updateTemplate(@PathVariable Integer id, 
                                       @RequestBody TemplateRequest request) {
        MessageTemplate existing = templateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Шаблон не найден"));

        // Проверяем уникальность кода, если он изменился
        if (!existing.getCode().equals(request.getCode())) {
            if (templateRepository.findByCode(request.getCode()).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, 
                    "Шаблон с кодом " + request.getCode() + " уже существует");
            }
        }

        existing.setCode(request.getCode());
        existing.setName(request.getName());
        existing.setChannel(request.getChannel().toUpperCase());
        existing.setSubjectTemplate(request.getSubjectTemplate());
        existing.setBodyTemplate(request.getBodyTemplate());
        existing.setVariables(request.getVariables());
        existing.setActive(request.isActive());

        templateRepository.update(existing);

        auditService.logAction("TEMPLATE_UPDATE", 
            "Обновлен шаблон: " + request.getCode(), 
            null);

        return toDto(existing);
    }

    /**
     * Удалить шаблон.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable Integer id) {
        MessageTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Шаблон не найден"));

        templateRepository.deleteById(id);

        auditService.logAction("TEMPLATE_DELETE", 
            "Удален шаблон: " + template.getCode(), 
            null);

        return ResponseEntity.noContent().build();
    }

    /**
     * Включить/выключить шаблон.
     */
    @PatchMapping("/{id}/toggle")
    public TemplateDto toggleTemplate(@PathVariable Integer id) {
        MessageTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Шаблон не найден"));

        template.setActive(!template.isActive());
        templateRepository.update(template);

        auditService.logAction("TEMPLATE_TOGGLE", 
            "Шаблон " + template.getCode() + " " + (template.isActive() ? "активирован" : "деактивирован"), 
            null);

        return toDto(template);
    }

    /**
     * Предварительный просмотр шаблона с подстановкой переменных.
     */
    @PostMapping("/{id}/preview")
    public PreviewResponse previewTemplate(@PathVariable Integer id,
                                            @RequestBody PreviewRequest request) {
        MessageTemplate template = templateRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Шаблон не найден"));

        String subject = template.getSubjectTemplate();
        String body = template.getBodyTemplate();

        // Подставляем переменные
        if (request.getVariables() != null) {
            for (var entry : request.getVariables().entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                if (subject != null) {
                    subject = subject.replace(placeholder, entry.getValue());
                }
                if (body != null) {
                    body = body.replace(placeholder, entry.getValue());
                }
            }
        }

        return new PreviewResponse(subject, body);
    }

    private TemplateDto toDto(MessageTemplate template) {
        return new TemplateDto(
            template.getId(),
            template.getCode(),
            template.getName(),
            template.getChannel(),
            template.getSubjectTemplate(),
            template.getBodyTemplate(),
            template.getVariables(),
            template.isActive(),
            template.getCreatedAt(),
            template.getUpdatedAt()
        );
    }

    // Внутренние классы
    public static class PreviewRequest {
        private java.util.Map<String, String> variables;

        public java.util.Map<String, String> getVariables() { return variables; }
        public void setVariables(java.util.Map<String, String> variables) { this.variables = variables; }
    }

    public record PreviewResponse(String subject, String body) {}
}
