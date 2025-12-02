/**
 * Test Send Page
 * 
 * Страница тестовой отправки уведомлений для проверки работоспособности
 * настроенных каналов связи.
 * 
 * @features
 * - Выбор канала отправки (Email, Telegram, SMS, WhatsApp)
 * - Динамическая валидация в зависимости от канала
 * - Информация о настройке каналов
 * - Отображение результата отправки
 */

import { Card, Form, Input, Select, Button, message, Alert, Typography, Tag, Row, Col, Divider } from 'antd'
import { SendOutlined, MailOutlined, MessageOutlined, MobileOutlined } from '@ant-design/icons'
import { useState, useCallback } from 'react'
import api from '../api'
import { PageHeader } from '../components/common'
import { colors } from '../theme'
import type { ChannelType } from '../types'

const { Text } = Typography
const { TextArea } = Input

// ============================================================================
// Типы
// ============================================================================

/** Форма тестовой отправки */
interface TestSendForm {
  /** Канал отправки */
  channel: ChannelType
  /** Получатель */
  recipient: string
  /** Тема (для Email) */
  subject?: string
  /** Текст сообщения */
  message: string
}

/** Результат отправки */
interface SendResult {
  /** Успешность операции */
  success: boolean
  /** Сообщение о результате */
  message: string
  /** ID созданного уведомления */
  notificationId?: string
  /** Статус уведомления */
  status?: string
}

/** Опция выбора канала */
interface ChannelOption {
  value: ChannelType
  label: string
  icon: React.ReactNode
  placeholder: string
  inputLabel: string
  hint: string
  color: string
}

// ============================================================================
// Конфигурация каналов
// ============================================================================

/** Конфигурация каналов для формы */
const CHANNEL_CONFIG: Record<ChannelType, Omit<ChannelOption, 'value'>> = {
  EMAIL: {
    label: 'Email',
    icon: <MailOutlined />,
    placeholder: 'user@example.com',
    inputLabel: 'Email адрес',
    hint: 'SMTP сервер должен быть настроен. Укажите полный email адрес получателя.',
    color: colors.channels.EMAIL,
  },
  TELEGRAM: {
    label: 'Telegram',
    icon: <MessageOutlined />,
    placeholder: 'Chat ID (например: 123456789)',
    inputLabel: 'Telegram Chat ID',
    hint: 'Бот должен быть настроен. Chat ID можно получить у @userinfobot.',
    color: colors.channels.TELEGRAM,
  },
  SMS: {
    label: 'SMS',
    icon: <MobileOutlined />,
    placeholder: '+79991234567',
    inputLabel: 'Номер телефона',
    hint: 'Провайдер SMS должен быть настроен. Формат: +79991234567.',
    color: colors.channels.SMS,
  },
  WHATSAPP: {
    label: 'WhatsApp',
    icon: <MessageOutlined style={{ color: colors.channels.WHATSAPP }} />,
    placeholder: '+79991234567',
    inputLabel: 'Номер WhatsApp',
    hint: 'WhatsApp Business API должен быть настроен. Формат: +79991234567.',
    color: colors.channels.WHATSAPP,
  },
}

// ============================================================================
// Компонент
// ============================================================================

/**
 * Страница тестовой отправки уведомлений
 */
