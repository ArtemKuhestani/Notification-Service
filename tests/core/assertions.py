"""
Notification Service - Custom Assertions
=========================================

Расширенные assertions для тестирования API.
"""

from __future__ import annotations

import json
import re
from datetime import datetime, timedelta
from typing import Any, Dict, List, Optional, Pattern, Type, Union

from deepdiff import DeepDiff

from .base import ApiResponse, NotificationStatus


class AssertionError(Exception):
    """Кастомная ошибка assertion с детальным сообщением."""
    
    def __init__(self, message: str, expected: Any = None, actual: Any = None):
        self.expected = expected
        self.actual = actual
        details = f"\n  Expected: {expected}\n  Actual: {actual}" if expected is not None else ""
        super().__init__(f"{message}{details}")


class ApiAssertions:
    """Assertions для API ответов."""
    
    @staticmethod
    def assert_status_code(response: ApiResponse, expected: int, message: str = "") -> None:
        """Проверка HTTP статус кода."""
        if response.status_code != expected:
            raise AssertionError(
                f"{message}Status code mismatch" if message else "Status code mismatch",
                expected=expected,
                actual=response.status_code
            )
    
    @staticmethod
    def assert_success(response: ApiResponse, message: str = "") -> None:
        """Проверка успешного ответа (2xx)."""
        if not response.is_success:
            raise AssertionError(
                f"{message}Expected success response" if message else "Expected success response",
                expected="2xx",
                actual=response.status_code
            )
    
    @staticmethod
    def assert_client_error(response: ApiResponse, message: str = "") -> None:
        """Проверка ошибки клиента (4xx)."""
        if not response.is_client_error:
            raise AssertionError(
                f"{message}Expected client error" if message else "Expected client error",
                expected="4xx",
                actual=response.status_code
            )
    
    @staticmethod
    def assert_server_error(response: ApiResponse, message: str = "") -> None:
        """Проверка ошибки сервера (5xx)."""
        if not response.is_server_error:
            raise AssertionError(
                f"{message}Expected server error" if message else "Expected server error",
                expected="5xx",
                actual=response.status_code
            )
    
    @staticmethod
    def assert_response_time(
        response: ApiResponse,
        max_ms: float,
        message: str = ""
    ) -> None:
        """Проверка времени ответа."""
        if response.duration_ms > max_ms:
            raise AssertionError(
                f"{message}Response time exceeded" if message else "Response time exceeded",
                expected=f"<= {max_ms}ms",
                actual=f"{response.duration_ms:.2f}ms"
            )
    
    @staticmethod
    def assert_json_schema(
        response: ApiResponse,
        schema: Dict[str, Any],
        message: str = ""
    ) -> None:
        """Проверка JSON схемы ответа."""
        from jsonschema import validate, ValidationError
        
        try:
            validate(instance=response.json(), schema=schema)
        except ValidationError as e:
            raise AssertionError(
                f"{message}JSON schema validation failed: {e.message}"
            )
    
    @staticmethod
    def assert_json_contains(
        response: ApiResponse,
        expected: Dict[str, Any],
        message: str = ""
    ) -> None:
        """Проверка наличия полей в JSON ответе."""
        body = response.json()
        
        def check_contains(expected_data: Dict, actual_data: Dict, path: str = "") -> None:
            for key, value in expected_data.items():
                current_path = f"{path}.{key}" if path else key
                
                if key not in actual_data:
                    raise AssertionError(
                        f"Missing key '{current_path}' in response",
                        expected=key,
                        actual=list(actual_data.keys())
                    )
                
                if isinstance(value, dict):
                    if not isinstance(actual_data[key], dict):
                        raise AssertionError(
                            f"Expected dict at '{current_path}'",
                            expected="dict",
                            actual=type(actual_data[key]).__name__
                        )
                    check_contains(value, actual_data[key], current_path)
                elif value is not ...:  # Ellipsis means "any value"
                    if actual_data[key] != value:
                        raise AssertionError(
                            f"Value mismatch at '{current_path}'",
                            expected=value,
                            actual=actual_data[key]
                        )
        
        check_contains(expected, body)
    
    @staticmethod
    def assert_json_equals(
        response: ApiResponse,
        expected: Dict[str, Any],
        ignore_keys: List[str] = None,
        message: str = ""
    ) -> None:
        """Полное сравнение JSON ответа."""
        body = response.json()
        
        exclude_paths = [f"root['{k}']" for k in (ignore_keys or [])]
        diff = DeepDiff(expected, body, exclude_paths=exclude_paths)
        
        if diff:
            raise AssertionError(
                f"{message}JSON response mismatch: {diff}"
            )
    
    @staticmethod
    def assert_header_exists(
        response: ApiResponse,
        header: str,
        message: str = ""
    ) -> None:
        """Проверка наличия заголовка."""
        header_lower = header.lower()
        headers_lower = {k.lower(): v for k, v in response.headers.items()}
        
        if header_lower not in headers_lower:
            raise AssertionError(
                f"{message}Missing header '{header}'",
                expected=header,
                actual=list(response.headers.keys())
            )
    
    @staticmethod
    def assert_header_value(
        response: ApiResponse,
        header: str,
        expected: str,
        message: str = ""
    ) -> None:
        """Проверка значения заголовка."""
        header_lower = header.lower()
        headers_lower = {k.lower(): v for k, v in response.headers.items()}
        
        if header_lower not in headers_lower:
            raise AssertionError(f"Missing header '{header}'")
        
        if headers_lower[header_lower] != expected:
            raise AssertionError(
                f"{message}Header value mismatch",
                expected=expected,
                actual=headers_lower[header_lower]
            )


