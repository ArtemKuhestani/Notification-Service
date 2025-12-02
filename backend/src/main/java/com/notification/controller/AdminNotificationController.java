package com.notification.controller;

import com.notification.domain.entity.Notification;
import com.notification.dto.*;
import com.notification.repository.NotificationRepository;
import com.notification.service.NotificationService;
import com.notification.service.RetryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Административный контроллер для управления уведомлениями.
 */
@RestController
@RequestMapping("/api/v1/admin/notifications")
@Tag(name = "Admin - Notifications", description = "Административные API для управления уведомлениями")
@SecurityRequirement(name = "bearerAuth")
public class AdminNotificationController {
    
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final RetryService retryService;
    
    public AdminNotificationController(
            NotificationRepository notificationRepository,
            NotificationService notificationService,
            RetryService retryService
    ) {
        this.notificationRepository = notificationRepository;
        this.notificationService = notificationService;
        this.retryService = retryService;
    }
    
    /**
     * DTO для тестовой отправки уведомления.
     */
    public record TestNotificationRequest(
        @NotBlank(message = "Канал обязателен")
        String channel,
        @NotBlank(message = "Получатель обязателен")
        String recipient,
        String subject,
        @NotBlank(message = "Сообщение обязательно")
        String message
    ) {}
    
    @PostMapping("/test")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Тестовая отправка", description = "Отправка тестового уведомления из админ панели")
    public ResponseEntity<Map<String, Object>> sendTestNotification(
            @Valid @RequestBody TestNotificationRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipAddress = getClientIp(httpRequest);
        
        // Создаем запрос для NotificationService
        // Используем clientId = 0 для тестовых уведомлений из админки
        SendNotificationRequest sendRequest = new SendNotificationRequest(
            request.channel(),
            request.recipient(),
            request.subject(),
            request.message(),
            null, // templateCode
            null, // templateVariables  
            "NORMAL", // priority
            "test-" + System.currentTimeMillis(), // idempotencyKey
            null, // callbackUrl
            Map.of("source", "admin_panel", "type", "test") // metadata
        );
        
        try {
            SendNotificationResponse response = notificationService.sendNotification(
                sendRequest, 
                0, // Admin test - используем 0 как системный clientId
                ipAddress
            );
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Тестовое уведомление отправлено",
                "notificationId", response.notificationId().toString(),
                "status", response.status()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", "Ошибка отправки: " + e.getMessage()
            ));
        }
    }
    
    @GetMapping
    @Operation(summary = "Список уведомлений", description = "Получение списка уведомлений с фильтрацией")
    public ResponseEntity<PagedResponse<NotificationDto>> getNotifications(
            @RequestParam(required = false) Integer clientId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String channel,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int offset = page * size;
        List<Notification> notifications = notificationRepository.findAll(
            clientId, status, channel, from, to, offset, size
        );
        long total = notificationRepository.count(clientId, status, channel, from, to);
        
        List<NotificationDto> dtos = notifications.stream()
            .map(this::toDto)
            .toList();
        
        return ResponseEntity.ok(new PagedResponse<>(
            dtos, page, size, total, (int) Math.ceil((double) total / size)
        ));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Детали уведомления", description = "Получение полной информации об уведомлении")
    public ResponseEntity<NotificationDto> getNotification(@PathVariable String id) {
        return notificationRepository.findById(id)
            .map(this::toDto)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Повторная отправка", description = "Ручной перезапуск отправки уведомления")
    public ResponseEntity<Map<String, String>> retryNotification(
            @PathVariable String id,
            HttpServletRequest request
    ) {
        Integer adminId = (Integer) request.getAttribute("adminId");
        String ipAddress = getClientIp(request);
        
        retryService.forceRetry(id, adminId, ipAddress);
        
        return ResponseEntity.ok(Map.of(
            "message", "Уведомление поставлено в очередь на повторную отправку",
            "notificationId", id
        ));
    }
    
    private NotificationDto toDto(Notification n) {
        return new NotificationDto(
            n.notificationId().toString(),
            n.clientId(),
            n.channelType().name(),
            maskRecipient(n.recipient(), n.channelType().name()),
            n.subject(),
            n.status().name(),
            n.priority().name(),
            n.retryCount(),
            n.errorMessage(),
            n.errorCode(),
            n.providerMessageId(),
            n.createdAt(),
            n.sentAt()
        );
    }
    
    private String maskRecipient(String recipient, String channelType) {
        if (recipient == null) return "***";
        
        return switch (channelType) {
            case "EMAIL" -> {
                int atIndex = recipient.indexOf("@");
                if (atIndex <= 2) yield "***" + recipient.substring(atIndex);
                yield recipient.substring(0, 2) + "***" + recipient.substring(atIndex);
            }
            default -> {
                if (recipient.length() < 6) yield "***";
                yield recipient.substring(0, 4) + "***" + recipient.substring(recipient.length() - 2);
            }
        };
    }
    
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
