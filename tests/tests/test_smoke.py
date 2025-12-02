"""
Notification Service - Smoke Tests
===================================

Быстрые проверки работоспособности сервиса.
Запускаются первыми для раннего обнаружения критических проблем.

Usage:
    pytest -m smoke
"""

import pytest

from api import NotificationApiClient
from core import ApiAssertions as api_assert


@pytest.mark.smoke
@pytest.mark.order(1)
class TestServiceHealth:
    """Проверка здоровья сервиса."""
    
    def test_health_endpoint_returns_200(self, api: NotificationApiClient):
        """
        [SMOKE-001] Health endpoint должен возвращать 200 OK.
        
        Критичность: BLOCKER
        """
        response = api.health_check()
        
        api_assert.assert_status_code(response, 200)
        api_assert.assert_json_contains(response, {"status": "UP"})
    
    def test_health_endpoint_response_time(self, api: NotificationApiClient):
        """
        [SMOKE-002] Health endpoint должен отвечать быстро (< 500ms).
        """
        response = api.health_check()
        
        api_assert.assert_success(response)
        api_assert.assert_response_time(response, max_ms=500)
    
    def test_database_connection(self, api: NotificationApiClient):
        """
        [SMOKE-003] База данных должна быть доступна.
        """
        response = api.health_check()
        body = response.json()
        
        assert "components" in body
        assert body["components"].get("database") == "UP"


@pytest.mark.smoke
@pytest.mark.order(2)
class TestAuthentication:
    """Проверка аутентификации."""
    
    def test_login_with_valid_credentials(self, api: NotificationApiClient, test_config):
        """
        [SMOKE-004] Вход с валидными credentials должен работать.
        """
        response = api.login(
            test_config.auth.admin_username,
            test_config.auth.admin_password
        )
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert "accessToken" in body
        assert "refreshToken" in body
        assert len(body["accessToken"]) > 20
    
    def test_login_with_invalid_credentials(self, api: NotificationApiClient):
        """
        [SMOKE-005] Вход с неверными credentials должен возвращать 401.
        """
        response = api.login("invalid_user", "wrong_password")
        
        api_assert.assert_status_code(response, 401)
    
    def test_protected_endpoint_without_auth(self, api: NotificationApiClient):
        """
        [SMOKE-006] Защищённый endpoint без авторизации должен возвращать 401.
        """
        response = api.get_notifications()
        
        api_assert.assert_client_error(response)
        assert response.status_code in [401, 403]


@pytest.mark.smoke
@pytest.mark.order(3)
class TestCoreEndpoints:
    """Проверка основных endpoints."""
    
    def test_get_notifications_list(self, auth_api: NotificationApiClient):
        """
        [SMOKE-007] Получение списка уведомлений должно работать.
        """
        response = auth_api.get_notifications()
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert "content" in body or isinstance(body, list)
    
    def test_get_clients_list(self, auth_api: NotificationApiClient):
        """
        [SMOKE-008] Получение списка API клиентов должно работать.
        """
        response = auth_api.get_clients()
        
        api_assert.assert_success(response)
    
    def test_get_channels_list(self, auth_api: NotificationApiClient):
        """
        [SMOKE-009] Получение списка каналов должно работать.
        """
        response = auth_api.get_channels()
        
        api_assert.assert_success(response)
    
    def test_get_templates_list(self, auth_api: NotificationApiClient):
        """
        [SMOKE-010] Получение списка шаблонов должно работать.
        """
        response = auth_api.get_templates()
        
        api_assert.assert_success(response)
    
    def test_get_dashboard_stats(self, auth_api: NotificationApiClient):
        """
        [SMOKE-011] Получение статистики дашборда должно работать.
        """
        response = auth_api.get_dashboard_stats()
        
        api_assert.assert_success(response)


@pytest.mark.smoke
@pytest.mark.order(4)
class TestNotificationSending:
    """Проверка отправки уведомлений."""
    
    def test_send_email_notification(
        self,
        auth_api: NotificationApiClient,
        email_notification: dict
    ):
        """
        [SMOKE-012] Отправка email уведомления должна работать.
        """
        response = auth_api.send_notification(**email_notification)
        
        # Ожидаем 202 Accepted
        assert response.status_code in [200, 201, 202]
        body = response.json()
        
        assert "notificationId" in body or "id" in body
    
    def test_get_notification_status(
        self,
        auth_api: NotificationApiClient,
        email_notification: dict
    ):
        """
        [SMOKE-013] Получение статуса уведомления должно работать.
        """
        # Создаём уведомление
        send_response = auth_api.send_notification(**email_notification)
        notification_id = send_response.json().get("notificationId") or send_response.json().get("id")
        
        # Проверяем статус
        status_response = auth_api.get_notification_status(notification_id)
        
        api_assert.assert_success(status_response)
        body = status_response.json()
        
        assert "status" in body


@pytest.mark.smoke
class TestApiKeyAuthentication:
    """Проверка авторизации по API ключу."""
    
    def test_create_and_use_api_key(
        self,
        auth_api: NotificationApiClient,
        api_client_data: dict,
        api: NotificationApiClient
    ):
        """
        [SMOKE-014] API ключ должен работать для отправки уведомлений.
        """
        # Создаём клиента
        create_response = auth_api.create_client(**api_client_data)
        
        if not create_response.is_success:
            pytest.skip("Could not create API client")
        
        client_data = create_response.json()
        api_key = client_data.get("apiKey")
        client_id = client_data.get("id")
        
        try:
            # Используем API ключ
            api.use_api_key(api_key)
            
            # Отправляем уведомление
            notification = {
                "channel": "EMAIL",
                "recipient": "test@example.com",
                "subject": "API Key Test",
                "message": "Testing API key authentication",
            }
            
            response = api.send_notification(**notification)
            
            # Проверяем что запрос принят (или ошибка канала, но не 401/403)
            assert response.status_code not in [401, 403], \
                f"API key authentication failed: {response.status_code}"
        
        finally:
            # Cleanup
            auth_api.delete_client(client_id)
