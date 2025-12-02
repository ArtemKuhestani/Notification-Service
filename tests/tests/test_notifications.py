"""
Notification Service - Notification Tests
==========================================

Тестирование полного цикла работы с уведомлениями.

Coverage:
- Отправка уведомлений
- Получение статуса
- Повторная отправка
- Отмена уведомлений
- Шаблоны
- Идемпотентность
"""

import time
import uuid

import pytest

from api import NotificationApiClient
from core import (
    ApiAssertions as api_assert,
    DataAssertions as data_assert,
    NotificationChannel,
    NotificationStatus,
    Priority,
    wait_for_condition,
)
from factories import IdGenerator, NotificationFactory, TemplateFactory


@pytest.mark.notifications
@pytest.mark.api
class TestSendNotification:
    """Тесты отправки уведомлений."""
    
    def test_send_email_notification(
        self,
        auth_api: NotificationApiClient,
        email_notification: dict
    ):
        """
        [NOTIF-001] Отправка email уведомления.
        """
        response = auth_api.send_notification(**email_notification)
        
        assert response.status_code in [200, 201, 202]
        body = response.json()
        
        # Должен вернуться ID уведомления
        notification_id = body.get("notificationId") or body.get("id")
        data_assert.assert_not_none(notification_id)
        data_assert.assert_uuid(notification_id)
    
    def test_send_telegram_notification(
        self,
        auth_api: NotificationApiClient,
        telegram_notification: dict
    ):
        """
        [NOTIF-002] Отправка Telegram уведомления.
        """
        response = auth_api.send_notification(**telegram_notification)
        
        # Может вернуть ошибку если Telegram не настроен
        if response.status_code in [200, 201, 202]:
            body = response.json()
            notification_id = body.get("notificationId") or body.get("id")
            data_assert.assert_not_none(notification_id)
        else:
            # Ожидаем конкретную ошибку (канал не настроен)
            assert response.status_code in [400, 503]
    
    def test_send_sms_notification(
        self,
        auth_api: NotificationApiClient,
        sms_notification: dict
    ):
        """
        [NOTIF-003] Отправка SMS уведомления.
        """
        response = auth_api.send_notification(**sms_notification)
        
        # SMS канал может быть не настроен
        if response.status_code in [200, 201, 202]:
            body = response.json()
            notification_id = body.get("notificationId") or body.get("id")
            data_assert.assert_not_none(notification_id)
        else:
            assert response.status_code in [400, 503]
    
    def test_send_with_all_priorities(self, auth_api: NotificationApiClient):
        """
        [NOTIF-004] Отправка уведомлений с разными приоритетами.
        """
        for priority in Priority:
            notification = NotificationFactory.create_email(priority=priority)
            response = auth_api.send_notification(**notification)
            
            assert response.status_code in [200, 201, 202], \
                f"Failed for priority {priority.value}"
    
    def test_send_with_metadata(self, auth_api: NotificationApiClient):
        """
        [NOTIF-005] Отправка уведомления с метаданными.
        """
        notification = NotificationFactory.create_email(
            metadata={
                "orderId": "ORD-12345",
                "userId": 42,
                "source": "test",
            }
        )
        
        response = auth_api.send_notification(**notification)
        
        assert response.status_code in [200, 201, 202]
    
    def test_send_with_callback_url(self, auth_api: NotificationApiClient):
        """
        [NOTIF-006] Отправка уведомления с callback URL.
        """
        notification = NotificationFactory.create_email(
            callbackUrl="https://webhook.test/notification-callback"
        )
        
        response = auth_api.send_notification(**notification)
        
        assert response.status_code in [200, 201, 202]


