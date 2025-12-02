/**
 * Dashboard Page
 * 
 * Главная страница панели управления с обзором статистики уведомлений.
 * Показывает общую статистику, данные за сегодня, распределение по каналам
 * и успешность доставки.
 * 
 * @features
 * - Автообновление данных каждые 30 секунд
 * - Круговая диаграмма успешности
 * - Статистика по каналам с прогресс-барами
 * - Адаптивная верстка для мобильных устройств
 */

import { useEffect, useState, useCallback } from 'react'
import { Row, Col, Card, Statistic, Table, Progress, Button, message, Tooltip } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  ClockCircleOutlined,
  SendOutlined,
  ReloadOutlined,
  ApiOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import { dashboardApi } from '../api/client'
import { PageLoader, PageHeader, ChannelTag, EmptyState } from '../components/common'
import { colors } from '../theme'
import type { DashboardStats, ChannelType, ChannelStats } from '../types'

/** Интервал автообновления в миллисекундах */
const AUTO_REFRESH_INTERVAL = 30000

/** Интерфейс для строки таблицы статистики каналов */
interface ChannelStatRow {
  key: string
  channel: ChannelType
  total: number
  sent: number
  failed: number
}

/**
 * Колонки таблицы статистики по каналам
 */
const channelColumns: ColumnsType<ChannelStatRow> = [
  {
    title: 'Канал',
    dataIndex: 'channel',
    key: 'channel',
    render: (channel: ChannelType) => <ChannelTag channel={channel} />,
  },
  {
    title: 'Всего',
    dataIndex: 'total',
    key: 'total',
    align: 'center',
  },
  {
    title: 'Отправлено',
    dataIndex: 'sent',
    key: 'sent',
    align: 'center',
    render: (sent: number) => (
      <span style={{ color: colors.success, fontWeight: 500 }}>{sent}</span>
    ),
  },
  {
    title: 'Ошибки',
    dataIndex: 'failed',
    key: 'failed',
    align: 'center',
    render: (failed: number) => (
      <span style={{ 
        color: failed > 0 ? colors.error : 'inherit',
        fontWeight: failed > 0 ? 500 : 400,
      }}>
        {failed}
      </span>
    ),
  },
  {
    title: 'Успешность',
    key: 'rate',
    align: 'center',
    render: (_: unknown, record: ChannelStatRow) => {
      const rate = record.total > 0 
        ? Math.round((record.sent / record.total) * 100) 
        : 100
      return (
        <Progress 
          percent={rate} 
          size="small" 
          status={rate >= 90 ? 'success' : rate >= 70 ? 'normal' : 'exception'}
          style={{ width: 100 }}
        />
      )
    },
  },
]

/**
 * Компонент Dashboard
 * 
 * Отображает статистику и метрики системы уведомлений
 */
