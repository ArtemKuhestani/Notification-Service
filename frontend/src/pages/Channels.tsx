/**
 * Channels Page
 * 
 * Страница настройки каналов отправки уведомлений.
 * Позволяет управлять Email, Telegram, SMS и WhatsApp каналами.
 * 
 * @features
 * - Настройка учетных данных для каждого канала
 * - Включение/выключение каналов
 * - Тестирование подключения
 * - Безопасное хранение паролей (маскирование)
 */

import { useState, useEffect } from 'react'
import {
  Card,
  Row,
  Col,
  Form,
  Input,
  Switch,
  Button,
  Typography,
  Tag,
  message,
  Divider,
  InputNumber,
  Space,
  Alert,
  Tooltip,
  Badge,
} from 'antd'
import {
  MailOutlined,
  SendOutlined,
  MessageOutlined,
  MobileOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SaveOutlined,
  ExperimentOutlined,
  ReloadOutlined,
  InfoCircleOutlined,
  LockOutlined,
} from '@ant-design/icons'
import api from '../api/client'
import { PageLoader, PageHeader, ErrorState } from '../components/common'
import { colors } from '../theme'
import type { ChannelConfig, ChannelType } from '../types'

const { Text } = Typography

/** Метаинформация о канале */
interface ChannelMeta {
  displayName: string
  icon: React.ReactNode
  description: string
  color: string
}

/** Словарь метаинформации для всех типов каналов */
const CHANNEL_META: Record<ChannelType, ChannelMeta> = {
  EMAIL: { 
    displayName: 'Email (SMTP)', 
    icon: <MailOutlined />,
    description: 'Отправка email через SMTP сервер',
    color: colors.channels.email,
  },
  TELEGRAM: { 
    displayName: 'Telegram Bot', 
    icon: <SendOutlined />,
    description: 'Отправка сообщений через Telegram Bot API',
    color: colors.channels.telegram,
  },
  SMS: { 
    displayName: 'SMS Gateway', 
    icon: <MobileOutlined />,
    description: 'Отправка SMS через провайдера',
    color: colors.channels.sms,
  },
  WHATSAPP: { 
    displayName: 'WhatsApp Business', 
    icon: <MessageOutlined />,
    description: 'Отправка через WhatsApp Business API',
    color: colors.channels.whatsapp,
  },
}

/**
 * Компонент формы настройки Email канала
 */
function EmailSettingsForm({ form }: { form: ReturnType<typeof Form.useForm>[0] }) {
  return (
    <Form form={form} layout="vertical" size="small">
      <Row gutter={16}>
        <Col span={16}>
          <Form.Item 
            label="SMTP Хост" 
            name="host" 
            rules={[{ required: true, message: 'Укажите SMTP хост' }]}
            tooltip="Адрес SMTP сервера"
          >
            <Input placeholder="smtp.gmail.com" />
          </Form.Item>
        </Col>
        <Col span={8}>
          <Form.Item 
            label="Порт" 
            name="port" 
            rules={[{ required: true, message: 'Укажите порт' }]}
            tooltip="Обычно 587 (TLS) или 465 (SSL)"
          >
            <InputNumber style={{ width: '100%' }} min={1} max={65535} placeholder="587" />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item label="Пользователь" name="username">
            <Input prefix={<MailOutlined />} placeholder="user@gmail.com" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item label="Пароль" name="password">
            <Input.Password prefix={<LockOutlined />} placeholder="Пароль или app password" />
          </Form.Item>
        </Col>
      </Row>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item 
            label="Email отправителя" 
            name="fromEmail"
            tooltip="Адрес, от которого будут отправляться письма"
          >
            <Input placeholder="noreply@example.com" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item 
            label="Имя отправителя" 
            name="fromName"
            tooltip="Отображаемое имя в письмах"
          >
            <Input placeholder="Notification Service" />
          </Form.Item>
        </Col>
      </Row>
      <Form.Item 
        label="Использовать TLS" 
        name="useTls" 
        valuePropName="checked"
        tooltip="Рекомендуется включить для безопасности"
      >
        <Switch checkedChildren="Да" unCheckedChildren="Нет" />
      </Form.Item>
    </Form>
  )
}

/**
 * Компонент формы настройки Telegram канала
 */
function TelegramSettingsForm({ form }: { form: ReturnType<typeof Form.useForm>[0] }) {
  return (
    <Form form={form} layout="vertical" size="small">
      <Form.Item 
        label="Bot Token" 
        name="botToken"
        rules={[{ required: true, message: 'Введите токен бота' }]}
        extra={
          <span style={{ fontSize: 12 }}>
            Получите токен у{' '}
            <a href="https://t.me/BotFather" target="_blank" rel="noopener noreferrer">
              @BotFather
            </a>
          </span>
        }
      >
        <Input.Password prefix={<LockOutlined />} placeholder="123456789:ABCdefGHI..." />
      </Form.Item>
      <Form.Item 
        label="Parse Mode" 
        name="parseMode"
        tooltip="Формат разметки сообщений"
      >
        <Input placeholder="Markdown или HTML" />
      </Form.Item>
      <Alert
        type="info"
        showIcon
        icon={<InfoCircleOutlined />}
        message="Важно"
        description="Пользователь должен написать /start вашему боту, прежде чем бот сможет отправлять ему сообщения."
        style={{ marginTop: 8 }}
      />
    </Form>
  )
}

