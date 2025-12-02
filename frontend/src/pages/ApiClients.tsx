/**
 * API Clients Page
 * 
 * Страница управления API-клиентами системы уведомлений.
 * Позволяет создавать, редактировать, удалять клиентов и управлять API-ключами.
 * 
 * @features
 * - CRUD операции для API-клиентов
 * - Генерация и регенерация API-ключей
 * - Управление лимитами запросов
 * - Активация/деактивация клиентов
 */

import { useState, useEffect, useCallback } from 'react'
import {
  Table,
  Button,
  Space,
  Tag,
  Typography,
  Modal,
  Form,
  Input,
  message,
  Popconfirm,
  Tooltip,
  Card,
  Switch,
  Alert,
  InputNumber,
  Badge,
} from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  KeyOutlined,
  CopyOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  WarningOutlined,
  SafetyOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import relativeTime from 'dayjs/plugin/relativeTime'
import 'dayjs/locale/ru'
import { clientApi } from '../api/client'
import { PageLoader, PageHeader, EmptyState } from '../components/common'
import { colors } from '../theme'
import type { ApiClient } from '../types'

// Настройка dayjs для относительного времени
dayjs.extend(relativeTime)
dayjs.locale('ru')

const { Text } = Typography

/** Тип записи таблицы API-клиентов */
interface ApiClientRecord extends ApiClient {
  key: string
}

/**
 * Компонент страницы управления API-клиентами
 */
