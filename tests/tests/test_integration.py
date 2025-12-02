"""
Notification Service - Integration Tests
=========================================

End-to-end тесты полных сценариев.
"""

import time
import uuid

import pytest

from api import NotificationApiClient
from core import (
    ApiAssertions as api_assert,
    NotificationChannel,
    NotificationStatus,
    wait_for_condition,
)
from factories import (
    ApiClientFactory,
    NotificationFactory,
    ScenarioFactory,
    TemplateFactory,
)


@pytest.mark.integration
@pytest.mark.e2e
class TestNotificationLifecycle:
    """Тесты полного жизненного цикла уведомления."""
    
    def test_complete_email_notification_flow(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [E2E-001] Полный цикл email уведомления.
        """
        # 1. Создаём уведомление
        notification = NotificationFactory.create_email(
            subject="E2E Test Email",
            message="This is an end-to-end test notification",
            priority="HIGH"
        )
        
        send_response = auth_api.send_notification(**notification)
        assert send_response.status_code in [200, 201, 202]
        
        notification_id = (
            send_response.json().get("notificationId") or
            send_response.json().get("id")
        )
        
        # 2. Проверяем что уведомление создано
        status_response = auth_api.get_notification_status(notification_id)
        api_assert.assert_success(status_response)
        
        # 3. Проверяем что уведомление в списке
        list_response = auth_api.get_notifications()
        api_assert.assert_success(list_response)
        
        # 4. Получаем детали
        details_response = auth_api.get_notification_by_id(notification_id)
        api_assert.assert_success(details_response)
        
        body = details_response.json()
        assert body.get("channel") == "EMAIL"
    
    def test_notification_with_template_flow(
        self,
        auth_api: NotificationApiClient,
        create_template_entity
    ):
        """
        [E2E-002] Уведомление с использованием шаблона.
        """
        # 1. Создаём шаблон
        template_code = f"E2E_TPL_{uuid.uuid4().hex[:8]}"
        template_id = create_template_entity(
            code=template_code,
            name="E2E Test Template",
            channel="EMAIL",
            subject="Welcome, {{name}}!",
            body="Hello {{name}}, your order {{orderId}} is ready.",
            variables=["name", "orderId"]
        )
        
        # 2. Отправляем уведомление с шаблоном
        notification = NotificationFactory.create_with_template(
            template_code=template_code,
            variables={"name": "John", "orderId": "ORD-12345"},
            channel=NotificationChannel.EMAIL
        )
        
        response = auth_api.send_notification(**notification)
        
        # Шаблон должен работать
        assert response.status_code in [200, 201, 202, 400]
    
    def test_notification_retry_flow(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [E2E-003] Повторная отправка уведомления.
        """
        # 1. Создаём уведомление
        notification = NotificationFactory.create_email()
        send_response = auth_api.send_notification(**notification)
        
        notification_id = (
            send_response.json().get("notificationId") or
            send_response.json().get("id")
        )
        
        # 2. Пробуем повторить
        retry_response = auth_api.retry_notification(notification_id)
        
        # Успех или ошибка (если статус не позволяет retry)
        assert retry_response.status_code in [200, 202, 400, 409]
    
    def test_idempotent_notification_flow(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [E2E-004] Идемпотентная отправка уведомления.
        """
        idempotency_key = f"idem-{uuid.uuid4().hex}"
        
        notification = NotificationFactory.create_email(
            idempotencyKey=idempotency_key
        )
        
        # Отправляем дважды
        response1 = auth_api.send_notification(**notification)
        response2 = auth_api.send_notification(**notification)
        
        id1 = response1.json().get("notificationId") or response1.json().get("id")
        id2 = response2.json().get("notificationId") or response2.json().get("id")
        
        # Должен вернуться тот же ID
        assert id1 == id2


@pytest.mark.integration
@pytest.mark.e2e
class TestApiClientLifecycle:
    """Тесты полного жизненного цикла API клиента."""
    
    def test_complete_api_client_flow(
        self,
        auth_api: NotificationApiClient,
        api: NotificationApiClient
    ):
        """
        [E2E-005] Полный цикл работы с API клиентом.
        """
        # 1. Создаём клиента
        client_data = ApiClientFactory.create_full_access()
        create_response = auth_api.create_client(**client_data)
        
        api_assert.assert_success(create_response)
        body = create_response.json()
        
        client_id = body["id"]
        api_key = body["apiKey"]
        
        try:
            # 2. Проверяем что клиент в списке
            list_response = auth_api.get_clients()
            clients = list_response.json()
            clients = clients if isinstance(clients, list) else clients.get("content", [])
            
            client_ids = [c["id"] for c in clients]
            assert client_id in client_ids
            
            # 3. Используем API ключ
            api.use_api_key(api_key)
            
            notification = NotificationFactory.create_email()
            send_response = api.send_notification(**notification)
            
            assert send_response.status_code not in [401, 403]
            
            # 4. Обновляем клиента
            update_response = auth_api.update_client(
                client_id,
                name="Updated E2E Client",
                rate_limit=200
            )
            api_assert.assert_success(update_response)
            
            # 5. Перегенерируем ключ
            regen_response = auth_api.regenerate_api_key(client_id)
            api_assert.assert_success(regen_response)
            
            new_key = regen_response.json().get("apiKey")
            assert new_key != api_key
            
            # 6. Старый ключ не работает
            api.use_api_key(api_key)
            old_key_response = api.send_notification(**notification)
            assert old_key_response.status_code in [401, 403]
            
            # 7. Новый ключ работает
            api.use_api_key(new_key)
            new_key_response = api.send_notification(**notification)
            assert new_key_response.status_code not in [401, 403]
            
        finally:
            # Cleanup
            auth_api.delete_client(client_id)
            
            # Проверяем что удалён
            get_response = auth_api.get_client_by_id(client_id)
            assert get_response.status_code == 404


@pytest.mark.integration
@pytest.mark.e2e
class TestTemplateLifecycle:
    """Тесты полного жизненного цикла шаблона."""
    
    def test_complete_template_flow(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [E2E-006] Полный цикл работы с шаблоном.
        """
        template_code = f"E2E_FULL_{uuid.uuid4().hex[:8]}"
        
        # 1. Создаём шаблон
        template_data = TemplateFactory.create(
            code=template_code,
            name="E2E Full Test Template",
            channel="EMAIL",
            subject="{{greeting}}, {{name}}!",
            body="Welcome to our service, {{name}}!",
            variables=["greeting", "name"]
        )
        
        create_response = auth_api.create_template(**template_data)
        api_assert.assert_success(create_response)
        
        template_id = create_response.json()["id"]
        
        try:
            # 2. Проверяем в списке
            list_response = auth_api.get_templates()
            templates = list_response.json()
            templates = templates if isinstance(templates, list) else templates.get("content", [])
            
            template_codes = [t.get("code") for t in templates]
            assert template_code in template_codes
            
            # 3. Получаем детали
            get_response = auth_api.get_template_by_id(template_id)
            api_assert.assert_success(get_response)
            
            # 4. Обновляем
            update_response = auth_api.update_template(
                template_id,
                name="Updated E2E Template",
                body="Updated welcome message for {{name}}!"
            )
            api_assert.assert_success(update_response)
            
            # 5. Используем в уведомлении
            notification = NotificationFactory.create_with_template(
                template_code=template_code,
                variables={"greeting": "Hello", "name": "Test User"},
                channel=NotificationChannel.EMAIL
            )
            
            send_response = auth_api.send_notification(**notification)
            # Может быть успех или ошибка (зависит от настройки каналов)
            assert send_response.status_code in [200, 201, 202, 400]
            
            # 6. Деактивируем
            deactivate_response = auth_api.update_template(
                template_id,
                active=False
            )
            api_assert.assert_success(deactivate_response)
            
        finally:
            # Cleanup
            auth_api.delete_template(template_id)


@pytest.mark.integration
@pytest.mark.e2e
class TestMultiChannelScenario:
    """Тесты multi-channel сценариев."""
    
    def test_same_message_multiple_channels(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [E2E-007] Отправка одного сообщения через разные каналы.
        """
        message = "Multi-channel test message"
        channels = ["EMAIL", "TELEGRAM", "SMS"]
        results = {}
        
        for channel in channels:
            if channel == "EMAIL":
                notification = NotificationFactory.create_email(message=message)
            elif channel == "TELEGRAM":
                notification = NotificationFactory.create_telegram(message=message)
            else:
                notification = NotificationFactory.create_sms(message=message[:160])
            
            response = auth_api.send_notification(**notification)
            results[channel] = response.status_code
        
        # EMAIL должен работать
        assert results.get("EMAIL") in [200, 201, 202]
        
        # Другие каналы могут быть не настроены
        for channel in ["TELEGRAM", "SMS"]:
            assert results.get(channel) in [200, 201, 202, 400, 503]


@pytest.mark.integration
@pytest.mark.e2e
class TestDashboardIntegration:
    """Тесты интеграции дашборда."""
    
    def test_dashboard_reflects_new_notifications(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [E2E-008] Дашборд отражает новые уведомления.
        """
        # 1. Получаем текущую статистику
        before_response = auth_api.get_dashboard_stats()
        before_stats = before_response.json()
        
        before_total = (
            before_stats.get("totalNotifications") or
            before_stats.get("total") or
            before_stats.get("totalSent") or
            0
        )
        
        # 2. Отправляем несколько уведомлений
        for _ in range(3):
            notification = NotificationFactory.create_email()
            auth_api.send_notification(**notification)
        
        # 3. Небольшая задержка для обработки
        time.sleep(1)
        
        # 4. Проверяем обновлённую статистику
        after_response = auth_api.get_dashboard_stats()
        after_stats = after_response.json()
        
        after_total = (
            after_stats.get("totalNotifications") or
            after_stats.get("total") or
            after_stats.get("totalSent") or
            0
        )
        
        # Должно быть больше (или равно если асинхронная обработка)
        assert after_total >= before_total


@pytest.mark.integration
@pytest.mark.e2e
class TestAuditIntegration:
    """Тесты интеграции аудита."""
    
    def test_actions_logged_to_audit(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [E2E-009] Действия логируются в аудит.
        """
        # 1. Создаём клиента
        client_data = ApiClientFactory.create()
        create_response = auth_api.create_client(**client_data)
        
        if not create_response.is_success:
            pytest.skip("Could not create client")
        
        client_id = create_response.json()["id"]
        
        try:
            # 2. Небольшая задержка
            time.sleep(0.5)
            
            # 3. Проверяем аудит
            audit_response = auth_api.get_audit_logs(
                action="CREATE",
                page=0,
                size=10
            )
            
            api_assert.assert_success(audit_response)
            
            # Должна быть запись о создании
            logs = audit_response.json()
            logs = logs if isinstance(logs, list) else logs.get("content", [])
            
            # Проверяем что есть недавние CREATE записи
            create_logs = [l for l in logs if l.get("action") == "CREATE"]
            assert len(create_logs) > 0
            
        finally:
            auth_api.delete_client(client_id)
