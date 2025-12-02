package com.notification.repository;

import com.notification.domain.entity.Notification;
import com.notification.domain.entity.Notification.ChannelType;
import com.notification.domain.entity.Notification.NotificationStatus;
import com.notification.domain.entity.Notification.Priority;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Репозиторий для работы с уведомлениями.
 */
@Repository
public class NotificationRepository {

    private static final Logger log = LoggerFactory.getLogger(NotificationRepository.class);

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public NotificationRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Сохраняет новое уведомление.
     */
    public Notification save(Notification notification) {
        UUID id = notification.notificationId() != null ? notification.notificationId() : UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = notification.expiresAt() != null 
            ? notification.expiresAt() 
            : now.plusHours(24);

        String metadataJson = null;
        if (notification.metadata() != null) {
            try {
                metadataJson = objectMapper.writeValueAsString(notification.metadata());
            } catch (JsonProcessingException e) {
                log.warn("Failed to serialize metadata", e);
            }
        }

        jdbcClient.sql("""
            INSERT INTO notifications (
                notification_id, client_id, channel_type, recipient, subject, message_body,
                status, priority, retry_count, max_retries, next_retry_at, error_message,
                error_code, provider_message_id, idempotency_key, callback_url, metadata,
                created_at, updated_at, sent_at, expires_at
            ) VALUES (
                :id, :clientId, :channelType, :recipient, :subject, :messageBody,
                :status, :priority, :retryCount, :maxRetries, :nextRetryAt, :errorMessage,
                :errorCode, :providerMessageId, :idempotencyKey, :callbackUrl, CAST(:metadata AS JSONB),
                :createdAt, :updatedAt, :sentAt, :expiresAt
            )
            """)
            .param("id", id)
            .param("clientId", notification.clientId())
            .param("channelType", notification.channelType().name())
            .param("recipient", notification.recipient())
            .param("subject", notification.subject())
            .param("messageBody", notification.messageBody())
            .param("status", notification.status().name())
            .param("priority", notification.priority().name())
            .param("retryCount", notification.retryCount())
            .param("maxRetries", notification.maxRetries())
            .param("nextRetryAt", notification.nextRetryAt())
            .param("errorMessage", notification.errorMessage())
            .param("errorCode", notification.errorCode())
            .param("providerMessageId", notification.providerMessageId())
            .param("idempotencyKey", notification.idempotencyKey())
            .param("callbackUrl", notification.callbackUrl())
            .param("metadata", metadataJson)
            .param("createdAt", now)
            .param("updatedAt", now)
            .param("sentAt", notification.sentAt())
            .param("expiresAt", expiresAt)
            .update();

        return Notification.builder()
            .notificationId(id)
            .clientId(notification.clientId())
            .channelType(notification.channelType())
            .recipient(notification.recipient())
            .subject(notification.subject())
            .messageBody(notification.messageBody())
            .status(notification.status())
            .priority(notification.priority())
            .retryCount(notification.retryCount())
            .maxRetries(notification.maxRetries())
            .nextRetryAt(notification.nextRetryAt())
            .errorMessage(notification.errorMessage())
            .errorCode(notification.errorCode())
            .providerMessageId(notification.providerMessageId())
            .idempotencyKey(notification.idempotencyKey())
            .callbackUrl(notification.callbackUrl())
            .metadata(notification.metadata())
            .createdAt(now)
            .updatedAt(now)
            .sentAt(notification.sentAt())
            .expiresAt(expiresAt)
            .build();
    }

    /**
     * Находит уведомление по ID.
     */
    public Optional<Notification> findById(UUID id) {
        return jdbcClient.sql("""
            SELECT * FROM notifications WHERE notification_id = :id
            """)
            .param("id", id)
            .query((rs, rowNum) -> mapRowToNotification(rs))
            .optional();
    }

    /**
     * Обновляет статус уведомления.
     */
    public void updateStatus(UUID id, NotificationStatus status, String errorMessage, String errorCode) {
        jdbcClient.sql("""
            UPDATE notifications 
            SET status = :status, error_message = :errorMessage, error_code = :errorCode,
                sent_at = CASE WHEN :status = 'SENT' THEN NOW() ELSE sent_at END
            WHERE notification_id = :id
            """)
            .param("id", id)
            .param("status", status.name())
            .param("errorMessage", errorMessage)
            .param("errorCode", errorCode)
            .update();
    }

