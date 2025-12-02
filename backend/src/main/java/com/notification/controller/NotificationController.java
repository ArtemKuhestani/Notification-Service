package com.notification.controller;

import com.notification.domain.entity.Notification;
import com.notification.dto.ApiErrorResponse;
import com.notification.dto.NotificationStatusResponse;
import com.notification.dto.SendNotificationRequest;
import com.notification.dto.SendNotificationResponse;
import com.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;
import java.util.UUID;

/**
 * REST контроллер для отправки уведомлений.
 * Используется внешними системами через API.
 */
@RestController
@RequestMapping("/api/v1")
@Tag(name = "Notifications", description = "API для отправки уведомлений")
@SecurityRequirement(name = "apiKey")
public class NotificationController {

    private static final Logger log = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Отправляет уведомление.
     */
    @PostMapping("/send")
    @Operation(summary = "Отправить уведомление", 
               description = "Создаёт и асинхронно отправляет уведомление по указанному каналу")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Уведомление принято в обработку",
            content = @Content(schema = @Schema(implementation = SendNotificationResponse.class))),
        @ApiResponse(responseCode = "400", description = "Невалидные данные",
            content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
        @ApiResponse(responseCode = "401", description = "Не авторизован"),
        @ApiResponse(responseCode = "429", description = "Превышен лимит запросов")
    })
    public ResponseEntity<SendNotificationResponse> sendNotification(
            @Valid @RequestBody SendNotificationRequest request,
            @RequestAttribute(name = "clientId", required = false) Integer clientId,
            HttpServletRequest httpRequest) {

        log.info("Received notification request: channel={}, recipient={}***", 
            request.channel(), 
            request.recipient() != null && request.recipient().length() > 2 
                ? request.recipient().substring(0, 2) : "");

        // Если clientId не установлен (для тестирования), используем 1
        if (clientId == null) {
            clientId = 1;
        }

        String clientIp = getClientIp(httpRequest);

        SendNotificationResponse response = notificationService.sendNotification(
            request, clientId, clientIp);

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Получает статус уведомления.
     */
    @GetMapping("/status/{id}")
    @Operation(summary = "Получить статус уведомления", 
               description = "Возвращает текущий статус отправки уведомления")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Статус уведомления",
            content = @Content(schema = @Schema(implementation = NotificationStatusResponse.class))),
        @ApiResponse(responseCode = "404", description = "Уведомление не найдено")
    })
    public ResponseEntity<NotificationStatusResponse> getStatus(
            @Parameter(description = "ID уведомления") @PathVariable("id") UUID notificationId) {

        Optional<Notification> notification = notificationService.getNotificationStatus(notificationId);

        if (notification.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Notification n = notification.get();
        NotificationStatusResponse response = new NotificationStatusResponse(
            n.notificationId(),
            n.status().name(),
            n.channelType().name(),
            maskRecipient(n.recipient()),
            n.createdAt(),
            n.sentAt(),
            n.retryCount(),
            n.errorMessage()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Повторно отправляет неудачное уведомление.
     */
    @PostMapping("/retry/{id}")
    @Operation(summary = "Повторить отправку уведомления", 
               description = "Повторно отправляет уведомление со статусом FAILED")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Повторная отправка запущена"),
        @ApiResponse(responseCode = "404", description = "Уведомление не найдено"),
        @ApiResponse(responseCode = "400", description = "Уведомление не может быть повторно отправлено")
    })
    public ResponseEntity<Void> retryNotification(
            @Parameter(description = "ID уведомления") @PathVariable("id") UUID notificationId) {

        boolean success = notificationService.retryNotification(notificationId);

        if (success) {
            return ResponseEntity.ok().build();
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String maskRecipient(String recipient) {
        if (recipient == null || recipient.length() < 4) {
            return "***";
        }
        if (recipient.contains("@")) {
            int atIndex = recipient.indexOf("@");
            if (atIndex > 2) {
                return recipient.substring(0, 2) + "***" + recipient.substring(atIndex);
            }
        }
        return recipient.substring(0, 2) + "***";
    }
}
