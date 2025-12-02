package com.notification.service;

import com.notification.domain.entity.Notification;
import com.notification.repository.NotificationRepository;
import com.notification.service.channel.ChannelRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Сервис для повторной отправки неудачных уведомлений.
 * Реализует экспоненциальную задержку: 1мин, 5мин, 15мин, 1час, 4часа.
 */
@Service
public class RetryService {
    
    private static final Logger log = LoggerFactory.getLogger(RetryService.class);
    
    // Интервалы повторных попыток в минутах
    private static final int[] RETRY_INTERVALS = {1, 5, 15, 60, 240}; // 1мин, 5мин, 15мин, 1час, 4часа
    private static final int MAX_RETRIES = 5;
    
    private final NotificationRepository notificationRepository;
    private final ChannelRouter channelRouter;
    private final AuditLogService auditLogService;
    private final WebhookService webhookService;
    
    public RetryService(
            NotificationRepository notificationRepository,
            ChannelRouter channelRouter,
            AuditLogService auditLogService,
            WebhookService webhookService
    ) {
        this.notificationRepository = notificationRepository;
        this.channelRouter = channelRouter;
        this.auditLogService = auditLogService;
        this.webhookService = webhookService;
    }
    
    /**
     * Запускается каждую минуту для обработки очереди повторных отправок.
     */
    @Scheduled(fixedDelay = 60000) // каждую минуту
    @Transactional
    public void processRetryQueue() {
        List<Notification> pendingRetries = notificationRepository.findPendingRetries(LocalDateTime.now());
        
        if (pendingRetries.isEmpty()) {
            return;
        }
        
        log.info("Processing {} pending retries", pendingRetries.size());
        
        for (Notification notification : pendingRetries) {
            processRetry(notification);
        }
    }
    
    /**
     * Обрабатывает одну повторную попытку.
     */
    private void processRetry(Notification notification) {
        log.info("Retrying notification {}, attempt {}", 
            notification.notificationId(), notification.retryCount() + 1);
        
        // Обновляем статус на SENDING
        notificationRepository.updateStatus(notification.notificationId().toString(), "SENDING");
        
        // Пробуем отправить с fallback
        String channelName = notification.channelType().name();
        String fallbackChannel = channelRouter.getDefaultFallback(channelName);
        
        ChannelRouter.SendResult result = channelRouter.sendWithFallback(
            channelName,
            fallbackChannel,
            notification.recipient(),
            notification.subject(),
            notification.messageBody()
        );
        
        if (result.success()) {
            // Успешно отправлено
            notificationRepository.updateStatus(notification.notificationId().toString(), "SENT");
            notificationRepository.updateSentAt(notification.notificationId().toString(), LocalDateTime.now());
            
            if (result.providerMessageId() != null) {
                notificationRepository.updateProviderMessageId(
                    notification.notificationId().toString(), 
                    result.providerMessageId()
                );
            }
            
            log.info("Retry successful for notification {}", notification.notificationId());
            
            // Отправляем webhook
            webhookService.sendWebhook(notification, "SENT", result.usedChannel());
            
        } else {
            // Не удалось отправить
            handleRetryFailure(notification, result);
        }
    }
    
    /**
     * Обрабатывает неудачную повторную попытку.
     */
    private void handleRetryFailure(Notification notification, ChannelRouter.SendResult result) {
        int newRetryCount = notification.retryCount() + 1;
        
        if (newRetryCount >= MAX_RETRIES || !result.retryable()) {
            // Превышено количество попыток или ошибка не подлежит повтору
            notificationRepository.updateStatus(notification.notificationId().toString(), "FAILED");
            notificationRepository.updateError(
                notification.notificationId().toString(), 
                result.errorMessage(), 
                result.errorCode()
            );
            
            log.error("Notification {} failed permanently after {} attempts: {}", 
                notification.notificationId(), newRetryCount, result.errorMessage());
            
            // Отправляем webhook об ошибке
            webhookService.sendWebhook(notification, "FAILED", null);
            
        } else {
            // Планируем следующую попытку
            LocalDateTime nextRetry = calculateNextRetry(newRetryCount);
            
            notificationRepository.updateForRetry(
                notification.notificationId().toString(),
                newRetryCount,
                nextRetry,
                result.errorMessage(),
                result.errorCode()
            );
            
            log.info("Notification {} scheduled for retry at {}", 
                notification.notificationId(), nextRetry);
        }
    }
    
    /**
     * Вычисляет время следующей попытки на основе экспоненциальной задержки.
     */
    public LocalDateTime calculateNextRetry(int retryCount) {
        int intervalIndex = Math.min(retryCount - 1, RETRY_INTERVALS.length - 1);
        int delayMinutes = RETRY_INTERVALS[intervalIndex];
        return LocalDateTime.now().plusMinutes(delayMinutes);
    }
    
    /**
     * Принудительная повторная отправка уведомления.
     */
    @Transactional
    public void forceRetry(String notificationId, Integer adminId, String ipAddress) {
        var notification = notificationRepository.findById(notificationId)
            .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        
        // Сбрасываем счётчик попыток и ставим в очередь
        notificationRepository.updateForRetry(
            notificationId,
            0,
            LocalDateTime.now(),
            null,
            null
        );
        notificationRepository.updateStatus(notificationId, "PENDING");
        
        // Логируем действие администратора
        auditLogService.logAction(
            adminId,
            "RETRY_NOTIFICATION",
            "notification",
            notificationId,
            null,
            null,
            ipAddress,
            null
        );
        
        log.info("Notification {} queued for forced retry by admin {}", notificationId, adminId);
    }
    
    /**
     * Получает максимальное количество попыток.
     */
    public int getMaxRetries() {
        return MAX_RETRIES;
    }
}
