package com.notification.service.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Маршрутизатор для выбора и управления каналами отправки уведомлений.
 * 
 * <p>Реализует паттерн Strategy для абстрагирования логики отправки
 * по различным каналам (Email, Telegram, SMS, WhatsApp).</p>
 * 
 * <h3>Основные функции:</h3>
 * <ul>
 *   <li>Динамическая регистрация адаптеров каналов</li>
 *   <li>Маршрутизация сообщений на нужный канал</li>
 *   <li>Поддержка fallback-каналов при ошибках</li>
 *   <li>Health-check всех каналов</li>
 * </ul>
 * 
 * <h3>Fallback-цепочка по умолчанию:</h3>
 * <pre>
 * EMAIL    → SMS
 * TELEGRAM → EMAIL  
 * WHATSAPP → TELEGRAM
 * SMS      → EMAIL
 * </pre>
 * 
 * @author Notification Service Team
 * @see ChannelAdapter
 * @see EmailChannelAdapter
 * @see TelegramChannelAdapter
 */
@Service
public class ChannelRouter {
    
    private static final Logger log = LoggerFactory.getLogger(ChannelRouter.class);
    
    /** Зарегистрированные адаптеры каналов */
    private final Map<String, ChannelAdapter> adapters = new HashMap<>();
    
    /** Fallback-цепочка по умолчанию */
    private static final Map<String, String> DEFAULT_FALLBACK = Map.of(
        "EMAIL", "SMS",
        "TELEGRAM", "EMAIL",
        "WHATSAPP", "TELEGRAM",
        "SMS", "EMAIL"
    );
    
    /**
     * Конструктор с автоматической регистрацией всех адаптеров.
     * 
     * @param channelAdapters список адаптеров из Spring контекста
     */
    public ChannelRouter(List<ChannelAdapter> channelAdapters) {
        for (ChannelAdapter adapter : channelAdapters) {
            adapters.put(adapter.getChannelName(), adapter);
            log.info("Registered channel adapter: {}", adapter.getChannelName());
        }
    }
    
    /**
     * Получает адаптер для указанного канала.
     * 
     * @param channelName имя канала (EMAIL, TELEGRAM, SMS, WHATSAPP)
     * @return Optional с адаптером или empty если канал не найден
     */
    public Optional<ChannelAdapter> getAdapter(String channelName) {
        return Optional.ofNullable(adapters.get(channelName.toUpperCase()));
    }
    
    /**
     * Отправляет сообщение через указанный канал.
     * 
     * @param channel   канал отправки
     * @param recipient получатель
     * @param subject   тема (для email)
     * @param message   текст сообщения
     * @return результат отправки
     */
    public SendResult send(String channel, String recipient, String subject, String message) {
        ChannelAdapter adapter = adapters.get(channel.toUpperCase());
        
        if (adapter == null) {
            return new SendResult(false, null, "Unknown channel: " + channel, "UNKNOWN_CHANNEL", false);
        }
        
        if (!adapter.isEnabled()) {
            return new SendResult(false, null, "Channel disabled: " + channel, "CHANNEL_DISABLED", false);
        }
        
        try {
            String providerMessageId = adapter.send(recipient, subject, message);
            return new SendResult(true, providerMessageId, null, null, false);
            
        } catch (ChannelException e) {
            log.error("Channel {} failed: {}", channel, e.getMessage());
            return new SendResult(false, null, e.getMessage(), e.getErrorCode(), e.isRetryable());
        }
    }
    
    /**
     * Отправляет сообщение с поддержкой fallback-канала.
     * 
     * <p>При ошибке основного канала автоматически пробует fallback.</p>
     * 
     * @param primaryChannel  основной канал
     * @param fallbackChannel резервный канал (может быть null)
     * @param recipient       получатель
     * @param subject         тема
     * @param message         сообщение
     * @return результат отправки с информацией об использованном канале
     */
    public SendResult sendWithFallback(
            String primaryChannel, 
            String fallbackChannel,
            String recipient, 
            String subject, 
            String message
    ) {
        // Пробуем основной канал
        SendResult result = send(primaryChannel, recipient, subject, message);
        
        if (result.success()) {
            return result;
        }
        
        // Если есть fallback и ошибка не recoverable
        if (fallbackChannel != null && !fallbackChannel.isEmpty()) {
            log.info("Primary channel {} failed, trying fallback: {}", primaryChannel, fallbackChannel);
            
            SendResult fallbackResult = send(fallbackChannel, recipient, subject, message);
            if (fallbackResult.success()) {
                return new SendResult(
                    true, 
                    fallbackResult.providerMessageId(), 
                    null, 
                    null, 
                    false,
                    fallbackChannel // Помечаем что использовали fallback
                );
            }
            
            return fallbackResult;
        }
        
        return result;
    }
    
    /**
     * Получает fallback-канал по умолчанию для указанного канала.
     * 
     * @param channel исходный канал
     * @return fallback-канал или null
     */
    public String getDefaultFallback(String channel) {
        return DEFAULT_FALLBACK.get(channel.toUpperCase());
    }
    
    /**
     * Проверяет здоровье всех зарегистрированных каналов.
     * 
     * @return карта: канал → статус (true = работает)
     */
    public Map<String, Boolean> healthCheckAll() {
        Map<String, Boolean> results = new HashMap<>();
        for (Map.Entry<String, ChannelAdapter> entry : adapters.entrySet()) {
            results.put(entry.getKey(), entry.getValue().healthCheck());
        }
        return results;
    }
    
    /**
     * Результат отправки сообщения.
     * 
     * @param success           успешность отправки
     * @param providerMessageId ID сообщения от провайдера
     * @param errorMessage      текст ошибки
     * @param errorCode         код ошибки
     * @param retryable         можно ли повторить
     * @param usedChannel       фактически использованный канал (при fallback)
     */
    public record SendResult(
        boolean success,
        String providerMessageId,
        String errorMessage,
        String errorCode,
        boolean retryable,
        String usedChannel
    ) {
        public SendResult(boolean success, String providerMessageId, String errorMessage, String errorCode, boolean retryable) {
            this(success, providerMessageId, errorMessage, errorCode, retryable, null);
        }
    }
}
