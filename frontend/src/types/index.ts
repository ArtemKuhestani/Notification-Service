/**
 * Notification Service - Типы данных
 * 
 * Централизованное хранение всех TypeScript типов и интерфейсов.
 * Обеспечивает строгую типизацию и автодополнение в IDE.
 */

// ============================================================================
// Пользователь и аутентификация
// ============================================================================

/** Информация о текущем пользователе */
export interface User {
  id: number
  email: string
  fullName: string
  role: 'ADMIN' | 'OPERATOR' | 'VIEWER'
}

/** Ответ на успешную аутентификацию */
export interface AuthResponse {
  accessToken: string
  refreshToken: string
  expiresIn: number
  user: User
}

// ============================================================================
// Уведомления
// ============================================================================

/** Каналы отправки */
export type ChannelType = 'EMAIL' | 'TELEGRAM' | 'SMS' | 'WHATSAPP'

/** Приоритеты уведомлений */
export type Priority = 'LOW' | 'NORMAL' | 'HIGH' | 'URGENT'

/** Статусы уведомлений */
export type NotificationStatus = 
  | 'PENDING'      // Ожидает отправки
  | 'PROCESSING'   // В процессе отправки
  | 'SENT'         // Успешно отправлено
  | 'DELIVERED'    // Доставлено (подтверждено)
  | 'FAILED'       // Ошибка отправки
  | 'CANCELLED'    // Отменено

/** Уведомление */
export interface Notification {
  id: string
  clientId: number
  channel: ChannelType
  recipient: string
  subject?: string
  status: NotificationStatus
  priority: Priority
  retryCount: number
  errorMessage?: string
  errorCode?: string
  providerMessageId?: string
  createdAt: string
  sentAt?: string
}

/** Запрос на отправку уведомления */
export interface SendNotificationRequest {
  channel: ChannelType
  recipient: string
  subject?: string
  message: string
  priority?: Priority
  templateCode?: string
  templateVariables?: Record<string, string>
  idempotencyKey?: string
  callbackUrl?: string
  metadata?: Record<string, unknown>
}

// ============================================================================
// API Клиенты
// ============================================================================

/** API клиент */
export interface ApiClient {
  id: number
  clientName: string
  apiKey: string
  description?: string
  active: boolean
  rateLimit: number
  callbackUrl?: string
  createdAt: string
  lastUsedAt?: string
}

/** Запрос на создание/обновление клиента */
export interface ApiClientRequest {
  clientName: string
  description?: string
  rateLimit?: number
  callbackUrl?: string
}

// ============================================================================
// Конфигурация каналов
// ============================================================================

/** Конфигурация канала */
export interface ChannelConfig {
  id: number
  channelType: ChannelType
  settings: Record<string, unknown>
  enabled: boolean
  createdAt: string
  updatedAt: string
}

/** Настройки Email канала */
export interface EmailSettings {
  host: string
  port: number
  username?: string
  password?: string
  fromEmail?: string
  fromName?: string
  useTls?: boolean
}

/** Настройки Telegram канала */
export interface TelegramSettings {
  botToken: string
  parseMode?: 'Markdown' | 'MarkdownV2' | 'HTML'
}

/** Настройки SMS канала */
export interface SmsSettings {
  apiUrl: string
  apiKey: string
  senderId?: string
}

/** Настройки WhatsApp канала */
export interface WhatsAppSettings {
  phoneNumberId: string
  accessToken: string
  businessAccountId?: string
}

// ============================================================================
// Шаблоны
// ============================================================================

/** Шаблон сообщения */
export interface Template {
  id: number
  code: string
  name: string
  channelType: ChannelType
  subject?: string
  body: string
  variables: string[]
  isActive: boolean
  createdAt: string
  updatedAt: string
}

/** Запрос на создание/обновление шаблона */
export interface TemplateRequest {
  code: string
  name: string
  channelType: ChannelType
  subject?: string
  body: string
  variables?: string
  active?: boolean
}

// ============================================================================
// Аудит
// ============================================================================

/** Запись аудит-лога */
export interface AuditLog {
  id: number
  action: string
  entityType: string
  entityId?: string
  oldValue?: string
  newValue?: string
  adminId?: number
  adminEmail?: string
  ipAddress?: string
  userAgent?: string
  createdAt: string
}

// ============================================================================
// Статистика дашборда
// ============================================================================

/** Статистика по каналу */
export interface ChannelStats {
  channel: ChannelType
  total: number
  sent: number
  failed: number
}

/** Ежедневная статистика */
export interface DailyStats {
  date: string
  total: number
  sent: number
  failed: number
}

/** Общая статистика дашборда */
export interface DashboardStats {
  totalNotifications: number
  sentCount: number
  failedCount: number
  pendingCount: number
  processingCount: number
  todayTotal: number
  todaySent: number
  todayFailed: number
  activeClients: number
  activeChannels: number
  channelStats: ChannelStats[]
  dailyStats: DailyStats[]
}

// ============================================================================
// Пагинация
// ============================================================================

/** Пагинированный ответ */
export interface PagedResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

// ============================================================================
// API ошибки
// ============================================================================

/** Ошибка API */
export interface ApiError {
  message: string
  code?: string
  details?: Record<string, string>
  timestamp?: string
  path?: string
}

// ============================================================================
// Утилитарные типы
// ============================================================================

/** Опции для select-компонентов */
export interface SelectOption<T = string> {
  value: T
  label: string
  disabled?: boolean
}

/** Метаданные канала для UI */
export interface ChannelMeta {
  type: ChannelType
  displayName: string
  description: string
  color: string
  icon: string
}

/** Константы для каналов */
export const CHANNEL_META: Record<ChannelType, Omit<ChannelMeta, 'type'>> = {
  EMAIL: {
    displayName: 'Email',
    description: 'Отправка через SMTP сервер',
    color: 'blue',
    icon: 'mail',
  },
  TELEGRAM: {
    displayName: 'Telegram',
    description: 'Отправка через Telegram Bot API',
    color: 'cyan',
    icon: 'send',
  },
  SMS: {
    displayName: 'SMS',
    description: 'Отправка через SMS провайдера',
    color: 'orange',
    icon: 'mobile',
  },
  WHATSAPP: {
    displayName: 'WhatsApp',
    description: 'Отправка через WhatsApp Business API',
    color: 'green',
    icon: 'message',
  },
}

/** Константы для статусов */
export const STATUS_META: Record<NotificationStatus, { label: string; color: string }> = {
  PENDING: { label: 'Ожидает', color: 'default' },
  PROCESSING: { label: 'Отправляется', color: 'processing' },
  SENT: { label: 'Отправлено', color: 'success' },
  DELIVERED: { label: 'Доставлено', color: 'success' },
  FAILED: { label: 'Ошибка', color: 'error' },
  CANCELLED: { label: 'Отменено', color: 'warning' },
}

/** Константы для приоритетов */
export const PRIORITY_META: Record<Priority, { label: string; color: string }> = {
  LOW: { label: 'Низкий', color: 'default' },
  NORMAL: { label: 'Обычный', color: 'blue' },
  HIGH: { label: 'Высокий', color: 'orange' },
  URGENT: { label: 'Срочный', color: 'red' },
}