export default function TestSend() {
  const [form] = Form.useForm<TestSendForm>()
  const [loading, setLoading] = useState(false)
  const [result, setResult] = useState<SendResult | null>(null)
  const [selectedChannel, setSelectedChannel] = useState<ChannelType>('EMAIL')

  /** Получение конфигурации текущего канала */
  const currentChannelConfig = CHANNEL_CONFIG[selectedChannel]

  /** Опции для Select */
  const channelOptions = (Object.entries(CHANNEL_CONFIG) as [ChannelType, Omit<ChannelOption, 'value'>][])
    .map(([value, config]) => ({
      value,
      label: (
        <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          {config.icon} {config.label}
        </span>
      ),
    }))

  /**
   * Обработчик отправки формы
   */
  const handleSubmit = useCallback(async (values: TestSendForm) => {
    setLoading(true)
    setResult(null)
    
    try {
      const response = await api.post('/admin/notifications/test', values)
      setResult(response.data)
      
      if (response.data.success) {
        message.success('Уведомление отправлено!')
      } else {
        message.error(response.data.message || 'Ошибка отправки')
      }
    } catch (error: unknown) {
      const errorMessage = error instanceof Error ? error.message : 'Ошибка отправки уведомления'
      const apiError = error as { response?: { data?: { message?: string } } }
      setResult({
        success: false,
        message: apiError?.response?.data?.message || errorMessage,
      })
      message.error('Ошибка отправки')
    } finally {
      setLoading(false)
    }
  }, [])

  /**
   * Обработчик смены канала
   */
  const handleChannelChange = useCallback((value: ChannelType) => {
    setSelectedChannel(value)
    setResult(null)
    // Сброс recipient при смене канала
    form.setFieldValue('recipient', '')
  }, [form])

  return (
    <div style={{ maxWidth: 900, margin: '0 auto' }}>
      <PageHeader
        title="Тестовая отправка"
        subtitle="Отправьте тестовое уведомление для проверки работоспособности каналов связи"
      />

      <Row gutter={24}>
        {/* Форма отправки */}
        <Col xs={24} lg={14}>
          <Card>
            <Form
              form={form}
              layout="vertical"
              onFinish={handleSubmit}
              initialValues={{ channel: 'EMAIL' }}
            >
              <Form.Item
                name="channel"
                label="Канал отправки"
                rules={[{ required: true, message: 'Выберите канал' }]}
              >
                <Select
                  size="large"
                  onChange={handleChannelChange}
                  options={channelOptions}
                />
              </Form.Item>

              <Form.Item
                name="recipient"
                label={currentChannelConfig.inputLabel}
                rules={[
                  { required: true, message: 'Введите получателя' },
                  ...(selectedChannel === 'EMAIL' 
                    ? [{ type: 'email' as const, message: 'Введите корректный email' }] 
                    : []),
                ]}
              >
                <Input 
                  size="large" 
                  placeholder={currentChannelConfig.placeholder}
                />
              </Form.Item>

              {selectedChannel === 'EMAIL' && (
                <Form.Item
                  name="subject"
                  label="Тема письма"
                >
                  <Input 
                    size="large" 
                    placeholder="Тестовое уведомление"
                  />
                </Form.Item>
              )}

              <Form.Item
                name="message"
                label="Текст сообщения"
                rules={[{ required: true, message: 'Введите текст сообщения' }]}
              >
                <TextArea 
                  rows={4} 
                  placeholder="Введите текст тестового сообщения..."
                  showCount
                  maxLength={1000}
                />
              </Form.Item>

              <Form.Item style={{ marginBottom: 0 }}>
                <Button 
                  type="primary" 
                  htmlType="submit" 
                  loading={loading}
                  icon={<SendOutlined />}
                  size="large"
                  block
                >
                  Отправить тестовое уведомление
                </Button>
              </Form.Item>
            </Form>
          </Card>
        </Col>

        {/* Информация о каналах и результат */}
        <Col xs={24} lg={10}>
          <Card title="Информация о каналах" size="small">
            {(Object.entries(CHANNEL_CONFIG) as [ChannelType, Omit<ChannelOption, 'value'>][]).map(
              ([channel, config], index, arr) => (
                <div key={channel}>
                  <div style={{ marginBottom: index < arr.length - 1 ? 16 : 0 }}>
                    <Tag color={config.color}>{channel}</Tag>
                    <Text type="secondary" style={{ fontSize: 12, display: 'block', marginTop: 4 }}>
                      {config.hint}
                    </Text>
                  </div>
                  {index < arr.length - 1 && <Divider style={{ margin: '12px 0' }} />}
                </div>
              )
            )}
          </Card>

          {/* Результат отправки */}
          {result && (
            <Card 
              title="Результат отправки" 
              size="small" 
              style={{ marginTop: 16 }}
            >
              <Alert
                type={result.success ? 'success' : 'error'}
                message={result.success ? 'Успешно!' : 'Ошибка'}
                description={
                  <div>
                    <p style={{ marginBottom: result.notificationId ? 8 : 0 }}>
                      {result.message}
                    </p>
                    {result.notificationId && (
                      <p style={{ marginBottom: result.status ? 8 : 0 }}>
                        <Text code>ID: {result.notificationId}</Text>
                      </p>
                    )}
                    {result.status && (
                      <p style={{ marginBottom: 0 }}>
                        Статус: <Tag color={result.success ? 'success' : 'error'}>{result.status}</Tag>
                      </p>
                    )}
                  </div>
                }
                showIcon
              />
            </Card>
          )}
        </Col>
      </Row>
    </div>
  )
}
