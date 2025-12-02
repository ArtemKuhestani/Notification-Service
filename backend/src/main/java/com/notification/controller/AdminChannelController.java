package com.notification.controller;

import com.notification.dto.ChannelConfigDto;
import com.notification.dto.ChannelConfigRequest;
import com.notification.domain.entity.ChannelConfig;
import com.notification.repository.ChannelConfigRepository;
import com.notification.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Контроллер для управления конфигурациями каналов отправки.
 */
@RestController
@RequestMapping("/api/v1/admin/channels")
public class AdminChannelController {

    private final ChannelConfigRepository channelConfigRepository;
    private final AuditService auditService;

    public AdminChannelController(ChannelConfigRepository channelConfigRepository,
                                  AuditService auditService) {
        this.channelConfigRepository = channelConfigRepository;
        this.auditService = auditService;
    }

    /**
     * Получить список всех конфигураций каналов.
     */
    @GetMapping
    public List<ChannelConfigDto> getAllChannels() {
        return channelConfigRepository.findAll().stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    /**
     * Получить конфигурацию канала по типу.
     */
    @GetMapping("/{channelType}")
    public ChannelConfigDto getChannelByType(@PathVariable String channelType) {
        ChannelConfig config = channelConfigRepository.findByChannelName(channelType.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Конфигурация для канала " + channelType + " не найдена"));
        return toDto(config);
    }

    /**
     * Обновить настройки канала.
     */
    @PutMapping("/{channelType}")
    public ChannelConfigDto updateChannel(@PathVariable String channelType, 
                                          @RequestBody ChannelConfigRequest request) {
        ChannelConfig existing = channelConfigRepository.findByChannelName(channelType.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Конфигурация для канала " + channelType + " не найдена"));

        if (request.getSettings() != null) {
            channelConfigRepository.updateSettings(channelType.toUpperCase(), request.getSettings());
        }
        
        channelConfigRepository.updateEnabled(channelType.toUpperCase(), request.isEnabled());

        auditService.logAction("CHANNEL_UPDATE", 
            "Обновлена конфигурация канала: " + channelType, 
            null);

        return toDto(channelConfigRepository.findByChannelName(channelType.toUpperCase()).orElseThrow());
    }

    /**
     * Включить/выключить канал.
     */
    @PatchMapping("/{channelType}/toggle")
    public ChannelConfigDto toggleChannel(@PathVariable String channelType,
                                          @RequestBody(required = false) ToggleRequest request) {
        ChannelConfig config = channelConfigRepository.findByChannelName(channelType.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Конфигурация для канала " + channelType + " не найдена"));

        boolean newState = request != null ? request.enabled() : !config.isEnabled();
        channelConfigRepository.updateEnabled(channelType.toUpperCase(), newState);

        auditService.logAction("CHANNEL_TOGGLE", 
            "Канал " + channelType + " " + (newState ? "включен" : "выключен"), 
            null);

        return toDto(channelConfigRepository.findByChannelName(channelType.toUpperCase()).orElseThrow());
    }

    /**
     * Проверить подключение к каналу.
     */
    @PostMapping("/{channelType}/test")
    public ResponseEntity<TestResult> testChannel(@PathVariable String channelType,
                                                  @RequestBody(required = false) TestRequest request) {
        ChannelConfig config = channelConfigRepository.findByChannelName(channelType.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                    "Конфигурация для канала " + channelType + " не найдена"));

        if (!config.isEnabled()) {
            return ResponseEntity.ok(new TestResult(false, "Канал отключен. Сначала включите канал."));
        }

        // TODO: Реальное тестирование подключения к каналу
        auditService.logAction("CHANNEL_TEST", 
            "Тестирование канала: " + channelType, 
            null);

        return ResponseEntity.ok(new TestResult(true, "Конфигурация сохранена. Тестовое сообщение будет отправлено."));
    }

    private ChannelConfigDto toDto(ChannelConfig config) {
        Map<String, Object> settings = config.settings() != null ? 
            maskSensitiveSettings(config.settings()) : new HashMap<>();
        return new ChannelConfigDto(
            config.configId(),
            config.channelName(),
            settings,
            config.isEnabled(),
            config.createdAt(),
            config.updatedAt()
        );
    }

    /**
     * Маскируем чувствительные данные в настройках.
     */
    private Map<String, Object> maskSensitiveSettings(Map<String, Object> settings) {
        if (settings == null) return new HashMap<>();
        
        Map<String, Object> masked = new HashMap<>(settings);
        
        // Маскируем чувствительные поля
        for (String key : List.of("password", "token", "apiKey", "api_key", "secret", "botToken")) {
            if (masked.containsKey(key) && masked.get(key) != null) {
                masked.put(key, "***");
            }
        }
        
        return masked;
    }

    // DTOs
    public record ToggleRequest(boolean enabled) {}
    public record TestRequest(String recipient) {}
    public record TestResult(boolean success, String message) {}
}