class NotificationAssertions:
    """Assertions для уведомлений."""
    
    @staticmethod
    def assert_notification_created(response: ApiResponse) -> str:
        """Проверка успешного создания уведомления."""
        ApiAssertions.assert_status_code(response, 202)
        body = response.json()
        
        if "notificationId" not in body:
            raise AssertionError("Response missing 'notificationId'")
        
        return body["notificationId"]
    
    @staticmethod
    def assert_notification_status(
        response: ApiResponse,
        expected_status: Union[NotificationStatus, str],
        message: str = ""
    ) -> None:
        """Проверка статуса уведомления."""
        ApiAssertions.assert_success(response)
        body = response.json()
        
        if "status" not in body:
            raise AssertionError("Response missing 'status' field")
        
        expected = expected_status.value if isinstance(expected_status, NotificationStatus) else expected_status
        
        if body["status"] != expected:
            raise AssertionError(
                f"{message}Notification status mismatch",
                expected=expected,
                actual=body["status"]
            )
    
    @staticmethod
    def assert_notification_sent(response: ApiResponse) -> None:
        """Проверка что уведомление отправлено."""
        NotificationAssertions.assert_notification_status(
            response, 
            NotificationStatus.SENT
        )
    
    @staticmethod
    def assert_notification_delivered(response: ApiResponse) -> None:
        """Проверка что уведомление доставлено."""
        NotificationAssertions.assert_notification_status(
            response,
            NotificationStatus.DELIVERED
        )
    
    @staticmethod
    def assert_notification_failed(response: ApiResponse) -> None:
        """Проверка что уведомление не доставлено."""
        NotificationAssertions.assert_notification_status(
            response,
            NotificationStatus.FAILED
        )


