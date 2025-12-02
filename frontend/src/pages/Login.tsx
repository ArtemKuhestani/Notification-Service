/**
 * Login Page
 * 
 * Страница авторизации для доступа к административной панели.
 * Использует JWT-аутентификацию с хранением токена в localStorage.
 * 
 * @features
 * - Email/password аутентификация
 * - Валидация формы
 * - Отображение ошибок
 * - Редирект на Dashboard после успешного входа
 */

import { useState, useCallback } from 'react'
import { Form, Input, Button, Card, Typography, message, Alert } from 'antd'
import { UserOutlined, LockOutlined, BellOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'
import { colors } from '../theme'

const { Title, Text } = Typography

// ============================================================================
// Типы
// ============================================================================

/** Данные формы входа */
interface LoginFormData {
  email: string
  password: string
}

// ============================================================================
// Компонент
// ============================================================================

/**
 * Страница авторизации
 */
export default function Login() {
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const navigate = useNavigate()
  const { login } = useAuth()

  /**
   * Обработчик отправки формы авторизации
   */
  const handleSubmit = useCallback(async (values: LoginFormData) => {
    setLoading(true)
    setError(null)
    
    const result = await login(values.email, values.password)
    
    if (result.success) {
      message.success('Вход выполнен успешно')
      navigate('/')
    } else {
      setError(result.error || 'Ошибка входа')
    }
    
    setLoading(false)
  }, [login, navigate])

  /**
   * Закрытие алерта с ошибкой
   */
  const handleErrorClose = useCallback(() => setError(null), [])

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: `linear-gradient(135deg, ${colors.primary} 0%, #764ba2 100%)`,
      }}
    >
      <Card
        style={{
          width: 400,
          boxShadow: '0 8px 32px rgba(0, 0, 0, 0.2)',
          borderRadius: 12,
        }}
      >
        {/* Заголовок */}
        <div style={{ textAlign: 'center', marginBottom: 32 }}>
          <BellOutlined style={{ fontSize: 48, color: colors.primary, marginBottom: 16 }} />
          <Title level={3} style={{ margin: 0 }}>
            Notification Service
          </Title>
          <Text type="secondary">Административная панель</Text>
        </div>

        {/* Сообщение об ошибке */}
        {error && (
          <Alert 
            message={error} 
            type="error" 
            showIcon 
            closable 
            onClose={handleErrorClose}
            style={{ marginBottom: 16 }}
          />
        )}

        {/* Форма входа */}
        <Form 
          name="login" 
          onFinish={handleSubmit} 
          layout="vertical" 
          requiredMark={false}
          autoComplete="on"
        >
          <Form.Item
            name="email"
            rules={[
              { required: true, message: 'Введите email' },
              { type: 'email', message: 'Введите корректный email' },
            ]}
          >
            <Input
              prefix={<UserOutlined style={{ color: colors.text.tertiary }} />}
              placeholder="Email"
              size="large"
              autoComplete="email"
            />
          </Form.Item>

          <Form.Item
            name="password"
            rules={[{ required: true, message: 'Введите пароль' }]}
          >
            <Input.Password
              prefix={<LockOutlined style={{ color: colors.text.tertiary }} />}
              placeholder="Пароль"
              size="large"
              autoComplete="current-password"
            />
          </Form.Item>

          <Form.Item style={{ marginBottom: 16 }}>
            <Button 
              type="primary" 
              htmlType="submit" 
              loading={loading} 
              block 
              size="large"
            >
              Войти
            </Button>
          </Form.Item>
        </Form>

        {/* Демо-доступ */}
        <div style={{ textAlign: 'center' }}>
          <Text type="secondary" style={{ fontSize: 12 }}>
            Демо-доступ: admin@notification-service.com / admin123
          </Text>
        </div>
      </Card>
    </div>
  )
}
