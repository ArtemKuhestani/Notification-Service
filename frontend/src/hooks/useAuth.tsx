/**
 * Notification Service - Auth Hook
 * 
 * Централизованное управление аутентификацией пользователя.
 * Обрабатывает вход, выход и автоматическое восстановление сессии.
 * 
 * @module hooks/useAuth
 */

import { useState, useCallback, useEffect, useMemo } from 'react'
import axios, { AxiosError } from 'axios'
import type { User } from '../types'

// ============================================================================
// Константы
// ============================================================================

/** Ключи для localStorage */
const STORAGE_KEYS = {
  ACCESS_TOKEN: 'auth_token',
  REFRESH_TOKEN: 'refresh_token',
  USER: 'auth_user',
} as const

/** URL API */
const API_URL = import.meta.env.VITE_API_URL || '/api/v1'

// ============================================================================
// Типы
// ============================================================================

/** Состояние аутентификации */
interface AuthState {
  isAuthenticated: boolean
  token: string | null
  user: User | null
  loading: boolean
}

/** Результат операции логина */
interface LoginResult {
  success: boolean
  error?: string
}

/** API ответ при логине */
interface LoginResponse {
  accessToken: string
  refreshToken: string
  admin: {
    id: number
    email: string
    fullName: string
    role: 'ADMIN' | 'OPERATOR' | 'VIEWER'
  }
}

/** Возвращаемое значение хука */
interface UseAuthReturn {
  /** Флаг аутентификации */
  isAuthenticated: boolean
  /** Текущий пользователь */
  user: User | null
  /** Флаг загрузки */
  loading: boolean
  /** JWT токен */
  token: string | null
  /** Вход в систему */
  login: (email: string, password: string) => Promise<LoginResult>
  /** Выход из системы */
  logout: () => void
  /** Обновление данных пользователя */
  updateUser: (user: User) => void
}

// ============================================================================
// Хук
// ============================================================================

/**
 * Хук для управления аутентификацией.
 * 
 * @example
 * ```tsx
 * const { isAuthenticated, user, login, logout } = useAuth()
 * 
 * if (!isAuthenticated) {
 *   return <LoginForm onSubmit={login} />
 * }
 * 
 * return <Dashboard user={user} onLogout={logout} />
 * ```
 */
export function useAuth(): UseAuthReturn {
  const [auth, setAuth] = useState<AuthState>({
    isAuthenticated: false,
    token: null,
    user: null,
    loading: true,
  })

  /**
   * Восстановление сессии при монтировании компонента.
   * Проверяет наличие токена в localStorage.
   */
  useEffect(() => {
    const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
    const userStr = localStorage.getItem(STORAGE_KEYS.USER)
    
    if (token && userStr) {
      try {
        const user = JSON.parse(userStr) as User
        setAuth({
          isAuthenticated: true,
          token,
          user,
          loading: false,
        })
      } catch {
        // Некорректные данные - очищаем
        clearStorage()
        setAuth({ isAuthenticated: false, token: null, user: null, loading: false })
      }
    } else {
      setAuth((prev) => ({ ...prev, loading: false }))
    }
  }, [])

  /**
   * Вход в систему.
   * 
   * @param email - Email пользователя
   * @param password - Пароль
   * @returns Результат операции
   */
  const login = useCallback(async (email: string, password: string): Promise<LoginResult> => {
    try {
      const response = await axios.post<LoginResponse>(`${API_URL}/auth/login`, { 
        email, 
        password,
      })
      
      const { accessToken, refreshToken, admin } = response.data
      
      const user: User = {
        id: admin.id,
        email: admin.email,
        fullName: admin.fullName,
        role: admin.role,
      }

      // Сохраняем в localStorage
      localStorage.setItem(STORAGE_KEYS.ACCESS_TOKEN, accessToken)
      localStorage.setItem(STORAGE_KEYS.REFRESH_TOKEN, refreshToken)
      localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user))

      setAuth({
        isAuthenticated: true,
        token: accessToken,
        user,
        loading: false,
      })

      return { success: true }
    } catch (error) {
      const axiosError = error as AxiosError<{ message?: string; error?: string }>
      const message = 
        axiosError.response?.data?.message || 
        axiosError.response?.data?.error || 
        'Ошибка входа. Проверьте учетные данные.'
      
      return { success: false, error: message }
    }
  }, [])

  /**
   * Выход из системы.
   * Очищает токены и редиректит на страницу входа.
   */
  const logout = useCallback((): void => {
    // Пробуем уведомить сервер (не критично если не удастся)
    const token = localStorage.getItem(STORAGE_KEYS.ACCESS_TOKEN)
    if (token) {
      axios.post(`${API_URL}/auth/logout`, null, {
        headers: { Authorization: `Bearer ${token}` },
      }).catch(() => {
        // Игнорируем ошибки при логауте
      })
    }

    clearStorage()
    setAuth({
      isAuthenticated: false,
      token: null,
      user: null,
      loading: false,
    })
  }, [])

  /**
   * Обновление данных пользователя.
   * 
   * @param user - Новые данные пользователя
   */
  const updateUser = useCallback((user: User): void => {
    localStorage.setItem(STORAGE_KEYS.USER, JSON.stringify(user))
    setAuth((prev) => ({ ...prev, user }))
  }, [])

  // Мемоизированный результат
  return useMemo(
    () => ({
      isAuthenticated: auth.isAuthenticated,
      user: auth.user,
      loading: auth.loading,
      token: auth.token,
      login,
      logout,
      updateUser,
    }),
    [auth, login, logout, updateUser]
  )
}

// ============================================================================
// Вспомогательные функции
// ============================================================================

/**
 * Очистка данных аутентификации из localStorage.
 */
function clearStorage(): void {
  localStorage.removeItem(STORAGE_KEYS.ACCESS_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.REFRESH_TOKEN)
  localStorage.removeItem(STORAGE_KEYS.USER)
}

export default useAuth
