"""
Notification Service - Test Framework
=====================================

Современный Python-фреймворк для комплексного тестирования Notification Service.

Архитектура:
- conftest.py: Фикстуры и конфигурация pytest
- config/: Настройки тестового окружения  
- core/: Базовые классы и утилиты
- api/: API клиенты для тестирования
- factories/: Фабрики тестовых данных
- tests/: Тестовые модули по категориям
- reports/: Отчёты о тестировании

Использование:
    pytest                          # Все тесты
    pytest -m smoke                 # Только smoke тесты
    pytest -m "api and not slow"    # API без медленных
    pytest --cov --cov-report=html  # С покрытием
    pytest --html=report.html       # HTML отчёт

Автор: Notification Service Team
Версия: 1.0.0
"""

__version__ = "1.0.0"
__author__ = "Notification Service Team"
