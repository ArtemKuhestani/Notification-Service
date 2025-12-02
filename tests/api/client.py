"""
Notification Service - API Clients
===================================

HTTP клиенты для тестирования API endpoints.
"""

from __future__ import annotations

import json
import logging
import time
from typing import Any, Dict, List, Optional

import httpx
from httpx import Response

from config import get_config
from core.base import ApiResponse

logger = logging.getLogger("notification_tests")


class HttpClient:
    """
    Синхронный HTTP клиент на базе httpx.
    
    Features:
    - Автоматические retry
    - Измерение времени
    - Логирование запросов/ответов
    """
    
    def __init__(
        self,
        base_url: str,
        timeout: int = 30,
        headers: Optional[Dict[str, str]] = None
    ):
        self.base_url = base_url.rstrip("/")
        self.timeout = timeout
        self.default_headers = headers or {}
        self._client: Optional[httpx.Client] = None
        self._response_times: List[float] = []
    
    def __enter__(self) -> "HttpClient":
        self._client = httpx.Client(
            base_url=self.base_url,
            timeout=self.timeout,
            headers=self.default_headers,
        )
        return self
    
    def __exit__(self, *args) -> None:
        if self._client:
            self._client.close()
    
    def _make_request(
        self,
        method: str,
        path: str,
        **kwargs
    ) -> ApiResponse:
        """Выполнение HTTP запроса."""
        if not self._client:
            self._client = httpx.Client(
                base_url=self.base_url,
                timeout=self.timeout,
                headers=self.default_headers,
            )
        
        # Merge headers
        headers = {**self.default_headers, **kwargs.pop("headers", {})}
        
        start = time.perf_counter()
        try:
            response: Response = self._client.request(
                method=method,
                url=path,
                headers=headers,
                **kwargs
            )
            duration = (time.perf_counter() - start) * 1000
            self._response_times.append(duration)
            
            # Parse body
            try:
                body = response.json()
            except json.JSONDecodeError:
                body = response.text
            
            logger.debug(
                f"{method} {path} -> {response.status_code} ({duration:.2f}ms)"
            )
            
            return ApiResponse(
                status_code=response.status_code,
                body=body,
                headers=dict(response.headers),
                duration_ms=duration,
                request_id=response.headers.get("X-Request-ID"),
            )
        except httpx.TimeoutException as e:
            duration = (time.perf_counter() - start) * 1000
            logger.error(f"{method} {path} -> TIMEOUT ({duration:.2f}ms)")
            raise TimeoutError(f"Request timed out: {e}")
        except httpx.RequestError as e:
            logger.error(f"{method} {path} -> ERROR: {e}")
            raise
    
    def get(self, path: str, params: Optional[Dict] = None, **kwargs) -> ApiResponse:
        """GET запрос."""
        return self._make_request("GET", path, params=params, **kwargs)
    
    def post(
        self,
        path: str,
        data: Any = None,
        json_data: Any = None,
        **kwargs
    ) -> ApiResponse:
        """POST запрос."""
        return self._make_request("POST", path, data=data, json=json_data, **kwargs)
    
    def put(
        self,
        path: str,
        data: Any = None,
        json_data: Any = None,
        **kwargs
    ) -> ApiResponse:
        """PUT запрос."""
        return self._make_request("PUT", path, data=data, json=json_data, **kwargs)
    
    def patch(
        self,
        path: str,
        data: Any = None,
        json_data: Any = None,
        **kwargs
    ) -> ApiResponse:
        """PATCH запрос."""
        return self._make_request("PATCH", path, data=data, json=json_data, **kwargs)
    
    def delete(self, path: str, **kwargs) -> ApiResponse:
        """DELETE запрос."""
        return self._make_request("DELETE", path, **kwargs)
    
    def set_auth_token(self, token: str) -> None:
        """Установить JWT токен."""
        self.default_headers["Authorization"] = f"Bearer {token}"
    
    def set_api_key(self, api_key: str) -> None:
        """Установить API ключ."""
        self.default_headers["X-API-Key"] = api_key
    
    def clear_auth(self) -> None:
        """Очистить авторизацию."""
        self.default_headers.pop("Authorization", None)
        self.default_headers.pop("X-API-Key", None)
    
    def get_average_response_time(self) -> float:
        """Средне время ответа."""
        if not self._response_times:
            return 0.0
        return sum(self._response_times) / len(self._response_times)
    
    def reset_metrics(self) -> None:
        """Сброс метрик."""
        self._response_times.clear()


