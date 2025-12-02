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

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Адаптер для отправки SMS через Twilio API.
 */
@Component
public class SmsChannelAdapter implements ChannelAdapter {
    
    private static final Logger log = LoggerFactory.getLogger(SmsChannelAdapter.class);
    private static final String CHANNEL_NAME = "SMS";
    private static final String TWILIO_API_URL = "https://api.twilio.com/2010-04-01/Accounts/";
    
    private final ChannelConfigRepository channelConfigRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${sms.twilio.account-sid:}")
    private String accountSid;
    
    @Value("${sms.twilio.auth-token:}")
    private String authToken;
    
    @Value("${sms.twilio.from-number:}")
    private String fromNumber;
    
    public SmsChannelAdapter(
            ChannelConfigRepository channelConfigRepository,
            ObjectMapper objectMapper
    ) {
        this.channelConfigRepository = channelConfigRepository;
        this.restTemplate = new RestTemplate();
        this.objectMapper = objectMapper;
    }
    
    @Override
    public String send(String recipient, String subject, String message) throws ChannelException {
        log.info("Sending SMS to: {}", maskPhoneNumber(recipient));
        
        if (accountSid == null || accountSid.isEmpty()) {
            throw new ChannelException("Twilio account SID not configured", "CONFIG_ERROR", false);
        }
        
        try {
            String url = TWILIO_API_URL + accountSid + "/Messages.json";
            
            // Формируем тело запроса в формате x-www-form-urlencoded
            String body = "To=" + encodeValue(normalizePhoneNumber(recipient)) 
                + "&From=" + encodeValue(fromNumber) 
                + "&Body=" + encodeValue(message);
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            headers.setBasicAuth(accountSid, authToken);
            
            HttpEntity<String> request = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            
            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode jsonResponse = objectMapper.readTree(response.getBody());
                String messageSid = jsonResponse.path("sid").asText();
                String status = jsonResponse.path("status").asText();
                
                log.info("SMS sent successfully, sid: {}, status: {}", messageSid, status);
                return messageSid;
            }
            
            throw new ChannelException("Unexpected response from Twilio API", "RESPONSE_ERROR", true);
            
        } catch (HttpClientErrorException e) {
            log.error("Twilio client error: {} - {}", e.getStatusCode(), e.getMessage());
            
            boolean retryable = e.getStatusCode() != HttpStatus.BAD_REQUEST;
            String errorCode = extractTwilioErrorCode(e);
            
            throw new ChannelException("SMS error: " + e.getMessage(), errorCode, retryable, e);
            
        } catch (HttpServerErrorException e) {
            log.error("Twilio server error: {}", e.getMessage());
            throw new ChannelException("SMS server error: " + e.getMessage(), "SERVER_ERROR", true, e);
            
        } catch (Exception e) {
            log.error("Failed to send SMS: {}", e.getMessage());
            throw new ChannelException("SMS sending failed: " + e.getMessage(), e);
        }
    }
    
    @Override
    public boolean healthCheck() {
        if (accountSid == null || accountSid.isEmpty()) {
            return false;
        }
        
        try {
            String url = TWILIO_API_URL + accountSid + ".json";
            
            HttpHeaders headers = new HttpHeaders();
            headers.setBasicAuth(accountSid, authToken);
            
            HttpEntity<Void> request = new HttpEntity<>(headers);
            
            ResponseEntity<String> response = restTemplate.exchange(
                url, HttpMethod.GET, request, String.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            log.warn("Twilio health check failed: {}", e.getMessage());
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
    
    /**
     * Нормализует номер телефона в формат E.164.
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null) return "";
        
        // Удаляем все нецифровые символы кроме +
        String normalized = phone.replaceAll("[^+\\d]", "");
        
        // Если номер не начинается с +, добавляем
        if (!normalized.startsWith("+")) {
            // Если начинается с 8 (Россия), заменяем на +7
            if (normalized.startsWith("8") && normalized.length() == 11) {
                normalized = "+7" + normalized.substring(1);
            } else if (normalized.startsWith("7") && normalized.length() == 11) {
                normalized = "+" + normalized;
            } else {
                normalized = "+" + normalized;
            }
        }
        
        return normalized;
    }
    
    private String encodeValue(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8.toString());
        } catch (Exception e) {
            return value;
        }
    }
    
    private String extractTwilioErrorCode(HttpClientErrorException e) {
        try {
            JsonNode json = objectMapper.readTree(e.getResponseBodyAsString());
            return json.path("code").asText("CLIENT_ERROR");
        } catch (Exception ex) {
            return "CLIENT_ERROR";
        }
    }
    
    private String maskPhoneNumber(String phone) {
        if (phone == null || phone.length() < 6) return "***";
        return phone.substring(0, 4) + "***" + phone.substring(phone.length() - 2);
    }
}
