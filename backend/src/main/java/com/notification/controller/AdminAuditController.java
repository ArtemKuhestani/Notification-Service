package com.notification.controller;

import com.notification.dto.AuditLogDto;
import com.notification.dto.PagedResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Контроллер для просмотра журнала аудита.
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
public class AdminAuditController {

    private final JdbcClient jdbcClient;

    public AdminAuditController(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Получить записи аудита с пагинацией и фильтрацией.
     */
    @GetMapping
    public PagedResponse<AuditLogDto> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {

        LocalDateTime fromDate = from != null ? LocalDate.parse(from).atStartOfDay() : null;
        LocalDateTime toDate = to != null ? LocalDate.parse(to).atTime(23, 59, 59) : null;

        StringBuilder sql = new StringBuilder("""
            SELECT al.log_id, al.admin_id, al.action_type, al.entity_type, al.entity_id, 
                   al.old_value, al.new_value, al.ip_address, al.user_agent, al.created_at,
                   a.email as admin_email
            FROM audit_log al
            LEFT JOIN admins a ON al.admin_id = a.admin_id
            WHERE 1=1
            """);

        StringBuilder countSql = new StringBuilder("SELECT COUNT(*) FROM audit_log WHERE 1=1");

        if (action != null && !action.isEmpty()) {
            sql.append(" AND al.action_type = :action");
            countSql.append(" AND action_type = :action");
        }
        if (entityType != null && !entityType.isEmpty()) {
            sql.append(" AND al.entity_type = :entityType");
            countSql.append(" AND entity_type = :entityType");
        }
        if (fromDate != null) {
            sql.append(" AND al.created_at >= :from");
            countSql.append(" AND created_at >= :from");
        }
        if (toDate != null) {
            sql.append(" AND al.created_at <= :to");
            countSql.append(" AND created_at <= :to");
        }

        sql.append(" ORDER BY al.created_at DESC LIMIT :limit OFFSET :offset");

        var query = jdbcClient.sql(sql.toString())
            .param("limit", size)
            .param("offset", page * size);

        var countQuery = jdbcClient.sql(countSql.toString());

        if (action != null && !action.isEmpty()) {
            query = query.param("action", action);
            countQuery = countQuery.param("action", action);
        }
        if (entityType != null && !entityType.isEmpty()) {
            query = query.param("entityType", entityType);
            countQuery = countQuery.param("entityType", entityType);
        }
        if (fromDate != null) {
            query = query.param("from", fromDate);
            countQuery = countQuery.param("from", fromDate);
        }
        if (toDate != null) {
            query = query.param("to", toDate);
            countQuery = countQuery.param("to", toDate);
        }

        List<AuditLogDto> content = query
            .query((rs, rowNum) -> new AuditLogDto(
                rs.getLong("log_id"),
                rs.getString("action_type"),
                rs.getString("entity_type"),
                rs.getString("entity_id"),
                rs.getObject("admin_id") != null ? rs.getInt("admin_id") : null,
                rs.getString("admin_email"),
                rs.getString("old_value"),
                rs.getString("new_value"),
                rs.getString("ip_address"),
                rs.getTimestamp("created_at").toLocalDateTime()
            ))
            .list();

        int total = countQuery.query(Integer.class).single();
        int totalPages = (int) Math.ceil((double) total / size);

        return new PagedResponse<>(content, page, size, total, totalPages);
    }

    /**
     * Получить уникальные типы действий.
     */
    @GetMapping("/actions")
    public List<String> getActionTypes() {
        return jdbcClient.sql("SELECT DISTINCT action_type FROM audit_log ORDER BY action_type")
            .query(String.class)
            .list();
    }

    /**
     * Удалить старые записи аудита.
     */
    @DeleteMapping("/cleanup")
    public CleanupResult cleanupOldLogs(@RequestParam(defaultValue = "90") int olderThanDays) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(olderThanDays);
        
        int deleted = jdbcClient.sql("DELETE FROM audit_log WHERE created_at < :cutoff")
            .param("cutoff", cutoffDate)
            .update();

        return new CleanupResult(deleted, "Удалено записей старше " + olderThanDays + " дней");
    }

    public record CleanupResult(int deletedCount, String message) {}
}