/**
 * Компонент формы настройки SMS канала
 */
function SmsSettingsForm({ form }: { form: ReturnType<typeof Form.useForm>[0] }) {
  return (
    <Form form={form} layout="vertical" size="small">
      <Form.Item 
        label="API URL" 
        name="apiUrl" 
        rules={[{ required: true, message: 'Укажите URL API' }]}
        tooltip="URL-адрес API провайдера SMS"
      >
        <Input placeholder="https://api.smsprovider.com/send" />
      </Form.Item>
      <Row gutter={16}>
        <Col span={12}>
          <Form.Item 
            label="API Key" 
            name="apiKey" 
            rules={[{ required: true, message: 'Введите API ключ' }]}
          >
            <Input.Password prefix={<LockOutlined />} placeholder="API ключ" />
          </Form.Item>
        </Col>
        <Col span={12}>
          <Form.Item 
            label="Sender ID" 
            name="senderId"
            tooltip="Имя отправителя (если поддерживается)"
          >
            <Input placeholder="MyCompany" />
          </Form.Item>
        </Col>
      </Row>
    </Form>
  )
}

/**
 * Компонент формы настройки WhatsApp канала
 */
function WhatsAppSettingsForm({ form }: { form: ReturnType<typeof Form.useForm>[0] }) {
  return (
    <Form form={form} layout="vertical" size="small">
      <Form.Item 
        label="Phone Number ID" 
        name="phoneNumberId"
        rules={[{ required: true, message: 'Введите Phone Number ID' }]}
        tooltip="ID номера телефона из Meta Business Suite"
      >
        <Input placeholder="123456789012345" />
      </Form.Item>
      <Form.Item 
        label="Access Token" 
        name="accessToken"
        rules={[{ required: true, message: 'Введите Access Token' }]}
      >
        <Input.Password prefix={<LockOutlined />} placeholder="Access Token" />
      </Form.Item>
      <Form.Item 
        label="Business Account ID" 
        name="businessAccountId"
        tooltip="ID бизнес-аккаунта (опционально)"
      >
        <Input placeholder="987654321098765" />
      </Form.Item>
    </Form>
  )
}

/**
 * Компонент карточки настройки канала
 */