export default function Dashboard() {
  const [loading, setLoading] = useState(true)
  const [stats, setStats] = useState<DashboardStats | null>(null)

  /**
   * Загружает статистику с сервера
   */
  const loadStats = useCallback(async () => {
    try {
      setLoading(true)
      const response = await dashboardApi.getStats()
      setStats(response.data)
    } catch {
      message.error('Ошибка загрузки статистики')
    } finally {
      setLoading(false)
    }
  }, [])

  // Загрузка данных при монтировании и автообновление
  useEffect(() => {
    loadStats()
    const interval = setInterval(loadStats, AUTO_REFRESH_INTERVAL)
    return () => clearInterval(interval)
  }, [loadStats])

  // Показываем лоадер при первой загрузке
  if (loading && !stats) {
    return <PageLoader />
  }

  // Расчет процента успешной доставки
  const successRate = stats && stats.totalNotifications > 0 
    ? Math.round((stats.sentCount / stats.totalNotifications) * 1000) / 10
    : 100

  // Подготовка данных для таблицы
  const tableData: ChannelStatRow[] = stats?.channelStats?.map((s: ChannelStats) => ({
    key: s.channel,
    channel: s.channel,
    total: s.total,
    sent: s.sent,
    failed: s.failed,
  })) || []

  return (
    <div>
      {/* Заголовок страницы */}
      <PageHeader 
        title="Дашборд"
        subtitle="Обзор статистики уведомлений"
        extra={
          <Tooltip title="Обновить данные">
            <Button
              icon={<ReloadOutlined spin={loading} />}
              onClick={loadStats}
              loading={loading}
            >
              Обновить
            </Button>
          </Tooltip>
        }
      />

      {/* Основная статистика */}
      <Row gutter={[16, 16]}>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable>
            <Statistic
              title="Всего уведомлений"
              value={stats?.totalNotifications || 0}
              prefix={<SendOutlined style={{ color: colors.primary }} />}
              valueStyle={{ color: colors.primary }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable>
            <Statistic
              title="Успешно отправлено"
              value={stats?.sentCount || 0}
              prefix={<CheckCircleOutlined style={{ color: colors.success }} />}
              valueStyle={{ color: colors.success }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable>
            <Statistic
              title="Ошибки доставки"
              value={stats?.failedCount || 0}
              prefix={<CloseCircleOutlined style={{ color: colors.error }} />}
              valueStyle={{ color: colors.error }}
            />
          </Card>
        </Col>
        <Col xs={24} sm={12} lg={6}>
          <Card hoverable>
            <Statistic
              title="В обработке"
              value={(stats?.pendingCount || 0) + (stats?.processingCount || 0)}
              prefix={<ClockCircleOutlined style={{ color: colors.warning }} />}
              valueStyle={{ color: colors.warning }}
            />
          </Card>
        </Col>
      </Row>

      {/* Статистика за сегодня */}
      <Card 
        title="Статистика за сегодня" 
        style={{ marginTop: 16 }}
        extra={
          <span style={{ color: colors.textSecondary, fontSize: 12 }}>
            {new Date().toLocaleDateString('ru-RU', { 
              weekday: 'long', 
              day: 'numeric', 
              month: 'long' 
            })}
          </span>
        }
      >
        <Row gutter={[32, 16]}>
          <Col xs={8}>
            <Statistic
              title="Отправлено"
              value={stats?.todayTotal || 0}
              valueStyle={{ fontSize: 28 }}
            />
          </Col>
          <Col xs={8}>
            <Statistic
              title="Успешно"
              value={stats?.todaySent || 0}
              valueStyle={{ fontSize: 28, color: colors.success }}
            />
          </Col>
          <Col xs={8}>
            <Statistic
              title="Ошибки"
              value={stats?.todayFailed || 0}
              valueStyle={{ 
                fontSize: 28, 
                color: (stats?.todayFailed || 0) > 0 ? colors.error : 'inherit' 
              }}
            />
          </Col>
        </Row>
      </Card>

      {/* Детальная аналитика */}
      <Row gutter={[16, 16]} style={{ marginTop: 16 }}>
        {/* Успешность доставки */}
        <Col xs={24} lg={8}>
          <Card title="Успешность доставки" style={{ height: '100%' }}>
            <div style={{ textAlign: 'center', padding: '20px 0' }}>
              <Progress
                type="circle"
                percent={successRate}
                format={(percent: number | undefined) => (
                  <span style={{ fontSize: 24, fontWeight: 600 }}>
                    {percent}%
                  </span>
                )}
                strokeColor={{
                  '0%': colors.primary,
                  '100%': colors.success,
                }}
                size={160}
                strokeWidth={8}
              />
              <p style={{ 
                marginTop: 20, 
                color: colors.textSecondary,
                fontSize: 14,
              }}>
                <strong style={{ color: colors.success }}>{stats?.sentCount || 0}</strong>
                {' '}из{' '}
                <strong>{stats?.totalNotifications || 0}</strong>
                {' '}успешно доставлено
              </p>
            </div>
          </Card>
        </Col>

        {/* Системная информация */}
        <Col xs={24} lg={8}>
          <Card title="Система" style={{ height: '100%' }}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ 
                display: 'flex', 
                justifyContent: 'space-between', 
                alignItems: 'center',
                padding: '12px 16px', 
                background: colors.bgGrey,
                borderRadius: 8,
              }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <ApiOutlined style={{ color: colors.primary }} />
                  Активные API-клиенты
                </span>
                <strong style={{ fontSize: 18, color: colors.primary }}>
                  {stats?.activeClients || 0}
                </strong>
              </div>
              
              <div style={{ 
                display: 'flex', 
                justifyContent: 'space-between', 
                alignItems: 'center',
                padding: '12px 16px', 
                background: colors.bgGrey,
                borderRadius: 8,
              }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <ThunderboltOutlined style={{ color: colors.channels.telegram }} />
                  Активные каналы
                </span>
                <strong style={{ fontSize: 18, color: colors.channels.telegram }}>
                  {stats?.activeChannels || 0}
                </strong>
              </div>
              
              <div style={{ 
                display: 'flex', 
                justifyContent: 'space-between', 
                alignItems: 'center',
                padding: '12px 16px', 
                background: colors.bgGrey,
                borderRadius: 8,
              }}>
                <span style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                  <ClockCircleOutlined style={{ color: colors.warning }} />
                  В очереди на обработку
                </span>
                <strong style={{ fontSize: 18, color: colors.warning }}>
                  {(stats?.pendingCount || 0) + (stats?.processingCount || 0)}
                </strong>
              </div>
            </div>
          </Card>
        </Col>

        {/* Распределение по каналам */}
        <Col xs={24} lg={8}>
          <Card title="Распределение по каналам" style={{ height: '100%' }}>
            {stats?.channelStats && stats.channelStats.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
                {stats.channelStats.map((channel) => {
                  const percentage = stats.totalNotifications > 0 
                    ? Math.round((channel.total / stats.totalNotifications) * 100) 
                    : 0
                  const channelType = channel.channel as ChannelType
                  const color = colors.channels[channelType.toLowerCase() as keyof typeof colors.channels] || colors.primary
                  
                  return (
                    <div key={channel.channel}>
                      <div style={{ 
                        display: 'flex', 
                        justifyContent: 'space-between', 
                        marginBottom: 6,
                        fontSize: 13,
                      }}>
                        <ChannelTag channel={channelType} />
                        <span style={{ color: colors.textSecondary }}>
                          {channel.total} ({percentage}%)
                        </span>
                      </div>
                      <Progress
                        percent={percentage}
                        showInfo={false}
                        strokeColor={color}
                        trailColor={colors.bgGrey}
                        size="small"
                      />
                    </div>
                  )
                })}
              </div>
            ) : (
              <EmptyState description="Нет данных о каналах" />
            )}
          </Card>
        </Col>
      </Row>

      {/* Таблица статистики по каналам */}
      <Card title="Детальная статистика по каналам" style={{ marginTop: 16 }}>
        <Table 
          columns={channelColumns} 
          dataSource={tableData}
          pagination={false} 
          size="middle"
          locale={{ emptyText: <EmptyState description="Нет данных" /> }}
        />
      </Card>
    </div>
  )
}
