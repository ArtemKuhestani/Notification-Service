package com.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.domain.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Сервис для отправки webhook-уведомлений клиентам.
 */
@Service
public class WebhookService {
    
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final String WEBHOOK_SECRET = "notification-service-webhook-secret";
    
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    public WebhookService(ObjectMapper objectMapper) {
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }
    
    /**
     * Отправляет webhook асинхронно.
     */
    @Async
    public void sendWebhook(Notification notification, String event, String usedChannel) {
        if (notification.callbackUrl() == null || notification.callbackUrl().isEmpty()) {
            return;
        }
        
        try {
            Map<String, Object> payload = buildPayload(notification, event, usedChannel);
            String jsonPayload = objectMapper.writeValueAsString(payload);
            
            // Генерируем подпись
            String signature = generateSignature(jsonPayload);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-Webhook-Signature", signature);
            headers.set("X-Webhook-Timestamp", String.valueOf(System.currentTimeMillis()));
            headers.set("X-Webhook-Event", event);
            
            HttpEntity<String> request = new HttpEntity<>(jsonPayload, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                notification.callbackUrl(), 
                request, 
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Webhook sent successfully for notification {} to {}", 
                    notification.notificationId(), notification.callbackUrl());
            } else {
                log.warn("Webhook returned non-2xx status {} for notification {}", 
                    response.getStatusCode(), notification.notificationId());
            }
            
        } catch (Exception e) {
            log.error("Failed to send webhook for notification {}: {}", 
                notification.notificationId(), e.getMessage());
        }
    }
    
    /**
     * Строит payload для webhook.
     */
    private Map<String, Object> buildPayload(Notification notification, String event, String usedChannel) {
        Map<String, Object> payload = new HashMap<>();
        
        payload.put("event", event);
        payload.put("notification_id", notification.notificationId().toString());
        payload.put("channel", usedChannel != null ? usedChannel : notification.channelType().name());
        payload.put("recipient", maskRecipient(notification.recipient(), notification.channelType().name()));
        payload.put("status", event);
        payload.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        if (notification.metadata() != null) {
            payload.put("metadata", notification.metadata());
        }
        
        if ("FAILED".equals(event)) {
            payload.put("error_message", notification.errorMessage());
            payload.put("error_code", notification.errorCode());
            payload.put("retry_count", notification.retryCount());
        }
        
        if ("SENT".equals(event) && notification.providerMessageId() != null) {
            payload.put("provider_message_id", notification.providerMessageId());
        }
        
        return payload;
    }
    
    /**
     * Генерирует HMAC-SHA256 подпись для верификации webhook.
     */
    private String generateSignature(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKeySpec = new SecretKeySpec(
                WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), 
                "HmacSHA256"
            );
            mac.init(secretKeySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return "sha256=" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            log.error("Failed to generate webhook signature", e);
            return "";
        }
    }
    
    /**
     * Маскирует получателя для безопасности.
     */
    private String maskRecipient(String recipient, String channelType) {
        if (recipient == null) return "***";
        
        return switch (channelType) {
            case "EMAIL" -> {
                int atIndex = recipient.indexOf("@");
                if (atIndex <= 2) yield "***" + recipient.substring(atIndex);
                yield recipient.substring(0, 2) + "***" + recipient.substring(atIndex);
            }
            case "TELEGRAM" -> {
                if (recipient.length() < 4) yield "***";
                yield recipient.substring(0, 2) + "***" + recipient.substring(recipient.length() - 2);
            }
            default -> {
                if (recipient.length() < 6) yield "***";
                yield recipient.substring(0, 4) + "***" + recipient.substring(recipient.length() - 2);
            }
        };
    }
}
