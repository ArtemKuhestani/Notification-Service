package com.notification.repository;

import com.notification.domain.entity.ApiClient;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с API-клиентами.
 */
@Repository
public class ApiClientRepository {

    private final JdbcClient jdbcClient;

    public ApiClientRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Находит клиента по хэшу API-ключа.
     */
    public Optional<ApiClient> findByApiKeyHash(String apiKeyHash) {
        return jdbcClient.sql("""
            SELECT * FROM api_clients WHERE api_key_hash = :hash AND is_active = TRUE
            """)
            .param("hash", apiKeyHash)
            .query((rs, rowNum) -> mapRowToApiClient(rs))
            .optional();
    }

    /**
     * Находит клиента по ID.
     */
    public Optional<ApiClient> findById(Integer clientId) {
        return jdbcClient.sql("""
            SELECT * FROM api_clients WHERE client_id = :id
            """)
            .param("id", clientId)
            .query((rs, rowNum) -> mapRowToApiClient(rs))
            .optional();
    }

    /**
     * Получает всех клиентов.
     */
    public List<ApiClient> findAll() {
        return jdbcClient.sql("""
            SELECT * FROM api_clients ORDER BY client_name
            """)
            .query((rs, rowNum) -> mapRowToApiClient(rs))
            .list();
    }

    /**
     * Создаёт нового клиента.
     */
    public ApiClient save(ApiClient client) {
        LocalDateTime now = LocalDateTime.now();

        Integer id = jdbcClient.sql("""
            INSERT INTO api_clients (
                client_name, client_description, api_key_hash, api_key_prefix,
                is_active, rate_limit, allowed_channels, callback_url_default,
                created_at, updated_at, created_by
            ) VALUES (
                :name, :description, :hash, :prefix,
                :active, :rateLimit, :channels, :callbackUrl,
                :createdAt, :updatedAt, :createdBy
            ) RETURNING client_id
            """)
            .param("name", client.clientName())
            .param("description", client.clientDescription())
            .param("hash", client.apiKeyHash())
            .param("prefix", client.apiKeyPrefix())
            .param("active", client.isActive())
            .param("rateLimit", client.rateLimit())
            .param("channels", client.allowedChannels() != null ? 
                client.allowedChannels().toArray(new String[0]) : null)
            .param("callbackUrl", client.callbackUrlDefault())
            .param("createdAt", now)
            .param("updatedAt", now)
            .param("createdBy", client.createdBy())
            .query(Integer.class)
            .single();

        return new ApiClient(
            id, client.clientName(), client.clientDescription(),
            client.apiKeyHash(), client.apiKeyPrefix(), client.isActive(),
            client.rateLimit(), client.allowedChannels(), client.allowedIps(),
            client.callbackUrlDefault(), now, now, client.createdBy(), null
        );
    }

    /**
     * Обновляет время последнего использования.
     */
    public void updateLastUsedAt(Integer clientId) {
        jdbcClient.sql("""
            UPDATE api_clients SET last_used_at = NOW() WHERE client_id = :id
            """)
            .param("id", clientId)
            .update();
    }

    /**
     * Активирует/деактивирует клиента.
     */
    public void setActive(Integer clientId, boolean active) {
        jdbcClient.sql("""
            UPDATE api_clients SET is_active = :active WHERE client_id = :id
            """)
            .param("id", clientId)
            .param("active", active)
            .update();
    }

    /**
     * Обновляет API-ключ клиента.
     */
    public void updateApiKey(Integer clientId, String newApiKeyHash, String newApiKeyPrefix) {
        jdbcClient.sql("""
            UPDATE api_clients 
            SET api_key_hash = :hash, api_key_prefix = :prefix 
            WHERE client_id = :id
            """)
            .param("id", clientId)
            .param("hash", newApiKeyHash)
            .param("prefix", newApiKeyPrefix)
            .update();
    }

    /**
     * Обновляет данные клиента.
     */
    public void update(Integer clientId, String name, String description, Integer rateLimit, String callbackUrl) {
        jdbcClient.sql("""
            UPDATE api_clients 
            SET client_name = :name, 
                client_description = :description, 
                rate_limit = :rateLimit,
                callback_url_default = :callbackUrl,
                updated_at = NOW()
            WHERE client_id = :id
            """)
            .param("id", clientId)
            .param("name", name)
            .param("description", description)
            .param("rateLimit", rateLimit)
            .param("callbackUrl", callbackUrl)
            .update();
    }

    /**
     * Обновляет статус активности клиента.
     */
    public void updateActive(Integer clientId, boolean active) {
        jdbcClient.sql("""
            UPDATE api_clients 
            SET is_active = :active, updated_at = NOW()
            WHERE client_id = :id
            """)
            .param("id", clientId)
            .param("active", active)
            .update();
    }

    /**
     * Удаляет клиента.
     */
    public void deleteById(Integer clientId) {
        jdbcClient.sql("DELETE FROM api_clients WHERE client_id = :id")
            .param("id", clientId)
            .update();
    }

    private ApiClient mapRowToApiClient(java.sql.ResultSet rs) throws java.sql.SQLException {
        List<String> allowedChannels = null;
        List<String> allowedIps = null;

        Array channelsArray = rs.getArray("allowed_channels");
        if (channelsArray != null) {
            String[] channels = (String[]) channelsArray.getArray();
            allowedChannels = Arrays.asList(channels);
        }

        Array ipsArray = rs.getArray("allowed_ips");
        if (ipsArray != null) {
            String[] ips = (String[]) ipsArray.getArray();
            allowedIps = Arrays.asList(ips);
        }

        Timestamp lastUsedAt = rs.getTimestamp("last_used_at");
        Integer createdBy = rs.getInt("created_by");
        if (rs.wasNull()) {
            createdBy = null;
        }

        return new ApiClient(
            rs.getInt("client_id"),
            rs.getString("client_name"),
            rs.getString("client_description"),
            rs.getString("api_key_hash"),
            rs.getString("api_key_prefix"),
            rs.getBoolean("is_active"),
            rs.getInt("rate_limit"),
            allowedChannels,
            allowedIps,
            rs.getString("callback_url_default"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("updated_at").toLocalDateTime(),
            createdBy,
            lastUsedAt != null ? lastUsedAt.toLocalDateTime() : null
        );
    }
}
