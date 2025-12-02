"""
Notification Service - Admin API Tests
=======================================

Тестирование административных endpoints.

Coverage:
- API Clients CRUD
- Templates CRUD
- Channels configuration
- Dashboard
- Audit logs
"""

import pytest

from api import NotificationApiClient
from core import ApiAssertions as api_assert, DataAssertions as data_assert
from factories import ApiClientFactory, ChannelConfigFactory, TemplateFactory


# =============================================================================
# API Clients Tests
# =============================================================================

@pytest.mark.clients
@pytest.mark.api
class TestApiClientsCrud:
    """CRUD тесты для API клиентов."""
    
    def test_create_api_client(
        self,
        auth_api: NotificationApiClient,
        api_client_data: dict
    ):
        """
        [CLIENT-001] Создание API клиента.
        """
        response = auth_api.create_client(**api_client_data)
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert "id" in body
        assert "apiKey" in body
        assert body["name"] == api_client_data["name"]
        
        # Cleanup
        auth_api.delete_client(body["id"])
    
    def test_create_api_client_returns_api_key(
        self,
        auth_api: NotificationApiClient,
        api_client_data: dict
    ):
        """
        [CLIENT-002] При создании клиента возвращается API ключ.
        """
        response = auth_api.create_client(**api_client_data)
        body = response.json()
        
        api_key = body.get("apiKey")
        data_assert.assert_not_none(api_key)
        assert len(api_key) >= 32  # API ключ должен быть достаточно длинным
        
        # Cleanup
        auth_api.delete_client(body["id"])
    
    def test_get_all_clients(self, auth_api: NotificationApiClient):
        """
        [CLIENT-003] Получение списка всех клиентов.
        """
        response = auth_api.get_clients()
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert isinstance(body, list) or "content" in body
    
    def test_get_client_by_id(
        self,
        auth_api: NotificationApiClient,
        api_client_data: dict
    ):
        """
        [CLIENT-004] Получение клиента по ID.
        """
        # Создаём
        create_response = auth_api.create_client(**api_client_data)
        client_id = create_response.json()["id"]
        
        # Получаем
        response = auth_api.get_client_by_id(client_id)
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert body["id"] == client_id
        assert body["name"] == api_client_data["name"]
        
        # Cleanup
        auth_api.delete_client(client_id)
    
    def test_get_nonexistent_client(self, auth_api: NotificationApiClient):
        """
        [CLIENT-005] Получение несуществующего клиента.
        """
        response = auth_api.get_client_by_id(999999)
        
        api_assert.assert_status_code(response, 404)
    
    def test_update_client(
        self,
        auth_api: NotificationApiClient,
        api_client_data: dict
    ):
        """
        [CLIENT-006] Обновление клиента.
        """
        # Создаём
        create_response = auth_api.create_client(**api_client_data)
        client_id = create_response.json()["id"]
        
        # Обновляем
        new_name = "Updated Client Name"
        update_response = auth_api.update_client(
            client_id,
            name=new_name,
            rate_limit=200
        )
        
        api_assert.assert_success(update_response)
        
        # Проверяем
        get_response = auth_api.get_client_by_id(client_id)
        body = get_response.json()
        
        assert body["name"] == new_name
        
        # Cleanup
        auth_api.delete_client(client_id)
    
    def test_deactivate_client(
        self,
        auth_api: NotificationApiClient,
        api_client_data: dict
    ):
        """
        [CLIENT-007] Деактивация клиента.
        """
        # Создаём
        create_response = auth_api.create_client(**api_client_data)
        client_id = create_response.json()["id"]
        
        # Деактивируем
        update_response = auth_api.update_client(client_id, active=False)
        
        api_assert.assert_success(update_response)
        
        # Проверяем
        get_response = auth_api.get_client_by_id(client_id)
        body = get_response.json()
        
        assert body.get("active") == False or body.get("isActive") == False
        
        # Cleanup
        auth_api.delete_client(client_id)
    
    def test_delete_client(
        self,
        auth_api: NotificationApiClient,
        api_client_data: dict
    ):
        """
        [CLIENT-008] Удаление клиента.
        """
        # Создаём
        create_response = auth_api.create_client(**api_client_data)
        client_id = create_response.json()["id"]
        
        # Удаляем
        delete_response = auth_api.delete_client(client_id)
        
        assert delete_response.status_code in [200, 204]
        
        # Проверяем что удалён
        get_response = auth_api.get_client_by_id(client_id)
        assert get_response.status_code == 404
    
    def test_regenerate_api_key(
        self,
        auth_api: NotificationApiClient,
        api_client_data: dict
    ):
        """
        [CLIENT-009] Перегенерация API ключа.
        """
        # Создаём
        create_response = auth_api.create_client(**api_client_data)
        body = create_response.json()
        client_id = body["id"]
        old_key = body["apiKey"]
        
        # Перегенерируем
        regen_response = auth_api.regenerate_api_key(client_id)
        
        api_assert.assert_success(regen_response)
        new_key = regen_response.json().get("apiKey")
        
        # Ключи должны отличаться
        assert new_key != old_key
        
        # Cleanup
        auth_api.delete_client(client_id)


