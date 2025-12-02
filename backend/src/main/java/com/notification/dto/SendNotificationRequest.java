package com.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * DTO для запроса отправки уведомления.
 * 
 * <p>Поддерживает два режима работы:</p>
 * <ol>
 *   <li><b>Прямая отправка</b>: заполняются subject и message</li>
 *   <li><b>Шаблоны</b>: заполняется templateCode и templateVariables</li>
 * </ol>
 * 
 * <h3>Пример JSON-запроса (прямая отправка):</h3>
 * <pre>{@code
 * {
 *   "channel": "EMAIL",
 *   "recipient": "user@example.com",
 *   "subject": "Тема письма",
 *   "message": "Текст сообщения",
 *   "priority": "HIGH"
 * }
 * }</pre>
 * 
 * <h3>Пример JSON-запроса (шаблон):</h3>
 * <pre>{@code
 * {
 *   "channel": "TELEGRAM",
 *   "recipient": "123456789",
 *   "templateCode": "order-confirmation",
 *   "templateVariables": {
 *     "orderNumber": "12345",
 *     "customerName": "Иван"
 *   }
 * }
 * }</pre>
 * 
 * @param channel            канал отправки: EMAIL, TELEGRAM, SMS, WHATSAPP
 * @param recipient          получатель (email, chat_id, телефон)
 * @param subject            тема сообщения (для email)
 * @param message            текст сообщения (если не используется шаблон)
 * @param templateCode       код шаблона для использования
 * @param templateVariables  переменные для подстановки в шаблон
 * @param priority           приоритет: HIGH, NORMAL, LOW
 * @param idempotencyKey     ключ для дедупликации повторных запросов
 * @param callbackUrl        URL для webhook при изменении статуса
 * @param metadata           дополнительные данные
 */
public record SendNotificationRequest(
    
    @NotBlank(message = "Канал обязателен")
    String channel,
    
    @NotBlank(message = "Получатель обязателен")
    @Size(max = 255, message = "Получатель не должен превышать 255 символов")
    String recipient,
    
    @Size(max = 500, message = "Тема не должна превышать 500 символов")
    String subject,
    
    String message,
    
    /** Код шаблона. Если указан, subject и message формируются из шаблона. */
    String templateCode,
    
    /** Переменные для подстановки в шаблон: {{key}} → value */
    Map<String, String> templateVariables,
    
    /** Приоритет обработки: HIGH (немедленно), NORMAL, LOW */
    String priority,
    
    @Size(max = 255, message = "Ключ идемпотентности не должен превышать 255 символов")
    String idempotencyKey,
    
    @Size(max = 500, message = "Callback URL не должен превышать 500 символов")
    String callbackUrl,
    
    /** Произвольные метаданные для хранения */
    Map<String, Object> metadata
) {
    /**
     * Фабричный метод для создания email-запроса.
     */
    public static SendNotificationRequest email(String recipient, String subject, String message) {
        return new SendNotificationRequest(
            "EMAIL", recipient, subject, message, 
            null, null,
            "NORMAL", null, null, null
        );
    }

    /**
     * Создаёт запрос для отправки по шаблону.
     */
    public static SendNotificationRequest withTemplate(String channel, String recipient, 
                                                        String templateCode, 
                                                        Map<String, String> variables) {
        return new SendNotificationRequest(
            channel, recipient, null, null,
            templateCode, variables,
            "NORMAL", null, null, null
        );
    }
}
