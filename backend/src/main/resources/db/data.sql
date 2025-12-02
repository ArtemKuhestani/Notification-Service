-- =============================================
-- Notification Service - Initial Data
-- =============================================

-- Создание администратора по умолчанию
-- Пароль: admin123 (bcrypt hash)
INSERT INTO admins (email, password_hash, full_name, role, is_active)
VALUES ('admin@notification-service.com', '$2a$10$N9qo8uLOickgx2ZMRZoMy.MqEZ3oRCCq3bYCr8a0xzOrmALe64J3K', 'System Administrator', 'ADMIN', TRUE)
ON CONFLICT (email) DO NOTHING;

-- Создание тестового API-клиента
-- API Key: ns_test_api_key_12345678 (SHA-256: хэш будет вычислен при реальном создании)
INSERT INTO api_clients (client_name, client_description, api_key_hash, api_key_prefix, is_active, rate_limit, created_by)
VALUES (
    'Test Client', 
    'Тестовый клиент для разработки', 
    'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855', 
    'ns_test_', 
    TRUE, 
    100,
    1
)
ON CONFLICT (client_name) DO NOTHING;

-- Конфигурация каналов по умолчанию (без реальных credentials)
INSERT INTO channel_configs (channel_name, provider_name, credentials, settings, is_enabled, priority)
VALUES 
    ('EMAIL', 'SMTP', '', '{"host": "smtp.gmail.com", "port": 587, "from_email": "noreply@example.com", "from_name": "Notification Service", "use_tls": true}', FALSE, 1),
    ('TELEGRAM', 'Telegram Bot API', '', '{"parse_mode": "Markdown", "disable_web_page_preview": false}', FALSE, 2),
    ('SMS', 'SMS Provider', '', '{"sender_id": "NotifySvc"}', FALSE, 3),
    ('WHATSAPP', 'WhatsApp Business API', '', '{}', FALSE, 4)
ON CONFLICT (channel_name) DO NOTHING;

-- Шаблоны сообщений по умолчанию
INSERT INTO message_templates (template_code, template_name, channel_type, subject_template, body_template, variables, is_active, created_by)
VALUES 
    ('USER_REGISTRATION', 'Регистрация пользователя', 'EMAIL', 'Подтверждение регистрации', 'Здравствуйте, {{name}}! Ваш код подтверждения: {{code}}. Код действителен в течение 10 минут.', ARRAY['name', 'code'], TRUE, 1),
    ('PASSWORD_RESET', 'Сброс пароля', 'EMAIL', 'Сброс пароля', 'Здравствуйте, {{name}}! Для сброса пароля перейдите по ссылке: {{link}}', ARRAY['name', 'link'], TRUE, 1),
    ('ORDER_CONFIRMATION', 'Подтверждение заказа', 'EMAIL', 'Заказ #{{order_id}} подтверждён', 'Ваш заказ #{{order_id}} на сумму {{amount}} успешно оформлен. Ожидайте доставку.', ARRAY['order_id', 'amount'], TRUE, 1),
    ('SMS_VERIFICATION', 'SMS верификация', 'SMS', NULL, 'Ваш код подтверждения: {{code}}', ARRAY['code'], TRUE, 1)
ON CONFLICT (template_code) DO NOTHING;