    /**
     * Обновляет статус и provider_message_id.
     */
    public void updateStatusAndProviderId(UUID id, NotificationStatus status, String providerMessageId) {
        jdbcClient.sql("""
            UPDATE notifications 
            SET status = :status, provider_message_id = :providerMessageId,
                sent_at = NOW()
            WHERE notification_id = :id
            """)
            .param("id", id)
            .param("status", status.name())
            .param("providerMessageId", providerMessageId)
            .update();
    }

    /**
     * Увеличивает счётчик попыток и устанавливает время следующей попытки.
     */
    public void incrementRetryCount(UUID id, LocalDateTime nextRetryAt) {
        jdbcClient.sql("""
            UPDATE notifications 
            SET retry_count = retry_count + 1, next_retry_at = :nextRetryAt
            WHERE notification_id = :id
            """)
            .param("id", id)
            .param("nextRetryAt", nextRetryAt)
            .update();
    }

    /**
     * Находит уведомления для повторной отправки.
     */
    public List<Notification> findPendingRetries(int limit) {
        return jdbcClient.sql("""
            SELECT * FROM notifications 
            WHERE status = 'PENDING' 
              AND next_retry_at IS NOT NULL 
              AND next_retry_at <= NOW()
              AND (expires_at IS NULL OR expires_at > NOW())
            ORDER BY priority DESC, next_retry_at ASC
            LIMIT :limit
            """)
            .param("limit", limit)
            .query((rs, rowNum) -> mapRowToNotification(rs))
            .list();
    }

    /**
     * Находит уведомления для повторной отправки (по времени).
     */
    public List<Notification> findPendingRetries(LocalDateTime before) {
        return jdbcClient.sql("""
            SELECT * FROM notifications 
            WHERE status = 'PENDING' 
              AND next_retry_at IS NOT NULL 
              AND next_retry_at <= :before
              AND (expires_at IS NULL OR expires_at > NOW())
            ORDER BY priority DESC, next_retry_at ASC
            LIMIT 100
            """)
            .param("before", before)
            .query((rs, rowNum) -> mapRowToNotification(rs))
            .list();
    }

    /**
     * Обновляет статус уведомления.
     */
    public void updateStatus(String id, String status) {
        jdbcClient.sql("""
            UPDATE notifications 
            SET status = :status, updated_at = NOW()
            WHERE notification_id = CAST(:id AS UUID)
            """)
            .param("id", id)
            .param("status", status)
            .update();
    }

    /**
     * Обновляет время отправки.
     */
    public void updateSentAt(String id, LocalDateTime sentAt) {
        jdbcClient.sql("""
            UPDATE notifications 
            SET sent_at = :sentAt, updated_at = NOW()
            WHERE notification_id = CAST(:id AS UUID)
            """)
            .param("id", id)
            .param("sentAt", sentAt)
            .update();
    }

    /**
     * Обновляет ID сообщения у провайдера.
     */
    public void updateProviderMessageId(String id, String providerMessageId) {
        jdbcClient.sql("""
            UPDATE notifications 
            SET provider_message_id = :providerMessageId, updated_at = NOW()
            WHERE notification_id = CAST(:id AS UUID)
            """)
            .param("id", id)
            .param("providerMessageId", providerMessageId)
            .update();
    }

    /**
     * Обновляет информацию об ошибке.
     */
    public void updateError(String id, String errorMessage, String errorCode) {
        jdbcClient.sql("""
            UPDATE notifications 
            SET error_message = :errorMessage, error_code = :errorCode, updated_at = NOW()
            WHERE notification_id = CAST(:id AS UUID)
            """)
            .param("id", id)
            .param("errorMessage", errorMessage)
            .param("errorCode", errorCode)
            .update();
    }

    /**
     * Обновляет для повторной попытки.
     */
    public void updateForRetry(String id, int retryCount, LocalDateTime nextRetryAt, String errorMessage, String errorCode) {
        jdbcClient.sql("""
            UPDATE notifications 
            SET retry_count = :retryCount, 
                next_retry_at = :nextRetryAt,
                error_message = :errorMessage,
                error_code = :errorCode,
                status = 'PENDING',
                updated_at = NOW()
            WHERE notification_id = CAST(:id AS UUID)
            """)
            .param("id", id)
            .param("retryCount", retryCount)
            .param("nextRetryAt", nextRetryAt)
            .param("errorMessage", errorMessage)
            .param("errorCode", errorCode)
            .update();
    }

