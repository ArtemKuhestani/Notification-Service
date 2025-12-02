package com.notification.service.channel;

/**
 * Интерфейс для адаптеров каналов отправки уведомлений.
 * 
 * <p>Реализует паттерн Strategy, позволяя динамически выбирать
 * канал отправки во время выполнения.</p>
 * 
 * <h3>Реализации:</h3>
 * <ul>
 *   <li>{@link EmailChannelAdapter} - отправка через SMTP</li>
 *   <li>{@link TelegramChannelAdapter} - отправка через Telegram Bot API</li>
 *   <li>{@link SmsChannelAdapter} - отправка через SMS-провайдера</li>
 *   <li>{@link WhatsAppChannelAdapter} - отправка через WhatsApp Business API</li>
 * </ul>
 * 
 * <h3>Пример реализации:</h3>
 * <pre>{@code
 * @Component
 * public class MyChannelAdapter implements ChannelAdapter {
 *     
 *     @Override
 *     public String send(String recipient, String subject, String message) 
 *             throws ChannelException {
 *         // Логика отправки
 *         return providerMessageId;
 *     }
 *     
 *     @Override
 *     public String getChannelName() { return "MY_CHANNEL"; }
 *     // ...
 * }
 * }</pre>
 * 
 * @author Notification Service Team
 * @see ChannelRouter
 * @see ChannelException
 */
public interface ChannelAdapter {
    
    /**
     * Отправляет сообщение через канал.
     * 
     * @param recipient получатель (email, chat_id, телефон)
     * @param subject   тема сообщения (может быть null для некоторых каналов)
     * @param message   текст сообщения
     * @return ID сообщения от провайдера (если есть), null если провайдер не возвращает ID
     * @throws ChannelException при ошибке отправки
     */
    String send(String recipient, String subject, String message) throws ChannelException;
    
    /**
     * Проверяет работоспособность канала.
     * 
     * <p>Может выполнять реальную проверку подключения или
     * просто проверять наличие необходимых настроек.</p>
     * 
     * @return true если канал работоспособен
     */
    boolean healthCheck();
    
    /**
     * Возвращает уникальное имя канала.
     * 
     * @return имя канала в верхнем регистре (EMAIL, TELEGRAM, SMS, WHATSAPP)
     */
    String getChannelName();
    
    /**
     * Проверяет, включен ли канал для отправки.
     * 
     * @return true если канал включен
     */
    boolean isEnabled();
    
    /**
     * Проверяет наличие необходимых настроек для работы канала.
     * 
     * <p>Например, для Email должны быть настроены SMTP-параметры,
     * для Telegram - токен бота.</p>
     * 
     * @return true если канал корректно настроен
     */
    default boolean isConfigured() {
        return true;
    }
}
