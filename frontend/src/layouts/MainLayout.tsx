/**
 * MainLayout Component
 * 
 * Основной макет приложения с боковой навигацией и хедером.
 * Используется для всех авторизованных страниц.
 * 
 * @features
 * - Сворачиваемое боковое меню
 * - Навигация между страницами
 * - Меню пользователя с выходом
 * - Адаптивный дизайн
 */

import { useState } from 'react'
import { Outlet, useNavigate, useLocation } from 'react-router-dom'
import { Layout, Menu, Button, Typography, Avatar, Dropdown, theme, Space, Tooltip, Badge } from 'antd'
import {
  DashboardOutlined,
  MailOutlined,
  ApiOutlined,
  SettingOutlined,
  LogoutOutlined,
  UserOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  BellOutlined,
  FileTextOutlined,
  AuditOutlined,
  SendOutlined,
  QuestionCircleOutlined,
} from '@ant-design/icons'
import { useAuth } from '../hooks/useAuth'
import { colors } from '../theme'

const { Header, Sider, Content, Footer } = Layout
const { Title, Text } = Typography

/**
 * Элементы главного меню навигации
 */
const menuItems = [
  {
    key: '/',
    icon: <DashboardOutlined />,
    label: 'Дашборд',
  },
  {
    key: '/notifications',
    icon: <MailOutlined />,
    label: 'Журнал уведомлений',
  },
  {
    key: '/test-send',
    icon: <SendOutlined />,
    label: 'Тестовая отправка',
  },
  {
    type: 'divider' as const,
  },
  {
    key: '/clients',
    icon: <ApiOutlined />,
    label: 'API-клиенты',
  },
  {
    key: '/channels',
    icon: <SettingOutlined />,
    label: 'Каналы',
  },
  {
    key: '/templates',
    icon: <FileTextOutlined />,
    label: 'Шаблоны',
  },
  {
    type: 'divider' as const,
  },
  {
    key: '/audit',
    icon: <AuditOutlined />,
    label: 'Аудит',
  },
]

/**
 * Главный макет приложения
 */
export default function MainLayout() {
  const [collapsed, setCollapsed] = useState(false)
  const navigate = useNavigate()
  const location = useLocation()
  const { user, logout } = useAuth()
  const { token } = theme.useToken()

  /** Обработчик клика по пункту меню */
  const handleMenuClick = ({ key }: { key: string }) => {
    navigate(key)
  }

  /** Обработчик выхода из системы */
  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  /** Элементы выпадающего меню пользователя */
  const userMenuItems = [
    {
      key: 'user-info',
      label: (
        <div style={{ padding: '4px 0' }}>
          <Text strong>{user?.fullName || 'Пользователь'}</Text>
          <br />
          <Text type="secondary" style={{ fontSize: 12 }}>
            {user?.email || ''}
          </Text>
        </div>
      ),
      disabled: true,
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'profile',
      icon: <UserOutlined />,
      label: 'Профиль',
      onClick: () => navigate('/profile'),
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'logout',
      icon: <LogoutOutlined style={{ color: colors.error }} />,
      label: <span style={{ color: colors.error }}>Выйти</span>,
      onClick: handleLogout,
    },
  ]

  return (
    <Layout style={{ minHeight: '100vh' }}>
      {/* Боковая панель */}
      <Sider
        trigger={null}
        collapsible
        collapsed={collapsed}
        width={240}
        style={{
          background: token.colorBgContainer,
          borderRight: `1px solid ${token.colorBorderSecondary}`,
          position: 'fixed',
          left: 0,
          top: 0,
          bottom: 0,
          zIndex: 100,
          overflow: 'auto',
        }}
      >
        {/* Логотип */}
        <div
          style={{
            height: 64,
            display: 'flex',
            alignItems: 'center',
            justifyContent: collapsed ? 'center' : 'flex-start',
            padding: collapsed ? 0 : '0 20px',
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
            transition: 'all 0.2s',
          }}
        >
          <Badge count={0} dot>
            <BellOutlined style={{ 
              fontSize: 26, 
              color: colors.primary,
              transition: 'all 0.2s',
            }} />
          </Badge>
          {!collapsed && (
            <Title 
              level={4} 
              style={{ 
                margin: '0 0 0 12px', 
                whiteSpace: 'nowrap',
                color: colors.primary,
                fontWeight: 600,
              }}
            >
              Notification
            </Title>
          )}
        </div>

        {/* Навигационное меню */}
        <Menu
          mode="inline"
          selectedKeys={[location.pathname]}
          items={menuItems}
          onClick={handleMenuClick}
          style={{ 
            border: 'none',
            padding: '8px 0',
          }}
        />

        {/* Версия приложения */}
        {!collapsed && (
          <div style={{
            position: 'absolute',
            bottom: 16,
            left: 0,
            right: 0,
            textAlign: 'center',
          }}>
            <Text type="secondary" style={{ fontSize: 11 }}>
              v1.0.0
            </Text>
          </div>
        )}
      </Sider>

      {/* Основной контент */}
      <Layout style={{ marginLeft: collapsed ? 80 : 240, transition: 'all 0.2s' }}>
        {/* Хедер */}
        <Header
          style={{
            padding: '0 24px',
            background: token.colorBgContainer,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            borderBottom: `1px solid ${token.colorBorderSecondary}`,
            position: 'sticky',
            top: 0,
            zIndex: 99,
          }}
        >
          {/* Левая часть хедера */}
          <Space>
            <Tooltip title={collapsed ? 'Развернуть меню' : 'Свернуть меню'}>
              <Button
                type="text"
                icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                onClick={() => setCollapsed(!collapsed)}
                style={{ fontSize: 16, width: 40, height: 40 }}
              />
            </Tooltip>
          </Space>

          {/* Правая часть хедера */}
          <Space size="middle">
            <Tooltip title="Справка">
              <Button 
                type="text" 
                icon={<QuestionCircleOutlined />}
                style={{ width: 40, height: 40 }}
              />
            </Tooltip>
            
            <Dropdown 
              menu={{ items: userMenuItems }} 
              placement="bottomRight"
              trigger={['click']}
            >
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  cursor: 'pointer',
                  padding: '4px 12px',
                  borderRadius: 8,
                  transition: 'background 0.2s',
                }}
                className="user-dropdown-trigger"
              >
                <Avatar 
                  icon={<UserOutlined />} 
                  style={{ 
                    marginRight: 8,
                    backgroundColor: colors.primary,
                  }} 
                />
                <Text style={{ maxWidth: 120 }} ellipsis>
                  {user?.fullName || 'Пользователь'}
                </Text>
              </div>
            </Dropdown>
          </Space>
        </Header>

        {/* Контент страницы */}
        <Content
          style={{
            margin: 24,
            padding: 24,
            background: token.colorBgContainer,
            borderRadius: token.borderRadiusLG,
            minHeight: 'calc(100vh - 64px - 48px - 48px)',
          }}
        >
          <Outlet />
        </Content>

        {/* Футер */}
        <Footer 
          style={{ 
            textAlign: 'center', 
            padding: '12px 24px',
            background: 'transparent',
          }}
        >
          <Text type="secondary" style={{ fontSize: 12 }}>
            Notification Service © {new Date().getFullYear()}
          </Text>
        </Footer>
      </Layout>
    </Layout>
  )
}
