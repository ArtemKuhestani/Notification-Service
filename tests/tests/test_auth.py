"""
Notification Service - Authentication Tests
=============================================

Полное тестирование аутентификации и авторизации.

Coverage:
- JWT токены
- API ключи
- Refresh токенов
- Защита endpoints
- Rate limiting
"""

import time
from datetime import datetime, timedelta

import pytest

from api import NotificationApiClient
from core import ApiAssertions as api_assert, DataAssertions as data_assert
from factories import ApiClientFactory, IdGenerator


@pytest.mark.auth
@pytest.mark.api
class TestJwtAuthentication:
    """Тесты JWT аутентификации."""
    
    def test_login_returns_tokens(self, api: NotificationApiClient, test_config):
        """
        [AUTH-001] Login должен возвращать access и refresh токены.
        """
        response = api.login(
            test_config.auth.admin_username,
            test_config.auth.admin_password
        )
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert "accessToken" in body
        assert "refreshToken" in body
        assert "expiresIn" in body or "expiresAt" in body
        
        # Токены должны быть непустыми
        data_assert.assert_not_none(body["accessToken"])
        data_assert.assert_not_none(body["refreshToken"])
    
    def test_access_token_format(self, api: NotificationApiClient, test_config):
        """
        [AUTH-002] Access token должен быть валидным JWT.
        """
        response = api.login(
            test_config.auth.admin_username,
            test_config.auth.admin_password
        )
        
        token = response.json()["accessToken"]
        
        # JWT имеет формат: header.payload.signature
        parts = token.split(".")
        assert len(parts) == 3, "JWT должен иметь 3 части"
        
        # Каждая часть должна быть непустой
        for i, part in enumerate(parts):
            assert len(part) > 0, f"JWT part {i} is empty"
    
    def test_protected_endpoint_with_valid_token(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [AUTH-003] Защищённый endpoint должен работать с валидным токеном.
        """
        response = auth_api.get_notifications()
        
        api_assert.assert_success(response)
    
    def test_protected_endpoint_without_token(self, api: NotificationApiClient):
        """
        [AUTH-004] Защищённый endpoint без токена должен возвращать 401.
        """
        response = api.get_notifications()
        
        assert response.status_code in [401, 403]
    
    def test_protected_endpoint_with_invalid_token(
        self,
        api: NotificationApiClient
    ):
        """
        [AUTH-005] Endpoint с невалидным токеном должен возвращать 401.
        """
        api.http.set_auth_token("invalid.jwt.token")
        
        response = api.get_notifications()
        
        assert response.status_code in [401, 403]
    
    def test_protected_endpoint_with_expired_token(
        self,
        api: NotificationApiClient
    ):
        """
        [AUTH-006] Endpoint с просроченным токеном должен возвращать 401.
        """
        # Это заведомо просроченный токен (можно сгенерировать с exp в прошлом)
        expired_token = (
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9."
            "eyJzdWIiOiIxIiwiZXhwIjoxNjAwMDAwMDAwfQ."
            "invalid_signature"
        )
        api.http.set_auth_token(expired_token)
        
        response = api.get_notifications()
        
        assert response.status_code in [401, 403]
    
    def test_login_with_wrong_password(self, api: NotificationApiClient, test_config):
        """
        [AUTH-007] Login с неверным паролем должен возвращать 401.
        """
        response = api.login(
            test_config.auth.admin_username,
            "wrong_password_123"
        )
        
        api_assert.assert_status_code(response, 401)
    
    def test_login_with_nonexistent_user(self, api: NotificationApiClient):
        """
        [AUTH-008] Login несуществующего пользователя должен возвращать 401.
        """
        response = api.login(
            "nonexistent_user_12345",
            "any_password"
        )
        
        api_assert.assert_status_code(response, 401)
    
    def test_login_with_empty_credentials(self, api: NotificationApiClient):
        """
        [AUTH-009] Login с пустыми credentials должен возвращать 400 или 401.
        """
        response = api.login("", "")
        
        assert response.status_code in [400, 401, 422]


@pytest.mark.auth
@pytest.mark.api
class TestTokenRefresh:
    """Тесты обновления токенов."""
    
    def test_refresh_token_success(self, api: NotificationApiClient, test_config):
        """
        [AUTH-010] Refresh token должен возвращать новый access token.
        """
        # Login
        login_response = api.login(
            test_config.auth.admin_username,
            test_config.auth.admin_password
        )
        
        refresh_token = login_response.json()["refreshToken"]
        
        # Refresh
        refresh_response = api.refresh_token(refresh_token)
        
        api_assert.assert_success(refresh_response)
        body = refresh_response.json()
        
        assert "accessToken" in body
        data_assert.assert_not_none(body["accessToken"])
    
    def test_refresh_with_invalid_token(self, api: NotificationApiClient):
        """
        [AUTH-011] Refresh с невалидным токеном должен возвращать 401.
        """
        response = api.refresh_token("invalid_refresh_token")
        
        assert response.status_code in [401, 403]
    
    def test_new_access_token_works(self, api: NotificationApiClient, test_config):
        """
        [AUTH-012] Новый access token должен работать для запросов.
        """
        # Login
        login_response = api.login(
            test_config.auth.admin_username,
            test_config.auth.admin_password
        )
        refresh_token = login_response.json()["refreshToken"]
        
        # Refresh
        refresh_response = api.refresh_token(refresh_token)
        new_token = refresh_response.json()["accessToken"]
        
        # Use new token
        api.http.set_auth_token(new_token)
        
        response = api.get_notifications()
        
        api_assert.assert_success(response)


@pytest.mark.auth
@pytest.mark.api
class TestApiKeyAuthentication:
    """Тесты аутентификации по API ключу."""
    
    def test_api_key_authentication_works(
        self,
        auth_api: NotificationApiClient,
        api: NotificationApiClient
    ):
        """
        [AUTH-013] API ключ должен работать для авторизации.
        """
        # Создаём клиента
        client_data = ApiClientFactory.create()
        create_response = auth_api.create_client(**client_data)
        
        if not create_response.is_success:
            pytest.skip("Could not create API client")
        
        body = create_response.json()
        api_key = body.get("apiKey")
        client_id = body.get("id")
        
        try:
            # Используем API ключ
            api.use_api_key(api_key)
            
            response = api.send_notification(
                channel="EMAIL",
                recipient="test@example.com",
                message="Test message"
            )
            
            # Запрос должен быть принят (не 401/403)
            assert response.status_code not in [401, 403]
        
        finally:
            auth_api.delete_client(client_id)
    
    def test_invalid_api_key_rejected(self, api: NotificationApiClient):
        """
        [AUTH-014] Невалидный API ключ должен отклоняться.
        """
        api.use_api_key("invalid_api_key_12345")
        
        response = api.send_notification(
            channel="EMAIL",
            recipient="test@example.com",
            message="Test"
        )
        
        assert response.status_code in [401, 403]
    
    def test_disabled_client_api_key_rejected(
        self,
        auth_api: NotificationApiClient,
        api: NotificationApiClient
    ):
        """
        [AUTH-015] API ключ деактивированного клиента должен отклоняться.
        """
        # Создаём клиента
        client_data = ApiClientFactory.create()
        create_response = auth_api.create_client(**client_data)
        
        if not create_response.is_success:
            pytest.skip("Could not create API client")
        
        body = create_response.json()
        api_key = body.get("apiKey")
        client_id = body.get("id")
        
        try:
            # Деактивируем клиента
            auth_api.update_client(client_id, active=False)
            
            # Пробуем использовать ключ
            api.use_api_key(api_key)
            
            response = api.send_notification(
                channel="EMAIL",
                recipient="test@example.com",
                message="Test"
            )
            
            assert response.status_code in [401, 403]
        
        finally:
            auth_api.delete_client(client_id)
    
    def test_regenerated_api_key_works(
        self,
        auth_api: NotificationApiClient,
        api: NotificationApiClient
    ):
        """
        [AUTH-016] После перегенерации ключа новый ключ должен работать.
        """
        # Создаём клиента
        client_data = ApiClientFactory.create()
        create_response = auth_api.create_client(**client_data)
        
        if not create_response.is_success:
            pytest.skip("Could not create API client")
        
        body = create_response.json()
        old_api_key = body.get("apiKey")
        client_id = body.get("id")
        
        try:
            # Перегенерируем ключ
            regen_response = auth_api.regenerate_api_key(client_id)
            
            if not regen_response.is_success:
                pytest.skip("Could not regenerate API key")
            
            new_api_key = regen_response.json().get("apiKey")
            
            # Новый ключ отличается
            assert new_api_key != old_api_key
            
            # Новый ключ работает
            api.use_api_key(new_api_key)
            
            response = api.send_notification(
                channel="EMAIL",
                recipient="test@example.com",
                message="Test"
            )
            
            assert response.status_code not in [401, 403]
        
        finally:
            auth_api.delete_client(client_id)
    
    def test_old_api_key_rejected_after_regeneration(
        self,
        auth_api: NotificationApiClient,
        api: NotificationApiClient
    ):
        """
        [AUTH-017] Старый ключ должен не работать после перегенерации.
        """
        # Создаём клиента
        client_data = ApiClientFactory.create()
        create_response = auth_api.create_client(**client_data)
        
        if not create_response.is_success:
            pytest.skip("Could not create API client")
        
        body = create_response.json()
        old_api_key = body.get("apiKey")
        client_id = body.get("id")
        
        try:
            # Перегенерируем ключ
            auth_api.regenerate_api_key(client_id)
            
            # Старый ключ не работает
            api.use_api_key(old_api_key)
            
            response = api.send_notification(
                channel="EMAIL",
                recipient="test@example.com",
                message="Test"
            )
            
            assert response.status_code in [401, 403]
        
        finally:
            auth_api.delete_client(client_id)


@pytest.mark.auth
@pytest.mark.api
class TestChannelRestrictions:
    """Тесты ограничений каналов для API клиентов."""
    
    def test_client_can_use_allowed_channel(
        self,
        auth_api: NotificationApiClient,
        api: NotificationApiClient
    ):
        """
        [AUTH-018] Клиент может отправлять через разрешённые каналы.
        """
        # Создаём клиента только для EMAIL
        client_data = ApiClientFactory.create_email_only()
        create_response = auth_api.create_client(**client_data)
        
        if not create_response.is_success:
            pytest.skip("Could not create API client")
        
        body = create_response.json()
        api_key = body.get("apiKey")
        client_id = body.get("id")
        
        try:
            api.use_api_key(api_key)
            
            response = api.send_notification(
                channel="EMAIL",
                recipient="test@example.com",
                message="Test"
            )
            
            # Должен быть принят (не 403)
            assert response.status_code != 403
        
        finally:
            auth_api.delete_client(client_id)
    
    def test_client_cannot_use_forbidden_channel(
        self,
        auth_api: NotificationApiClient,
        api: NotificationApiClient
    ):
        """
        [AUTH-019] Клиент не может отправлять через запрещённые каналы.
        """
        # Создаём клиента только для EMAIL
        client_data = ApiClientFactory.create_email_only()
        create_response = auth_api.create_client(**client_data)
        
        if not create_response.is_success:
            pytest.skip("Could not create API client")
        
        body = create_response.json()
        api_key = body.get("apiKey")
        client_id = body.get("id")
        
        try:
            api.use_api_key(api_key)
            
            # Пробуем отправить через TELEGRAM (не разрешён)
            response = api.send_notification(
                channel="TELEGRAM",
                recipient="123456789",
                message="Test"
            )
            
            # Должен быть отклонён
            assert response.status_code in [400, 403]
        
        finally:
            auth_api.delete_client(client_id)


@pytest.mark.auth
@pytest.mark.api
@pytest.mark.slow
class TestRateLimiting:
    """Тесты rate limiting."""
    
    def test_rate_limit_enforced(
        self,
        auth_api: NotificationApiClient,
        api: NotificationApiClient
    ):
        """
        [AUTH-020] Rate limit должен ограничивать количество запросов.
        """
        # Создаём клиента с низким лимитом
        client_data = ApiClientFactory.create(rate_limit=5)
        create_response = auth_api.create_client(**client_data)
        
        if not create_response.is_success:
            pytest.skip("Could not create API client")
        
        body = create_response.json()
        api_key = body.get("apiKey")
        client_id = body.get("id")
        
        try:
            api.use_api_key(api_key)
            
            # Отправляем больше запросов чем лимит
            success_count = 0
            throttled_count = 0
            
            for _ in range(10):
                response = api.send_notification(
                    channel="EMAIL",
                    recipient="test@example.com",
                    message="Test"
                )
                
                if response.status_code == 429:
                    throttled_count += 1
                elif response.is_success or response.status_code == 202:
                    success_count += 1
            
            # Должны быть отклонённые запросы
            # (это зависит от реализации rate limiting)
            assert success_count <= 5 or throttled_count > 0
        
        finally:
            auth_api.delete_client(client_id)
    
    def test_rate_limit_resets(
        self,
        auth_api: NotificationApiClient,
        api: NotificationApiClient
    ):
        """
        [AUTH-021] Rate limit должен сбрасываться со временем.
        """
        pytest.skip("Long test - enable manually")
        
        # Создаём клиента с низким лимитом
        client_data = ApiClientFactory.create(rate_limit=2)
        create_response = auth_api.create_client(**client_data)
        
        if not create_response.is_success:
            pytest.skip("Could not create API client")
        
        body = create_response.json()
        api_key = body.get("apiKey")
        client_id = body.get("id")
        
        try:
            api.use_api_key(api_key)
            
            # Исчерпываем лимит
            for _ in range(5):
                api.send_notification(
                    channel="EMAIL",
                    recipient="test@example.com",
                    message="Test"
                )
            
            # Ждём сброса (обычно 1 минута)
            time.sleep(61)
            
            # Новый запрос должен пройти
            response = api.send_notification(
                channel="EMAIL",
                recipient="test@example.com",
                message="Test after reset"
            )
            
            assert response.status_code != 429
        
        finally:
            auth_api.delete_client(client_id)
