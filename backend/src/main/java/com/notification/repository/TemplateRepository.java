package com.notification.repository;

import com.notification.entity.MessageTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

/**
 * Репозиторий для работы с шаблонами сообщений.
 */
@Repository
public class TemplateRepository {

    private final JdbcClient jdbcClient;

    public TemplateRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Найти все шаблоны с пагинацией.
     */
    public List<MessageTemplate> findAll(int offset, int limit) {
        return jdbcClient.sql("""
            SELECT template_id, template_code, template_name, channel_type, 
                   subject_template, body_template, variables, is_active, 
                   created_at, updated_at
            FROM message_templates
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """)
            .param("limit", limit)
            .param("offset", offset)
            .query(this::mapRow)
            .list();
    }

    /**
     * Подсчитать общее количество шаблонов.
     */
    public int count() {
        return jdbcClient.sql("SELECT COUNT(*) FROM message_templates")
            .query(Integer.class)
            .single();
    }

    /**
     * Найти шаблоны по каналу.
     */
    public List<MessageTemplate> findByChannel(String channel, int offset, int limit) {
        return jdbcClient.sql("""
            SELECT template_id, template_code, template_name, channel_type, 
                   subject_template, body_template, variables, is_active, 
                   created_at, updated_at
            FROM message_templates
            WHERE channel_type = :channel
            ORDER BY created_at DESC
            LIMIT :limit OFFSET :offset
            """)
            .param("channel", channel)
            .param("limit", limit)
            .param("offset", offset)
            .query(this::mapRow)
            .list();
    }

    /**
     * Подсчитать шаблоны по каналу.
     */
    public int countByChannel(String channel) {
        return jdbcClient.sql("SELECT COUNT(*) FROM message_templates WHERE channel_type = :channel")
            .param("channel", channel)
            .query(Integer.class)
            .single();
    }

    /**
     * Найти шаблон по ID.
     */
    public Optional<MessageTemplate> findById(Integer id) {
        return jdbcClient.sql("""
            SELECT template_id, template_code, template_name, channel_type, 
                   subject_template, body_template, variables, is_active, 
                   created_at, updated_at
            FROM message_templates
            WHERE template_id = :id
            """)
            .param("id", id)
            .query(this::mapRow)
            .optional();
    }

    /**
     * Найти шаблон по коду.
     */
    public Optional<MessageTemplate> findByCode(String code) {
        return jdbcClient.sql("""
            SELECT template_id, template_code, template_name, channel_type, 
                   subject_template, body_template, variables, is_active, 
                   created_at, updated_at
            FROM message_templates
            WHERE template_code = :code
            """)
            .param("code", code)
            .query(this::mapRow)
            .optional();
    }

    /**
     * Найти активный шаблон по коду и каналу.
     */
    public Optional<MessageTemplate> findActiveByCodeAndChannel(String code, String channel) {
        return jdbcClient.sql("""
            SELECT template_id, template_code, template_name, channel_type, 
                   subject_template, body_template, variables, is_active, 
                   created_at, updated_at
            FROM message_templates
            WHERE template_code = :code AND channel_type = :channel AND is_active = true
            """)
            .param("code", code)
            .param("channel", channel)
            .query(this::mapRow)
            .optional();
    }

    /**
     * Сохранить новый шаблон.
     */
    public Integer save(MessageTemplate template) {
        return jdbcClient.sql("""
            INSERT INTO message_templates (template_code, template_name, channel_type, 
                                           subject_template, body_template, variables, is_active)
            VALUES (:code, :name, :channel, :subjectTemplate, 
                    :bodyTemplate, :variables::VARCHAR[], :isActive)
            RETURNING template_id
            """)
            .param("code", template.getCode())
            .param("name", template.getName())
            .param("channel", template.getChannel())
            .param("subjectTemplate", template.getSubjectTemplate())
            .param("bodyTemplate", template.getBodyTemplate())
            .param("variables", template.getVariables())
            .param("isActive", template.isActive())
            .query(Integer.class)
            .single();
    }

    /**
     * Обновить шаблон.
     */
    public void update(MessageTemplate template) {
        jdbcClient.sql("""
            UPDATE message_templates
            SET template_code = :code,
                template_name = :name,
                channel_type = :channel,
                subject_template = :subjectTemplate,
                body_template = :bodyTemplate,
                variables = :variables::VARCHAR[],
                is_active = :isActive,
                updated_at = NOW()
            WHERE template_id = :id
            """)
            .param("id", template.getId())
            .param("code", template.getCode())
            .param("name", template.getName())
            .param("channel", template.getChannel())
            .param("subjectTemplate", template.getSubjectTemplate())
            .param("bodyTemplate", template.getBodyTemplate())
            .param("variables", template.getVariables())
            .param("isActive", template.isActive())
            .update();
    }

    /**
     * Удалить шаблон по ID.
     */
    public void deleteById(Integer id) {
        jdbcClient.sql("DELETE FROM message_templates WHERE template_id = :id")
            .param("id", id)
            .update();
    }

    private MessageTemplate mapRow(ResultSet rs, int rowNum) throws SQLException {
        MessageTemplate template = new MessageTemplate();
        template.setId(rs.getInt("template_id"));
        template.setCode(rs.getString("template_code"));
        template.setName(rs.getString("template_name"));
        template.setChannel(rs.getString("channel_type"));
        template.setSubjectTemplate(rs.getString("subject_template"));
        template.setBodyTemplate(rs.getString("body_template"));
        
        // Variables хранятся как VARCHAR[] в PostgreSQL
        java.sql.Array varsArray = rs.getArray("variables");
        if (varsArray != null) {
            String[] vars = (String[]) varsArray.getArray();
            template.setVariables(String.join(",", vars));
        }
        
        template.setActive(rs.getBoolean("is_active"));
        template.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
        
        var updatedAt = rs.getTimestamp("updated_at");
        if (updatedAt != null) {
            template.setUpdatedAt(updatedAt.toLocalDateTime());
        }
        
        return template;
    }
}
