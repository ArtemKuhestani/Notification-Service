-- =============================================
-- Notification Service Database Schema
-- PostgreSQL 15+
-- =============================================

-- Включение расширения для генерации UUID
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- =============================================
-- Таблица администраторов
-- =============================================
CREATE TABLE IF NOT EXISTS admins (
    admin_id SERIAL PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(60) NOT NULL,
    full_name VARCHAR(200) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'VIEWER',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP NULL,
    last_login_at TIMESTAMP NULL,
    last_login_ip INET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_admin_role CHECK (role IN ('ADMIN', 'VIEWER'))
);

CREATE INDEX IF NOT EXISTS idx_admins_email ON admins(email);
CREATE INDEX IF NOT EXISTS idx_admins_active ON admins(is_active) WHERE is_active = TRUE;

-- =============================================
-- Таблица API-клиентов
-- =============================================
CREATE TABLE IF NOT EXISTS api_clients (
    client_id SERIAL PRIMARY KEY,
    client_name VARCHAR(100) NOT NULL UNIQUE,
    client_description TEXT NULL,
    api_key_hash VARCHAR(64) NOT NULL,
    api_key_prefix VARCHAR(8) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    rate_limit INTEGER NOT NULL DEFAULT 100,
    allowed_channels VARCHAR[] NULL,
    allowed_ips INET[] NULL,
    callback_url_default VARCHAR(500) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by INTEGER NULL,
    last_used_at TIMESTAMP NULL,
    
    CONSTRAINT fk_api_clients_admin FOREIGN KEY (created_by) REFERENCES admins(admin_id)
);

CREATE INDEX IF NOT EXISTS idx_api_clients_api_key_hash ON api_clients(api_key_hash);
CREATE INDEX IF NOT EXISTS idx_api_clients_active ON api_clients(is_active) WHERE is_active = TRUE;

-- =============================================
-- Таблица конфигурации каналов
-- =============================================
CREATE TABLE IF NOT EXISTS channel_configs (
    config_id SERIAL PRIMARY KEY,
    channel_name VARCHAR(20) NOT NULL UNIQUE,
    provider_name VARCHAR(50) NOT NULL,
    credentials BYTEA NOT NULL,
    settings JSONB NULL,
    is_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    priority INTEGER NOT NULL DEFAULT 0,
    daily_limit INTEGER NULL,
    daily_sent_count INTEGER NOT NULL DEFAULT 0,
    last_health_check TIMESTAMP NULL,
    health_status VARCHAR(20) DEFAULT 'UNKNOWN',
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT chk_channel_name CHECK (channel_name IN ('EMAIL', 'TELEGRAM', 'WHATSAPP', 'SMS')),
    CONSTRAINT chk_health_status CHECK (health_status IN ('HEALTHY', 'UNHEALTHY', 'UNKNOWN'))
);

-- =============================================
-- Таблица журнала уведомлений
-- =============================================
CREATE TABLE IF NOT EXISTS notifications (
    notification_id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_id INTEGER NOT NULL,
    channel_type VARCHAR(20) NOT NULL,
    recipient VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NULL,
    message_body TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    priority VARCHAR(10) NOT NULL DEFAULT 'NORMAL',
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 5,
    next_retry_at TIMESTAMP NULL,
    error_message TEXT NULL,
    error_code VARCHAR(50) NULL,
    provider_message_id VARCHAR(255) NULL,
    idempotency_key VARCHAR(255) NULL UNIQUE,
    callback_url VARCHAR(500) NULL,
    metadata JSONB NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    sent_at TIMESTAMP NULL,
    expires_at TIMESTAMP NULL,
    
    CONSTRAINT fk_notifications_client FOREIGN KEY (client_id) REFERENCES api_clients(client_id),
    CONSTRAINT chk_notification_channel CHECK (channel_type IN ('EMAIL', 'TELEGRAM', 'WHATSAPP', 'SMS')),
    CONSTRAINT chk_notification_status CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'DELIVERED', 'FAILED', 'EXPIRED')),
    CONSTRAINT chk_notification_priority CHECK (priority IN ('HIGH', 'NORMAL', 'LOW'))
);

CREATE INDEX IF NOT EXISTS idx_notifications_status ON notifications(status);
CREATE INDEX IF NOT EXISTS idx_notifications_client_id ON notifications(client_id);
CREATE INDEX IF NOT EXISTS idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_notifications_next_retry ON notifications(next_retry_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_notifications_idempotency ON notifications(idempotency_key);
CREATE INDEX IF NOT EXISTS idx_notifications_channel_type ON notifications(channel_type);

-- =============================================
-- Таблица шаблонов сообщений
-- =============================================
CREATE TABLE IF NOT EXISTS message_templates (
    template_id SERIAL PRIMARY KEY,
    template_code VARCHAR(50) NOT NULL UNIQUE,
    template_name VARCHAR(200) NOT NULL,
    channel_type VARCHAR(20) NOT NULL,
    subject_template VARCHAR(500) NULL,
    body_template TEXT NOT NULL,
    variables VARCHAR[] NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_by INTEGER NULL,
    
    CONSTRAINT fk_templates_admin FOREIGN KEY (created_by) REFERENCES admins(admin_id),
    CONSTRAINT chk_template_channel CHECK (channel_type IN ('EMAIL', 'TELEGRAM', 'WHATSAPP', 'SMS'))
);

CREATE INDEX IF NOT EXISTS idx_templates_code ON message_templates(template_code);
CREATE INDEX IF NOT EXISTS idx_templates_channel ON message_templates(channel_type);

-- =============================================
-- Таблица журнала аудита
-- =============================================
CREATE TABLE IF NOT EXISTS audit_log (
    log_id BIGSERIAL PRIMARY KEY,
    admin_id INTEGER NULL,
    action_type VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id VARCHAR(50) NULL,
    old_value JSONB NULL,
    new_value JSONB NULL,
    ip_address INET NOT NULL,
    user_agent TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_audit_admin FOREIGN KEY (admin_id) REFERENCES admins(admin_id)
);

CREATE INDEX IF NOT EXISTS idx_audit_log_admin_id ON audit_log(admin_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_action_type ON audit_log(action_type);
CREATE INDEX IF NOT EXISTS idx_audit_log_entity_type ON audit_log(entity_type);
CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at DESC);

-- =============================================
-- Таблица очереди повторных отправок
-- =============================================
CREATE TABLE IF NOT EXISTS retry_queue (
    queue_id BIGSERIAL PRIMARY KEY,
    notification_id UUID NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    attempt_number INTEGER NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMP NULL,
    error_message TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    CONSTRAINT fk_retry_notification FOREIGN KEY (notification_id) REFERENCES notifications(notification_id),
    CONSTRAINT chk_retry_status CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX IF NOT EXISTS idx_retry_queue_scheduled ON retry_queue(scheduled_at) WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS idx_retry_queue_notification ON retry_queue(notification_id);

-- Примечание: Триггеры для автоматического обновления updated_at опущены 
-- так как Spring JDBC не поддерживает $$ блоки в SQL скриптах.
-- Обновление updated_at выполняется на уровне приложения.
