"""
Notification Service - Core Test Utilities
===========================================

Базовые классы и утилиты для тестирования.
"""

from __future__ import annotations

import asyncio
import json
import logging
import time
import uuid
from abc import ABC, abstractmethod
from contextlib import contextmanager
from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum
from functools import wraps
from typing import Any, Callable, Dict, Generator, List, Optional, Type, TypeVar

from rich.console import Console
from rich.table import Table
from tenacity import (
    retry,
    retry_if_exception_type,
    stop_after_attempt,
    wait_exponential,
)

# Logging setup
logger = logging.getLogger("notification_tests")
console = Console()

T = TypeVar("T")


# =============================================================================
# Enums
# =============================================================================

class TestStatus(Enum):
    """Статусы тестов."""
    PASSED = "passed"
    FAILED = "failed"
    SKIPPED = "skipped"
    ERROR = "error"
    XFAIL = "xfail"


class Severity(Enum):
    """Уровни критичности тестов (Allure)."""
    BLOCKER = "blocker"
    CRITICAL = "critical"
    NORMAL = "normal"
    MINOR = "minor"
    TRIVIAL = "trivial"


class NotificationChannel(Enum):
    """Каналы уведомлений."""
    EMAIL = "EMAIL"
    TELEGRAM = "TELEGRAM"
    SMS = "SMS"
    PUSH = "PUSH"
    WHATSAPP = "WHATSAPP"


class NotificationStatus(Enum):
    """Статусы уведомлений."""
    PENDING = "PENDING"
    SENT = "SENT"
    DELIVERED = "DELIVERED"
    FAILED = "FAILED"
    CANCELLED = "CANCELLED"


class Priority(Enum):
    """Приоритеты уведомлений."""
    LOW = "LOW"
    NORMAL = "NORMAL"
    HIGH = "HIGH"
    CRITICAL = "CRITICAL"


# =============================================================================
# Data Classes
# =============================================================================

@dataclass
class TestResult:
    """Результат выполнения теста."""
    name: str
    status: TestStatus
    duration_ms: float
    timestamp: datetime = field(default_factory=datetime.now)
    error_message: Optional[str] = None
    error_traceback: Optional[str] = None
    assertions: List[str] = field(default_factory=list)
    metadata: Dict[str, Any] = field(default_factory=dict)
    
    def to_dict(self) -> Dict[str, Any]:
        """Сериализация в словарь."""
        return {
            "name": self.name,
            "status": self.status.value,
            "duration_ms": self.duration_ms,
            "timestamp": self.timestamp.isoformat(),
            "error_message": self.error_message,
            "assertions": self.assertions,
            "metadata": self.metadata,
        }


@dataclass
class ApiResponse:
    """Обёртка над HTTP ответом."""
    status_code: int
    body: Any
    headers: Dict[str, str]
    duration_ms: float
    request_id: Optional[str] = None
    
    @property
    def is_success(self) -> bool:
        """Успешный ответ (2xx)."""
        return 200 <= self.status_code < 300
    
    @property
    def is_client_error(self) -> bool:
        """Ошибка клиента (4xx)."""
        return 400 <= self.status_code < 500
    
    @property
    def is_server_error(self) -> bool:
        """Ошибка сервера (5xx)."""
        return 500 <= self.status_code < 600
    
    def json(self) -> Dict[str, Any]:
        """Тело как JSON."""
        if isinstance(self.body, dict):
            return self.body
        if isinstance(self.body, str):
            return json.loads(self.body)
        return {}


@dataclass
class PerformanceMetrics:
    """Метрики производительности."""
    min_ms: float
    max_ms: float
    avg_ms: float
    median_ms: float
    p95_ms: float
    p99_ms: float
    total_requests: int
    failed_requests: int
    requests_per_second: float
    
    @property
    def success_rate(self) -> float:
        """Процент успешных запросов."""
        if self.total_requests == 0:
            return 0.0
        return (self.total_requests - self.failed_requests) / self.total_requests * 100


# =============================================================================
# Base Classes
# =============================================================================

