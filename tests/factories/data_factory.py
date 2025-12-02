"""
Notification Service - Test Data Factories
===========================================

Фабрики для генерации тестовых данных с использованием Faker.
"""

from __future__ import annotations

import random
import string
import uuid
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional

from faker import Faker

from core.base import NotificationChannel, NotificationStatus, Priority

fake = Faker("ru_RU")
Faker.seed(42)  # Для воспроизводимости


class IdGenerator:
    """Генератор уникальных идентификаторов."""
    
    _counter = 0
    
    @classmethod
    def next_id(cls) -> int:
        """Следующий числовой ID."""
        cls._counter += 1
        return cls._counter
    
    @staticmethod
    def uuid() -> str:
        """UUID v4."""
        return str(uuid.uuid4())
    
    @staticmethod
    def short_uuid() -> str:
        """Короткий UUID (8 символов)."""
        return uuid.uuid4().hex[:8]
    
    @staticmethod
    def idempotency_key() -> str:
        """Ключ идемпотентности."""
        return f"idem-{uuid.uuid4().hex[:16]}"
    
    @classmethod
    def reset(cls) -> None:
        """Сброс счётчика."""
        cls._counter = 0


class UserFactory:
    """Фабрика для генерации пользовательских данных."""
    
    @staticmethod
    def email() -> str:
        """Случайный email."""
        return f"test-{IdGenerator.short_uuid()}@notification.test"
    
    @staticmethod
    def phone() -> str:
        """Случайный телефон в формате +7XXXXXXXXXX."""
        return f"+7{fake.msisdn()[3:13]}"
    
    @staticmethod
    def telegram_chat_id() -> str:
        """Случайный Telegram chat ID."""
        return str(random.randint(100000000, 999999999))
    
    @staticmethod
    def name() -> str:
        """Случайное имя."""
        return fake.name()
    
    @staticmethod
    def username() -> str:
        """Случайный username."""
        return f"user_{IdGenerator.short_uuid()}"
    
    @staticmethod
    def password(length: int = 12) -> str:
        """Случайный пароль."""
        chars = string.ascii_letters + string.digits + "!@#$%"
        return "".join(random.choice(chars) for _ in range(length))


