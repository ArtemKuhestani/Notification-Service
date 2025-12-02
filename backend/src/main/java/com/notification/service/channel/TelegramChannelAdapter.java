package com.notification.service.channel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.repository.ChannelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Адаптер для отправки уведомлений через Telegram Bot API.
 * 
 * <p>Использует Telegram Bot API для отправки сообщений пользователям.
 * Поддерживает Markdown-форматирование.</p>
 * 
 * <h3>Настройка:</h3>
 * <ol>
 *   <li>Создайте бота через @BotFather</li>
 *   <li>Получите токен бота</li>
 *   <li>Укажите токен в переменной TELEGRAM_BOT_TOKEN</li>
 * </ol>
 * 
 * <h3>Формат получателя:</h3>
 * <p>Chat ID пользователя (число). Получить можно через @userinfobot.</p>
 * 
 * <h3>Формат сообщения:</h3>
 * <p>Поддерживается Markdown: *bold*, _italic_, `code`, [link](url)</p>
 * 
 * @author Notification Service Team
 * @see ChannelAdapter
 * @see <a href="https://core.telegram.org/bots/api">Telegram Bot API</a>
 */
@Component
public class TelegramChannelAdapter implements ChannelAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(TelegramChannelAdapter.class);
    private static final String CHANNEL_NAME = "TELEGRAM";
    private static final String TELEGRAM_API_URL = "https://api.telegram.org/bot";
    
    private final ChannelConfigRepository channelConfigRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${telegram.bot.token:}")
    private String defaultBotToken;
    
    public TelegramChannelAdapter(
            ChannelConfigRepository channelConfigRepository,
            ObjectMapper objectMapper
    ) {
        this.channelConfigRepository = channelConfigRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }
    
    /**
     * Отправляет сообщение в Telegram.
     * 
     * @param recipient chat_id получателя
     * @param subject   заголовок (будет выделен жирным)
     * @param message   текст сообщения (поддерживается Markdown)
     * @return message_id отправленного сообщения
     * @throws ChannelException при ошибке API
     */
    @Override
    public String send(String recipient, String subject, String message) throws ChannelException {
        log.info("Sending Telegram message to chat_id: {}", maskChatId(recipient));
        
        String botToken = getBotToken();
        if (botToken == null || botToken.isEmpty()) {
            throw new ChannelException("Telegram bot token not configured", "CONFIG_ERROR", false);
        }
        
        try {
            String url = TELEGRAM_API_URL + botToken + "/sendMessage";
            
            // Формируем текст сообщения с темой если есть
            String fullMessage = subject != null && !subject.isEmpty() 
                ? "*" + escapeMarkdown(subject) + "*\n\n" + message 
                : message;
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("chat_id", recipient);
            requestBody.put("text", fullMessage);
            requestBody.put("parse_mode", "Markdown");
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                if (jsonResponse.has("ok") && jsonResponse.get("ok").asBoolean()) {
                    String messageId = jsonResponse.path("result").path("message_id").asText();
                    log.info("Telegram message sent successfully, message_id: {}", messageId);
                    return messageId;
                } else {
                    String description = jsonResponse.path("description").asText("Unknown error");
                    throw new ChannelException("Telegram API error: " + description, "API_ERROR", true);
                }
            }
            
            throw new ChannelException("Unexpected response from Telegram API", "RESPONSE_ERROR", true);
            
        } catch (HttpClientErrorException e) {
            log.error("Telegram client error: {} - {}", e.getStatusCode(), e.getMessage());
            boolean retryable = e.getStatusCode() != HttpStatus.BAD_REQUEST 
                && e.getStatusCode() != HttpStatus.FORBIDDEN;
            throw new ChannelException("Telegram error: " + e.getMessage(), "CLIENT_ERROR", retryable, e);
            
        } catch (HttpServerErrorException e) {
            log.error("Telegram server error: {}", e.getMessage());
            throw new ChannelException("Telegram server error: " + e.getMessage(), "SERVER_ERROR", true, e);
            
        } catch (Exception e) {
            log.error("Failed to send Telegram message: {}", e.getMessage());
            throw new ChannelException("Telegram sending failed: " + e.getMessage(), e);
        }
    }
    
    /**
     * Проверяет работоспособность через getMe API.
     */
    @Override
    public boolean healthCheck() {
        String botToken = getBotToken();
        if (botToken == null || botToken.isEmpty()) {
            return false;
        }
        
        try {
            String url = TELEGRAM_API_URL + botToken + "/getMe";
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                return jsonResponse.has("ok") && jsonResponse.get("ok").asBoolean();
            }
            return false;
        } catch (Exception e) {
            log.warn("Telegram health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }
    
    @Override
    public boolean isEnabled() {
        return channelConfigRepository.findByChannelName(CHANNEL_NAME)
            .map(config -> config.isEnabled())
            .orElse(false);
    }
    
    private String getBotToken() {
        // Сначала пробуем получить из БД
        return channelConfigRepository.findByChannelName(CHANNEL_NAME)
            .map(config -> {
                // Credentials хранятся в зашифрованном виде, здесь упрощённо
                // В продакшене нужно расшифровывать
                return defaultBotToken;
            })
            .orElse(defaultBotToken);
    }
    
    private String maskChatId(String chatId) {
        if (chatId == null || chatId.length() < 4) return "***";
        return chatId.substring(0, 2) + "***" + chatId.substring(chatId.length() - 2);
    }
    
    private String escapeMarkdown(String text) {
        if (text == null) return "";
        return text
            .replace("_", "\\_")
            .replace("*", "\\*")
            .replace("[", "\\[")
            .replace("`", "\\`");
    }
}
