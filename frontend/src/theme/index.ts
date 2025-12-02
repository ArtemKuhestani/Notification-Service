/**
 * Notification Service - Тема и стили
 * 
 * Централизованная конфигурация темы Ant Design.
 * Определяет цветовую палитру, типографику и компоненты.
 */

import type { ThemeConfig } from 'antd'

/**
 * Цветовая палитра приложения
 */
export const colors = {
  // Основные цвета
  primary: '#1890ff',
  success: '#52c41a',
  warning: '#faad14',
  error: '#ff4d4f',
  info: '#1890ff',
  
  // Цвета каналов
  channels: {
    EMAIL: '#1890ff',
    TELEGRAM: '#0088cc',
    SMS: '#fa8c16',
    WHATSAPP: '#25d366',
    email: '#1890ff',
    telegram: '#0088cc',
    sms: '#fa8c16',
    whatsapp: '#25d366',
  },
  
  // Цвета статусов
  statuses: {
    PENDING: '#8c8c8c',
    PROCESSING: '#1890ff',
    SENT: '#52c41a',
    DELIVERED: '#52c41a',
    FAILED: '#ff4d4f',
    CANCELLED: '#faad14',
  },
  
  // Цвета приоритетов
  priorities: {
    LOW: '#8c8c8c',
    NORMAL: '#1890ff',
    HIGH: '#fa8c16',
    URGENT: '#ff4d4f',
  },
  
  // Нейтральные
  background: '#f0f2f5',
  cardBackground: '#ffffff',
  border: '#d9d9d9',
  bgGrey: '#f5f5f5',
  
  // Текст (короткие алиасы)
  textPrimary: 'rgba(0, 0, 0, 0.88)',
  textSecondary: 'rgba(0, 0, 0, 0.65)',
  textTertiary: 'rgba(0, 0, 0, 0.45)',
  
  // Текст (объект для совместимости)
  text: {
    primary: 'rgba(0, 0, 0, 0.88)',
    secondary: 'rgba(0, 0, 0, 0.65)',
    tertiary: 'rgba(0, 0, 0, 0.45)',
  },
} as const

/**
 * Конфигурация темы Ant Design
 */
export const antTheme: ThemeConfig = {
  token: {
    // Основные цвета
    colorPrimary: colors.primary,
    colorSuccess: colors.success,
    colorWarning: colors.warning,
    colorError: colors.error,
    colorInfo: colors.info,
    
    // Типографика
    fontFamily: `-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif`,
    fontSize: 14,
    
    // Скругления
    borderRadius: 6,
    borderRadiusLG: 8,
    borderRadiusSM: 4,
    
    // Отступы
    padding: 16,
    paddingLG: 24,
    paddingSM: 12,
    paddingXS: 8,
    
    // Тени
    boxShadow: '0 1px 2px 0 rgba(0, 0, 0, 0.03), 0 1px 6px -1px rgba(0, 0, 0, 0.02), 0 2px 4px 0 rgba(0, 0, 0, 0.02)',
    boxShadowSecondary: '0 6px 16px 0 rgba(0, 0, 0, 0.08), 0 3px 6px -4px rgba(0, 0, 0, 0.12), 0 9px 28px 8px rgba(0, 0, 0, 0.05)',
  },
  
  components: {
    // Карточки
    Card: {
      headerBg: 'transparent',
      paddingLG: 24,
    },
    
    // Таблицы
    Table: {
      headerBg: '#fafafa',
      rowHoverBg: '#fafafa',
    },
    
    // Кнопки
    Button: {
      primaryShadow: '0 2px 0 rgba(5, 145, 255, 0.1)',
    },
    
    // Меню
    Menu: {
      itemBg: 'transparent',
      subMenuItemBg: 'transparent',
    },
    
    // Модальные окна
    Modal: {
      titleFontSize: 18,
    },
    
    // Уведомления
    Message: {
      contentPadding: 12,
    },
  },
}

/**
 * CSS переменные для использования в styled-components или inline стилях
 */
export const cssVariables = `
  :root {
    --color-primary: ${colors.primary};
    --color-success: ${colors.success};
    --color-warning: ${colors.warning};
    --color-error: ${colors.error};
    --color-background: ${colors.background};
    --color-card-bg: ${colors.cardBackground};
    --color-border: ${colors.border};
    --color-text-primary: ${colors.text.primary};
    --color-text-secondary: ${colors.text.secondary};
    --color-text-tertiary: ${colors.text.tertiary};
    
    --channel-email: ${colors.channels.EMAIL};
    --channel-telegram: ${colors.channels.TELEGRAM};
    --channel-sms: ${colors.channels.SMS};
    --channel-whatsapp: ${colors.channels.WHATSAPP};
    
    --border-radius: 6px;
    --border-radius-lg: 8px;
    --border-radius-sm: 4px;
    
    --spacing-xs: 4px;
    --spacing-sm: 8px;
    --spacing-md: 16px;
    --spacing-lg: 24px;
    --spacing-xl: 32px;
    
    --shadow-sm: 0 1px 2px 0 rgba(0, 0, 0, 0.05);
    --shadow-md: 0 4px 6px -1px rgba(0, 0, 0, 0.1);
    --shadow-lg: 0 10px 15px -3px rgba(0, 0, 0, 0.1);
  }
`

// Алиас для обратной совместимости
export const theme = antTheme

export default antTheme