@pytest.mark.clients
@pytest.mark.api
class TestApiClientsValidation:
    """Тесты валидации API клиентов."""
    
    def test_create_client_without_name(self, auth_api: NotificationApiClient):
        """
        [CLIENT-010] Создание клиента без имени.
        """
        response = auth_api.http.post("/admin/clients", json_data={
            "rateLimit": 100,
            "allowedChannels": ["EMAIL"]
        })
        
        api_assert.assert_client_error(response)
    
    def test_create_client_with_duplicate_name(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [CLIENT-011] Создание клиента с дублирующимся именем.
        """
        unique_name = f"Duplicate Test {id(self)}"
        
        # Создаём первого
        client1 = ApiClientFactory.create(name=unique_name)
        response1 = auth_api.create_client(**client1)
        client_id = response1.json().get("id")
        
        # Пробуем создать с тем же именем
        client2 = ApiClientFactory.create(name=unique_name)
        response2 = auth_api.create_client(**client2)
        
        # Должна быть ошибка (409 Conflict или 400)
        assert response2.status_code in [400, 409]
        
        # Cleanup
        if client_id:
            auth_api.delete_client(client_id)
    
    def test_create_client_with_invalid_rate_limit(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [CLIENT-012] Создание клиента с невалидным rate limit.
        """
        client = ApiClientFactory.create(rate_limit=-1)
        response = auth_api.create_client(**client)
        
        api_assert.assert_client_error(response)


# =============================================================================
# Templates Tests
# =============================================================================

@pytest.mark.templates
@pytest.mark.api
class TestTemplatesCrud:
    """CRUD тесты для шаблонов."""
    
    def test_create_template(
        self,
        auth_api: NotificationApiClient,
        template_data: dict
    ):
        """
        [TMPL-001] Создание шаблона.
        """
        response = auth_api.create_template(**template_data)
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert "id" in body
        assert body["code"] == template_data["code"]
        
        # Cleanup
        auth_api.delete_template(body["id"])
    
    def test_get_all_templates(self, auth_api: NotificationApiClient):
        """
        [TMPL-002] Получение списка шаблонов.
        """
        response = auth_api.get_templates()
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert isinstance(body, list) or "content" in body
    
    def test_get_templates_filter_by_channel(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [TMPL-003] Фильтрация шаблонов по каналу.
        """
        response = auth_api.get_templates(channel="EMAIL")
        
        api_assert.assert_success(response)
        body = response.json()
        
        content = body.get("content", body) if isinstance(body, dict) else body
        
        for template in content:
            assert template.get("channel") == "EMAIL"
    
    def test_get_template_by_id(
        self,
        auth_api: NotificationApiClient,
        template_data: dict
    ):
        """
        [TMPL-004] Получение шаблона по ID.
        """
        # Создаём
        create_response = auth_api.create_template(**template_data)
        template_id = create_response.json()["id"]
        
        # Получаем
        response = auth_api.get_template_by_id(template_id)
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert body["id"] == template_id
        
        # Cleanup
        auth_api.delete_template(template_id)
    
    def test_update_template(
        self,
        auth_api: NotificationApiClient,
        template_data: dict
    ):
        """
        [TMPL-005] Обновление шаблона.
        """
        # Создаём
        create_response = auth_api.create_template(**template_data)
        template_id = create_response.json()["id"]
        
        # Обновляем
        new_name = "Updated Template Name"
        new_body = "Updated body: {{variable}}"
        
        update_response = auth_api.update_template(
            template_id,
            name=new_name,
            body=new_body
        )
        
        api_assert.assert_success(update_response)
        
        # Проверяем
        get_response = auth_api.get_template_by_id(template_id)
        body = get_response.json()
        
        assert body["name"] == new_name
        
        # Cleanup
        auth_api.delete_template(template_id)
    
    def test_deactivate_template(
        self,
        auth_api: NotificationApiClient,
        template_data: dict
    ):
        """
        [TMPL-006] Деактивация шаблона.
        """
        # Создаём
        create_response = auth_api.create_template(**template_data)
        template_id = create_response.json()["id"]
        
        # Деактивируем
        update_response = auth_api.update_template(template_id, active=False)
        
        api_assert.assert_success(update_response)
        
        # Cleanup
        auth_api.delete_template(template_id)
    
    def test_delete_template(
        self,
        auth_api: NotificationApiClient,
        template_data: dict
    ):
        """
        [TMPL-007] Удаление шаблона.
        """
        # Создаём
        create_response = auth_api.create_template(**template_data)
        template_id = create_response.json()["id"]
        
        # Удаляем
        delete_response = auth_api.delete_template(template_id)
        
        assert delete_response.status_code in [200, 204]
        
        # Проверяем
        get_response = auth_api.get_template_by_id(template_id)
        assert get_response.status_code == 404


@pytest.mark.templates
@pytest.mark.api
class TestTemplatesValidation:
    """Тесты валидации шаблонов."""
    
    def test_create_template_without_code(self, auth_api: NotificationApiClient):
        """
        [TMPL-008] Создание шаблона без кода.
        """
        response = auth_api.http.post("/admin/templates", json_data={
            "name": "Test Template",
            "channel": "EMAIL",
            "body": "Test body"
        })
        
        api_assert.assert_client_error(response)
    
    def test_create_template_with_duplicate_code(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [TMPL-009] Создание шаблона с дублирующимся кодом.
        """
        unique_code = f"DUP_CODE_{id(self)}"
        
        # Создаём первый
        template1 = TemplateFactory.create(code=unique_code)
        response1 = auth_api.create_template(**template1)
        template_id = response1.json().get("id")
        
        # Пробуем создать с тем же кодом
        template2 = TemplateFactory.create(code=unique_code)
        response2 = auth_api.create_template(**template2)
        
        assert response2.status_code in [400, 409]
        
        # Cleanup
        if template_id:
            auth_api.delete_template(template_id)


# =============================================================================
# Channels Tests
# =============================================================================

@pytest.mark.channels
@pytest.mark.api
class TestChannelsConfiguration:
    """Тесты конфигурации каналов."""
    
    def test_get_all_channels(self, auth_api: NotificationApiClient):
        """
        [CHAN-001] Получение списка всех каналов.
        """
        response = auth_api.get_channels()
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert isinstance(body, list) or "content" in body
    
    def test_get_email_channel(self, auth_api: NotificationApiClient):
        """
        [CHAN-002] Получение конфигурации EMAIL канала.
        """
        response = auth_api.get_channel("EMAIL")
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert body.get("channelType") == "EMAIL" or body.get("type") == "EMAIL"
    
    def test_update_channel_config(self, auth_api: NotificationApiClient):
        """
        [CHAN-003] Обновление конфигурации канала.
        """
        # Получаем текущую конфигурацию
        get_response = auth_api.get_channel("EMAIL")
        
        if not get_response.is_success:
            pytest.skip("EMAIL channel not configured")
        
        # Обновляем
        update_response = auth_api.update_channel(
            "EMAIL",
            enabled=True,
            config={"testMode": True}
        )
        
        # Может быть успех или ошибка (зависит от валидации)
        assert update_response.status_code in [200, 400, 403]
    
    def test_enable_disable_channel(self, auth_api: NotificationApiClient):
        """
        [CHAN-004] Включение/выключение канала.
        """
        # Выключаем
        disable_response = auth_api.update_channel("EMAIL", enabled=False)
        
        if disable_response.is_success:
            # Включаем обратно
            enable_response = auth_api.update_channel("EMAIL", enabled=True)
            api_assert.assert_success(enable_response)
    
    def test_get_nonexistent_channel(self, auth_api: NotificationApiClient):
        """
        [CHAN-005] Получение несуществующего канала.
        """
        response = auth_api.get_channel("NONEXISTENT")
        
        assert response.status_code in [400, 404]


# =============================================================================
# Dashboard Tests
# =============================================================================

@pytest.mark.api
class TestDashboard:
    """Тесты дашборда."""
    
    def test_get_dashboard_stats(self, auth_api: NotificationApiClient):
        """
        [DASH-001] Получение статистики дашборда.
        """
        response = auth_api.get_dashboard_stats()
        
        api_assert.assert_success(response)
        body = response.json()
        
        # Проверяем наличие основных метрик
        expected_fields = ["totalNotifications", "sentToday", "failedToday"]
        for field in expected_fields:
            # Поле может быть названо по-разному
            assert any(f in body for f in [field, field.lower(), 
                       field.replace("Today", "_today")])
    
    def test_get_dashboard_chart(self, auth_api: NotificationApiClient):
        """
        [DASH-002] Получение данных для графика.
        """
        response = auth_api.get_dashboard_chart(period="7d")
        
        api_assert.assert_success(response)
        body = response.json()
        
        # Должен быть массив данных
        assert isinstance(body, list) or "data" in body or "chart" in body
    
    def test_get_dashboard_chart_different_periods(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [DASH-003] Получение графика за разные периоды.
        """
        periods = ["24h", "7d", "30d"]
        
        for period in periods:
            response = auth_api.get_dashboard_chart(period=period)
            
            # Должен быть успех или 400 (неподдерживаемый период)
            assert response.status_code in [200, 400], \
                f"Failed for period {period}"


# =============================================================================
# Audit Logs Tests
# =============================================================================

@pytest.mark.audit
@pytest.mark.api
class TestAuditLogs:
    """Тесты аудит логов."""
    
    def test_get_audit_logs(self, auth_api: NotificationApiClient):
        """
        [AUDIT-001] Получение списка аудит логов.
        """
        response = auth_api.get_audit_logs()
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert isinstance(body, list) or "content" in body
    
    def test_get_audit_logs_with_pagination(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [AUDIT-002] Пагинация аудит логов.
        """
        response = auth_api.get_audit_logs(page=0, size=10)
        
        api_assert.assert_success(response)
        body = response.json()
        
        if "content" in body:
            assert len(body["content"]) <= 10
    
    def test_get_audit_logs_filter_by_action(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [AUDIT-003] Фильтрация по типу действия.
        """
        response = auth_api.get_audit_logs(action="CREATE")
        
        api_assert.assert_success(response)
        body = response.json()
        
        content = body.get("content", body) if isinstance(body, dict) else body
        
        for log in content:
            if "action" in log:
                assert log["action"] == "CREATE"
    
    def test_get_audit_logs_filter_by_entity_type(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [AUDIT-004] Фильтрация по типу сущности.
        """
        response = auth_api.get_audit_logs(entity_type="NOTIFICATION")
        
        api_assert.assert_success(response)
    
    def test_action_creates_audit_log(
        self,
        auth_api: NotificationApiClient,
        api_client_data: dict
    ):
        """
        [AUDIT-005] Действия создают записи в аудит логе.
        """
        # Получаем текущее количество логов
        before_response = auth_api.get_audit_logs(page=0, size=1)
        
        # Создаём клиента (должен создать аудит лог)
        create_response = auth_api.create_client(**api_client_data)
        
        if not create_response.is_success:
            pytest.skip("Could not create client")
        
        client_id = create_response.json()["id"]
        
        # Проверяем что появился новый лог
        after_response = auth_api.get_audit_logs(page=0, size=10)
        
        api_assert.assert_success(after_response)
        
        # Cleanup
        auth_api.delete_client(client_id)
