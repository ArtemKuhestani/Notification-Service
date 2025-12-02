/**
 * Notification Service - API Client
 * 
 * Централизованный модуль для работы с REST API.
 * Использует axios с автоматическим обновлением токенов.
 * 
 * @module api
 */

import axios, { AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios'

// ============================================================================
// Конфигурация
// ============================================================================

const API_BASE_URL = import.meta.env.VITE_API_URL || '/api/v1'

/** Ключи для localStorage */
const STORAGE_KEYS = {
  ACCESS_TOKEN: 'auth_token',
  REFRESH_TOKEN: 'refresh_token',
  USER: 'auth_user',
} as const

// ============================================================================
// Создание axios instance
// ============================================================================

/**
 * Основной axios instance с преднастроенными interceptors.
 * Автоматически добавляет токен авторизации к запросам.
 */
const api: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

// ============================================================================
// Request Interceptor
// ============================================================================

api.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error: AxiosError) => Promise.reject(error)
)

// ============================================================================
// Response Interceptor
// ============================================================================

/**
 * Интерцептор ответов с автоматическим обновлением токена.
 * При получении 401 пытается обновить токен через refresh endpoint.
 */
api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean }

    // Если 401 и это не повторный запрос - пробуем обновить токен
    if (error.response?.status === 401 && !originalRequest._retry) {
      originalRequest._retry = true

      const refreshToken = localStorage.getItem(STORAGE_KEYS.REFRESH_TOKEN)
      if (refreshToken) {
        try {
          const response = await axios.post(`${API_BASE_URL}/auth/refresh`, { refreshToken })
          const { accessToken } = response.data

          localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken)
          
          if (originalRequest.headers) {
            originalRequest.headers.Authorization = `Bearer ${accessToken}`
          }
          
          return api(originalRequest)
        } catch {
          // Refresh не удался - очищаем токены
          clearAuthData()
        }
      } else {
        clearAuthData()
      }

      // Редирект на страницу входа
      window.location.href = '/login'
    }

    return Promise.reject(error)
  }
)

/**
 * Очистка данных аутентификации из localStorage.
 */
function clearAuthData(): void {
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.USER)
}

// ============================================================================
// Auth API
// ============================================================================

export const authApi = {
  /** Вход в систему */
  login: (email: string, password: string) => 
    api.post('/auth/login', { email, password }),
  
  /** Выход из системы */
  logout: () => api.post('/auth/logout'),
  
  /** Обновление токена */
  refresh: (refreshToken: string) => 
    api.post('/auth/refresh', { refreshToken }),
  
  /** Получение информации о текущем пользователе */
  me: () => api.get('/auth/me'),
}

// ============================================================================
// Notifications API
// ============================================================================

export const notificationApi = {
  /** Отправить уведомление (публичный API) */
  send: (data: {
    channel: string
    recipient: string
    subject?: string
    message: string
    priority?: string
    templateCode?: string
    templateVariables?: Record<string, string>
    idempotencyKey?: string
    callbackUrl?: string
    metadata?: Record<string, unknown>
  }) => api.post('/send', data),

  /** Получить статус уведомления */
  getStatus: (id: string) => api.get(`/status/${id}`),

  /** [Admin] Получить список уведомлений с фильтрацией */
  getAll: (params?: {
    status?: string
    channel?: string
    clientId?: number
    from?: string
    to?: string
    page?: number
    size?: number
  }) => api.get('/admin/notifications', { params }),

  /** [Admin] Получить детали уведомления */
  getById: (id: string) => api.get(`/admin/notifications/${id}`),

  /** [Admin] Повторить отправку уведомления */
  retry: (id: string) => api.post(`/admin/notifications/${id}/retry`),

  /** [Admin] Отменить уведомление */
  cancel: (id: string) => api.post(`/admin/notifications/${id}/cancel`),

  /** [Admin] Тестовая отправка */
  testSend: (data: {
    channel: string
    recipient: string
    subject?: string
    message: string
  }) => api.post('/admin/notifications/test', data),
}

// ============================================================================
// API Clients
// ============================================================================