@pytest.mark.notifications
@pytest.mark.api
class TestNotificationValidation:
    """Тесты валидации уведомлений."""
    
    def test_send_without_channel(self, auth_api: NotificationApiClient):
        """
        [NOTIF-007] Отправка без указания канала должна возвращать 400.
        """
        response = auth_api.http.post("/send", json_data={
            "recipient": "test@example.com",
            "message": "Test",
        })
        
        api_assert.assert_client_error(response)
    
    def test_send_without_recipient(self, auth_api: NotificationApiClient):
        """
        [NOTIF-008] Отправка без получателя должна возвращать 400.
        """
        response = auth_api.http.post("/send", json_data={
            "channel": "EMAIL",
            "message": "Test",
        })
        
        api_assert.assert_client_error(response)
    
    def test_send_without_message(self, auth_api: NotificationApiClient):
        """
        [NOTIF-009] Отправка без сообщения должна возвращать 400.
        """
        response = auth_api.http.post("/send", json_data={
            "channel": "EMAIL",
            "recipient": "test@example.com",
        })
        
        api_assert.assert_client_error(response)
    
    def test_send_with_invalid_channel(self, auth_api: NotificationApiClient):
        """
        [NOTIF-010] Отправка с невалидным каналом должна возвращать 400.
        """
        response = auth_api.send_notification(
            channel="INVALID_CHANNEL",
            recipient="test@example.com",
            message="Test"
        )
        
        api_assert.assert_client_error(response)
    
    def test_send_with_invalid_email(self, auth_api: NotificationApiClient):
        """
        [NOTIF-011] Отправка на невалидный email должна возвращать 400.
        """
        response = auth_api.send_notification(
            channel="EMAIL",
            recipient="not-an-email",
            message="Test"
        )
        
        api_assert.assert_client_error(response)
    
    def test_send_with_invalid_priority(self, auth_api: NotificationApiClient):
        """
        [NOTIF-012] Отправка с невалидным приоритетом должна возвращать 400.
        """
        response = auth_api.send_notification(
            channel="EMAIL",
            recipient="test@example.com",
            message="Test",
            priority="INVALID_PRIORITY"
        )
        
        api_assert.assert_client_error(response)


