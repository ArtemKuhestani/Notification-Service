/**
 * Notifications Page
 * 
 * Страница журнала уведомлений с полным функционалом управления.
 * Позволяет просматривать, фильтровать, повторять и отменять уведомления.
 * 
 * @features
 * - Таблица с пагинацией и сортировкой
 * - Фильтрация по статусу, каналу и дате
 * - Повторная отправка неудачных уведомлений
 * - Отмена ожидающих уведомлений
 * - Экспорт данных в CSV
 * - Детальный просмотр уведомления в модальном окне
 */

import { useState, useEffect, useCallback } from 'react'
import {
  Table,
  Button,
  Space,
  Input,
  Select,
  DatePicker,
  Card,
  Modal,
  message,
  Tooltip,
  Descriptions,
  Alert,
} from 'antd'
import {
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  RedoOutlined,
  StopOutlined,
  DownloadOutlined,
  FilterOutlined,
  ClearOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { notificationApi } from '../api/client'
import { PageHeader, ChannelTag, StatusTag, PriorityTag, EmptyState } from '../components/common'
import { colors } from '../theme'
import type { Notification, ChannelType, NotificationStatus, Priority } from '../types'

const { RangePicker } = DatePicker

/** Тип записи таблицы уведомлений */
interface NotificationRecord extends Notification {
  key: string
}

/** Тип фильтров */
interface NotificationFilters {
  status?: NotificationStatus
  channel?: ChannelType
  priority?: Priority
  search: string
  dateRange?: [dayjs.Dayjs, dayjs.Dayjs]
}

/** Начальное состояние фильтров */
const initialFilters: NotificationFilters = {
  status: undefined,
  channel: undefined,
  priority: undefined,
  search: '',
  dateRange: undefined,
}

/**
 * Компонент страницы журнала уведомлений
 */
export default function Notifications() {
  const [loading, setLoading] = useState(false)
  const [data, setData] = useState<NotificationRecord[]>([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 })
  const [selectedRecord, setSelectedRecord] = useState<NotificationRecord | null>(null)
  const [detailsModalOpen, setDetailsModalOpen] = useState(false)
  const [filters, setFilters] = useState<NotificationFilters>(initialFilters)

  /**
   * Загружает данные уведомлений с сервера
   */
  const loadData = useCallback(async (page = 0, size = 20) => {
    try {
      setLoading(true)
      const params: Record<string, string | number> = { page, size }
      
      if (filters.status) params.status = filters.status
      if (filters.channel) params.channel = filters.channel
      if (filters.priority) params.priority = filters.priority

      const response = await notificationApi.getAll(params as any)
      const result = response.data
      
      setData(result.content.map((item: Notification) => ({ ...item, key: item.id })))
      setPagination({
        current: result.page + 1,
        pageSize: result.size,
        total: result.totalElements,
      })
    } catch {
      message.error('Ошибка загрузки уведомлений')
    } finally {
      setLoading(false)
    }
  }, [filters])

  // Загрузка данных при изменении фильтров
  useEffect(() => {
    loadData()
  }, [loadData])

  /** Обновляет текущую страницу */
  const handleRefresh = () => {
    loadData(pagination.current - 1, pagination.pageSize)
  }

  /** Сбрасывает все фильтры */
  const handleClearFilters = () => {
    setFilters(initialFilters)
  }

  /** Повторная отправка уведомления */
  const handleRetry = async (record: NotificationRecord) => {
    try {
      message.loading({ content: 'Повторная отправка...', key: 'retry' })
      await notificationApi.retry(record.id)
      message.success({ content: 'Уведомление поставлено в очередь', key: 'retry' })
      handleRefresh()
    } catch {
      message.error({ content: 'Ошибка повторной отправки', key: 'retry' })
    }
  }

  /** Отмена уведомления */
  const handleCancel = async (record: NotificationRecord) => {
    Modal.confirm({
      title: 'Отмена уведомления',
      content: 'Вы уверены, что хотите отменить это уведомление?',
      okText: 'Отменить',
      okType: 'danger',
      cancelText: 'Нет',
      onOk: async () => {
        try {
          await notificationApi.cancel(record.id)
          message.success('Уведомление отменено')
          handleRefresh()
        } catch {
          message.error('Ошибка отмены уведомления')
        }
      },
    })
  }

  /** Открывает модальное окно с деталями */
  const handleViewDetails = (record: NotificationRecord) => {
    setSelectedRecord(record)
    setDetailsModalOpen(true)
  }

  /** Обработчик изменения таблицы (пагинация, сортировка) */
  const handleTableChange = (pag: { current?: number; pageSize?: number }) => {
    loadData((pag.current || 1) - 1, pag.pageSize || 20)
  }

  /** Экспорт данных в CSV */
  const handleExport = () => {
    message.info('Функция экспорта в разработке')
  }

  /** Проверяет, есть ли активные фильтры */
  const hasActiveFilters = filters.status || filters.channel || filters.priority || filters.search

  /**
   * Конфигурация колонок таблицы
   */
  const columns: ColumnsType<NotificationRecord> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 120,
      fixed: 'left',
      render: (id: string) => (
        <Tooltip title={`Нажмите для копирования: ${id}`}>
          <code 
            style={{ 
              fontSize: 11, 
              cursor: 'pointer',
              padding: '2px 6px',
              background: colors.bgGrey,
              borderRadius: 4,
            }}
            onClick={() => {
              navigator.clipboard.writeText(id)
              message.success('ID скопирован')
            }}
          >
            {id.slice(0, 8)}...
          </code>
        </Tooltip>
      ),
    },
    {
      title: 'Дата',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      sorter: (a, b) => dayjs(a.createdAt).unix() - dayjs(b.createdAt).unix(),
      render: (date: string) => (
        <Tooltip title={dayjs(date).format('DD.MM.YYYY HH:mm:ss')}>
          <span style={{ fontSize: 13 }}>
            {dayjs(date).format('DD.MM.YY HH:mm')}
          </span>
        </Tooltip>
      ),
    },
    {
      title: 'Канал',
      dataIndex: 'channel',
      key: 'channel',
      width: 110,
      render: (channel: ChannelType) => <ChannelTag channel={channel} />,
    },
    {
      title: 'Получатель',
      dataIndex: 'recipient',
      key: 'recipient',
      ellipsis: true,
      render: (recipient: string) => (
        <Tooltip title={recipient}>
          <span>{recipient}</span>
        </Tooltip>
      ),
    },
    {
      title: 'Тема',
      dataIndex: 'subject',
      key: 'subject',
      ellipsis: true,
      render: (subject: string) => (
        <Tooltip title={subject}>
          <span>{subject || '—'}</span>
        </Tooltip>
      ),
    },
    {
      title: 'Приоритет',
      dataIndex: 'priority',
      key: 'priority',
      width: 100,
      render: (priority: Priority) => <PriorityTag priority={priority} />,
    },
    {
      title: 'Статус',
      dataIndex: 'status',
      key: 'status',
      width: 120,
      render: (status: NotificationStatus) => <StatusTag status={status} />,
    },
    {
      title: 'Попытки',
      dataIndex: 'retryCount',
      key: 'retryCount',
      width: 80,
      align: 'center',
      render: (count: number) => (
        <span style={{ 
          color: count > 2 ? colors.warning : count > 0 ? colors.textSecondary : 'inherit',
          fontWeight: count > 2 ? 500 : 400,
        }}>
          {count}
        </span>
      ),
    },
    {
      title: 'Действия',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="Подробнее">
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handleViewDetails(record)}
            />
          </Tooltip>
          {record.status === 'FAILED' && (
            <Tooltip title="Повторить отправку">
              <Button
                type="text"
                size="small"
                icon={<RedoOutlined style={{ color: colors.primary }} />}
                onClick={() => handleRetry(record)}
              />
            </Tooltip>
          )}
          {(record.status === 'PENDING' || record.status === 'PROCESSING') && (
            <Tooltip title="Отменить">
              <Button
                type="text"
                size="small"
                danger
                icon={<StopOutlined />}
                onClick={() => handleCancel(record)}
              />
            </Tooltip>
          )}
        </Space>
      ),
    },
  ]

  return (
    <div>
      {/* Заголовок страницы */}
      <PageHeader 
        title="Журнал уведомлений"
        subtitle={`Всего записей: ${pagination.total}`}
        extra={
          <Space>
            <Button icon={<DownloadOutlined />} onClick={handleExport}>
              Экспорт
            </Button>
            <Button 
              icon={<ReloadOutlined spin={loading} />} 
              onClick={handleRefresh}
              loading={loading}
            >
              Обновить
            </Button>
          </Space>
        }
      />

      {/* Панель фильтров */}
      <Card 
        size="small" 
        style={{ marginBottom: 16 }}
        title={
          <Space>
            <FilterOutlined />
            <span>Фильтры</span>
            {hasActiveFilters && (
              <Button 
                type="link" 
                size="small" 
                icon={<ClearOutlined />}
                onClick={handleClearFilters}
              >
                Сбросить
              </Button>
            )}
          </Space>
        }
      >
        <Space wrap size="middle">
          <Input
            placeholder="Поиск по получателю..."
            prefix={<SearchOutlined style={{ color: colors.textSecondary }} />}
            style={{ width: 220 }}
            value={filters.search}
            onChange={(e) => setFilters({ ...filters, search: e.target.value })}
            allowClear
          />
          <Select
            placeholder="Статус"
            style={{ width: 140 }}
            allowClear
            value={filters.status}
            onChange={(value) => setFilters({ ...filters, status: value })}
            options={[
              { value: 'PENDING', label: 'Ожидает' },
              { value: 'PROCESSING', label: 'Обработка' },
              { value: 'SENT', label: 'Отправлено' },
              { value: 'DELIVERED', label: 'Доставлено' },
              { value: 'FAILED', label: 'Ошибка' },
            ]}
          />
          <Select
            placeholder="Канал"
            style={{ width: 140 }}
            allowClear
            value={filters.channel}
            onChange={(value) => setFilters({ ...filters, channel: value })}
            options={[
              { value: 'EMAIL', label: 'Email' },
              { value: 'TELEGRAM', label: 'Telegram' },
              { value: 'SMS', label: 'SMS' },
              { value: 'WHATSAPP', label: 'WhatsApp' },
            ]}
          />
          <Select
            placeholder="Приоритет"
            style={{ width: 140 }}
            allowClear
            value={filters.priority}
            onChange={(value) => setFilters({ ...filters, priority: value })}
            options={[
              { value: 'LOW', label: 'Низкий' },
              { value: 'NORMAL', label: 'Обычный' },
              { value: 'HIGH', label: 'Высокий' },
              { value: 'CRITICAL', label: 'Критический' },
            ]}
          />
          <RangePicker 
            placeholder={['Дата с', 'Дата по']} 
            format="DD.MM.YYYY"
            value={filters.dateRange}
            onChange={(dates) => setFilters({ 
              ...filters, 
              dateRange: dates as [dayjs.Dayjs, dayjs.Dayjs] | undefined 
            })}
          />
        </Space>
      </Card>

      {/* Таблица уведомлений */}
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={data}
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) => `${range[0]}-${range[1]} из ${total}`,
            pageSizeOptions: ['10', '20', '50', '100'],
          }}
          onChange={handleTableChange}
          size="middle"
          scroll={{ x: 1100 }}
          locale={{ emptyText: <EmptyState description="Нет уведомлений" /> }}
        />
      </Card>

      {/* Модальное окно с деталями уведомления */}
      <Modal
        title={
          <Space>
            <EyeOutlined />
            <span>Детали уведомления</span>
          </Space>
        }
        open={detailsModalOpen}
        onCancel={() => setDetailsModalOpen(false)}
        footer={[
          <Button key="close" onClick={() => setDetailsModalOpen(false)}>
            Закрыть
          </Button>,
          selectedRecord?.status === 'FAILED' && (
            <Button
              key="retry"
              type="primary"
              icon={<RedoOutlined />}
              onClick={() => {
                handleRetry(selectedRecord)
                setDetailsModalOpen(false)
              }}
            >
              Повторить отправку
            </Button>
          ),
        ].filter(Boolean)}
        width={700}
      >
        {selectedRecord && (
          <div>
            {/* Ошибка доставки (если есть) */}
            {selectedRecord.errorMessage && (
              <Alert
                type="error"
                message="Ошибка доставки"
                description={selectedRecord.errorMessage}
                style={{ marginBottom: 16 }}
                showIcon
              />
            )}

            {/* Основная информация */}
            <Descriptions 
              bordered 
              size="small" 
              column={2}
              labelStyle={{ fontWeight: 500, width: 150 }}
            >
              <Descriptions.Item label="ID" span={2}>
                <code style={{ 
                  fontSize: 12, 
                  padding: '2px 8px', 
                  background: colors.bgGrey,
                  borderRadius: 4,
                }}>
                  {selectedRecord.id}
                </code>
              </Descriptions.Item>
              <Descriptions.Item label="Канал">
                <ChannelTag channel={selectedRecord.channel as ChannelType} />
              </Descriptions.Item>
              <Descriptions.Item label="Статус">
                <StatusTag status={selectedRecord.status as NotificationStatus} />
              </Descriptions.Item>
              <Descriptions.Item label="Получатель" span={2}>
                {selectedRecord.recipient}
              </Descriptions.Item>
              <Descriptions.Item label="Тема" span={2}>
                {selectedRecord.subject || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Приоритет">
                <PriorityTag priority={selectedRecord.priority as Priority} />
              </Descriptions.Item>
              <Descriptions.Item label="Попытки">
                <span style={{ 
                  color: selectedRecord.retryCount > 2 ? colors.warning : 'inherit',
                  fontWeight: selectedRecord.retryCount > 2 ? 500 : 400,
                }}>
                  {selectedRecord.retryCount}
                </span>
              </Descriptions.Item>
              <Descriptions.Item label="Создано">
                {dayjs(selectedRecord.createdAt).format('DD.MM.YYYY HH:mm:ss')}
              </Descriptions.Item>
              <Descriptions.Item label="Отправлено">
                {selectedRecord.sentAt 
                  ? dayjs(selectedRecord.sentAt).format('DD.MM.YYYY HH:mm:ss')
                  : '—'
                }
              </Descriptions.Item>
            </Descriptions>
          </div>
        )}
      </Modal>
    </div>
  )
}