class BaseTestCase(ABC):
    """
    Базовый класс для тестовых случаев.
    
    Обеспечивает:
    - Setup/teardown хуки
    - Логирование
    - Общие утилиты
    """
    
    @classmethod
    def setup_class(cls) -> None:
        """Инициализация перед всеми тестами класса."""
        logger.info(f"Setting up test class: {cls.__name__}")
    
    @classmethod
    def teardown_class(cls) -> None:
        """Очистка после всех тестов класса."""
        logger.info(f"Tearing down test class: {cls.__name__}")
    
    def setup_method(self) -> None:
        """Инициализация перед каждым тестом."""
        self._test_start_time = time.time()
        logger.debug(f"Starting test: {self.__class__.__name__}")
    
    def teardown_method(self) -> None:
        """Очистка после каждого теста."""
        duration = (time.time() - self._test_start_time) * 1000
        logger.debug(f"Test completed in {duration:.2f}ms")
    
    @staticmethod
    def generate_unique_id() -> str:
        """Генерация уникального ID для теста."""
        return str(uuid.uuid4())
    
    @staticmethod
    def generate_idempotency_key() -> str:
        """Генерация ключа идемпотентности."""
        return f"test-{uuid.uuid4().hex[:16]}"


class BaseApiClient(ABC):
    """
    Базовый класс для API клиентов.
    
    Обеспечивает:
    - HTTP методы с retry логикой
    - Автоматическое логирование
    - Метрики производительности
    """
    
    def __init__(self, base_url: str, timeout: int = 30):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self._session = None
        self._metrics: List[float] = []
    
    @abstractmethod
    def get(self, path: str, **kwargs) -> ApiResponse:
        """GET запрос."""
        pass
    
    @abstractmethod
    def post(self, path: str, data: Any = None, **kwargs) -> ApiResponse:
        """POST запрос."""
        pass
    
    @abstractmethod
    def put(self, path: str, data: Any = None, **kwargs) -> ApiResponse:
        """PUT запрос."""
        pass
    
    @abstractmethod
    def delete(self, path: str, **kwargs) -> ApiResponse:
        """DELETE запрос."""
        pass
    
    def get_metrics(self) -> PerformanceMetrics:
        """Получить метрики производительности."""
        if not self._metrics:
            return PerformanceMetrics(
                min_ms=0, max_ms=0, avg_ms=0, median_ms=0,
                p95_ms=0, p99_ms=0, total_requests=0,
                failed_requests=0, requests_per_second=0
            )
        
        sorted_metrics = sorted(self._metrics)
        total = len(sorted_metrics)
        
        return PerformanceMetrics(
            min_ms=sorted_metrics[0],
            max_ms=sorted_metrics[-1],
            avg_ms=sum(sorted_metrics) / total,
            median_ms=sorted_metrics[total // 2],
            p95_ms=sorted_metrics[int(total * 0.95)],
            p99_ms=sorted_metrics[int(total * 0.99)],
            total_requests=total,
            failed_requests=0,  # TODO: track failures
            requests_per_second=0,  # TODO: calculate RPS
        )
    
    def reset_metrics(self) -> None:
        """Сброс метрик."""
        self._metrics.clear()


# =============================================================================
# Decorators
# =============================================================================

def timer(func: Callable[..., T]) -> Callable[..., T]:
    """Декоратор для измерения времени выполнения."""
    @wraps(func)
    def wrapper(*args, **kwargs) -> T:
        start = time.perf_counter()
        try:
            result = func(*args, **kwargs)
            return result
        finally:
            duration = (time.perf_counter() - start) * 1000
            logger.debug(f"{func.__name__} completed in {duration:.2f}ms")
    return wrapper


def async_timer(func: Callable[..., T]) -> Callable[..., T]:
    """Декоратор для измерения времени async функций."""
    @wraps(func)
    async def wrapper(*args, **kwargs) -> T:
        start = time.perf_counter()
        try:
            result = await func(*args, **kwargs)
            return result
        finally:
            duration = (time.perf_counter() - start) * 1000
            logger.debug(f"{func.__name__} completed in {duration:.2f}ms")
    return wrapper


def retry_on_failure(
    max_attempts: int = 3,
    delay: float = 1.0,
    exceptions: tuple = (Exception,)
) -> Callable:
    """Декоратор retry с экспоненциальной задержкой."""
    def decorator(func: Callable[..., T]) -> Callable[..., T]:
        @wraps(func)
        @retry(
            stop=stop_after_attempt(max_attempts),
            wait=wait_exponential(multiplier=delay, min=1, max=10),
            retry=retry_if_exception_type(exceptions),
            reraise=True,
        )
        def wrapper(*args, **kwargs) -> T:
            return func(*args, **kwargs)
        return wrapper
    return decorator


def skip_if_not_configured(config_key: str) -> Callable:
    """Пропустить тест если конфигурация отсутствует."""
    import pytest
    
    def decorator(func: Callable) -> Callable:
        @wraps(func)
        def wrapper(*args, **kwargs):
            from config import get_config
            config = get_config()
            value = getattr(config.channels, config_key, None)
            if not value:
                pytest.skip(f"Configuration '{config_key}' not set")
            return func(*args, **kwargs)
        return wrapper
    return decorator


# =============================================================================
# Context Managers
# =============================================================================

@contextmanager
def measure_time() -> Generator[Dict[str, float], None, None]:
    """Контекстный менеджер для измерения времени."""
    result: Dict[str, float] = {}
    start = time.perf_counter()
    try:
        yield result
    finally:
        result["duration_ms"] = (time.perf_counter() - start) * 1000


@contextmanager
def assert_raises(
    expected_exception: Type[Exception],
    message_contains: Optional[str] = None
) -> Generator[None, None, None]:
    """Контекстный менеджер для проверки исключений."""
    try:
        yield
        raise AssertionError(f"Expected {expected_exception.__name__} was not raised")
    except expected_exception as e:
        if message_contains and message_contains not in str(e):
            raise AssertionError(
                f"Exception message '{str(e)}' does not contain '{message_contains}'"
            )


# =============================================================================
# Utility Functions
# =============================================================================

def wait_for_condition(
    condition: Callable[[], bool],
    timeout: float = 10.0,
    poll_interval: float = 0.5,
    message: str = "Condition not met"
) -> bool:
    """
    Ждать выполнения условия.
    
    Args:
        condition: Функция-условие, возвращающая bool
        timeout: Максимальное время ожидания (сек)
        poll_interval: Интервал проверки (сек)
        message: Сообщение об ошибке
        
    Returns:
        True если условие выполнено
        
    Raises:
        TimeoutError: Если условие не выполнено за timeout
    """
    start = time.time()
    while time.time() - start < timeout:
        if condition():
            return True
        time.sleep(poll_interval)
    raise TimeoutError(f"{message} (waited {timeout}s)")


async def async_wait_for_condition(
    condition: Callable[[], bool],
    timeout: float = 10.0,
    poll_interval: float = 0.5,
    message: str = "Condition not met"
) -> bool:
    """Асинхронная версия wait_for_condition."""
    start = time.time()
    while time.time() - start < timeout:
        if condition():
            return True
        await asyncio.sleep(poll_interval)
    raise TimeoutError(f"{message} (waited {timeout}s)")


def pretty_print_response(response: ApiResponse) -> None:
    """Красивый вывод API ответа."""
    table = Table(title="API Response")
    table.add_column("Property", style="cyan")
    table.add_column("Value", style="green")
    
    table.add_row("Status Code", str(response.status_code))
    table.add_row("Duration", f"{response.duration_ms:.2f}ms")
    table.add_row("Request ID", response.request_id or "N/A")
    table.add_row("Body", json.dumps(response.body, indent=2, ensure_ascii=False)[:500])
    
    console.print(table)


def generate_test_email() -> str:
    """Генерация тестового email."""
    return f"test-{uuid.uuid4().hex[:8]}@notification.test"


def generate_test_phone() -> str:
    """Генерация тестового телефона."""
    import random
    return f"+7900{random.randint(1000000, 9999999)}"


def mask_sensitive_data(data: Dict[str, Any], keys: List[str] = None) -> Dict[str, Any]:
    """Маскировка чувствительных данных для логов."""
    if keys is None:
        keys = ["password", "token", "api_key", "secret", "authorization"]
    
    masked = data.copy()
    for key in masked:
        if any(k in key.lower() for k in keys):
            masked[key] = "***MASKED***"
        elif isinstance(masked[key], dict):
            masked[key] = mask_sensitive_data(masked[key], keys)
    return masked