@pytest.mark.notifications
@pytest.mark.api
class TestNotificationStatus:
    """Тесты получения статуса уведомлений."""
    
    def test_get_notification_status(
        self,
        auth_api: NotificationApiClient,
        email_notification: dict
    ):
        """
        [NOTIF-013] Получение статуса уведомления.
        """
        # Отправляем уведомление
        send_response = auth_api.send_notification(**email_notification)
        notification_id = (
            send_response.json().get("notificationId") or 
            send_response.json().get("id")
        )
        
        # Получаем статус
        status_response = auth_api.get_notification_status(notification_id)
        
        api_assert.assert_success(status_response)
        body = status_response.json()
        
        assert "status" in body
        assert body["status"] in [s.value for s in NotificationStatus]
    
    def test_get_nonexistent_notification_status(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [NOTIF-014] Получение статуса несуществующего уведомления.
        """
        fake_id = str(uuid.uuid4())
        
        response = auth_api.get_notification_status(fake_id)
        
        api_assert.assert_status_code(response, 404)
    
    def test_status_contains_required_fields(
        self,
        auth_api: NotificationApiClient,
        email_notification: dict
    ):
        """
        [NOTIF-015] Статус должен содержать обязательные поля.
        """
        # Отправляем
        send_response = auth_api.send_notification(**email_notification)
        notification_id = (
            send_response.json().get("notificationId") or 
            send_response.json().get("id")
        )
        
        # Получаем статус
        status_response = auth_api.get_notification_status(notification_id)
        body = status_response.json()
        
        # Проверяем обязательные поля
        expected_fields = ["status", "channel"]
        for field in expected_fields:
            assert field in body, f"Missing field: {field}"


@pytest.mark.notifications
@pytest.mark.api
class TestIdempotency:
    """Тесты идемпотентности."""
    
    def test_same_idempotency_key_returns_same_result(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [NOTIF-016] Повторный запрос с тем же ключом возвращает тот же результат.
        """
        idempotency_key = IdGenerator.idempotency_key()
        
        notification = NotificationFactory.create_email(
            idempotencyKey=idempotency_key
        )
        
        # Первый запрос
        response1 = auth_api.send_notification(**notification)
        id1 = response1.json().get("notificationId") or response1.json().get("id")
        
        # Второй запрос с тем же ключом
        response2 = auth_api.send_notification(**notification)
        id2 = response2.json().get("notificationId") or response2.json().get("id")
        
        # Должен вернуться тот же ID
        assert id1 == id2
    
    def test_different_idempotency_keys_create_different_notifications(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [NOTIF-017] Разные ключи создают разные уведомления.
        """
        notification1 = NotificationFactory.create_email(
            idempotencyKey=IdGenerator.idempotency_key()
        )
        notification2 = NotificationFactory.create_email(
            idempotencyKey=IdGenerator.idempotency_key()
        )
        
        response1 = auth_api.send_notification(**notification1)
        response2 = auth_api.send_notification(**notification2)
        
        id1 = response1.json().get("notificationId") or response1.json().get("id")
        id2 = response2.json().get("notificationId") or response2.json().get("id")
        
        # ID должны быть разными
        assert id1 != id2


@pytest.mark.notifications
@pytest.mark.api
class TestNotificationRetry:
    """Тесты повторной отправки."""
    
    def test_retry_notification(
        self,
        auth_api: NotificationApiClient,
        email_notification: dict
    ):
        """
        [NOTIF-018] Повторная отправка уведомления.
        """
        # Отправляем
        send_response = auth_api.send_notification(**email_notification)
        notification_id = (
            send_response.json().get("notificationId") or 
            send_response.json().get("id")
        )
        
        # Пробуем повторить
        retry_response = auth_api.retry_notification(notification_id)
        
        # Должен быть успех или ошибка (если статус не позволяет retry)
        assert retry_response.status_code in [200, 202, 400, 409]
    
    def test_retry_nonexistent_notification(self, auth_api: NotificationApiClient):
        """
        [NOTIF-019] Повтор несуществующего уведомления.
        """
        fake_id = str(uuid.uuid4())
        
        response = auth_api.retry_notification(fake_id)
        
        api_assert.assert_status_code(response, 404)


@pytest.mark.notifications
@pytest.mark.api
class TestNotificationCancel:
    """Тесты отмены уведомлений."""
    
    def test_cancel_pending_notification(
        self,
        auth_api: NotificationApiClient,
        email_notification: dict
    ):
        """
        [NOTIF-020] Отмена ожидающего уведомления.
        """
        # Отправляем
        send_response = auth_api.send_notification(**email_notification)
        notification_id = (
            send_response.json().get("notificationId") or 
            send_response.json().get("id")
        )
        
        # Пробуем отменить
        cancel_response = auth_api.cancel_notification(notification_id)
        
        # Должен быть успех или ошибка (если уже отправлено)
        assert cancel_response.status_code in [200, 204, 400, 409]
    
    def test_cancel_nonexistent_notification(self, auth_api: NotificationApiClient):
        """
        [NOTIF-021] Отмена несуществующего уведомления.
        """
        fake_id = str(uuid.uuid4())
        
        response = auth_api.cancel_notification(fake_id)
        
        api_assert.assert_status_code(response, 404)


@pytest.mark.notifications
@pytest.mark.api
class TestTemplateNotifications:
    """Тесты уведомлений с шаблонами."""
    
    def test_send_with_template(
        self,
        auth_api: NotificationApiClient,
        create_template_entity
    ):
        """
        [NOTIF-022] Отправка уведомления с шаблоном.
        """
        # Создаём шаблон
        template_id = create_template_entity(
            code="TEST_TEMPLATE",
            name="Test Template",
            channel="EMAIL",
            subject="Hello, {{name}}!",
            body="Welcome, {{name}}! Your order {{orderId}} is confirmed.",
            variables=["name", "orderId"]
        )
        
        # Отправляем с шаблоном
        notification = NotificationFactory.create_with_template(
            template_code="TEST_TEMPLATE",
            variables={"name": "John", "orderId": "ORD-123"},
            channel=NotificationChannel.EMAIL
        )
        
        response = auth_api.send_notification(**notification)
        
        assert response.status_code in [200, 201, 202, 400]  # 400 если шаблон не найден
    
    def test_send_with_nonexistent_template(self, auth_api: NotificationApiClient):
        """
        [NOTIF-023] Отправка с несуществующим шаблоном.
        """
        notification = NotificationFactory.create_with_template(
            template_code="NONEXISTENT_TEMPLATE",
            variables={"key": "value"},
            channel=NotificationChannel.EMAIL
        )
        
        response = auth_api.send_notification(**notification)
        
        # Должна быть ошибка 400 или 404
        assert response.status_code in [400, 404]
    
    def test_send_with_missing_template_variables(
        self,
        auth_api: NotificationApiClient,
        create_template_entity
    ):
        """
        [NOTIF-024] Отправка без обязательных переменных шаблона.
        """
        # Создаём шаблон с переменными
        template_id = create_template_entity(
            code="VARS_TEMPLATE",
            name="Variables Template",
            channel="EMAIL",
            body="Hello, {{name}}! Code: {{code}}",
            variables=["name", "code"]
        )
        
        # Отправляем без переменных
        notification = NotificationFactory.create_with_template(
            template_code="VARS_TEMPLATE",
            variables={},  # Пустые переменные
            channel=NotificationChannel.EMAIL
        )
        
        response = auth_api.send_notification(**notification)
        
        # Должна быть ошибка валидации
        assert response.status_code in [400, 422]


@pytest.mark.notifications
@pytest.mark.api
class TestNotificationsList:
    """Тесты списка уведомлений."""
    
    def test_get_notifications_list(self, auth_api: NotificationApiClient):
        """
        [NOTIF-025] Получение списка уведомлений.
        """
        response = auth_api.get_notifications()
        
        api_assert.assert_success(response)
        body = response.json()
        
        # Должен быть массив или объект с content
        assert isinstance(body, list) or "content" in body
    
    def test_get_notifications_with_pagination(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [NOTIF-026] Пагинация списка уведомлений.
        """
        response = auth_api.get_notifications(page=0, size=5)
        
        api_assert.assert_success(response)
        body = response.json()
        
        if "content" in body:
            assert len(body["content"]) <= 5
    
    def test_get_notifications_filter_by_status(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [NOTIF-027] Фильтрация по статусу.
        """
        response = auth_api.get_notifications(status="SENT")
        
        api_assert.assert_success(response)
        body = response.json()
        
        content = body.get("content", body) if isinstance(body, dict) else body
        
        # Все уведомления должны иметь статус SENT
        for notification in content:
            if "status" in notification:
                assert notification["status"] == "SENT"
    
    def test_get_notifications_filter_by_channel(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [NOTIF-028] Фильтрация по каналу.
        """
        response = auth_api.get_notifications(channel="EMAIL")
        
        api_assert.assert_success(response)
        body = response.json()
        
        content = body.get("content", body) if isinstance(body, dict) else body
        
        for notification in content:
            if "channel" in notification:
                assert notification["channel"] == "EMAIL"
    
    def test_get_notification_by_id(
        self,
        auth_api: NotificationApiClient,
        email_notification: dict
    ):
        """
        [NOTIF-029] Получение уведомления по ID.
        """
        # Создаём уведомление
        send_response = auth_api.send_notification(**email_notification)
        notification_id = (
            send_response.json().get("notificationId") or 
            send_response.json().get("id")
        )
        
        # Получаем детали
        response = auth_api.get_notification_by_id(notification_id)
        
        api_assert.assert_success(response)
        body = response.json()
        
        assert body.get("id") == notification_id or \
               body.get("notificationId") == notification_id
