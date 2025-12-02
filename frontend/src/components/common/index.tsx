/**
 * Notification Service - Общие компоненты
 * 
 * Переиспользуемые UI компоненты для админ-панели.
 * Обеспечивают единообразие интерфейса.
 */

import React from 'react'
import { 
  Card, 
  Spin, 
  Alert, 
  Button, 
  Tag, 
  Empty,
  Result,
  Typography,
  Popconfirm
} from 'antd'
import { 
  ReloadOutlined, 
  MailOutlined, 
  SendOutlined, 
  MobileOutlined, 
  MessageOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  SyncOutlined,
  StopOutlined,
} from '@ant-design/icons'
import type { ChannelType, NotificationStatus, Priority } from '../../types'
import { colors } from '../../theme'

const { Text } = Typography

// ============================================================================
// Loading States
// ============================================================================

interface PageLoaderProps {
  tip?: string
}

/**
 * Полноэкранный индикатор загрузки для страниц.
 */
export const PageLoader: React.FC<PageLoaderProps> = ({ tip = 'Загрузка...' }) => (
  <div style={{ 
    display: 'flex', 
    justifyContent: 'center', 
    alignItems: 'center',
    minHeight: 400,
    padding: 50,
  }}>
    <Spin size="large" tip={tip} />
  </div>
)

/**
 * Компактный индикатор загрузки для карточек.
 */
export const CardLoader: React.FC = () => (
  <Card>
    <div style={{ textAlign: 'center', padding: 40 }}>
      <Spin />
    </div>
  </Card>
)

// ============================================================================
// Error States
// ============================================================================

interface ErrorStateProps {
  message?: string
  description?: string
  onRetry?: () => void
}

/**
 * Компонент отображения ошибки с кнопкой повтора.
 */
export const ErrorState: React.FC<ErrorStateProps> = ({ 
  message = 'Произошла ошибка',
  description = 'Не удалось загрузить данные. Попробуйте еще раз.',
  onRetry,
}) => (
  <Alert
    type="error"
    message={message}
    description={description}
    showIcon
    action={
      onRetry && (
        <Button size="small" onClick={onRetry} icon={<ReloadOutlined />}>
          Повторить
        </Button>
      )
    }
  />
)

/**
 * Полноэкранное состояние ошибки.
 */
export const ErrorPage: React.FC<ErrorStateProps> = ({
  message = 'Ошибка загрузки',
  description = 'Не удалось загрузить страницу',
  onRetry,
}) => (
  <Result
    status="error"
    title={message}
    subTitle={description}
    extra={
      onRetry && (
        <Button type="primary" onClick={onRetry} icon={<ReloadOutlined />}>
          Попробовать снова
        </Button>
      )
    }
  />
)

// ============================================================================
// Empty States
// ============================================================================

interface EmptyStateProps {
  description?: string
  children?: React.ReactNode
}

/**
 * Компонент для пустых списков.
 */
export const EmptyState: React.FC<EmptyStateProps> = ({ 
  description = 'Данные отсутствуют',
  children,
}) => (
  <Empty
    image={Empty.PRESENTED_IMAGE_SIMPLE}
    description={description}
  >
    {children}
  </Empty>
)

// ============================================================================
// Channel Components
// ============================================================================

const channelIcons: Record<ChannelType, React.ReactNode> = {
  EMAIL: <MailOutlined />,
  TELEGRAM: <SendOutlined />,
  SMS: <MobileOutlined />,
  WHATSAPP: <MessageOutlined />,
}

const channelLabels: Record<ChannelType, string> = {
  EMAIL: 'Email',
  TELEGRAM: 'Telegram',
  SMS: 'SMS',
  WHATSAPP: 'WhatsApp',
}

interface ChannelTagProps {
  channel: ChannelType
  showIcon?: boolean
}

/**
 * Тег для отображения канала с иконкой и цветом.
 */
export const ChannelTag: React.FC<ChannelTagProps> = ({ channel, showIcon = true }) => (
  <Tag 
    color={colors.channels[channel]} 
    icon={showIcon ? channelIcons[channel] : undefined}
  >
    {channelLabels[channel]}
  </Tag>
)

// ============================================================================
// Status Components
// ============================================================================

const statusIcons: Record<NotificationStatus, React.ReactNode> = {
  PENDING: <ClockCircleOutlined />,
  PROCESSING: <SyncOutlined spin />,
  SENT: <CheckCircleOutlined />,
  DELIVERED: <CheckCircleOutlined />,
  FAILED: <CloseCircleOutlined />,
  CANCELLED: <StopOutlined />,
}

const statusLabels: Record<NotificationStatus, string> = {
  PENDING: 'Ожидает',
  PROCESSING: 'Отправляется',
  SENT: 'Отправлено',
  DELIVERED: 'Доставлено',
  FAILED: 'Ошибка',
  CANCELLED: 'Отменено',
}

