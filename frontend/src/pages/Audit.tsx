/**
 * Audit Page
 * 
 * Страница журнала аудита системы.
 * Отображает все действия администраторов и изменения в системе.
 * 
 * @features
 * - Просмотр истории действий
 * - Фильтрация по дате, типу сущности, действию
 * - Детальный просмотр изменений (diff)
 * - Поиск по записям
 */

import { useState, useEffect, useCallback } from 'react'
import {
  Card,
  Table,
  Typography,
  Tag,
  Space,
  DatePicker,
  Select,
  Button,
  Input,
  Tooltip,
  Modal,
  Divider,
  Descriptions,
} from 'antd'
import {
  ReloadOutlined,
  SearchOutlined,
  EyeOutlined,
  InfoCircleOutlined,
  ClearOutlined,
  FilterOutlined,
  HistoryOutlined,
  UserOutlined,
  GlobalOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs, { Dayjs } from 'dayjs'
import { auditApi } from '../api/client'
import { PageHeader, EmptyState } from '../components/common'
import { colors } from '../theme'
import type { AuditLog } from '../types'

const { Text } = Typography
const { RangePicker } = DatePicker

/** Запись аудита для таблицы */
interface AuditRecord extends AuditLog {
  key: string
}

/** Цвета для типов действий */
const ACTION_COLORS: Record<string, string> = {
  CREATE: 'green',
  UPDATE: 'blue',
  DELETE: 'red',
  LOGIN: 'cyan',
  LOGOUT: 'default',
  TOGGLE: 'orange',
  REGENERATE: 'purple',
  TEST: 'magenta',
}

/** Метки для типов действий */
const ACTION_LABELS: Record<string, string> = {
  CREATE: 'Создание',
  UPDATE: 'Изменение',
  DELETE: 'Удаление',
  LOGIN: 'Вход',
  LOGOUT: 'Выход',
  TOGGLE: 'Переключение',
  REGENERATE: 'Регенерация',
  TEST: 'Тест',
}

/** Опции типов сущностей для фильтра */
const entityTypeOptions = [
  { value: '', label: 'Все типы' },
  { value: 'API_CLIENT', label: 'API клиент' },
  { value: 'CHANNEL', label: 'Канал' },
  { value: 'TEMPLATE', label: 'Шаблон' },
  { value: 'NOTIFICATION', label: 'Уведомление' },
  { value: 'ADMIN', label: 'Администратор' },
]

/** Опции действий для фильтра */
const actionOptions = [
  { value: '', label: 'Все действия' },
  { value: 'CREATE', label: 'Создание' },
  { value: 'UPDATE', label: 'Изменение' },
  { value: 'DELETE', label: 'Удаление' },
  { value: 'LOGIN', label: 'Вход' },
  { value: 'TOGGLE', label: 'Переключение' },
]

/**
 * Компонент страницы журнала аудита
 */
export default function Audit() {
  const [loading, setLoading] = useState(true)
  const [logs, setLogs] = useState<AuditRecord[]>([])
  const [pagination, setPagination] = useState({ current: 1, pageSize: 20, total: 0 })
  const [detailsModal, setDetailsModal] = useState<{ open: boolean; record: AuditRecord | null }>({
    open: false,
    record: null,
  })
  
  // Состояние фильтров
  const [dateRange, setDateRange] = useState<[Dayjs | null, Dayjs | null]>([null, null])
  const [entityType, setEntityType] = useState('')
  const [action, setAction] = useState('')
  const [searchText, setSearchText] = useState('')

  /**
   * Загружает записи аудита
   */
  const loadLogs = useCallback(async (page = 1, size = 20) => {
    try {
      setLoading(true)
      
      const params: Record<string, string | number> = {
        page: page - 1,
        size,
      }
      
      if (entityType) params.entityType = entityType
      if (action) params.action = action
      if (searchText) params.search = searchText
      if (dateRange[0]) params.startDate = dateRange[0].format('YYYY-MM-DD')
      if (dateRange[1]) params.endDate = dateRange[1].format('YYYY-MM-DD')

      const response = await auditApi.getAll(params)
      
      // Обработка данных - может быть Page объект или массив
      if (response.data.content) {
        setLogs(response.data.content.map((l: AuditLog) => ({ ...l, key: String(l.id) })))
        setPagination({
          current: page,
          pageSize: size,
          total: response.data.totalElements,
        })
      } else if (Array.isArray(response.data)) {
        setLogs(response.data.map((l: AuditLog) => ({ ...l, key: String(l.id) })))
        setPagination({
          current: 1,
          pageSize: size,
          total: response.data.length,
        })
      }
    } catch {
      setLogs([])
    } finally {
      setLoading(false)
    }
  }, [entityType, action, searchText, dateRange])

  useEffect(() => {
    loadLogs()
  }, [loadLogs])

  /** Обработчик изменения таблицы */
  const handleTableChange = (newPagination: { current?: number; pageSize?: number }) => {
    loadLogs(newPagination.current || 1, newPagination.pageSize || 20)
  }

  /** Применяет фильтры */
  const handleSearch = () => {
    loadLogs(1, pagination.pageSize)
  }

  /** Сбрасывает все фильтры */
  const handleReset = () => {
    setDateRange([null, null])
    setEntityType('')
    setAction('')
    setSearchText('')
    setTimeout(() => loadLogs(1, 20), 0)
  }

  /** Показывает модальное окно с деталями */
  const showDetails = (record: AuditRecord) => {
    setDetailsModal({ open: true, record })
  }

  /** Форматирует JSON для отображения */
  const formatJson = (value: string | undefined): string => {
    if (!value) return ''
    try {
      return JSON.stringify(JSON.parse(value), null, 2)
    } catch {
      return value
    }
  }

  /** Проверяет наличие активных фильтров */
  const hasActiveFilters = entityType || action || searchText || dateRange[0] || dateRange[1]

  /**
   * Конфигурация колонок таблицы
   */
  const columns: ColumnsType<AuditRecord> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 60,
      align: 'center',
    },
    {
      title: 'Время',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (date: string) => (
        <Tooltip title={dayjs(date).format('DD.MM.YYYY HH:mm:ss')}>
          <span style={{ fontSize: 13 }}>
            {dayjs(date).format('DD.MM.YY HH:mm')}
          </span>
        </Tooltip>
      ),
    },
    {
      title: 'Действие',
      dataIndex: 'action',
      key: 'action',
      width: 130,
      render: (actionType: string) => (
        <Tag color={ACTION_COLORS[actionType] || 'default'}>
          {ACTION_LABELS[actionType] || actionType}
        </Tag>
      ),
    },
    {
      title: 'Сущность',
      key: 'entity',
      width: 180,
      render: (_, record) => (
        <Space direction="vertical" size={0}>
          <Text style={{ fontSize: 13 }}>{record.entityType}</Text>
          {record.entityId && (
            <Text type="secondary" style={{ fontSize: 11 }}>
              ID: {record.entityId}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Администратор',
      dataIndex: 'adminEmail',
      key: 'adminEmail',
      ellipsis: true,
      render: (email: string) => (
        email ? (
          <Tooltip title={email}>
            <Space>
              <UserOutlined style={{ color: colors.textSecondary }} />
              <span style={{ fontSize: 13 }}>{email}</span>
            </Space>
          </Tooltip>
        ) : (
          <Text type="secondary">—</Text>
        )
      ),
    },
    {
      title: 'IP',
      dataIndex: 'ipAddress',
      key: 'ipAddress',
      width: 130,
      render: (ip: string) => (
        ip ? (
          <Tooltip title={`IP: ${ip}`}>
            <Space>
              <GlobalOutlined style={{ color: colors.textSecondary, fontSize: 12 }} />
              <code style={{ fontSize: 11 }}>{ip}</code>
            </Space>
          </Tooltip>
        ) : (
          <Text type="secondary">—</Text>
        )
      ),
    },
    {
      title: 'Детали',
      key: 'details',
      width: 80,
      align: 'center',
      render: (_, record) => {
        const hasChanges = record.oldValue || record.newValue
        return hasChanges ? (
          <Tooltip title="Просмотреть изменения">
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined style={{ color: colors.primary }} />}
              onClick={() => showDetails(record)}
            />
          </Tooltip>
        ) : (
          <Text type="secondary">—</Text>
        )
      },
    },
  ]

  return (
    <div>
      {/* Заголовок страницы */}
      <PageHeader
        title="Журнал аудита"
        subtitle={`Всего записей: ${pagination.total}`}
        extra={
          <Button 
            icon={<ReloadOutlined spin={loading} />} 
            onClick={() => loadLogs(pagination.current, pagination.pageSize)}
            loading={loading}
          >
            Обновить
          </Button>
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
                onClick={handleReset}
              >
                Сбросить
              </Button>
            )}
          </Space>
        }
      >
        <Space wrap size="middle">
          <RangePicker
            value={dateRange}
            onChange={(dates) => setDateRange(dates as [Dayjs | null, Dayjs | null])}
            format="DD.MM.YYYY"
            placeholder={['С даты', 'По дату']}
          />
          
          <Select
            value={action}
            onChange={setAction}
            options={actionOptions}
            style={{ width: 150 }}
            placeholder="Действие"
          />
          
          <Select
            value={entityType}
            onChange={setEntityType}
            options={entityTypeOptions}
            style={{ width: 150 }}
            placeholder="Тип сущности"
          />
          
          <Input
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            placeholder="Поиск..."
            style={{ width: 220 }}
            prefix={<SearchOutlined style={{ color: colors.textSecondary }} />}
            onPressEnter={handleSearch}
            allowClear
          />
          
          <Button type="primary" icon={<SearchOutlined />} onClick={handleSearch}>
            Найти
          </Button>
        </Space>
      </Card>

      {/* Таблица записей */}
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={logs}
          loading={loading}
          pagination={{
            ...pagination,
            showSizeChanger: true,
            showQuickJumper: true,
            showTotal: (total, range) => `${range[0]}-${range[1]} из ${total}`,
            pageSizeOptions: ['10', '20', '50', '100'],
          }}
          onChange={handleTableChange}
          rowKey="id"
          size="middle"
          locale={{ emptyText: <EmptyState description="Нет записей аудита" /> }}
        />
      </Card>

      {/* Модальное окно с деталями */}
      <Modal
        title={
          <Space>
            <HistoryOutlined style={{ color: colors.primary }} />
            <span>Детали записи #{detailsModal.record?.id}</span>
          </Space>
        }
        open={detailsModal.open}
        onCancel={() => setDetailsModal({ open: false, record: null })}
        footer={
          <Button onClick={() => setDetailsModal({ open: false, record: null })}>
            Закрыть
          </Button>
        }
        width={750}
      >
        {detailsModal.record && (
          <div>
            {/* Основная информация */}
            <Descriptions 
              bordered 
              size="small" 
              column={2}
              labelStyle={{ fontWeight: 500, width: 130 }}
            >
              <Descriptions.Item label="Время">
                {dayjs(detailsModal.record.createdAt).format('DD.MM.YYYY HH:mm:ss')}
              </Descriptions.Item>
              <Descriptions.Item label="Действие">
                <Tag color={ACTION_COLORS[detailsModal.record.action]}>
                  {ACTION_LABELS[detailsModal.record.action] || detailsModal.record.action}
                </Tag>
              </Descriptions.Item>
              <Descriptions.Item label="Сущность">
                {detailsModal.record.entityType}
                {detailsModal.record.entityId && (
                  <Text type="secondary"> (ID: {detailsModal.record.entityId})</Text>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="IP адрес">
                {detailsModal.record.ipAddress || '—'}
              </Descriptions.Item>
              <Descriptions.Item label="Администратор" span={2}>
                {detailsModal.record.adminEmail || '—'}
              </Descriptions.Item>
            </Descriptions>

            {/* Изменения */}
            {(detailsModal.record.oldValue || detailsModal.record.newValue) && (
              <>
                <Divider orientation="left" style={{ fontSize: 13 }}>
                  <InfoCircleOutlined /> Изменения
                </Divider>

                {detailsModal.record.oldValue && (
                  <div style={{ marginBottom: 16 }}>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      Старое значение:
                    </Text>
                    <Card 
                      size="small" 
                      style={{ 
                        marginTop: 8, 
                        background: '#fff1f0',
                        border: `1px solid ${colors.error}20`,
                      }}
                    >
                      <pre style={{ 
                        margin: 0, 
                        whiteSpace: 'pre-wrap', 
                        fontSize: 12,
                        fontFamily: 'monospace',
                        maxHeight: 200,
                        overflow: 'auto',
                      }}>
                        {formatJson(detailsModal.record.oldValue)}
                      </pre>
                    </Card>
                  </div>
                )}
                
                {detailsModal.record.newValue && (
                  <div>
                    <Text type="secondary" style={{ fontSize: 12 }}>
                      Новое значение:
                    </Text>
                    <Card 
                      size="small" 
                      style={{ 
                        marginTop: 8, 
                        background: '#f6ffed',
                        border: `1px solid ${colors.success}20`,
                      }}
                    >
                      <pre style={{ 
                        margin: 0, 
                        whiteSpace: 'pre-wrap', 
                        fontSize: 12,
                        fontFamily: 'monospace',
                        maxHeight: 200,
                        overflow: 'auto',
                      }}>
                        {formatJson(detailsModal.record.newValue)}
                      </pre>
                    </Card>
                  </div>
                )}
              </>
            )}
          </div>
        )}
      </Modal>
    </div>
  )
}
