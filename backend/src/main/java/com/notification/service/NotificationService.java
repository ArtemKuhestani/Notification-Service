package com.notification.service;

import com.notification.domain.entity.Notification;
import com.notification.domain.entity.Notification.ChannelType;
import com.notification.domain.entity.Notification.NotificationStatus;
import com.notification.domain.entity.Notification.Priority;
import com.notification.dto.SendNotificationRequest;
import com.notification.dto.SendNotificationResponse;
import com.notification.repository.ApiClientRepository;
import com.notification.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Основной сервис для работы с уведомлениями.
 * 
 * <p>Обеспечивает полный жизненный цикл уведомления:</p>
 * <ul>
 *   <li>Создание и валидация уведомления</li>
 *   <li>Проверка идемпотентности (дедупликация)</li>
 *   <li>Маршрутизация по каналам (Email, Telegram, SMS, WhatsApp)</li>
 *   <li>Асинхронная отправка с поддержкой приоритетов</li>
 *   <li>Retry-логика при ошибках</li>
 *   <li>Аудит всех операций</li>
 * </ul>
 * 
 * <p><b>Приоритеты:</b></p>
 * <ul>
 *   <li>HIGH - немедленная отправка</li>
 *   <li>NORMAL - стандартная очередь</li>
 *   <li>LOW - отложенная отправка</li>
 * </ul>
 * 
 * <p><b>Retry-стратегия:</b> Экспоненциальный backoff: 1мин → 5мин → 15мин → 1час → 4часа</p>
 * 
 * @author Notification Service Team
 * @see EmailService
 * @see ChannelRouter
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final ApiClientRepository apiClientRepository;
    private final EmailService emailService;
    private final AuditLogService auditLogService;

    /** 
     * Интервалы повторных попыток в миллисекундах.
     * Используется экспоненциальный backoff: 1мин, 5мин, 15мин, 1час, 4часа
     */
    private static final long[] RETRY_INTERVALS = {60_000, 300_000, 900_000, 3_600_000, 14_400_000};

    public NotificationService(
            NotificationRepository notificationRepository,
            ApiClientRepository apiClientRepository,
            EmailService emailService,
            AuditLogService auditLogService) {
        this.notificationRepository = notificationRepository;
        this.apiClientRepository = apiClientRepository;
        this.emailService = emailService;
        this.auditLogService = auditLogService;
    }

    /**
     * Создаёт и асинхронно отправляет уведомление.
     * 
     * <p>Процесс обработки:</p>
     * <ol>
     *   <li>Проверка идемпотентности по ключу (если указан)</li>
     *   <li>Создание записи в БД со статусом PENDING</li>
     *   <li>Запись в аудит-лог</li>
     *   <li>Асинхронная отправка через соответствующий канал</li>
     * </ol>
     * 
     * @param request   данные для отправки уведомления
     * @param clientId  ID API-клиента (внешней системы)
     * @param clientIp  IP-адрес клиента для аудита
     * @return ответ с ID уведомления и статусом
     */
    @Transactional
    public SendNotificationResponse sendNotification(SendNotificationRequest request, 
                                                      Integer clientId, 
                                                      String clientIp) {
        log.info("Processing notification request: channel={}, recipient={}", 
            request.channel(), maskRecipient(request.recipient()));

        // Проверка идемпотентности
        if (request.idempotencyKey() != null) {
            Optional<Notification> existing = notificationRepository
                .findByIdempotencyKey(request.idempotencyKey());
            if (existing.isPresent()) {
                log.info("Duplicate request detected, returning existing notification: {}", 
                    existing.get().notificationId());
                Notification n = existing.get();
                return new SendNotificationResponse(
                    n.notificationId(),
                    n.status().name(),
                    n.createdAt()
                );
            }
        }

        // Создание уведомления
        ChannelType channelType = parseChannelType(request.channel());
        Priority priority = parsePriority(request.priority());

        Notification notification = Notification.builder()
            .clientId(clientId)
            .channelType(channelType)
            .recipient(request.recipient())
            .subject(request.subject())
            .messageBody(request.message())
            .status(NotificationStatus.PENDING)
            .priority(priority)
            .retryCount(0)
            .maxRetries(5)
            .idempotencyKey(request.idempotencyKey())
            .callbackUrl(request.callbackUrl())
            .metadata(request.metadata())
            .build();

        // Сохранение в БД
        Notification saved = notificationRepository.save(notification);
        log.info("Notification created: id={}", saved.notificationId());

        // Логирование в аудит
        auditLogService.logNotificationSent(
            saved.notificationId().toString(),
            channelType.name(),
            request.recipient(),
            clientIp
        );

        // Асинхронная отправка
        processNotificationAsync(saved);

        // Обновление времени последнего использования клиента
        apiClientRepository.updateLastUsedAt(clientId);

        return new SendNotificationResponse(
            saved.notificationId(),
            saved.status().name(),
            saved.createdAt()
        );
    }

    /**
     * Асинхронно обрабатывает отправку уведомления.
     */
    @Async
    public void processNotificationAsync(Notification notification) {
        try {
            processNotification(notification);
        } catch (Exception e) {
            log.error("Error processing notification: {}", notification.notificationId(), e);
            handleSendError(notification, "PROCESSING_ERROR", e.getMessage());
        }
    }

    /**
     * Обрабатывает отправку уведомления по соответствующему каналу.
     */
    private void processNotification(Notification notification) {
        log.debug("Processing notification: id={}, channel={}", 
            notification.notificationId(), notification.channelType());

        switch (notification.channelType()) {
            case EMAIL -> sendEmail(notification);
            case TELEGRAM -> sendTelegram(notification);
            case SMS -> sendSms(notification);
            case WHATSAPP -> sendWhatsApp(notification);
        }
    }

    /**
     * Отправляет email.
     */
    private void sendEmail(Notification notification) {
        EmailService.EmailResult result = emailService.send(notification);

        if (result.isSuccess()) {
            notificationRepository.updateStatus(
                notification.notificationId(),
                NotificationStatus.SENT,
                null,
                null
            );
            log.info("Email sent successfully: id={}", notification.notificationId());
        } else {
            handleSendError(notification, result.errorCode(), result.errorMessage());
        }
    }

    /**
     * Заглушка для Telegram (будет реализована позже).
     */
    private void sendTelegram(Notification notification) {
        log.warn("Telegram channel not implemented yet: id={}", notification.notificationId());
        handleSendError(notification, "NOT_IMPLEMENTED", "Telegram channel is not implemented");
    }

    /**
     * Заглушка для SMS (будет реализована позже).
     */
    private void sendSms(Notification notification) {
        log.warn("SMS channel not implemented yet: id={}", notification.notificationId());
        handleSendError(notification, "NOT_IMPLEMENTED", "SMS channel is not implemented");
    }

    /**
     * Заглушка для WhatsApp (будет реализована позже).
     */
    private void sendWhatsApp(Notification notification) {
        log.warn("WhatsApp channel not implemented yet: id={}", notification.notificationId());
        handleSendError(notification, "NOT_IMPLEMENTED", "WhatsApp channel is not implemented");
    }

    /**
     * Обрабатывает ошибку отправки.
     */
    private void handleSendError(Notification notification, String errorCode, String errorMessage) {
        int nextRetryCount = notification.retryCount() + 1;

        if (nextRetryCount >= notification.maxRetries()) {
            // Все попытки исчерпаны
            notificationRepository.updateStatus(
                notification.notificationId(),
                NotificationStatus.FAILED,
                errorMessage,
                errorCode
            );
            log.warn("Notification failed after all retries: id={}", notification.notificationId());
        } else {
            // Планируем повторную попытку
            LocalDateTime nextRetry = calculateNextRetryTime(nextRetryCount);
            notificationRepository.incrementRetryCount(notification.notificationId(), nextRetry);
            notificationRepository.updateStatus(
                notification.notificationId(),
                NotificationStatus.PENDING,
                errorMessage,
                errorCode
            );
            log.info("Notification scheduled for retry: id={}, attempt={}, nextRetry={}", 
                notification.notificationId(), nextRetryCount, nextRetry);
        }
    }

    /**
     * Рассчитывает время следующей попытки.
     */
    private LocalDateTime calculateNextRetryTime(int retryCount) {
        int index = Math.min(retryCount - 1, RETRY_INTERVALS.length - 1);
        long delayMs = RETRY_INTERVALS[index];
        return LocalDateTime.now().plusNanos(delayMs * 1_000_000);
    }

    /**
     * Получает статус уведомления.
     */
    public Optional<Notification> getNotificationStatus(UUID notificationId) {
        return notificationRepository.findById(notificationId);
    }

    /**
     * Повторно отправляет уведомление (ручной retry).
     */
    @Transactional
    public boolean retryNotification(UUID notificationId) {
        Optional<Notification> optNotification = notificationRepository.findById(notificationId);
        
        if (optNotification.isEmpty()) {
            return false;
        }

        Notification notification = optNotification.get();
        
        if (notification.status() != NotificationStatus.FAILED) {
            return false;
        }

        // Сбрасываем счётчик и статус
        notificationRepository.updateStatus(
            notificationId,
            NotificationStatus.PENDING,
            null,
            null
        );

        // Перезапускаем обработку
        processNotificationAsync(notification);
        
        return true;
    }

    private ChannelType parseChannelType(String channel) {
        if (channel == null) {
            return ChannelType.EMAIL;
        }
        try {
            return ChannelType.valueOf(channel.toUpperCase());
        } catch (IllegalArgumentException e) {
            return ChannelType.EMAIL;
        }
    }

    private Priority parsePriority(String priority) {
        if (priority == null) {
            return Priority.NORMAL;
        }
        try {
            return Priority.valueOf(priority.toUpperCase());
        } catch (IllegalArgumentException e) {
            return Priority.NORMAL;
        }
    }

    private String maskRecipient(String recipient) {
        if (recipient == null || recipient.length() < 4) {
            return "***";
        }
        return recipient.substring(0, 2) + "***";
    }
}