class NotificationApiClient:
    """
    Клиент для Notification API.
    
    Покрывает все эндпоинты сервиса уведомлений.
    """
    
    def __init__(self, base_url: Optional[str] = None, timeout: int = 30):
        config = get_config()
        self.base_url = base_url or config.api.api_url
        self.http = HttpClient(self.base_url, timeout)
        self._auth_token: Optional[str] = None
        self._api_key: Optional[str] = None
    
    # =========================================================================
    # Health
    # =========================================================================
    
    def health_check(self) -> ApiResponse:
        """GET /health - Проверка здоровья сервиса."""
        return self.http.get("/health")
    
    # =========================================================================
    # Authentication
    # =========================================================================
    
    def login(self, username: str, password: str) -> ApiResponse:
        """POST /auth/login - Авторизация администратора."""
        response = self.http.post("/auth/login", json_data={
            "username": username,
            "password": password,
        })
        
        if response.is_success:
            self._auth_token = response.json().get("accessToken")
            self.http.set_auth_token(self._auth_token)
        
        return response
    
    def refresh_token(self, refresh_token: str) -> ApiResponse:
        """POST /auth/refresh - Обновление токена."""
        return self.http.post("/auth/refresh", json_data={
            "refreshToken": refresh_token,
        })
    
    def logout(self) -> None:
        """Выход из системы."""
        self._auth_token = None
        self.http.clear_auth()
    
    def get_current_user(self) -> ApiResponse:
        """GET /auth/me - Информация о текущем пользователе."""
        return self.http.get("/auth/me")
    
    # =========================================================================
    # Notifications - Public API
    # =========================================================================
    
    def send_notification(
        self,
        channel: str,
        recipient: str,
        message: str,
        subject: Optional[str] = None,
        priority: str = "NORMAL",
        template_code: Optional[str] = None,
        template_variables: Optional[Dict[str, str]] = None,
        idempotency_key: Optional[str] = None,
        callback_url: Optional[str] = None,
        metadata: Optional[Dict[str, Any]] = None,
    ) -> ApiResponse:
        """POST /send - Отправка уведомления."""
        payload = {
            "channel": channel,
            "recipient": recipient,
            "message": message,
            "priority": priority,
        }
        
        if subject:
            payload["subject"] = subject
        if template_code:
            payload["templateCode"] = template_code
        if template_variables:
            payload["templateVariables"] = template_variables
        if idempotency_key:
            payload["idempotencyKey"] = idempotency_key
        if callback_url:
            payload["callbackUrl"] = callback_url
        if metadata:
            payload["metadata"] = metadata
        
        return self.http.post("/send", json_data=payload)
    
    def get_notification_status(self, notification_id: str) -> ApiResponse:
        """GET /status/{id} - Получение статуса уведомления."""
        return self.http.get(f"/status/{notification_id}")
    
    # =========================================================================
    # Notifications - Admin API
    # =========================================================================
    
    def get_notifications(
        self,
        status: Optional[str] = None,
        channel: Optional[str] = None,
        client_id: Optional[int] = None,
        from_date: Optional[str] = None,
        to_date: Optional[str] = None,
        page: int = 0,
        size: int = 20,
    ) -> ApiResponse:
        """GET /admin/notifications - Список уведомлений."""
        params = {"page": page, "size": size}
        if status:
            params["status"] = status
        if channel:
            params["channel"] = channel
        if client_id:
            params["clientId"] = client_id
        if from_date:
            params["from"] = from_date
        if to_date:
            params["to"] = to_date
        
        return self.http.get("/admin/notifications", params=params)
    
    def get_notification_by_id(self, notification_id: str) -> ApiResponse:
        """GET /admin/notifications/{id} - Детали уведомления."""
        return self.http.get(f"/admin/notifications/{notification_id}")
    
    def retry_notification(self, notification_id: str) -> ApiResponse:
        """POST /admin/notifications/{id}/retry - Повторная отправка."""
        return self.http.post(f"/admin/notifications/{notification_id}/retry")
    
    def cancel_notification(self, notification_id: str) -> ApiResponse:
        """POST /admin/notifications/{id}/cancel - Отмена уведомления."""
        return self.http.post(f"/admin/notifications/{notification_id}/cancel")
    
    def test_send(
        self,
        channel: str,
        recipient: str,
        message: str,
        subject: Optional[str] = None,
    ) -> ApiResponse:
        """POST /admin/notifications/test - Тестовая отправка."""
        payload = {
            "channel": channel,
            "recipient": recipient,
            "message": message,
        }
        if subject:
            payload["subject"] = subject
        
        return self.http.post("/admin/notifications/test", json_data=payload)
    
    # =========================================================================
    # API Clients
    # =========================================================================
    
    def get_clients(self) -> ApiResponse:
        """GET /admin/clients - Список API клиентов."""
        return self.http.get("/admin/clients")
    
    def create_client(
        self,
        name: str,
        description: Optional[str] = None,
        rate_limit: int = 100,
        allowed_channels: Optional[List[str]] = None,
    ) -> ApiResponse:
        """POST /admin/clients - Создание API клиента."""
        payload = {
            "name": name,
            "rateLimit": rate_limit,
            "allowedChannels": allowed_channels or ["EMAIL"],
        }
        if description:
            payload["description"] = description
        
        return self.http.post("/admin/clients", json_data=payload)
    
    def get_client_by_id(self, client_id: int) -> ApiResponse:
        """GET /admin/clients/{id} - Детали клиента."""
        return self.http.get(f"/admin/clients/{client_id}")
    
    def update_client(
        self,
        client_id: int,
        name: Optional[str] = None,
        description: Optional[str] = None,
        active: Optional[bool] = None,
        rate_limit: Optional[int] = None,
        allowed_channels: Optional[List[str]] = None,
    ) -> ApiResponse:
        """PUT /admin/clients/{id} - Обновление клиента."""
        payload = {}
        if name:
            payload["name"] = name
        if description:
            payload["description"] = description
        if active is not None:
            payload["active"] = active
        if rate_limit:
            payload["rateLimit"] = rate_limit
        if allowed_channels:
            payload["allowedChannels"] = allowed_channels
        
        return self.http.put(f"/admin/clients/{client_id}", json_data=payload)
    
    def delete_client(self, client_id: int) -> ApiResponse:
        """DELETE /admin/clients/{id} - Удаление клиента."""
        return self.http.delete(f"/admin/clients/{client_id}")
    
    def regenerate_api_key(self, client_id: int) -> ApiResponse:
        """POST /admin/clients/{id}/regenerate-key - Перегенерация ключа."""
        return self.http.post(f"/admin/clients/{client_id}/regenerate-key")
    
    # =========================================================================
    # Channels
    # =========================================================================
    
    def get_channels(self) -> ApiResponse:
        """GET /admin/channels - Список каналов."""
        return self.http.get("/admin/channels")
    
    def get_channel(self, channel_type: str) -> ApiResponse:
        """GET /admin/channels/{type} - Конфигурация канала."""
        return self.http.get(f"/admin/channels/{channel_type}")
    
    def update_channel(
        self,
        channel_type: str,
        enabled: Optional[bool] = None,
        config: Optional[Dict[str, Any]] = None,
    ) -> ApiResponse:
        """PUT /admin/channels/{type} - Обновление канала."""
        payload = {}
        if enabled is not None:
            payload["enabled"] = enabled
        if config:
            payload["config"] = config
        
        return self.http.put(f"/admin/channels/{channel_type}", json_data=payload)
    
    def test_channel(self, channel_type: str) -> ApiResponse:
        """POST /admin/channels/{type}/test - Тестирование канала."""
        return self.http.post(f"/admin/channels/{channel_type}/test")
    
    # =========================================================================
    # Templates
    # =========================================================================
    
    def get_templates(
        self,
        channel: Optional[str] = None,
        active: Optional[bool] = None,
    ) -> ApiResponse:
        """GET /admin/templates - Список шаблонов."""
        params = {}
        if channel:
            params["channel"] = channel
        if active is not None:
            params["active"] = active
        
        return self.http.get("/admin/templates", params=params)
    
    def create_template(
        self,
        code: str,
        name: str,
        channel: str,
        subject: Optional[str] = None,
        body: str = "",
        variables: Optional[List[str]] = None,
    ) -> ApiResponse:
        """POST /admin/templates - Создание шаблона."""
        payload = {
            "code": code,
            "name": name,
            "channel": channel,
            "body": body,
            "variables": variables or [],
        }
        if subject:
            payload["subject"] = subject
        
        return self.http.post("/admin/templates", json_data=payload)
    
    def get_template_by_id(self, template_id: int) -> ApiResponse:
        """GET /admin/templates/{id} - Детали шаблона."""
        return self.http.get(f"/admin/templates/{template_id}")
    
    def update_template(
        self,
        template_id: int,
        name: Optional[str] = None,
        subject: Optional[str] = None,
        body: Optional[str] = None,
        active: Optional[bool] = None,
    ) -> ApiResponse:
        """PUT /admin/templates/{id} - Обновление шаблона."""
        payload = {}
        if name:
            payload["name"] = name
        if subject:
            payload["subject"] = subject
        if body:
            payload["body"] = body
        if active is not None:
            payload["active"] = active
        
        return self.http.put(f"/admin/templates/{template_id}", json_data=payload)
    
    def delete_template(self, template_id: int) -> ApiResponse:
        """DELETE /admin/templates/{id} - Удаление шаблона."""
        return self.http.delete(f"/admin/templates/{template_id}")
    
    # =========================================================================
    # Dashboard
    # =========================================================================
    
    def get_dashboard_stats(self) -> ApiResponse:
        """GET /admin/dashboard/stats - Статистика дашборда."""
        return self.http.get("/admin/dashboard/stats")
    
    def get_dashboard_chart(
        self,
        period: str = "7d",
        channel: Optional[str] = None,
    ) -> ApiResponse:
        """GET /admin/dashboard/chart - Данные для графика."""
        params = {"period": period}
        if channel:
            params["channel"] = channel
        
        return self.http.get("/admin/dashboard/chart", params=params)
    
    # =========================================================================
    # Audit Logs
    # =========================================================================
    
    def get_audit_logs(
        self,
        action: Optional[str] = None,
        entity_type: Optional[str] = None,
        user_id: Optional[int] = None,
        from_date: Optional[str] = None,
        to_date: Optional[str] = None,
        page: int = 0,
        size: int = 20,
    ) -> ApiResponse:
        """GET /admin/audit - Список аудит логов."""
        params = {"page": page, "size": size}
        if action:
            params["action"] = action
        if entity_type:
            params["entityType"] = entity_type
        if user_id:
            params["userId"] = user_id
        if from_date:
            params["from"] = from_date
        if to_date:
            params["to"] = to_date
        
        return self.http.get("/admin/audit", params=params)
    
    def get_audit_log_by_id(self, log_id: int) -> ApiResponse:
        """GET /admin/audit/{id} - Детали аудит лога."""
        return self.http.get(f"/admin/audit/{log_id}")
    
    # =========================================================================
    # Utilities
    # =========================================================================
    
    def authenticate_admin(self) -> str:
        """Быстрая авторизация администратора из конфига."""
        config = get_config()
        response = self.login(
            config.auth.admin_username,
            config.auth.admin_password
        )
        
        if not response.is_success:
            raise RuntimeError(f"Admin authentication failed: {response.body}")
        
        return self._auth_token
    
    def use_api_key(self, api_key: str) -> None:
        """Переключение на API key авторизацию."""
        self._api_key = api_key
        self.http.clear_auth()
        self.http.set_api_key(api_key)
    
    def close(self) -> None:
        """Закрытие клиента."""
        if self.http._client:
            self.http._client.close()


# Factory function
def create_api_client() -> NotificationApiClient:
    """Создать API клиент с конфигурацией из окружения."""
    config = get_config()
    return NotificationApiClient(
        base_url=config.api.api_url,
        timeout=config.api.timeout
    )
