package com.notification.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notification.domain.entity.ApiClient;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

/**
 * Фильтр для проверки API-ключей и rate limiting.
 */
@Component
@Order(1)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {
    
    private static final Logger log = LoggerFactory.getLogger(ApiKeyAuthenticationFilter.class);
    private static final String API_KEY_HEADER = "X-API-Key";
    
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;
    
    public ApiKeyAuthenticationFilter(RateLimitService rateLimitService, ObjectMapper objectMapper) {
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }
    
    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        
        String path = request.getRequestURI();
        
        // Проверяем только API эндпоинты для отправки
        if (!shouldFilter(path)) {
            filterChain.doFilter(request, response);
            return;
        }
        
        String apiKey = request.getHeader(API_KEY_HEADER);
        
        if (apiKey == null || apiKey.isEmpty()) {
            sendError(response, 401, "API key is required", "MISSING_API_KEY");
            return;
        }
        
        // Хэшируем ключ для поиска в БД
        String apiKeyHash = hashApiKey(apiKey);
        
        // Проверяем rate limit и получаем клиента
        RateLimitService.RateLimitResult result = rateLimitService.checkRateLimit(apiKeyHash);
        
        // Добавляем заголовки rate limit
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetTime()));
        
        if (!result.isAllowed()) {
            if (result.errorMessage().contains("Invalid")) {
                sendError(response, 401, "Invalid API key", "INVALID_API_KEY");
            } else if (result.errorMessage().contains("inactive")) {
                sendError(response, 403, "API client is inactive", "CLIENT_INACTIVE");
            } else {
                response.setHeader("Retry-After", "60");
                sendError(response, 429, "Rate limit exceeded", "RATE_LIMIT_EXCEEDED");
            }
            return;
        }
        
        // Получаем клиента для использования в контроллере
        Optional<ApiClient> clientOpt = rateLimitService.getClientByApiKey(apiKeyHash);
        clientOpt.ifPresent(client -> {
            request.setAttribute("apiClient", client);
            request.setAttribute("clientId", client.clientId());
        });
        
        filterChain.doFilter(request, response);
    }
    
    private boolean shouldFilter(String path) {
        return path.startsWith("/api/v1/send") 
            || path.startsWith("/api/v1/status") 
            || path.startsWith("/api/v1/retry");
    }
    
    private String hashApiKey(String apiKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(apiKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            log.error("Failed to hash API key", e);
            return "";
        }
    }
    
    private void sendError(HttpServletResponse response, int status, String message, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        Map<String, Object> error = Map.of(
            "status", status,
            "error", code,
            "message", message
        );
        
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }
}
