"""
Notification Service - Test Configuration
==========================================

Централизованная конфигурация тестового фреймворка.
Поддержка разных окружений: local, docker, staging, production.
"""

from __future__ import annotations

import os
from dataclasses import dataclass, field
from enum import Enum
from pathlib import Path
from typing import Optional

from dotenv import load_dotenv


class Environment(Enum):
    """Тестовые окружения."""
    LOCAL = "local"
    DOCKER = "docker"
    STAGING = "staging"
    PRODUCTION = "production"


@dataclass
class DatabaseConfig:
    """Конфигурация базы данных для тестов."""
    host: str = "localhost"
    port: int = 5432
    database: str = "notification_db"
    username: str = "postgres"
    password: str = "postgres"
    
    @property
    def url(self) -> str:
        """SQLAlchemy connection URL."""
        return f"postgresql://{self.username}:{self.password}@{self.host}:{self.port}/{self.database}"
    
    @property
    def jdbc_url(self) -> str:
        """JDBC connection URL."""
        return f"jdbc:postgresql://{self.host}:{self.port}/{self.database}"


@dataclass
class ApiConfig:
    """Конфигурация API endpoints."""
    base_url: str = "http://localhost:8080"
    api_version: str = "v1"
    timeout: int = 30
    
    @property
    def api_url(self) -> str:
        """Полный URL API."""
        return f"{self.base_url}/api/{self.api_version}"
    
    # Endpoints
    @property
    def health_url(self) -> str:
        return f"{self.api_url}/health"
    
    @property
    def auth_url(self) -> str:
        return f"{self.api_url}/auth"
    
    @property
    def send_url(self) -> str:
        return f"{self.api_url}/send"
    
    @property
    def status_url(self) -> str:
        return f"{self.api_url}/status"
    
    @property
    def admin_url(self) -> str:
        return f"{self.api_url}/admin"


@dataclass
class AuthConfig:
    """Конфигурация аутентификации для тестов."""
    admin_username: str = "admin"
    admin_password: str = "admin123"
    test_api_key: str = ""  # Генерируется динамически
    jwt_secret: str = "test-secret-key"


@dataclass
class ChannelConfig:
    """Конфигурация каналов для тестов."""
    # Email (MailHog для тестов)
    email_host: str = "localhost"
    email_port: int = 1025
    email_from: str = "test@notification.local"
    
    # Telegram (тестовый бот)
    telegram_bot_token: str = ""
    telegram_test_chat_id: str = ""
    
    # SMS (мок)
    sms_api_url: str = "http://localhost:8081/mock/sms"
    sms_api_key: str = "test-sms-key"


@dataclass  
class TestConfig:
    """Главная конфигурация тестового фреймворка."""
    environment: Environment = Environment.DOCKER
    database: DatabaseConfig = field(default_factory=DatabaseConfig)
    api: ApiConfig = field(default_factory=ApiConfig)
    auth: AuthConfig = field(default_factory=AuthConfig)
    channels: ChannelConfig = field(default_factory=ChannelConfig)
    
    # Directories
    root_dir: Path = field(default_factory=lambda: Path(__file__).parent.parent)
    reports_dir: Path = field(default_factory=lambda: Path(__file__).parent.parent / "reports")
    
    # Test settings
    parallel_workers: int = 4
    retry_count: int = 3
    retry_delay: float = 1.0
    
    # Timeouts (seconds)
    default_timeout: int = 30
    long_timeout: int = 120
    short_timeout: int = 5
    
    # Performance thresholds
    max_response_time_ms: int = 500
    max_notification_delay_ms: int = 5000
    
    @classmethod
    def from_env(cls, env_file: Optional[str] = None) -> TestConfig:
        """
        Загрузка конфигурации из переменных окружения.
        
        Args:
            env_file: Путь к .env файлу
            
        Returns:
            Инициализированная конфигурация
        """
        if env_file:
            load_dotenv(env_file)
        else:
            # Попробовать найти .env в корне проекта
            root = Path(__file__).parent.parent.parent
            env_path = root / ".env.test"
            if env_path.exists():
                load_dotenv(env_path)
        
        env_name = os.getenv("TEST_ENV", "docker")
        
        return cls(
            environment=Environment(env_name),
            database=DatabaseConfig(
                host=os.getenv("TEST_DB_HOST", "localhost"),
                port=int(os.getenv("TEST_DB_PORT", "5432")),
                database=os.getenv("TEST_DB_NAME", "notification_db"),
                username=os.getenv("TEST_DB_USER", "postgres"),
                password=os.getenv("TEST_DB_PASSWORD", "postgres"),
            ),
            api=ApiConfig(
                base_url=os.getenv("TEST_API_URL", "http://localhost:8080"),
                timeout=int(os.getenv("TEST_API_TIMEOUT", "30")),
            ),
            auth=AuthConfig(
                admin_username=os.getenv("TEST_ADMIN_USER", "admin"),
                admin_password=os.getenv("TEST_ADMIN_PASS", "admin123"),
            ),
            channels=ChannelConfig(
                email_host=os.getenv("TEST_SMTP_HOST", "localhost"),
                email_port=int(os.getenv("TEST_SMTP_PORT", "1025")),
                telegram_bot_token=os.getenv("TEST_TELEGRAM_TOKEN", ""),
                telegram_test_chat_id=os.getenv("TEST_TELEGRAM_CHAT", ""),
            ),
            parallel_workers=int(os.getenv("TEST_WORKERS", "4")),
        )
    
    def ensure_dirs(self) -> None:
        """Создание необходимых директорий."""
        self.reports_dir.mkdir(parents=True, exist_ok=True)
        (self.reports_dir / "coverage").mkdir(exist_ok=True)
        (self.reports_dir / "allure").mkdir(exist_ok=True)
        (self.reports_dir / "performance").mkdir(exist_ok=True)


# Singleton для глобального доступа
_config: Optional[TestConfig] = None


def get_config() -> TestConfig:
    """Получить глобальную конфигурацию."""
    global _config
    if _config is None:
        _config = TestConfig.from_env()
        _config.ensure_dirs()
    return _config


def reset_config() -> None:
    """Сбросить конфигурацию (для тестов)."""
    global _config
    _config = None
