# 🔔 Notification Service

<div align="center">

![Java](https://img.shields.io/badge/Java-17+-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green?style=for-the-badge&logo=springboot)
![React](https://img.shields.io/badge/React-18-blue?style=for-the-badge&logo=react)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue?style=for-the-badge&logo=postgresql)
![Docker](https://img.shields.io/badge/Docker-Ready-blue?style=for-the-badge&logo=docker)
![License](https://img.shields.io/badge/License-MIT-yellow?style=for-the-badge)

**Микросервис для централизованной отправки уведомлений по различным каналам связи**

[Быстрый старт](#-быстрый-старт) • [API документация](#-api-документация) • [Архитектура](#-архитектура) • [Разработка](#-разработка)

</div>

---

## ✨ Возможности

- 📧 **Email** — отправка писем через SMTP с поддержкой HTML и вложений
- 💬 **Telegram** — уведомления через Telegram Bot API с поддержкой Markdown
- 📱 **SMS** — SMS-рассылки через провайдеров (SMSC, Twilio и др.)
- 📲 **WhatsApp** — сообщения через WhatsApp Business API

### Дополнительно

- 🎨 **Шаблоны** — поддержка шаблонов сообщений с переменными
- 📊 **Аналитика** — статистика доставки, дашборды
- 🔐 **Безопасность** — JWT аутентификация, API-ключи для внешних систем
- 📝 **Аудит** — полный журнал всех действий
- 🔄 **Retry** — автоматические повторные попытки при ошибках
- ⚡ **Приоритеты** — поддержка HIGH/NORMAL/LOW приоритетов

---

## 🚀 Быстрый старт

### Предварительные требования

| Требование | Версия | Для чего |
|------------|--------|----------|
| Docker | 20+ | Основной способ запуска |
| Docker Compose | 2.0+ | Оркестрация контейнеров |
| Java | 17+ | Локальная разработка backend |
| Node.js | 18+ | Локальная разработка frontend |

### 🐳 Запуск через Docker Compose (рекомендуется)

```bash
# 1. Клонируем репозиторий
git clone <repository-url>
cd notification

# 2. Копируем и настраиваем переменные окружения
cp .env.example .env
# Отредактируйте .env файл — настройте SMTP, токены ботов и т.д.

# 3. Запускаем все сервисы
docker-compose up -d

# 4. Проверяем логи
docker-compose logs -f backend
```

### 🔗 После запуска доступны

| Сервис | URL | Описание |
|--------|-----|----------|
| Admin Panel | http://localhost:3000 | Веб-интерфейс администратора |
| Backend API | http://localhost:8080 | REST API |
| Swagger UI | http://localhost:8080/swagger-ui.html | Документация API |
| Health Check | http://localhost:8080/api/v1/health | Состояние сервиса |

### 👤 Учетные данные по умолчанию

```
Email:    admin@notification-service.com
Password: admin123
```

> ⚠️ **Важно:** Смените пароль после первого входа!

---

## 📚 API Документация

### Аутентификация

API поддерживает два способа аутентификации:

#### 1. API-ключ (для внешних систем)

```bash
curl -X POST http://localhost:8080/api/v1/send \
  -H "Content-Type: application/json" \
  -H "X-API-Key: your-api-key" \
  -d '{
    "channel": "EMAIL",
    "recipient": "user@example.com",
    "subject": "Тема письма",
    "message": "Текст сообщения"
  }'
```

#### 2. JWT-токен (для админ-панели)

```bash
# Получение токена
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@notification-service.com",
    "password": "admin123"
  }'

# Использование токена
curl http://localhost:8080/api/v1/admin/clients \
  -H "Authorization: Bearer <your-jwt-token>"
```

### Отправка уведомления

```http
POST /api/v1/send
Content-Type: application/json
X-API-Key: your-api-key
```

#### Параметры запроса

| Поле | Тип | Обязательно | Описание |
|------|-----|-------------|----------|
| `channel` | string | ✅ | Канал: EMAIL, TELEGRAM, SMS, WHATSAPP |
| `recipient` | string | ✅ | Получатель (email/chat_id/телефон) |
| `subject` | string | для EMAIL | Тема письма |
| `message` | string | ✅ | Текст сообщения |
| `priority` | string | ❌ | Приоритет: HIGH, NORMAL (default), LOW |
| `templateId` | number | ❌ | ID шаблона |
| `params` | object | ❌ | Параметры для шаблона |
| `callbackUrl` | string | ❌ | URL для webhook при изменении статуса |

#### Пример запроса

```json
{
  "channel": "TELEGRAM",
  "recipient": "123456789",
  "message": "Привет, *{{name}}*! Ваш код: `{{code}}`",
  "priority": "HIGH",
  "params": {
    "name": "Алексей",
    "code": "1234"
  }
}
```

#### Ответ (HTTP 202 Accepted)

```json
{
  "notification_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "created_at": "2025-01-15T10:30:00Z"
}
```

### Проверка статуса

```http
GET /api/v1/status/{notification_id}
```

```json
{
  "id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "SENT",
  "channel": "EMAIL",
  "recipient": "user@example.com",
  "created_at": "2025-01-15T10:30:00Z",
  "sent_at": "2025-01-15T10:30:05Z"
}
```

### Статусы уведомлений

| Статус | Описание |
|--------|----------|
| `PENDING` | В очереди на отправку |
| `PROCESSING` | Обрабатывается |
| `SENT` | Успешно отправлено |
| `FAILED` | Ошибка отправки |
| `CANCELLED` | Отменено |

### Форматы получателей

| Канал | Формат | Пример |
|-------|--------|--------|
| EMAIL | Email адрес | `user@example.com` |
| TELEGRAM | Chat ID | `123456789` |
| SMS | Телефон (E.164) | `+79001234567` |
| WHATSAPP | Телефон (E.164) | `+79001234567` |

---

## 🏗️ Архитектура

### Общая схема

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   API Clients   │────▶│   Nginx Proxy   │────▶│  Spring Boot    │
│  (внеш. сист.)  │     │   (port 80)     │     │  (port 8080)    │
└─────────────────┘     └─────────────────┘     └────────┬────────┘
                                                         │
┌─────────────────┐     ┌─────────────────┐             │
│  React Admin    │────▶│   Nginx Proxy   │◀────────────┘
│  (port 3000)    │     │   (static)      │
└─────────────────┘     └─────────────────┘
                                                ┌────────┴────────┐
                        ┌─────────────────┐     │   PostgreSQL    │
                        │  Email/Telegram/│◀────│   (port 5432)   │
                        │  SMS/WhatsApp   │     └─────────────────┘
                        └─────────────────┘
```

### Структура проекта

```
notification/
├── 📁 backend/                      # Spring Boot приложение
│   ├── 📁 src/main/java/com/notification/
│   │   ├── 📁 config/               # Spring конфигурации
│   │   │   ├── SecurityConfig.java  # JWT, CORS, Security
│   │   │   ├── JwtAuthFilter.java   # JWT фильтр
│   │   │   └── AsyncConfig.java     # Асинхронная обработка
│   │   ├── 📁 controller/           # REST контроллеры
│   │   │   ├── NotificationController.java   # /api/v1/send
│   │   │   ├── AdminController.java          # /api/v1/admin/*
│   │   │   └── HealthController.java         # /api/v1/health
│   │   ├── 📁 domain/entity/        # JPA сущности
│   │   ├── 📁 dto/                  # Data Transfer Objects
│   │   ├── 📁 repository/           # JDBC репозитории
│   │   └── 📁 service/              # Бизнес-логика
│   │       ├── 📁 channel/          # Отправка по каналам
│   │       │   ├── EmailSender.java
│   │       │   ├── TelegramSender.java
│   │       │   ├── SmsSender.java
│   │       │   └── WhatsAppSender.java
│   │       ├── NotificationService.java
│   │       └── TemplateService.java
│   ├── 📁 resources/
│   │   ├── 📁 db/                   # SQL миграции
│   │   └── application.properties
│   ├── Dockerfile
│   └── build.gradle
│
├── 📁 frontend/                     # React приложение
│   ├── 📁 src/
│   │   ├── 📁 api/                  # API клиент (axios)
│   │   ├── 📁 components/           # React компоненты
│   │   │   └── 📁 common/           # Переиспользуемые компоненты
│   │   ├── 📁 hooks/                # React хуки
│   │   ├── 📁 layouts/              # Layouts
│   │   ├── 📁 pages/                # Страницы
│   │   ├── 📁 theme/                # Тема и стили
│   │   └── 📁 types/                # TypeScript типы
│   ├── Dockerfile
│   └── package.json
│
├── docker-compose.yml               # Оркестрация контейнеров
├── .env.example                     # Пример переменных окружения
└── README.md
```

### База данных

```sql
-- Основные таблицы
admins              -- Администраторы системы
api_clients         -- API клиенты (внешние системы)
channel_configs     -- Конфигурации каналов (SMTP, Telegram токены и т.д.)
notifications       -- Журнал уведомлений
message_templates   -- Шаблоны сообщений
audit_log          -- Аудит всех действий
```

---

## 💻 Разработка

### Локальный запуск

#### Backend

```bash
cd backend

# Запуск PostgreSQL через Docker
docker run -d \
  --name notification-postgres \
  -e POSTGRES_DB=notification_service \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  postgres:15-alpine

# Запуск приложения
./gradlew bootRun

# Или с Maven
./mvnw spring-boot:run
```

#### Frontend

```bash
cd frontend

# Установка зависимостей
npm install

# Запуск dev-сервера
npm run dev

# Сборка для production
npm run build
```

### Переменные окружения

Основные переменные в `.env`:

```bash
# База данных
POSTGRES_DB=notification_service
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your-secure-password

# JWT
JWT_SECRET=your-256-bit-secret-key

# Email (SMTP)
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-email@gmail.com
MAIL_PASSWORD=your-app-password

# Telegram
TELEGRAM_BOT_TOKEN=your-bot-token

# SMS (пример для SMSC)
SMS_API_URL=https://smsc.ru/rest
SMS_API_KEY=your-api-key
SMS_SENDER_ID=YourBrand

# WhatsApp Business API
WHATSAPP_PHONE_NUMBER_ID=your-phone-id
WHATSAPP_ACCESS_TOKEN=your-access-token
```

### Тестирование

```bash
# Backend тесты
cd backend
./gradlew test

# Frontend тесты
cd frontend
npm run test
```

---

## 🛠️ Технологический стек

### Backend

| Технология | Версия | Назначение |
|------------|--------|------------|
| Java | 17 | Язык программирования |
| Spring Boot | 3.2.0 | Основной фреймворк |
| Spring Security | 6.x | Безопасность, JWT |
| Spring JDBC | - | Доступ к данным (JdbcClient) |
| PostgreSQL | 15 | База данных |
| Lombok | - | Генерация boilerplate |
| Gradle | 8.x | Система сборки |

### Frontend

| Технология | Версия | Назначение |
|------------|--------|------------|
| React | 18.x | UI фреймворк |
| TypeScript | 5.x | Типизация |
| Vite | 5.x | Сборщик |
| Ant Design | 5.x | UI компоненты |
| React Router | 6.x | Роутинг |
| Axios | 1.x | HTTP клиент |

### Инфраструктура

| Технология | Назначение |
|------------|------------|
| Docker | Контейнеризация |
| Docker Compose | Оркестрация |
| Nginx | Reverse proxy, статика |

---

## 📋 API Endpoints

### Публичные

| Метод | Endpoint | Описание |
|-------|----------|----------|
| POST | `/api/v1/send` | Отправка уведомления |
| GET | `/api/v1/status/{id}` | Статус уведомления |
| GET | `/api/v1/health` | Health check |

### Административные (требуют JWT)

| Метод | Endpoint | Описание |
|-------|----------|----------|
| POST | `/api/v1/auth/login` | Авторизация |
| GET | `/api/v1/admin/clients` | Список API клиентов |
| POST | `/api/v1/admin/clients` | Создать клиента |
| GET | `/api/v1/admin/channels` | Конфигурации каналов |
| PUT | `/api/v1/admin/channels/{type}` | Обновить канал |
| GET | `/api/v1/admin/templates` | Список шаблонов |
| POST | `/api/v1/admin/templates` | Создать шаблон |
| GET | `/api/v1/admin/notifications` | История уведомлений |
| GET | `/api/v1/admin/dashboard/stats` | Статистика |
| GET | `/api/v1/admin/audit` | Журнал аудита |

---

## 🐛 Troubleshooting

### Контейнер backend не запускается

```bash
# Проверьте логи
docker-compose logs backend

# Убедитесь, что PostgreSQL готов
docker-compose logs postgres
```

### Ошибки подключения к БД

```bash
# Проверьте, что PostgreSQL запущен и доступен
docker-compose exec postgres pg_isready

# Проверьте credentials в .env файле
```

### CORS ошибки

Убедитесь, что в `application.properties` настроен правильный CORS:

```properties
cors.allowed-origins=http://localhost:3000,http://localhost:5173
```

---

## 📝 Лицензия

MIT License - подробности в файле [LICENSE](LICENSE)

---

<div align="center">

**Сделано с ❤️**

</div>
