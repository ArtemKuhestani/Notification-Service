"""
Notification Service - Performance Tests
=========================================

Тестирование производительности и нагрузки.

Использует Locust для нагрузочного тестирования.
Запуск: locust -f test_performance.py --host=http://localhost:8080
"""

import time
from typing import List

import pytest

from api import NotificationApiClient
from core import ApiAssertions as api_assert, PerformanceMetrics
from factories import NotificationFactory


@pytest.mark.performance
@pytest.mark.slow
class TestApiPerformance:
    """Тесты производительности API."""
    
    def test_health_endpoint_response_time(
        self,
        api: NotificationApiClient
    ):
        """
        [PERF-001] Health endpoint должен отвечать менее чем за 100ms.
        """
        times: List[float] = []
        
        for _ in range(10):
            response = api.health_check()
            times.append(response.duration_ms)
        
        avg_time = sum(times) / len(times)
        max_time = max(times)
        
        assert avg_time < 100, f"Average response time {avg_time:.2f}ms > 100ms"
        assert max_time < 500, f"Max response time {max_time:.2f}ms > 500ms"
    
    def test_login_response_time(
        self,
        api: NotificationApiClient,
        test_config
    ):
        """
        [PERF-002] Login должен выполняться менее чем за 500ms.
        """
        times: List[float] = []
        
        for _ in range(5):
            response = api.login(
                test_config.auth.admin_username,
                test_config.auth.admin_password
            )
            times.append(response.duration_ms)
            api.logout()
        
        avg_time = sum(times) / len(times)
        
        assert avg_time < 500, f"Average login time {avg_time:.2f}ms > 500ms"
    
    def test_send_notification_response_time(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [PERF-003] Отправка уведомления должна занимать менее 1000ms.
        """
        times: List[float] = []
        
        for _ in range(10):
            notification = NotificationFactory.create_email()
            response = auth_api.send_notification(**notification)
            times.append(response.duration_ms)
        
        avg_time = sum(times) / len(times)
        p95_time = sorted(times)[int(len(times) * 0.95)]
        
        assert avg_time < 500, f"Average send time {avg_time:.2f}ms > 500ms"
        assert p95_time < 1000, f"P95 send time {p95_time:.2f}ms > 1000ms"
    
    def test_get_notifications_list_response_time(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [PERF-004] Получение списка уведомлений должно быть быстрым.
        """
        times: List[float] = []
        
        for _ in range(10):
            response = auth_api.get_notifications(page=0, size=20)
            times.append(response.duration_ms)
        
        avg_time = sum(times) / len(times)
        
        assert avg_time < 200, f"Average list time {avg_time:.2f}ms > 200ms"


@pytest.mark.performance
@pytest.mark.slow
class TestConcurrency:
    """Тесты параллельной нагрузки."""
    
    def test_concurrent_health_checks(
        self,
        test_config
    ):
        """
        [PERF-005] Параллельные health checks.
        """
        import concurrent.futures
        from api import create_api_client
        
        def check_health():
            client = create_api_client()
            response = client.health_check()
            client.close()
            return response.is_success, response.duration_ms
        
        num_requests = 50
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=10) as executor:
            futures = [executor.submit(check_health) for _ in range(num_requests)]
            results = [f.result() for f in concurrent.futures.as_completed(futures)]
        
        successes = sum(1 for success, _ in results if success)
        times = [t for _, t in results]
        
        success_rate = successes / num_requests * 100
        avg_time = sum(times) / len(times)
        
        assert success_rate >= 99, f"Success rate {success_rate:.1f}% < 99%"
        assert avg_time < 500, f"Average time {avg_time:.2f}ms > 500ms"
    
    def test_concurrent_notifications(
        self,
        test_config
    ):
        """
        [PERF-006] Параллельная отправка уведомлений.
        """
        import concurrent.futures
        from api import create_api_client
        
        def send_notification():
            client = create_api_client()
            client.authenticate_admin()
            notification = NotificationFactory.create_email()
            response = client.send_notification(**notification)
            client.close()
            return response.status_code in [200, 201, 202], response.duration_ms
        
        num_requests = 20
        
        with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
            futures = [executor.submit(send_notification) for _ in range(num_requests)]
            results = [f.result() for f in concurrent.futures.as_completed(futures)]
        
        successes = sum(1 for success, _ in results if success)
        times = [t for _, t in results]
        
        success_rate = successes / num_requests * 100
        avg_time = sum(times) / len(times)
        
        assert success_rate >= 90, f"Success rate {success_rate:.1f}% < 90%"
        assert avg_time < 2000, f"Average time {avg_time:.2f}ms > 2000ms"


@pytest.mark.performance
@pytest.mark.slow
class TestThroughput:
    """Тесты пропускной способности."""
    
    def test_notifications_throughput(
        self,
        auth_api: NotificationApiClient
    ):
        """
        [PERF-007] Пропускная способность отправки уведомлений.
        """
        num_notifications = 50
        
        start_time = time.time()
        
        successes = 0
        for _ in range(num_notifications):
            notification = NotificationFactory.create_email()
            response = auth_api.send_notification(**notification)
            if response.status_code in [200, 201, 202]:
                successes += 1
        
        elapsed_time = time.time() - start_time
        throughput = num_notifications / elapsed_time
        success_rate = successes / num_notifications * 100
        
        print(f"\nThroughput: {throughput:.2f} notifications/second")
        print(f"Success rate: {success_rate:.1f}%")
        print(f"Total time: {elapsed_time:.2f}s")
        
        # Минимальные требования
        assert throughput >= 5, f"Throughput {throughput:.2f}/s < 5/s"
        assert success_rate >= 90, f"Success rate {success_rate:.1f}% < 90%"


# =============================================================================
# Locust Load Test (отдельный запуск)
# =============================================================================

try:
    from locust import HttpUser, between, task
    
    class NotificationServiceUser(HttpUser):
        """
        Locust user для нагрузочного тестирования.
        
        Запуск:
            locust -f test_performance.py --host=http://localhost:8080
        """
        
        wait_time = between(0.5, 2)
        
        def on_start(self):
            """Авторизация при старте."""
            response = self.client.post("/api/v1/auth/login", json={
                "username": "admin",
                "password": "admin123"
            })
            if response.ok:
                self.token = response.json().get("accessToken")
                self.headers = {"Authorization": f"Bearer {self.token}"}
            else:
                self.headers = {}
        
        @task(10)
        def health_check(self):
            """Проверка здоровья (самый частый запрос)."""
            self.client.get("/api/v1/health")
        
        @task(5)
        def get_notifications(self):
            """Получение списка уведомлений."""
            self.client.get(
                "/api/v1/admin/notifications",
                headers=self.headers,
                params={"page": 0, "size": 20}
            )
        
        @task(3)
        def send_notification(self):
            """Отправка уведомления."""
            notification = {
                "channel": "EMAIL",
                "recipient": f"test-{time.time()}@example.com",
                "subject": "Load Test",
                "message": "This is a load test notification",
                "priority": "NORMAL"
            }
            self.client.post(
                "/api/v1/send",
                headers=self.headers,
                json=notification
            )
        
        @task(2)
        def get_dashboard(self):
            """Получение статистики."""
            self.client.get(
                "/api/v1/admin/dashboard/stats",
                headers=self.headers
            )
        
        @task(1)
        def get_clients(self):
            """Получение списка клиентов."""
            self.client.get(
                "/api/v1/admin/clients",
                headers=self.headers
            )

except ImportError:
    # Locust не установлен - пропускаем
    pass
