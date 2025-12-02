/**
 * @file Pages Barrel Export
 * @description Централизованный экспорт всех страниц приложения.
 * Упрощает импорты и обеспечивает единую точку входа для всех страниц.
 * 
 * @example
 * // Вместо
 * import Dashboard from '../pages/Dashboard'
 * import Notifications from '../pages/Notifications'
 * 
 * // Используем
 * import { Dashboard, Notifications } from '../pages'
 */

// ============================================================================
// Страницы авторизации
// ============================================================================

export { default as Login } from './Login'

// ============================================================================
// Основные страницы
// ============================================================================

export { default as Dashboard } from './Dashboard'
export { default as Notifications } from './Notifications'
export { default as Templates } from './Templates'

// ============================================================================
// Страницы администрирования
// ============================================================================

export { default as ApiClients } from './ApiClients'
export { default as Channels } from './Channels'
export { default as Audit } from './Audit'

// ============================================================================
// Утилитарные страницы
// ============================================================================

export { default as TestSend } from './TestSend'