export default function ApiClients() {
  const [loading, setLoading] = useState(true)
  const [clients, setClients] = useState<ApiClientRecord[]>([])
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [isKeyModalOpen, setIsKeyModalOpen] = useState(false)
  const [editingClient, setEditingClient] = useState<ApiClientRecord | null>(null)
  const [generatedKey, setGeneratedKey] = useState<string>('')
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()

  /**
   * Загружает список API-клиентов
   */
  const loadClients = useCallback(async () => {
    try {
      setLoading(true)
      const response = await clientApi.getAll()
      const data = Array.isArray(response.data) ? response.data : [response.data]
      setClients(data.map((c: ApiClient) => ({ 
        ...c, 
        key: String(c.id),
      })))
    } catch {
      message.error('Ошибка загрузки клиентов')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadClients()
  }, [loadClients])

  /** Открывает модальное окно для создания клиента */
  const handleAdd = () => {
    setEditingClient(null)
    form.resetFields()
    form.setFieldsValue({ active: true, rateLimit: 100 })
    setIsModalOpen(true)
  }

  /** Открывает модальное окно для редактирования клиента */
  const handleEdit = (record: ApiClientRecord) => {
    setEditingClient(record)
    form.setFieldsValue({
      clientName: record.clientName,
      description: record.description,
      rateLimit: record.rateLimit,
      active: record.active,
    })
    setIsModalOpen(true)
  }

  /** Удаляет клиента */
  const handleDelete = async (record: ApiClientRecord) => {
    try {
      await clientApi.delete(record.id)
      message.success('Клиент удалён')
      loadClients()
    } catch {
      message.error('Ошибка удаления клиента')
    }
  }

  /** Регенерирует API-ключ */
  const handleRegenerateKey = async (record: ApiClientRecord) => {
    Modal.confirm({
      title: 'Перегенерировать API-ключ?',
      icon: <WarningOutlined style={{ color: colors.warning }} />,
      content: (
        <div>
          <p>Старый ключ перестанет работать немедленно.</p>
          <p>Убедитесь, что обновите ключ во всех системах, использующих этого клиента.</p>
        </div>
      ),
      okText: 'Перегенерировать',
      okType: 'danger',
      cancelText: 'Отмена',
      onOk: async () => {
        try {
          const response = await clientApi.regenerateKey(record.id)
          setGeneratedKey(response.data.apiKey)
          setIsKeyModalOpen(true)
        } catch {
          message.error('Ошибка генерации ключа')
        }
      },
    })
  }

  /** Сохраняет клиента (создание или обновление) */
  const handleSave = async () => {
    try {
      setSaving(true)
      const values = await form.validateFields()
      
      if (editingClient) {
        await clientApi.update(editingClient.id, values)
        message.success('Клиент обновлён')
      } else {
        const response = await clientApi.create(values)
        setGeneratedKey(response.data.apiKey)
        setIsKeyModalOpen(true)
        message.success('Клиент создан')
      }
      
      setIsModalOpen(false)
      loadClients()
    } catch {
      message.error('Ошибка сохранения')
    } finally {
      setSaving(false)
    }
  }

  /** Копирует текст в буфер обмена */
  const copyToClipboard = (text: string) => {
    navigator.clipboard.writeText(text)
    message.success('Скопировано в буфер обмена')
  }

  /**
   * Конфигурация колонок таблицы
   */
  const columns: ColumnsType<ApiClientRecord> = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 60,
      align: 'center',
    },
    {
      title: 'Название',
      dataIndex: 'name',
      key: 'name',
      render: (name: string, record) => (
        <div>
          <Space>
            <Text strong>{name}</Text>
            {!record.active && (
              <Tag color="default" style={{ fontSize: 10 }}>неактивен</Tag>
            )}
          </Space>
          {record.description && (
            <>
              <br />
              <Text type="secondary" style={{ fontSize: 12 }}>
                {record.description}
              </Text>
            </>
          )}
        </div>
      ),
    },
    {
      title: 'Статус',
      dataIndex: 'active',
      key: 'active',
      width: 110,
      align: 'center',
      render: (active: boolean) => (
        <Badge 
          status={active ? 'success' : 'default'} 
          text={
            <span style={{ 
              color: active ? colors.success : colors.textSecondary 
            }}>
              {active ? 'Активен' : 'Неактивен'}
            </span>
          }
        />
      ),
    },
    {
      title: 'API Key',
      dataIndex: 'apiKeyPrefix',
      key: 'apiKeyPrefix',
      width: 130,
      render: (prefix: string) => (
        <Tooltip title="Префикс ключа (полный ключ скрыт)">
          <code style={{ 
            padding: '2px 8px', 
            background: colors.bgGrey,
            borderRadius: 4,
            fontSize: 12,
          }}>
            {prefix}***
          </code>
        </Tooltip>
      ),
    },
    {
      title: 'Лимит',
      dataIndex: 'rateLimit',
      key: 'rateLimit',
      width: 120,
      align: 'center',
      render: (limit: number) => (
        <Tag color="blue">{limit} req/min</Tag>
      ),
    },
    {
      title: 'Последняя активность',
      dataIndex: 'lastUsedAt',
      key: 'lastUsedAt',
      width: 160,
      render: (date: string | null) => (
        date ? (
          <Tooltip title={dayjs(date).format('DD.MM.YYYY HH:mm:ss')}>
            <span style={{ color: colors.textSecondary }}>
              {dayjs(date).fromNow()}
            </span>
          </Tooltip>
        ) : (
          <Text type="secondary">Никогда</Text>
        )
      ),
    },
    {
      title: 'Действия',
      key: 'actions',
      width: 140,
      align: 'center',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="Редактировать">
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            />
          </Tooltip>
          <Tooltip title="Перегенерировать ключ">
            <Button
              type="text"
              size="small"
              icon={<KeyOutlined style={{ color: colors.warning }} />}
              onClick={() => handleRegenerateKey(record)}
            />
          </Tooltip>
          <Popconfirm
            title="Удалить клиента?"
            description="Все уведомления этого клиента будут сохранены"
            onConfirm={() => handleDelete(record)}
            okText="Удалить"
            okType="danger"
            cancelText="Отмена"
            icon={<DeleteOutlined style={{ color: colors.error }} />}
          >
            <Tooltip title="Удалить">
              <Button type="text" size="small" danger icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  // Лоадер при первой загрузке
  if (loading && clients.length === 0) {
    return <PageLoader />
  }

  return (
    <div>
      {/* Заголовок страницы */}
      <PageHeader
        title="API-клиенты"
        subtitle={`Всего клиентов: ${clients.length}`}
        extra={
          <Space>
            <Button 
              icon={<ReloadOutlined spin={loading} />} 
              onClick={loadClients}
              loading={loading}
            >
              Обновить
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
              Добавить клиента
            </Button>
          </Space>
        }
      />

      {/* Таблица клиентов */}
      <Card bodyStyle={{ padding: 0 }}>
        <Table 
          columns={columns} 
          dataSource={clients} 
          pagination={clients.length > 10 ? { pageSize: 10 } : false} 
          size="middle" 
          loading={loading}
          locale={{ emptyText: <EmptyState description="Нет API-клиентов" /> }}
        />
      </Card>

      {/* Модальное окно создания/редактирования */}
      <Modal
        title={
          <Space>
            {editingClient ? <EditOutlined /> : <PlusOutlined />}
            <span>{editingClient ? 'Редактировать клиента' : 'Новый API-клиент'}</span>
          </Space>
        }
        open={isModalOpen}
        onOk={handleSave}
        onCancel={() => setIsModalOpen(false)}
        okText="Сохранить"
        cancelText="Отмена"
        confirmLoading={saving}
        width={500}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="Название системы"
            rules={[
              { required: true, message: 'Введите название' },
              { min: 2, message: 'Минимум 2 символа' },
            ]}
          >
            <Input placeholder="Например: CRM System" />
          </Form.Item>
          
          <Form.Item name="description" label="Описание">
            <Input.TextArea 
              placeholder="Краткое описание системы-клиента" 
              rows={3}
              showCount
              maxLength={500}
            />
          </Form.Item>
          
          <Form.Item
            name="rateLimit"
            label="Лимит запросов (в минуту)"
            tooltip="Максимальное количество запросов от этого клиента в минуту"
            rules={[{ required: true, message: 'Укажите лимит' }]}
          >
            <InputNumber 
              min={1} 
              max={10000} 
              style={{ width: '100%' }}
              addonAfter="req/min"
            />
          </Form.Item>
          
          <Form.Item 
            name="active" 
            label="Статус" 
            valuePropName="checked"
          >
            <Switch 
              checkedChildren={<CheckCircleOutlined />}
              unCheckedChildren={<CloseCircleOutlined />}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* Модальное окно с API-ключом */}
      <Modal
        title={
          <Space>
            <SafetyOutlined style={{ color: colors.success }} />
            <span>API-ключ создан</span>
          </Space>
        }
        open={isKeyModalOpen}
        onOk={() => setIsKeyModalOpen(false)}
        onCancel={() => setIsKeyModalOpen(false)}
        okText="Готово"
        cancelButtonProps={{ style: { display: 'none' } }}
        closable={false}
        maskClosable={false}
      >
        <Alert
          type="warning"
          icon={<WarningOutlined />}
          message="Сохраните этот ключ!"
          description="Ключ будет показан только один раз. После закрытия окна его нельзя будет просмотреть."
          style={{ marginBottom: 16 }}
          showIcon
        />
        
        <Card 
          size="small"
          style={{ 
            background: colors.bgGrey,
            border: `1px dashed ${colors.border}`,
          }}
        >
          <div
            style={{
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'space-between',
              gap: 12,
            }}
          >
            <code style={{ 
              fontSize: 13, 
              wordBreak: 'break-all',
              flex: 1,
              padding: '8px 12px',
              background: '#fff',
              borderRadius: 4,
              border: `1px solid ${colors.border}`,
            }}>
              {generatedKey}
            </code>
            <Tooltip title="Копировать">
              <Button
                type="primary"
                icon={<CopyOutlined />}
                onClick={() => copyToClipboard(generatedKey)}
              >
                Копировать
              </Button>
            </Tooltip>
          </div>
        </Card>
        
        <div style={{ marginTop: 16, color: colors.textSecondary, fontSize: 12 }}>
          <p><strong>Использование:</strong></p>
          <code style={{ 
            display: 'block',
            padding: 8,
            background: colors.bgGrey,
            borderRadius: 4,
            fontSize: 11,
          }}>
            X-API-Key: {generatedKey.slice(0, 20)}...
          </code>
        </div>
      </Modal>
    </div>
  )
}