export const clientApi = {
  /** Получить список всех клиентов */
  getAll: () => api.get('/admin/clients'),
  
  /** Получить клиента по ID */
  getById: (id: number) => api.get(`/admin/clients/${id}`),
  
  /** Создать нового клиента */
  create: (data: { 
    name: string
    description?: string
    rateLimit?: number
    callbackUrl?: string 
  }) => api.post('/admin/clients', data),
  
  /** Обновить клиента */
  update: (id: number, data: { 
    description?: string
    rateLimit?: number
    callbackUrl?: string 
  }) => api.put(`/admin/clients/${id}`, data),
  
  /** Удалить клиента */
  delete: (id: number) => api.delete(`/admin/clients/${id}`),
  
  /** Активировать/деактивировать клиента */
  toggleActive: (id: number, active: boolean) => 
    api.patch(`/admin/clients/${id}/active`, { active }),
  
  /** Перегенерировать API ключ */
  regenerateKey: (id: number) => 
    api.post(`/admin/clients/${id}/regenerate-key`),
}

// ============================================================================
// Channel Configs
// ============================================================================

export const channelApi = {
  /** Получить все конфигурации каналов */
  getAll: () => api.get('/admin/channels'),
  
  /** Получить конфигурацию канала по типу */
  getByType: (type: string) => api.get(`/admin/channels/${type}`),
  
  /** Обновить настройки канала */
  update: (type: string, data: {
    settings?: Record<string, unknown>
    enabled?: boolean
    priority?: number
  }) => api.put(`/admin/channels/${type}`, data),
  
  /** Включить/выключить канал */
  toggle: (type: string, enabled: boolean) => 
    api.patch(`/admin/channels/${type}/toggle`, { enabled }),
  
  /** Тестировать подключение к каналу */
  test: (type: string) => 
    api.post(`/admin/channels/${type}/test`),
}

// ============================================================================
// Templates
// ============================================================================

export const templateApi = {
  /** Получить список шаблонов */
  getAll: (params?: { channel?: string; page?: number; size?: number }) => 
    api.get('/admin/templates', { params }),
  
  /** Получить шаблон по ID */
  getById: (id: number) => api.get(`/admin/templates/${id}`),
  
  /** Получить шаблон по коду */
  getByCode: (code: string) => api.get(`/admin/templates/code/${code}`),
  
  /** Создать шаблон */
  create: (data: {
    code: string
    name: string
    channel: string
    subjectTemplate?: string
    bodyTemplate: string
    variables?: string
    active?: boolean
  }) => api.post('/admin/templates', data),
  
  /** Обновить шаблон */
  update: (id: number, data: {
    code?: string
    name?: string
    channel?: string
    subjectTemplate?: string
    bodyTemplate?: string
    variables?: string
    active?: boolean
  }) => api.put(`/admin/templates/${id}`, data),
  
  /** Удалить шаблон */
  delete: (id: number) => api.delete(`/admin/templates/${id}`),
  
  /** Переключить активность шаблона */
  toggle: (id: number) => api.patch(`/admin/templates/${id}/toggle`),
  
  /** Предпросмотр шаблона с переменными */
  preview: (id: number, variables: Record<string, string>) => 
    api.post(`/admin/templates/${id}/preview`, { variables }),
}

// ============================================================================
// Dashboard & Stats
// ============================================================================

export const dashboardApi = {
  /** Получить общую статистику */
  getStats: () => api.get('/admin/dashboard/stats'),
  
  /** Получить статистику по каналам */
  getChannelStats: (from?: string, to?: string) => 
    api.get('/admin/dashboard/stats/channels', { params: { from, to } }),
  
  /** Получить статистику по клиентам */
  getClientStats: (from?: string, to?: string) => 
    api.get('/admin/dashboard/stats/clients', { params: { from, to } }),
  
  /** Получить почасовую статистику */
  getHourlyStats: (date?: string) => 
    api.get('/admin/dashboard/stats/hourly', { params: { date } }),
}

// ============================================================================
// Audit Log
// ============================================================================

export const auditApi = {
  /** Получить записи аудит-лога */
  getAll: (params?: {
    action?: string
    entityType?: string
    from?: string
    to?: string
    page?: number
    size?: number
  }) => api.get('/admin/audit', { params }),
  
  /** Получить список типов действий */
  getActions: () => api.get('/admin/audit/actions'),
}

// ============================================================================
// Health & Metrics
// ============================================================================

export const healthApi = {
  /** Проверка состояния сервиса */
  check: () => api.get('/health'),
  
  /** Детальная проверка состояния */
  detailed: () => api.get('/health/detailed'),
}

// ============================================================================
// Export
// ============================================================================

export default api
