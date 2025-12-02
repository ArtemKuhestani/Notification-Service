package com.notification.service;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Сервис для логирования действий администратора.
 */
@Service
public class AuditService {

    private final JdbcClient jdbcClient;

    public AuditService(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Логирует действие администратора.
     */
    @Async
    public void logAction(String action, String description, String details) {
        try {
            String clientIp = getClientIp();
            
            jdbcClient.sql("""
                INSERT INTO audit_log (action_type, entity_type, entity_id, ip_address)
                VALUES (:action, :description, :details, :ip::INET)
                """)
                .param("action", action)
                .param("description", description != null ? description : "")
                .param("details", details)
                .param("ip", clientIp)
                .update();
        } catch (Exception e) {
            // Логирование не должно ломать основную функциональность
        }
    }

    private String getClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) 
                RequestContextHolder.getRequestAttributes();
            if (attrs != null && attrs.getRequest() != null) {
                String xff = attrs.getRequest().getHeader("X-Forwarded-For");
                if (xff != null && !xff.isEmpty()) {
                    return xff.split(",")[0].trim();
                }
                return attrs.getRequest().getRemoteAddr();
            }
        } catch (Exception e) {
            // Ignore
        }
        return "0.0.0.0";
    }
}