function ChannelCard({ 
  channel, 
  onRefresh 
}: { 
  channel: ChannelConfig
  onRefresh: () => void 
}) {
  const [form] = Form.useForm()
  const [saving, setSaving] = useState(false)
  const [testing, setTesting] = useState(false)
  const [toggling, setToggling] = useState(false)
  
  const channelType = channel.channelType as ChannelType
  const meta = CHANNEL_META[channelType] || {
    displayName: channel.channelType,
    icon: <MailOutlined />,
    description: '',
    color: colors.primary,
  }

  // Заполняем форму при загрузке
  useEffect(() => {
    if (channel.settings) {
      form.setFieldsValue(channel.settings)
    }
  }, [channel.settings, form])

  /**
   * Сохраняет настройки канала
   */
  const handleSave = async () => {
    try {
      const values = await form.validateFields()
      setSaving(true)
      
      // Фильтруем пустые и замаскированные значения
      const cleanSettings: Record<string, unknown> = {}
      Object.entries(values).forEach(([key, value]) => {
        if (value !== undefined && value !== '' && value !== '***' && value !== null) {
          cleanSettings[key] = value
        }
      })

      await api.put(`/admin/channels/${channel.channelType}`, {
        settings: cleanSettings,
        enabled: channel.enabled,
      })
      
      message.success(`Настройки ${meta.displayName} сохранены`)
      onRefresh()
    } catch (err) {
      const error = err as { response?: { data?: { message?: string } } }
      message.error(error?.response?.data?.message || 'Ошибка сохранения настроек')
    } finally {
      setSaving(false)
    }
  }

  /**
   * Переключает состояние канала (вкл/выкл)
   */
  const handleToggle = async (enabled: boolean) => {
    try {
      setToggling(true)
      await api.patch(`/admin/channels/${channel.channelType}/toggle`, { enabled })
      message.success(`Канал ${enabled ? 'включён' : 'выключен'}`)
      onRefresh()
    } catch (err) {
      const error = err as { response?: { data?: { message?: string } } }
      message.error(error?.response?.data?.message || 'Ошибка изменения статуса')
    } finally {
      setToggling(false)
    }
  }

  /**
   * Тестирует подключение канала
   */
  const handleTest = async () => {
    try {
      setTesting(true)
      const response = await api.post(`/admin/channels/${channel.channelType}/test`)
      if (response.data.success) {
        message.success(response.data.message || 'Тест пройден успешно!')
      } else {
        message.warning(response.data.message || 'Тест не пройден')
      }
    } catch (err) {
      const error = err as { response?: { data?: { message?: string } } }
      message.error(error?.response?.data?.message || 'Ошибка тестирования')
    } finally {
      setTesting(false)
    }
  }

  /**
   * Рендерит форму настроек в зависимости от типа канала
   */
  const renderForm = () => {
    switch (channelType) {
      case 'EMAIL':
        return <EmailSettingsForm form={form} />
      case 'TELEGRAM':
        return <TelegramSettingsForm form={form} />
      case 'SMS':
        return <SmsSettingsForm form={form} />
      case 'WHATSAPP':
        return <WhatsAppSettingsForm form={form} />
      default:
        return <Text type="secondary">Конфигурация недоступна для этого типа канала</Text>
    }
  }

  return (
    <Card
      title={
        <Space>
          <span style={{ color: meta.color }}>{meta.icon}</span>
          <span>{meta.displayName}</span>
        </Space>
      }
      extra={
        <Space>
          <Badge 
            status={channel.enabled ? 'success' : 'default'} 
            text={
              <Tag 
                color={channel.enabled ? 'success' : 'default'}
                icon={channel.enabled ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
              >
                {channel.enabled ? 'Активен' : 'Выключен'}
              </Tag>
            }
          />
          <Tooltip title={channel.enabled ? 'Выключить канал' : 'Включить канал'}>
            <Switch
              checked={channel.enabled}
              onChange={handleToggle}
              loading={toggling}
              checkedChildren="ON"
              unCheckedChildren="OFF"
            />
          </Tooltip>
        </Space>
      }
      style={{ 
        height: '100%',
        borderTop: `3px solid ${meta.color}`,
      }}
    >
      <Text type="secondary" style={{ display: 'block', marginBottom: 16 }}>
        {meta.description}
      </Text>
      
      {renderForm()}
      
      <Divider style={{ margin: '16px 0' }} />
      
      <Space>
        <Button
          type="primary"
          icon={<SaveOutlined />}
          loading={saving}
          onClick={handleSave}
        >
          Сохранить
        </Button>
        <Tooltip title={!channel.enabled ? 'Сначала включите канал' : 'Проверить подключение'}>
          <Button
            icon={<ExperimentOutlined />}
            loading={testing}
            onClick={handleTest}
            disabled={!channel.enabled}
          >
            Тест подключения
          </Button>
        </Tooltip>
      </Space>
    </Card>
  )
}

/**
 * Главный компонент страницы настройки каналов
 */
export default function Channels() {
  const [loading, setLoading] = useState(true)
  const [channels, setChannels] = useState<ChannelConfig[]>([])
  const [error, setError] = useState<string | null>(null)

  /**
   * Загружает список каналов с сервера
   */
  const loadChannels = async () => {
    try {
      setLoading(true)
      setError(null)
      const response = await api.get('/admin/channels')
      const data = Array.isArray(response.data) ? response.data : []
      setChannels(data)
    } catch (err) {
      const error = err as { response?: { data?: { message?: string } } }
      setError(error?.response?.data?.message || 'Ошибка загрузки каналов')
      message.error('Ошибка загрузки каналов')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadChannels()
  }, [])

  // Лоадер при первой загрузке
  if (loading) {
    return <PageLoader />
  }

  // Отображение ошибки
  if (error) {
    return (
      <ErrorState
        message="Ошибка загрузки"
        description={error}
        onRetry={loadChannels}
      />
    )
  }

  // Подсчет активных каналов
  const activeChannels = channels.filter(c => c.enabled).length

  return (
    <div>
      {/* Заголовок страницы */}
      <PageHeader
        title="Каналы отправки"
        subtitle={`Активно ${activeChannels} из ${channels.length} каналов`}
        extra={
          <Button 
            icon={<ReloadOutlined spin={loading} />} 
            onClick={loadChannels}
            loading={loading}
          >
            Обновить
          </Button>
        }
      />
      
      {channels.length === 0 ? (
        <Alert
          type="info"
          showIcon
          message="Каналы не найдены"
          description="В системе не настроено ни одного канала отправки. Обратитесь к администратору."
        />
      ) : (
        <Row gutter={[16, 16]}>
          {channels.map((channel) => (
            <Col xs={24} xl={12} key={channel.channelType}>
              <ChannelCard channel={channel} onRefresh={loadChannels} />
            </Col>
          ))}
        </Row>
      )}
    </div>
  )
}