class DataAssertions:
    """Общие assertions для данных."""
    
    @staticmethod
    def assert_equals(
        actual: Any,
        expected: Any,
        message: str = ""
    ) -> None:
        """Проверка равенства."""
        if actual != expected:
            raise AssertionError(
                f"{message}Values are not equal",
                expected=expected,
                actual=actual
            )
    
    @staticmethod
    def assert_not_equals(
        actual: Any,
        not_expected: Any,
        message: str = ""
    ) -> None:
        """Проверка неравенства."""
        if actual == not_expected:
            raise AssertionError(
                f"{message}Values should not be equal",
                expected=f"not {not_expected}",
                actual=actual
            )
    
    @staticmethod
    def assert_true(value: bool, message: str = "") -> None:
        """Проверка истинности."""
        if not value:
            raise AssertionError(f"{message}Expected True", expected=True, actual=value)
    
    @staticmethod
    def assert_false(value: bool, message: str = "") -> None:
        """Проверка ложности."""
        if value:
            raise AssertionError(f"{message}Expected False", expected=False, actual=value)
    
    @staticmethod
    def assert_none(value: Any, message: str = "") -> None:
        """Проверка на None."""
        if value is not None:
            raise AssertionError(f"{message}Expected None", expected=None, actual=value)
    
    @staticmethod
    def assert_not_none(value: Any, message: str = "") -> None:
        """Проверка на не-None."""
        if value is None:
            raise AssertionError(f"{message}Expected not None", expected="not None", actual=None)
    
    @staticmethod
    def assert_in(item: Any, container: Any, message: str = "") -> None:
        """Проверка вхождения."""
        if item not in container:
            raise AssertionError(
                f"{message}Item not in container",
                expected=f"{item} in container",
                actual=container
            )
    
    @staticmethod
    def assert_not_in(item: Any, container: Any, message: str = "") -> None:
        """Проверка отсутствия."""
        if item in container:
            raise AssertionError(
                f"{message}Item should not be in container",
                expected=f"{item} not in container",
                actual=container
            )
    
    @staticmethod
    def assert_type(value: Any, expected_type: Type, message: str = "") -> None:
        """Проверка типа."""
        if not isinstance(value, expected_type):
            raise AssertionError(
                f"{message}Type mismatch",
                expected=expected_type.__name__,
                actual=type(value).__name__
            )
    
    @staticmethod
    def assert_length(
        container: Any,
        expected_length: int,
        message: str = ""
    ) -> None:
        """Проверка длины."""
        actual_length = len(container)
        if actual_length != expected_length:
            raise AssertionError(
                f"{message}Length mismatch",
                expected=expected_length,
                actual=actual_length
            )
    
    @staticmethod
    def assert_greater_than(
        actual: Any,
        threshold: Any,
        message: str = ""
    ) -> None:
        """Проверка больше."""
        if not actual > threshold:
            raise AssertionError(
                f"{message}Value not greater than threshold",
                expected=f"> {threshold}",
                actual=actual
            )
    
    @staticmethod
    def assert_less_than(
        actual: Any,
        threshold: Any,
        message: str = ""
    ) -> None:
        """Проверка меньше."""
        if not actual < threshold:
            raise AssertionError(
                f"{message}Value not less than threshold",
                expected=f"< {threshold}",
                actual=actual
            )
    
    @staticmethod
    def assert_between(
        actual: Any,
        min_value: Any,
        max_value: Any,
        message: str = ""
    ) -> None:
        """Проверка диапазона."""
        if not min_value <= actual <= max_value:
            raise AssertionError(
                f"{message}Value not in range",
                expected=f"[{min_value}, {max_value}]",
                actual=actual
            )
    
    @staticmethod
    def assert_matches(
        value: str,
        pattern: Union[str, Pattern],
        message: str = ""
    ) -> None:
        """Проверка regex паттерна."""
        if isinstance(pattern, str):
            pattern = re.compile(pattern)
        
        if not pattern.match(value):
            raise AssertionError(
                f"{message}Value does not match pattern",
                expected=pattern.pattern,
                actual=value
            )
    
    @staticmethod
    def assert_uuid(value: str, message: str = "") -> None:
        """Проверка формата UUID."""
        uuid_pattern = re.compile(
            r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
            re.IGNORECASE
        )
        DataAssertions.assert_matches(value, uuid_pattern, f"{message}Invalid UUID: ")
    
    @staticmethod
    def assert_email(value: str, message: str = "") -> None:
        """Проверка формата email."""
        email_pattern = re.compile(r'^[\w\.-]+@[\w\.-]+\.\w+$')
        DataAssertions.assert_matches(value, email_pattern, f"{message}Invalid email: ")
    
    @staticmethod
    def assert_iso_datetime(value: str, message: str = "") -> datetime:
        """Проверка формата ISO datetime."""
        try:
            return datetime.fromisoformat(value.replace("Z", "+00:00"))
        except ValueError:
            raise AssertionError(
                f"{message}Invalid ISO datetime format",
                expected="ISO 8601 format",
                actual=value
            )
    
    @staticmethod
    def assert_datetime_recent(
        value: Union[str, datetime],
        max_age_seconds: int = 60,
        message: str = ""
    ) -> None:
        """Проверка что datetime недавний."""
        if isinstance(value, str):
            dt = DataAssertions.assert_iso_datetime(value)
        else:
            dt = value
        
        now = datetime.now(dt.tzinfo) if dt.tzinfo else datetime.now()
        age = (now - dt).total_seconds()
        
        if age > max_age_seconds:
            raise AssertionError(
                f"{message}Datetime is too old",
                expected=f"< {max_age_seconds}s ago",
                actual=f"{age:.1f}s ago"
            )


# Алиасы для удобства
api = ApiAssertions
notification = NotificationAssertions
data = DataAssertions
