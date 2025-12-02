# Notification Service - Test Framework

Современный Python-фреймворк для комплексного автоматизированного тестирования Notification Service.

## 🏗️ Архитектура

```
tests/
├── config/              # Конфигурация тестового окружения
│   ├── __init__.py
│   └── settings.py      # Настройки (API URL, credentials, etc.)
├── core/                # Базовые классы и утилиты
│   ├── __init__.py
│   ├── base.py          # BaseTestCase, ApiResponse, enums
│   └── assertions.py    # Кастомные assertions
├── api/                 # API клиенты
│   ├── __init__.py
│   └── client.py        # NotificationApiClient
├── factories/           # Фабрики тестовых данных
│   ├── __init__.py
│   └── data_factory.py  # Faker-based factories
├── tests/               # Тестовые модули
│   ├── test_smoke.py        # Smoke тесты (BLOCKER)
│   ├── test_auth.py         # Аутентификация/авторизация
│   ├── test_notifications.py # Уведомления
│   ├── test_admin_api.py    # Admin API (CRUD)
│   ├── test_security.py     # Безопасность
│   ├── test_performance.py  # Производительность
│   └── test_integration.py  # E2E сценарии
├── reports/             # Отчёты (генерируются)
├── conftest.py          # Pytest fixtures
├── pytest.ini           # Pytest конфигурация
├── requirements.txt     # Зависимости
└── run_tests.py         # CLI runner
```

## 🚀 Быстрый старт

### 1. Установка зависимостей

```bash
cd tests
python -m venv venv
source venv/bin/activate  # Linux/Mac
# или
.\venv\Scripts\activate   # Windows

pip install -r requirements.txt
```

### 2. Настройка окружения

```bash
cp .env.example .env.test
# Отредактируйте .env.test
```

### 3. Запуск тестов

```bash
# Все тесты
pytest

# Smoke тесты (быстрая проверка)
pytest -m smoke

# С покрытием
pytest --cov --cov-report=html

# Параллельно
pytest -n 4
```

## 📊 Категории тестов

| Маркер | Описание | Кол-во |
|--------|----------|--------|
| `smoke` | Быстрые проверки работоспособности | ~15 |
| `auth` | Аутентификация и авторизация | ~20 |
| `notifications` | Отправка и статусы уведомлений | ~30 |
| `clients` | CRUD API клиентов | ~15 |
| `templates` | CRUD шаблонов | ~10 |
| `channels` | Конфигурация каналов | ~5 |
| `security` | SQL injection, XSS, bypass | ~20 |
| `performance` | Нагрузка и скорость | ~10 |
| `integration` | E2E сценарии | ~10 |

**Всего: ~135+ тестов**

## 🎯 Примеры запуска

```bash
# Только API тесты
python run_tests.py --api

# Security тесты с отчётом
python run_tests.py --security --report

# Все тесты с покрытием и Allure
python run_tests.py --coverage --allure

# Параллельно на 8 ядрах
python run_tests.py --parallel -n 8

# Против staging окружения
python run_tests.py --env staging --api-url https://api.staging.example.com

# Только неудачные тесты (retry)
python run_tests.py --failed
```

## 📈 Отчёты

### HTML отчёт
```bash
pytest --html=reports/test_report.html --self-contained-html
```

### Coverage отчёт
```bash
pytest --cov --cov-report=html:reports/coverage
# Открыть reports/coverage/index.html
```

### Allure отчёт
```bash
pytest --alluredir=reports/allure
allure serve reports/allure
```

## 🔧 Конфигурация

### pytest.ini
```ini
[pytest]
markers =
    smoke: Quick sanity checks
    api: API endpoint tests
    security: Security tests
    performance: Performance tests
    slow: Tests > 5 seconds

timeout = 30
asyncio_mode = auto
```

### Переменные окружения
```bash
TEST_ENV=docker              # local, docker, staging
TEST_API_URL=http://localhost:8080
TEST_ADMIN_USER=admin
TEST_ADMIN_PASS=admin123
```

## 🧪 Написание тестов

### Базовый тест
```python
import pytest
from api import NotificationApiClient
from core import ApiAssertions as api_assert

@pytest.mark.api
class TestExample:
    def test_health_check(self, api: NotificationApiClient):
        response = api.health_check()
        api_assert.assert_success(response)
        api_assert.assert_response_time(response, max_ms=500)
```

### С фабрикой данных
```python
from factories import NotificationFactory

def test_send_notification(self, auth_api):
    notification = NotificationFactory.create_email(
        subject="Test",
        priority="HIGH"
    )
    response = auth_api.send_notification(**notification)
    assert response.status_code in [200, 201, 202]
```

### С фикстурой-фабрикой
```python
def test_with_template(self, auth_api, create_template_entity):
    template_id = create_template_entity(
        code="MY_TPL",
        body="Hello, {{name}}!"
    )
    # Шаблон автоматически удалится после теста
```

## 🛡️ Security тесты

Фреймворк включает тесты на:
- SQL Injection
- XSS (Cross-Site Scripting)
- Authorization bypass
- Rate limiting
- Input validation
- Sensitive data exposure

## ⚡ Performance тесты

Включают:
- Response time assertions
- Concurrent requests
- Throughput measurement
- Locust integration для нагрузочного тестирования

### Locust
```bash
locust -f tests/test_performance.py --host=http://localhost:8080
```

## 🔄 CI/CD интеграция

### GitHub Actions
```yaml
- name: Run Tests
  run: |
    cd tests
    pip install -r requirements.txt
    pytest --cov --cov-report=xml -m "not slow"
```

### GitLab CI
```yaml
test:
  script:
    - cd tests
    - pip install -r requirements.txt
    - pytest --junitxml=report.xml
  artifacts:
    reports:
      junit: tests/report.xml
```

## 📋 Checklist покрытия

- [x] Health endpoints
- [x] Authentication (JWT, API keys)
- [x] Authorization (roles, permissions)
- [x] Notifications CRUD
- [x] Templates CRUD
- [x] API Clients CRUD
- [x] Channels configuration
- [x] Dashboard statistics
- [x] Audit logs
- [x] Input validation
- [x] Error handling
- [x] Security vulnerabilities
- [x] Performance benchmarks
- [x] E2E scenarios

## 📞 Troubleshooting

### Тесты не находят API
```bash
# Проверьте что сервис запущен
curl http://localhost:8080/api/v1/health

# Проверьте переменные
echo $TEST_API_URL
```

### Timeout ошибки
```bash
# Увеличьте timeout
pytest --timeout=60
```

### Параллельные тесты падают
```bash
# Уменьшите workers
pytest -n 2
```
