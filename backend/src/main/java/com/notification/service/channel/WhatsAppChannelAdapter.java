package com.notification.service.channel;

import com.notification.repository.ChannelConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Адаптер для WhatsApp Business API (заглушка).
 * Требует подключения к официальному партнёру Meta.
 */
@Component
public class WhatsAppChannelAdapter implements ChannelAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(WhatsAppChannelAdapter.class);
    private static final String CHANNEL_NAME = "WHATSAPP";
    
    private final ChannelConfigRepository channelConfigRepository;
    
    public WhatsAppChannelAdapter(ChannelConfigRepository channelConfigRepository) {
        this.channelConfigRepository = channelConfigRepository;
    }
    
    @Override
    public String send(String recipient, String subject, String message) throws ChannelException {
        log.info("Attempting to send WhatsApp message to: {}", maskNumber(recipient));
        
        // WhatsApp Business API требует интеграции с официальным партнёром Meta
        // Это заглушка для демонстрации архитектуры
        
        throw new ChannelException(
            "WhatsApp channel not configured. Please integrate with WhatsApp Business API partner.",
            "NOT_CONFIGURED",
            false
        );
    }
    
    @Override
    public boolean healthCheck() {
        // Возвращаем false пока не настроена интеграция
        return false;
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
    
    private String maskNumber(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 2);
    }
}
