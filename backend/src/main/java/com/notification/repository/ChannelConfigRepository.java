package com.notification.repository;

import com.notification.domain.entity.ChannelConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Репозиторий для работы с конфигурацией каналов.
 */
@Repository
public class ChannelConfigRepository {

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public ChannelConfigRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Находит конфигурацию по имени канала.
     */
    public Optional<ChannelConfig> findByChannelName(String channelName) {
        return jdbcClient.sql("""
            SELECT * FROM channel_configs WHERE channel_name = :name
            """)
            .param("name", channelName)
            .query(this::mapRowToConfig)
            .optional();
    }

    /**
     * Получает все конфигурации каналов.
     */
    public List<ChannelConfig> findAll() {
        return jdbcClient.sql("""
            SELECT * FROM channel_configs ORDER BY priority
            """)
            .query(this::mapRowToConfig)
            .list();
    }

    /**
     * Получает все активные каналы.
     */
    public List<ChannelConfig> findAllEnabled() {
        return jdbcClient.sql("""
            SELECT * FROM channel_configs WHERE is_enabled = TRUE ORDER BY priority
            """)
            .query(this::mapRowToConfig)
            .list();
    }

    /**
     * Обновляет статус канала.
     */
    public void updateEnabled(String channelName, boolean enabled) {
        jdbcClient.sql("""
            UPDATE channel_configs 
            SET is_enabled = :enabled, updated_at = NOW() 
            WHERE channel_name = :name
            """)
            .param("name", channelName)
            .param("enabled", enabled)
            .update();
    }

    /**
     * Обновляет настройки канала (принимает Map, конвертирует в JSON).
     */
    public void updateSettings(String channelName, Map<String, Object> settings) {
        String settingsJson = null;
        if (settings != null) {
            try {
                settingsJson = objectMapper.writeValueAsString(settings);
            } catch (JsonProcessingException e) {
                throw new RuntimeException("Failed to serialize settings", e);
            }
        }
        
        jdbcClient.sql("""
            UPDATE channel_configs 
            SET settings = CAST(:settings AS JSONB), updated_at = NOW() 
            WHERE channel_name = :name
            """)
            .param("name", channelName)
            .param("settings", settingsJson)
            .update();
    }

    /**
     * Обновляет статус здоровья канала.
     */
    public void updateHealthStatus(String channelName, String healthStatus) {
        jdbcClient.sql("""
            UPDATE channel_configs 
            SET health_status = :status, last_health_check = NOW(), updated_at = NOW() 
            WHERE channel_name = :name
            """)
            .param("name", channelName)
            .param("status", healthStatus)
            .update();
    }

    /**
     * Инкрементирует счётчик отправок за день.
     */
    public void incrementDailySentCount(String channelName) {
        jdbcClient.sql("""
            UPDATE channel_configs 
            SET daily_sent_count = daily_sent_count + 1, updated_at = NOW() 
            WHERE channel_name = :name
            """)
            .param("name", channelName)
            .update();
    }

    /**
     * Сбрасывает дневной счётчик для всех каналов.
     */
    public void resetDailyCounters() {
        jdbcClient.sql("""
            UPDATE channel_configs SET daily_sent_count = 0, updated_at = NOW()
            """)
            .update();
    }

    /**
     * Проверяет, не превышен ли дневной лимит.
     */
    public boolean isWithinDailyLimit(String channelName) {
        Long count = jdbcClient.sql("""
            SELECT COUNT(*) FROM channel_configs 
            WHERE channel_name = :name 
            AND (daily_limit IS NULL OR daily_sent_count < daily_limit)
            """)
            .param("name", channelName)
            .query(Long.class)
            .single();
        return count > 0;
    }

    private ChannelConfig mapRowToConfig(ResultSet rs, int rowNum) throws SQLException {
        var lastHealthCheck = rs.getTimestamp("last_health_check");
        
        // Parse settings JSON string to Map
        String settingsJson = rs.getString("settings");
        Map<String, Object> settings = null;
        if (settingsJson != null && !settingsJson.isEmpty()) {
            try {
                settings = objectMapper.readValue(settingsJson, 
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                settings = Map.of();
            }
        }
        
        return new ChannelConfig(
            rs.getInt("config_id"),
            rs.getString("channel_name"),
            rs.getString("provider_name"),
            rs.getBytes("credentials"),
            settings,
            rs.getBoolean("is_enabled"),
            rs.getInt("priority"),
            rs.getObject("daily_limit") != null ? rs.getInt("daily_limit") : null,
            rs.getInt("daily_sent_count"),
            lastHealthCheck != null ? lastHealthCheck.toLocalDateTime() : null,
            rs.getString("health_status"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime()
        );
    }
}
