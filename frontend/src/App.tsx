/**
 * @file App.tsx
 * @description Корневой компонент приложения с настройкой роутинга.
 * 
 * Маршрутизация:
 * - /login - страница авторизации (публичная)
 * - / - дашборд (защищенная)
 * - /notifications - журнал уведомлений
 * - /clients - управление API-клиентами  
 * - /channels - настройка каналов
 * - /templates - шаблоны сообщений
 * - /test-send - тестовая отправка
 * - /audit - журнал аудита
 * 
 * Защита маршрутов реализована через проверку isAuthenticated.
 * Неавторизованные пользователи перенаправляются на /login.
 */

import { Routes, Route, Navigate } from 'react-router-dom'
import MainLayout from './layouts/MainLayout'
import { useAuth } from './hooks/useAuth'
import {
  Dashboard,
  Notifications,
  ApiClients,
  Channels,
  Templates,
  TestSend,
  Audit,
  Login,
} from './pages'

/**
 * Корневой компонент приложения
 */
function App() {
  const { isAuthenticated } = useAuth()

  // Публичные маршруты для неавторизованных пользователей
  if (!isAuthenticated) {
    return (
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="*" element={<Navigate to="/login" replace />} />
      </Routes>
    )
  }

  // Защищенные маршруты для авторизованных пользователей
  return (
    <Routes>
      <Route path="/" element={<MainLayout />}>
        <Route index element={<Dashboard />} />
        <Route path="notifications" element={<Notifications />} />
        <Route path="clients" element={<ApiClients />} />
        <Route path="channels" element={<Channels />} />
        <Route path="templates" element={<Templates />} />
        <Route path="test-send" element={<TestSend />} />
        <Route path="audit" element={<Audit />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}

export default App
