package com.notification.security;

import com.notification.domain.entity.ApiClient;
import com.notification.repository.ApiClientRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сервис для ограничения частоты запросов (Rate Limiting).
 */
@Service
public class RateLimitService {
    
    private static final Logger log = LoggerFactory.getLogger(RateLimitService.class);
    private static final int DEFAULT_RATE_LIMIT = 100; // запросов в минуту
    private static final long WINDOW_SIZE_MS = 60_000; // 1 минута
    
    private final ApiClientRepository apiClientRepository;
    
    // Хранилище счётчиков запросов: clientId -> RateLimitBucket
    private final Map<Integer, RateLimitBucket> buckets = new ConcurrentHashMap<>();
    
    public RateLimitService(ApiClientRepository apiClientRepository) {
        this.apiClientRepository = apiClientRepository;
    }
    
    /**
     * Проверяет, разрешён ли запрос для данного API-ключа.
     * @param apiKeyHash хэш API-ключа
     * @return результат проверки с информацией о лимите
     */
    public RateLimitResult checkRateLimit(String apiKeyHash) {
        Optional<ApiClient> clientOpt = apiClientRepository.findByApiKeyHash(apiKeyHash);
        
        if (clientOpt.isEmpty()) {
            return new RateLimitResult(false, 0, 0, 0, "Invalid API key");
        }
        
        ApiClient client = clientOpt.get();
        
        if (!client.isActive()) {
            return new RateLimitResult(false, 0, 0, 0, "API client is inactive");
        }
        
        int rateLimit = client.rateLimit() != null ? client.rateLimit() : DEFAULT_RATE_LIMIT;
        
        RateLimitBucket bucket = buckets.computeIfAbsent(
            client.clientId(), 
            id -> new RateLimitBucket(rateLimit)
        );
        
        // Обновляем лимит если он изменился
        bucket.setLimit(rateLimit);
        
        boolean allowed = bucket.tryConsume();
        
        if (!allowed) {
            log.warn("Rate limit exceeded for client {}: {} requests per minute", 
                client.clientName(), rateLimit);
        }
        
        return new RateLimitResult(
            allowed,
            rateLimit,
            bucket.getRemaining(),
            bucket.getResetTime(),
            allowed ? null : "Rate limit exceeded"
        );
    }
    
    /**
     * Получает информацию о клиенте по API-ключу.
     */
    public Optional<ApiClient> getClientByApiKey(String apiKeyHash) {
        return apiClientRepository.findByApiKeyHash(apiKeyHash);
    }
    
    /**
     * Результат проверки rate limit.
     */
    public record RateLimitResult(
        boolean allowed,
        int limit,
        int remaining,
        long resetTime,
        String errorMessage
    ) {
        public boolean isAllowed() {
            return allowed;
        }
    }
    
    /**
     * Bucket для хранения состояния rate limit клиента.
     */
    private static class RateLimitBucket {
        private int limit;
        private final AtomicInteger counter = new AtomicInteger(0);
        private volatile long windowStart = System.currentTimeMillis();
        
        public RateLimitBucket(int limit) {
            this.limit = limit;
        }
        
        public synchronized void setLimit(int limit) {
            this.limit = limit;
        }
        
        public synchronized boolean tryConsume() {
            long now = System.currentTimeMillis();
            
            // Если окно истекло, сбрасываем счётчик
            if (now - windowStart >= WINDOW_SIZE_MS) {
                counter.set(0);
                windowStart = now;
            }
            
            // Проверяем лимит
            if (counter.get() >= limit) {
                return false;
            }
            
            // Увеличиваем счётчик
            counter.incrementAndGet();
            return true;
        }
        
        public int getRemaining() {
            return Math.max(0, limit - counter.get());
        }
        
        public long getResetTime() {
            return windowStart + WINDOW_SIZE_MS;
        }
    }
}