const statusColors: Record<NotificationStatus, string> = {
  PENDING: 'default',
  PROCESSING: 'processing',
  SENT: 'success',
  DELIVERED: 'success',
  FAILED: 'error',
  CANCELLED: 'warning',
}

interface StatusTagProps {
  status: NotificationStatus
  showIcon?: boolean
}

/**
 * Тег для отображения статуса уведомления.
 */
export const StatusTag: React.FC<StatusTagProps> = ({ status, showIcon = true }) => (
  <Tag 
    color={statusColors[status]} 
    icon={showIcon ? statusIcons[status] : undefined}
  >
    {statusLabels[status]}
  </Tag>
)

// ============================================================================
// Priority Components
// ============================================================================

const priorityLabels: Record<Priority, string> = {
  LOW: 'Низкий',
  NORMAL: 'Обычный',
  HIGH: 'Высокий',
  URGENT: 'Срочный',
}

interface PriorityTagProps {
  priority: Priority
}

/**
 * Тег для отображения приоритета.
 */
export const PriorityTag: React.FC<PriorityTagProps> = ({ priority }) => (
  <Tag color={colors.priorities[priority]}>
    {priorityLabels[priority]}
  </Tag>
)

// ============================================================================
// Page Header
// ============================================================================

interface PageHeaderProps {
  title: string
  subtitle?: string
  extra?: React.ReactNode
}

/**
 * Заголовок страницы с опциональным подзаголовком и действиями.
 */
export const PageHeader: React.FC<PageHeaderProps> = ({ title, subtitle, extra }) => (
  <div style={{ 
    display: 'flex', 
    justifyContent: 'space-between', 
    alignItems: 'flex-start',
    marginBottom: 24,
  }}>
    <div>
      <Typography.Title level={4} style={{ margin: 0 }}>
        {title}
      </Typography.Title>
      {subtitle && (
        <Text type="secondary" style={{ marginTop: 4, display: 'block' }}>
          {subtitle}
        </Text>
      )}
    </div>
    {extra && <div>{extra}</div>}
  </div>
)

// ============================================================================
// Stats Card
// ============================================================================

interface StatsCardProps {
  title: string
  value: number | string
  icon?: React.ReactNode
  color?: string
  suffix?: string
  loading?: boolean
}

/**
 * Карточка статистики для дашборда.
 */
export const StatsCard: React.FC<StatsCardProps> = ({
  title,
  value,
  icon,
  color = colors.primary,
  suffix,
  loading,
}) => (
  <Card>
    {loading ? (
      <div style={{ textAlign: 'center', padding: 20 }}>
        <Spin size="small" />
      </div>
    ) : (
      <div style={{ display: 'flex', alignItems: 'center' }}>
        {icon && (
          <div style={{ 
            fontSize: 32, 
            color, 
            marginRight: 16,
            opacity: 0.85,
          }}>
            {icon}
          </div>
        )}
        <div>
          <Text type="secondary" style={{ fontSize: 14 }}>{title}</Text>
          <div style={{ 
            fontSize: 28, 
            fontWeight: 600, 
            lineHeight: 1.2,
            color: colors.text.primary,
          }}>
            {value}
            {suffix && <span style={{ fontSize: 14, fontWeight: 400 }}> {suffix}</span>}
          </div>
        </div>
      </div>
    )}
  </Card>
)

// ============================================================================
// Confirmation Dialog
// ============================================================================

interface ConfirmButtonProps {
  onConfirm: () => void
  title?: string
  description?: string
  okText?: string
  cancelText?: string
  danger?: boolean
  children: React.ReactNode
  loading?: boolean
  disabled?: boolean
}

/**
 * Кнопка с подтверждением действия через Popconfirm.
 */
export const ConfirmButton: React.FC<ConfirmButtonProps> = ({
  onConfirm,
  title = 'Подтверждение',
  description = 'Вы уверены?',
  okText = 'Да',
  cancelText = 'Отмена',
  danger = false,
  children,
  loading,
  disabled,
}) => {
  return (
    <Popconfirm
      title={title}
      description={description}
      okText={okText}
      cancelText={cancelText}
      onConfirm={onConfirm}
      okButtonProps={{ danger }}
    >
      <Button 
        danger={danger}
        loading={loading}
        disabled={disabled}
      >
        {children}
      </Button>
    </Popconfirm>
  )
}

// ============================================================================
// Export
// ============================================================================

export default {
  PageLoader,
  CardLoader,
  ErrorState,
  ErrorPage,
  EmptyState,
  ChannelTag,
  StatusTag,
  PriorityTag,
  PageHeader,
  StatsCard,
}