    /**
     * Находит уведомление по ID (строка).
     */
    public Optional<Notification> findById(String id) {
        return jdbcClient.sql("""
            SELECT * FROM notifications WHERE notification_id = CAST(:id AS UUID)
            """)
            .param("id", id)
            .query((rs, rowNum) -> mapRowToNotification(rs))
            .optional();
    }

    /**
     * Получает список уведомлений с фильтрацией.
     */
    public List<Notification> findAll(Integer clientId, String status, String channel, 
                                       LocalDateTime from, LocalDateTime to, int offset, int limit) {
        StringBuilder sql = new StringBuilder("SELECT * FROM notifications WHERE 1=1");
        Map<String, Object> params = new HashMap<>();

        if (clientId != null) {
            sql.append(" AND client_id = :clientId");
            params.put("clientId", clientId);
        }
        if (status != null) {
            sql.append(" AND status = :status");
            params.put("status", status);
        }
        if (channel != null) {
            sql.append(" AND channel_type = :channel");
            params.put("channel", channel);
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

        return query.query((rs, rowNum) -> mapRowToNotification(rs)).list();
    }

    /**
     * Подсчитывает количество уведомлений с фильтрацией.
     */
    public long count(Integer clientId, String status, String channel, LocalDateTime from, LocalDateTime to) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM notifications WHERE 1=1");
        Map<String, Object> params = new HashMap<>();

        if (clientId != null) {
            sql.append(" AND client_id = :clientId");
            params.put("clientId", clientId);
        }
        if (status != null) {
            sql.append(" AND status = :status");
            params.put("status", status);
        }
        if (channel != null) {
            sql.append(" AND channel_type = :channel");
            params.put("channel", channel);
        }
        if (from != null) {
            sql.append(" AND created_at >= :from");
            params.put("from", from);
        }
        if (to != null) {
            sql.append(" AND created_at <= :to");
            params.put("to", to);
        }

        var query = jdbcClient.sql(sql.toString());
        params.forEach(query::param);

        return query.query(Long.class).single();
    }

    /**
     * Проверяет существование по ключу идемпотентности.
     */
    public Optional<Notification> findByIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null) return Optional.empty();
        
        return jdbcClient.sql("""
            SELECT * FROM notifications WHERE idempotency_key = :key
            """)
            .param("key", idempotencyKey)
            .query((rs, rowNum) -> mapRowToNotification(rs))
            .optional();
    }

    private Notification mapRowToNotification(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> metadata = null;
        String metadataStr = rs.getString("metadata");
        if (metadataStr != null) {
            try {
                metadata = objectMapper.readValue(metadataStr, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse metadata JSON", e);
            }
        }

        Timestamp nextRetryAt = rs.getTimestamp("next_retry_at");
        Timestamp sentAt = rs.getTimestamp("sent_at");
        Timestamp expiresAt = rs.getTimestamp("expires_at");

        return Notification.builder()
            .notificationId(UUID.fromString(rs.getString("notification_id")))
            .clientId(rs.getInt("client_id"))
            .channelType(ChannelType.valueOf(rs.getString("channel_type")))
            .recipient(rs.getString("recipient"))
            .subject(rs.getString("subject"))
            .messageBody(rs.getString("message_body"))
            .status(NotificationStatus.valueOf(rs.getString("status")))
            .priority(Priority.valueOf(rs.getString("priority")))
            .retryCount(rs.getInt("retry_count"))
            .maxRetries(rs.getInt("max_retries"))
            .nextRetryAt(nextRetryAt != null ? nextRetryAt.toLocalDateTime() : null)
            .errorMessage(rs.getString("error_message"))
            .errorCode(rs.getString("error_code"))
            .providerMessageId(rs.getString("provider_message_id"))
            .idempotencyKey(rs.getString("idempotency_key"))
            .callbackUrl(rs.getString("callback_url"))
            .metadata(metadata)
            .createdAt(rs.getTimestamp("created_at").toLocalDateTime())
            .updatedAt(rs.getTimestamp("updated_at").toLocalDateTime())
            .sentAt(sentAt != null ? sentAt.toLocalDateTime() : null)
            .expiresAt(expiresAt != null ? expiresAt.toLocalDateTime() : null)
            .build();
    }
}
