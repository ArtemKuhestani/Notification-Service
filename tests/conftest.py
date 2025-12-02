"""
Notification Service - Pytest Fixtures
========================================

Глобальные фикстуры для всех тестов.
"""

from __future__ import annotations

import logging
import os
import sys
from pathlib import Path
from typing import Generator

import pytest

# Добавляем директорию tests в PYTHONPATH
sys.path.insert(0, str(Path(__file__).parent))

from api import NotificationApiClient, create_api_client
from config import TestConfig, get_config, reset_config
from factories import (
    ApiClientFactory,
    IdGenerator,
    NotificationFactory,
    TemplateFactory,
    UserFactory,
)

# Настройка логирования
logging.basicConfig(
    level=logging.DEBUG if os.getenv("DEBUG") else logging.INFO,
    format="%(asctime)s | %(levelname)-8s | %(name)s | %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger("notification_tests")


# =============================================================================
# Configuration Fixtures
# =============================================================================

@pytest.fixture(scope="session")
def test_config() -> TestConfig:
    """Конфигурация тестового окружения (один раз на сессию)."""
    config = get_config()
    config.ensure_dirs()
    logger.info(f"Test environment: {config.environment.value}")
    logger.info(f"API URL: {config.api.api_url}")
    return config


@pytest.fixture(scope="session", autouse=True)
def setup_test_session(test_config: TestConfig) -> Generator[None, None, None]:
    """Инициализация тестовой сессии."""
    logger.info("=" * 60)
    logger.info("Starting Notification Service Test Session")
    logger.info("=" * 60)
    
    # Reset ID generator
    IdGenerator.reset()
    
    yield
    
    logger.info("=" * 60)
    logger.info("Test Session Completed")
    logger.info("=" * 60)
    
    # Cleanup
    reset_config()


# =============================================================================
# API Client Fixtures
# =============================================================================

@pytest.fixture(scope="session")
def api_client(test_config: TestConfig) -> Generator[NotificationApiClient, None, None]:
    """API клиент для тестов (один на сессию)."""
    client = create_api_client()
    yield client
    client.close()


@pytest.fixture(scope="function")
def api(test_config: TestConfig) -> Generator[NotificationApiClient, None, None]:
    """API клиент для каждого теста (изолированный)."""
    client = create_api_client()
    yield client
    client.close()


@pytest.fixture(scope="session")
def authenticated_client(
    api_client: NotificationApiClient,
    test_config: TestConfig
) -> NotificationApiClient:
    """Авторизованный API клиент."""
    api_client.authenticate_admin()
    return api_client


@pytest.fixture(scope="function")
def auth_api(api: NotificationApiClient) -> NotificationApiClient:
    """Авторизованный API клиент для каждого теста."""
    api.authenticate_admin()
    return api


# =============================================================================
# Data Fixtures
# =============================================================================

@pytest.fixture
def random_email() -> str:
    """Случайный email."""
    return UserFactory.email()


@pytest.fixture
def random_phone() -> str:
    """Случайный телефон."""
    return UserFactory.phone()


@pytest.fixture
def random_telegram_id() -> str:
    """Случайный Telegram chat ID."""
    return UserFactory.telegram_chat_id()


@pytest.fixture
def email_notification() -> dict:
    """Данные email уведомления."""
    return NotificationFactory.create_email()


@pytest.fixture
def telegram_notification() -> dict:
    """Данные Telegram уведомления."""
    return NotificationFactory.create_telegram()


@pytest.fixture
def sms_notification() -> dict:
    """Данные SMS уведомления."""
    return NotificationFactory.create_sms()


@pytest.fixture
def api_client_data() -> dict:
    """Данные для создания API клиента."""
    return ApiClientFactory.create()


@pytest.fixture
def template_data() -> dict:
    """Данные для создания шаблона."""
    return TemplateFactory.create()


@pytest.fixture
def idempotency_key() -> str:
    """Ключ идемпотентности."""
    return IdGenerator.idempotency_key()


# =============================================================================
# Test Helpers
# =============================================================================

@pytest.fixture
def create_notification(auth_api: NotificationApiClient):
    """
    Фикстура-фабрика для создания уведомлений.
    
    Usage:
        def test_example(create_notification):
            notification_id = create_notification(channel="EMAIL", ...)
    """
    created_ids = []
    
    def _create(**kwargs) -> str:
        data = NotificationFactory.create(**kwargs)
        response = auth_api.send_notification(**data)
        
        if response.is_success:
            notification_id = response.json().get("notificationId")
            created_ids.append(notification_id)
            return notification_id
        raise RuntimeError(f"Failed to create notification: {response.body}")
    
    yield _create
    
    # Cleanup - опционально отменить созданные уведомления
    for nid in created_ids:
        try:
            auth_api.cancel_notification(nid)
        except Exception:
            pass


@pytest.fixture
def create_api_client_entity(auth_api: NotificationApiClient):
    """
    Фикстура-фабрика для создания API клиентов.
    """
    created_ids = []
    
    def _create(**kwargs) -> tuple[int, str]:
        data = ApiClientFactory.create(**kwargs)
        response = auth_api.create_client(**data)
        
        if response.is_success:
            body = response.json()
            client_id = body.get("id")
            api_key = body.get("apiKey")
            created_ids.append(client_id)
            return client_id, api_key
        raise RuntimeError(f"Failed to create client: {response.body}")
    
    yield _create
    
    # Cleanup
    for cid in created_ids:
        try:
            auth_api.delete_client(cid)
        except Exception:
            pass


@pytest.fixture
def create_template_entity(auth_api: NotificationApiClient):
    """
    Фикстура-фабрика для создания шаблонов.
    """
    created_ids = []
    
    def _create(**kwargs) -> int:
        data = TemplateFactory.create(**kwargs)
        response = auth_api.create_template(**data)
        
        if response.is_success:
            template_id = response.json().get("id")
            created_ids.append(template_id)
            return template_id
        raise RuntimeError(f"Failed to create template: {response.body}")
    
    yield _create
    
    # Cleanup
    for tid in created_ids:
        try:
            auth_api.delete_template(tid)
        except Exception:
            pass


# =============================================================================
# Markers Configuration
# =============================================================================

def pytest_configure(config):
    """Регистрация кастомных маркеров."""
    config.addinivalue_line("markers", "smoke: Quick sanity checks")
    config.addinivalue_line("markers", "api: API endpoint tests")
    config.addinivalue_line("markers", "integration: Integration tests")
    config.addinivalue_line("markers", "e2e: End-to-end tests")
    config.addinivalue_line("markers", "performance: Performance tests")
    config.addinivalue_line("markers", "security: Security tests")
    config.addinivalue_line("markers", "auth: Authentication tests")
    config.addinivalue_line("markers", "notifications: Notification flow tests")
    config.addinivalue_line("markers", "channels: Channel configuration tests")
    config.addinivalue_line("markers", "templates: Template management tests")
    config.addinivalue_line("markers", "clients: API client tests")
    config.addinivalue_line("markers", "audit: Audit log tests")
    config.addinivalue_line("markers", "slow: Slow tests (> 5s)")


def pytest_collection_modifyitems(config, items):
    """Модификация тестов при сборке."""
    # Добавляем маркер api ко всем тестам в tests/api/
    for item in items:
        if "test_api" in str(item.fspath):
            item.add_marker(pytest.mark.api)
        
        # Добавляем timeout к slow тестам
        if "slow" in [m.name for m in item.iter_markers()]:
            item.add_marker(pytest.mark.timeout(120))


# =============================================================================
# Reporting Hooks
# =============================================================================

@pytest.hookimpl(hookwrapper=True)
def pytest_runtest_makereport(item, call):
    """Хук для расширенного репортинга."""
    outcome = yield
    report = outcome.get_result()
    
    # Добавляем время выполнения
    if report.when == "call":
        report.duration_formatted = f"{report.duration:.3f}s"
    
    # Логируем failures
    if report.failed:
        logger.error(f"FAILED: {item.name}")
        if hasattr(report, "longrepr"):
            logger.error(f"  Reason: {report.longrepr}")
