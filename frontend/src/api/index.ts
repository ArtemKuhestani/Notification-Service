/**
 * API Module Re-exports
 * 
 * еэкспортирует все API-функции из client.ts для обратной совместимости.
 * овый код должен импортировать напрямую из './api/client'.
 * 
 * @deprecated спользуйте прямой импорт из './api/client'
 */

export {
  default,
  authApi,
  notificationApi,
  clientApi,
  channelApi,
  templateApi,
  dashboardApi,
  auditApi,
  healthApi,
} from './client'