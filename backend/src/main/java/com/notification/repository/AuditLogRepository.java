package com.notification.repository;

import com.notification.domain.entity.AuditLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Репозиторий для работы с журналом аудита.
 */
@Repository
public class AuditLogRepository {

    private static final Logger log = LoggerFactory.getLogger(AuditLogRepository.class);

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public AuditLogRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Сохраняет запись аудита.
     */
    public void save(AuditLog auditLog) {
        String oldValueJson = null;
        String newValueJson = null;

        try {
            if (auditLog.oldValue() != null) {
                oldValueJson = objectMapper.writeValueAsString(auditLog.oldValue());
            }
            if (auditLog.newValue() != null) {
                newValueJson = objectMapper.writeValueAsString(auditLog.newValue());
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize audit log values", e);
        }

        jdbcClient.sql("""
            INSERT INTO audit_log (
                admin_id, action_type, entity_type, entity_id, 
                old_value, new_value, ip_address, user_agent, created_at
            ) VALUES (
                :adminId, :actionType, :entityType, :entityId,
                CAST(:oldValue AS JSONB), CAST(:newValue AS JSONB), 
                CAST(:ipAddress AS INET), :userAgent, NOW()
            )
            """)
            .param("adminId", auditLog.adminId())
            .param("actionType", auditLog.actionType())
            .param("entityType", auditLog.entityType())
            .param("entityId", auditLog.entityId())
            .param("oldValue", oldValueJson)
            .param("newValue", newValueJson)
            .param("ipAddress", auditLog.ipAddress())
            .param("userAgent", auditLog.userAgent())
            .update();
    }

    /**
     * Получает записи аудита с фильтрацией.
     */
    public List<AuditLog> findAll(Integer adminId, String actionType, String entityType,
                                   LocalDateTime from, LocalDateTime to, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM audit_log WHERE 1=1");
        Map<String, Object> params = new HashMap<>();

        if (adminId != null) {
            sql.append(" AND admin_id = :adminId");
            params.put("adminId", adminId);
        }
        if (actionType != null) {
            sql.append(" AND action_type = :actionType");
            params.put("actionType", actionType);
        }
        if (entityType != null) {
            sql.append(" AND entity_type = :entityType");
            params.put("entityType", entityType);
        }
        if (from != null) {
            sql.append(" AND created_at >= :from");
            params.put("from", from);
        }
        if (to != null) {
            sql.append(" AND created_at <= :to");
            params.put("to", to);
        }

        sql.append(" ORDER BY created_at DESC LIMIT :limit OFFSET :offset");
        params.put("limit", limit);
        params.put("offset", offset);

        var query = jdbcClient.sql(sql.toString());
        params.forEach(query::param);

        return query.query((rs, rowNum) -> mapRowToAuditLog(rs)).list();
    }

    /**
     * Получает записи аудита за последние N дней.
     */
    public List<AuditLog> findRecentByEntityType(String entityType, int days, int limit) {
        return jdbcClient.sql("""
            SELECT * FROM audit_log 
            WHERE entity_type = :entityType 
              AND created_at >= NOW() - INTERVAL '%d days'
            ORDER BY created_at DESC 
            LIMIT :limit
            """.formatted(days))
            .param("entityType", entityType)
            .param("limit", limit)
            .query((rs, rowNum) -> mapRowToAuditLog(rs))
            .list();
    }

    private AuditLog mapRowToAuditLog(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> oldValue = null;
        Map<String, Object> newValue = null;

        String oldValueStr = rs.getString("old_value");
        String newValueStr = rs.getString("new_value");

        try {
            if (oldValueStr != null) {
                oldValue = objectMapper.readValue(oldValueStr, new TypeReference<>() {});
            }
            if (newValueStr != null) {
                newValue = objectMapper.readValue(newValueStr, new TypeReference<>() {});
            }
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse audit log JSON values", e);
        }

        Integer adminId = rs.getInt("admin_id");
        if (rs.wasNull()) {
            adminId = null;
        }

        return AuditLog.builder()
            .logId(rs.getLong("log_id"))
            .adminId(adminId)
            .actionType(rs.getString("action_type"))
            .entityType(rs.getString("entity_type"))
            .entityId(rs.getString("entity_id"))
            .oldValue(oldValue)
            .newValue(newValue)
            .ipAddress(rs.getString("ip_address"))
            .userAgent(rs.getString("user_agent"))
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .build();
    }
}