class NotificationFactory:
    """Фабрика для генерации уведомлений."""
    
    @staticmethod
    def create(
        channel: NotificationChannel = NotificationChannel.EMAIL,
        recipient: Optional[str] = None,
        message: Optional[str] = None,
        subject: Optional[str] = None,
        priority: Priority = Priority.NORMAL,
        **kwargs
    ) -> Dict[str, Any]:
        """
        Создание данных уведомления.
        
        Args:
            channel: Канал отправки
            recipient: Получатель (генерируется автоматически)
            message: Текст сообщения
            subject: Тема (для email)
            priority: Приоритет
            **kwargs: Дополнительные поля
            
        Returns:
            Dict с данными уведомления
        """
        # Генерация получателя по типу канала
        if recipient is None:
            if channel == NotificationChannel.EMAIL:
                recipient = UserFactory.email()
            elif channel == NotificationChannel.TELEGRAM:
                recipient = UserFactory.telegram_chat_id()
            elif channel == NotificationChannel.SMS:
                recipient = UserFactory.phone()
            else:
                recipient = UserFactory.email()
        
        # Генерация контента
        if message is None:
            message = fake.paragraph(nb_sentences=2)
        
        if subject is None and channel == NotificationChannel.EMAIL:
            subject = fake.sentence(nb_words=5)
        
        data = {
            "channel": channel.value,
            "recipient": recipient,
            "message": message,
            "priority": priority.value,
        }
        
        if subject:
            data["subject"] = subject
        
        # Дополнительные поля
        data.update(kwargs)
        
        return data
    
    @staticmethod
    def create_email(
        recipient: Optional[str] = None,
        subject: Optional[str] = None,
        message: Optional[str] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """Создание email уведомления."""
        return NotificationFactory.create(
            channel=NotificationChannel.EMAIL,
            recipient=recipient,
            subject=subject,
            message=message,
            **kwargs
        )
    
    @staticmethod
    def create_telegram(
        chat_id: Optional[str] = None,
        message: Optional[str] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """Создание Telegram уведомления."""
        return NotificationFactory.create(
            channel=NotificationChannel.TELEGRAM,
            recipient=chat_id,
            message=message,
            **kwargs
        )
    
    @staticmethod
    def create_sms(
        phone: Optional[str] = None,
        message: Optional[str] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """Создание SMS уведомления."""
        # SMS ограничены по длине
        if message is None:
            message = fake.sentence(nb_words=10)[:160]
        
        return NotificationFactory.create(
            channel=NotificationChannel.SMS,
            recipient=phone,
            message=message,
            **kwargs
        )
    
    @staticmethod
    def create_with_template(
        template_code: str,
        variables: Dict[str, str],
        channel: NotificationChannel = NotificationChannel.EMAIL,
        recipient: Optional[str] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """Создание уведомления с шаблоном."""
        data = NotificationFactory.create(
            channel=channel,
            recipient=recipient,
            message="",  # Будет заменено шаблоном
            **kwargs
        )
        data["templateCode"] = template_code
        data["templateVariables"] = variables
        return data
    
    @staticmethod
    def create_batch(
        count: int,
        channel: NotificationChannel = NotificationChannel.EMAIL,
        **kwargs
    ) -> List[Dict[str, Any]]:
        """Создание пакета уведомлений."""
        return [
            NotificationFactory.create(channel=channel, **kwargs)
            for _ in range(count)
        ]


class ApiClientFactory:
    """Фабрика для генерации API клиентов."""
    
    @staticmethod
    def create(
        name: Optional[str] = None,
        description: Optional[str] = None,
        rate_limit: int = 100,
        allowed_channels: Optional[List[str]] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """Создание данных API клиента."""
        if name is None:
            name = f"Test Client {IdGenerator.short_uuid()}"
        
        if description is None:
            description = fake.sentence(nb_words=10)
        
        if allowed_channels is None:
            allowed_channels = ["EMAIL"]
        
        return {
            "name": name,
            "description": description,
            "rateLimit": rate_limit,
            "allowedChannels": allowed_channels,
            **kwargs
        }
    
    @staticmethod
    def create_full_access(name: Optional[str] = None) -> Dict[str, Any]:
        """Клиент с доступом ко всем каналам."""
        return ApiClientFactory.create(
            name=name,
            allowed_channels=["EMAIL", "TELEGRAM", "SMS", "PUSH", "WHATSAPP"],
            rate_limit=1000,
        )
    
    @staticmethod
    def create_email_only(name: Optional[str] = None) -> Dict[str, Any]:
        """Клиент только для email."""
        return ApiClientFactory.create(
            name=name,
            allowed_channels=["EMAIL"],
            rate_limit=100,
        )


class TemplateFactory:
    """Фабрика для генерации шаблонов."""
    
    @staticmethod
    def create(
        code: Optional[str] = None,
        name: Optional[str] = None,
        channel: str = "EMAIL",
        subject: Optional[str] = None,
        body: Optional[str] = None,
        variables: Optional[List[str]] = None,
        **kwargs
    ) -> Dict[str, Any]:
        """Создание данных шаблона."""
        if code is None:
            code = f"TPL_{IdGenerator.short_uuid().upper()}"
        
        if name is None:
            name = f"Template {fake.word().capitalize()}"
        
        if body is None:
            body = "Привет, {{name}}! {{message}}"
            variables = ["name", "message"]
        
        if channel == "EMAIL" and subject is None:
            subject = "{{subject}}"
            if variables is None:
                variables = []
            variables.append("subject")
        
        return {
            "code": code,
            "name": name,
            "channel": channel,
            "subject": subject,
            "body": body,
            "variables": variables or [],
            **kwargs
        }
    
    @staticmethod
    def create_welcome_email() -> Dict[str, Any]:
        """Шаблон приветственного email."""
        return TemplateFactory.create(
            code="WELCOME_EMAIL",
            name="Welcome Email",
            channel="EMAIL",
            subject="Добро пожаловать, {{name}}!",
            body="""
            <h1>Привет, {{name}}!</h1>
            <p>Добро пожаловать в наш сервис.</p>
            <p>Ваш email: {{email}}</p>
            """,
            variables=["name", "email"],
        )
    
    @staticmethod
    def create_otp_sms() -> Dict[str, Any]:
        """Шаблон SMS с OTP кодом."""
        return TemplateFactory.create(
            code="OTP_SMS",
            name="OTP Code",
            channel="SMS",
            body="Ваш код подтверждения: {{code}}. Действует 5 минут.",
            variables=["code"],
        )


class ChannelConfigFactory:
    """Фабрика для конфигурации каналов."""
    
    @staticmethod
    def email_config(
        host: str = "smtp.example.com",
        port: int = 587,
        username: str = "test@example.com",
        password: str = "secret",
        from_address: str = "noreply@example.com",
        use_tls: bool = True,
    ) -> Dict[str, Any]:
        """Конфигурация email канала."""
        return {
            "host": host,
            "port": port,
            "username": username,
            "password": password,
            "fromAddress": from_address,
            "useTls": use_tls,
        }
    
    @staticmethod
    def telegram_config(
        bot_token: str = "123456:ABC-DEF",
    ) -> Dict[str, Any]:
        """Конфигурация Telegram канала."""
        return {
            "botToken": bot_token,
        }
    
    @staticmethod
    def sms_config(
        api_url: str = "https://api.sms-provider.com",
        api_key: str = "test-api-key",
        sender: str = "NOTIFY",
    ) -> Dict[str, Any]:
        """Конфигурация SMS канала."""
        return {
            "apiUrl": api_url,
            "apiKey": api_key,
            "sender": sender,
        }


class AuditLogFactory:
    """Фабрика для аудит логов (для тестов чтения)."""
    
    @staticmethod
    def create_expected(
        action: str = "CREATE",
        entity_type: str = "NOTIFICATION",
        entity_id: Optional[str] = None,
        user_id: int = 1,
        details: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """Ожидаемые данные аудит лога."""
        return {
            "action": action,
            "entityType": entity_type,
            "entityId": entity_id or IdGenerator.uuid(),
            "userId": user_id,
            "details": details or {},
        }


# =============================================================================
# Bulk Generation
# =============================================================================

class BulkFactory:
    """Массовая генерация тестовых данных."""
    
    @staticmethod
    def generate_notifications(
        count: int,
        channels: Optional[List[NotificationChannel]] = None,
    ) -> List[Dict[str, Any]]:
        """Генерация списка уведомлений."""
        if channels is None:
            channels = [NotificationChannel.EMAIL]
        
        return [
            NotificationFactory.create(channel=random.choice(channels))
            for _ in range(count)
        ]
    
    @staticmethod
    def generate_clients(count: int) -> List[Dict[str, Any]]:
        """Генерация списка API клиентов."""
        return [ApiClientFactory.create() for _ in range(count)]
    
    @staticmethod
    def generate_templates(
        count: int,
        channels: Optional[List[str]] = None,
    ) -> List[Dict[str, Any]]:
        """Генерация списка шаблонов."""
        if channels is None:
            channels = ["EMAIL", "SMS", "TELEGRAM"]
        
        return [
            TemplateFactory.create(channel=random.choice(channels))
            for _ in range(count)
        ]


# =============================================================================
# Test Scenarios
# =============================================================================

class ScenarioFactory:
    """Генерация комплексных тестовых сценариев."""
    
    @staticmethod
    def notification_lifecycle() -> Dict[str, Any]:
        """
        Сценарий жизненного цикла уведомления.
        
        Returns:
            Данные для полного тестового сценария
        """
        notification = NotificationFactory.create_email()
        notification["idempotencyKey"] = IdGenerator.idempotency_key()
        notification["callbackUrl"] = "https://webhook.test/callback"
        notification["metadata"] = {
            "orderId": IdGenerator.short_uuid(),
            "userId": IdGenerator.next_id(),
        }
        
        return {
            "notification": notification,
            "expected_statuses": [
                NotificationStatus.PENDING.value,
                NotificationStatus.SENT.value,
                NotificationStatus.DELIVERED.value,
            ],
        }
    
    @staticmethod
    def multi_channel_campaign() -> Dict[str, Any]:
        """
        Сценарий рассылки по нескольким каналам.
        """
        recipient_email = UserFactory.email()
        recipient_phone = UserFactory.phone()
        recipient_telegram = UserFactory.telegram_chat_id()
        
        campaign_id = IdGenerator.short_uuid()
        
        return {
            "campaign_id": campaign_id,
            "notifications": [
                {
                    **NotificationFactory.create_email(recipient=recipient_email),
                    "metadata": {"campaignId": campaign_id, "channel": "email"},
                },
                {
                    **NotificationFactory.create_sms(phone=recipient_phone),
                    "metadata": {"campaignId": campaign_id, "channel": "sms"},
                },
                {
                    **NotificationFactory.create_telegram(chat_id=recipient_telegram),
                    "metadata": {"campaignId": campaign_id, "channel": "telegram"},
                },
            ],
        }
    
    @staticmethod
    def rate_limit_test(client_rate_limit: int = 10) -> Dict[str, Any]:
        """
        Сценарий тестирования rate limit.
        """
        client = ApiClientFactory.create(rate_limit=client_rate_limit)
        
        # Генерируем больше запросов чем лимит
        notifications = NotificationFactory.create_batch(
            count=client_rate_limit + 5
        )
        
        return {
            "client": client,
            "notifications": notifications,
            "expected_success_count": client_rate_limit,
            "expected_throttled_count": 5,
        }
