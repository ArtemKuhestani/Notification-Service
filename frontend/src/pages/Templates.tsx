/**
 * Templates Page
 * 
 * Страница управления шаблонами сообщений.
 * Позволяет создавать, редактировать и удалять шаблоны для разных каналов.
 * 
 * @features
 * - CRUD операции для шаблонов
 * - Поддержка переменных {{variable}}
 * - Предпросмотр шаблонов
 * - Фильтрация по каналам
 */

import { useState, useEffect, useCallback } from 'react'
import {
  Card,
  Table,
  Button,
  Space,
  Typography,
  Modal,
  Form,
  Input,
  Select,
  Tag,
  message,
  Popconfirm,
  Tooltip,
  Badge,
  Switch,
  Divider,
} from 'antd'
import {
  PlusOutlined,
  EditOutlined,
  DeleteOutlined,
  CopyOutlined,
  EyeOutlined,
  ReloadOutlined,
  FileTextOutlined,
  CodeOutlined,
} from '@ant-design/icons'
import type { ColumnsType } from 'antd/es/table'
import dayjs from 'dayjs'
import { templateApi } from '../api/client'
import { PageHeader, ChannelTag, EmptyState } from '../components/common'
import { colors } from '../theme'
import type { Template, ChannelType } from '../types'

const { Text, Paragraph } = Typography
const { TextArea } = Input

/** Запись шаблона в таблице */
interface TemplateRecord extends Template {
  key: string
}

/** Опции выбора каналов */
const channelOptions = [
  { value: 'EMAIL', label: 'Email' },
  { value: 'TELEGRAM', label: 'Telegram' },
  { value: 'SMS', label: 'SMS' },
  { value: 'WHATSAPP', label: 'WhatsApp' },
]

/**
 * Компонент страницы управления шаблонами
 */
export default function Templates() {
  const [loading, setLoading] = useState(true)
  const [templates, setTemplates] = useState<TemplateRecord[]>([])
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [isPreviewOpen, setIsPreviewOpen] = useState(false)
  const [previewTemplate, setPreviewTemplate] = useState<TemplateRecord | null>(null)
  const [editingTemplate, setEditingTemplate] = useState<TemplateRecord | null>(null)
  const [saving, setSaving] = useState(false)
  const [form] = Form.useForm()

  /**
   * Загружает список шаблонов
   */
  const loadTemplates = useCallback(async () => {
    try {
      setLoading(true)
      const response = await templateApi.getAll()
      const data = Array.isArray(response.data) ? response.data : []
      setTemplates(data.map((t: Template) => ({ ...t, key: String(t.id) })))
    } catch {
      message.error('Ошибка загрузки шаблонов')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadTemplates()
  }, [loadTemplates])

  /** Открывает модальное окно создания шаблона */
  const handleAdd = () => {
    setEditingTemplate(null)
    form.resetFields()
    form.setFieldsValue({ isActive: true })
    setIsModalOpen(true)
  }

  /** Открывает модальное окно редактирования */
  const handleEdit = (record: TemplateRecord) => {
    setEditingTemplate(record)
    form.setFieldsValue({
      name: record.name,
      channelType: record.channelType,
      subject: record.subject,
      body: record.body,
      variables: record.variables?.join(', '),
      isActive: record.isActive,
    })
    setIsModalOpen(true)
  }

  /** Открывает предпросмотр шаблона */
  const handlePreview = (record: TemplateRecord) => {
    setPreviewTemplate(record)
    setIsPreviewOpen(true)
  }

  /** Удаляет шаблон */
  const handleDelete = async (record: TemplateRecord) => {
    try {
      await templateApi.delete(record.id)
      message.success('Шаблон удалён')
      loadTemplates()
    } catch {
      message.error('Ошибка удаления шаблона')
    }
  }

  /** Сохраняет шаблон */
  const handleSave = async () => {
    try {
      setSaving(true)
      const values = await form.validateFields()
      
      const templateData = {
        ...values,
        variables: values.variables
          ? values.variables.split(',').map((v: string) => v.trim()).filter(Boolean)
          : [],
      }

      if (editingTemplate) {
        await templateApi.update(editingTemplate.id, templateData)
        message.success('Шаблон обновлён')
      } else {
        await templateApi.create(templateData)
        message.success('Шаблон создан')
      }

      setIsModalOpen(false)
      loadTemplates()
    } catch {
      message.error('Ошибка сохранения')
    } finally {
      setSaving(false)
    }
  }

  /** Копирует текст в буфер обмена */
  const copyTemplateBody = (body: string) => {
    navigator.clipboard.writeText(body)
    message.success('Текст шаблона скопирован')
  }

  /**
   * Конфигурация колонок таблицы
   */
  const columns: ColumnsType<TemplateRecord> = [
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
        <Space direction="vertical" size={0}>
          <Text strong>{name}</Text>
          {record.subject && (
            <Text type="secondary" style={{ fontSize: 12 }}>
              Тема: {record.subject}
            </Text>
          )}
        </Space>
      ),
    },
    {
      title: 'Канал',
      dataIndex: 'channelType',
      key: 'channelType',
      width: 110,
      render: (channel: ChannelType) => <ChannelTag channel={channel} />,
    },
    {
      title: 'Переменные',
      dataIndex: 'variables',
      key: 'variables',
      width: 200,
      render: (variables: string[]) => (
        variables && variables.length > 0 ? (
          <Space wrap size={[4, 4]}>
            {variables.slice(0, 3).map((v) => (
              <Tooltip key={v} title={`Переменная: ${v}`}>
                <Tag color="purple" style={{ fontSize: 11 }}>{`{{${v}}}`}</Tag>
              </Tooltip>
            ))}
            {variables.length > 3 && (
              <Tooltip title={variables.slice(3).map(v => `{{${v}}}`).join(', ')}>
                <Tag style={{ fontSize: 11 }}>+{variables.length - 3}</Tag>
              </Tooltip>
            )}
          </Space>
        ) : (
          <Text type="secondary" style={{ fontSize: 12 }}>—</Text>
        )
      ),
    },
    {
      title: 'Статус',
      dataIndex: 'isActive',
      key: 'isActive',
      width: 100,
      align: 'center',
      render: (isActive: boolean) => (
        <Badge 
          status={isActive ? 'success' : 'default'}
          text={
            <span style={{ 
              color: isActive ? colors.success : colors.textSecondary,
              fontSize: 13,
            }}>
              {isActive ? 'Активен' : 'Выкл'}
            </span>
          }
        />
      ),
    },
    {
      title: 'Обновлён',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 110,
      render: (date: string) => (
        <Tooltip title={dayjs(date).format('DD.MM.YYYY HH:mm')}>
          <span style={{ fontSize: 12, color: colors.textSecondary }}>
            {dayjs(date).format('DD.MM.YY')}
          </span>
        </Tooltip>
      ),
    },
    {
      title: 'Действия',
      key: 'actions',
      width: 130,
      align: 'center',
      render: (_, record) => (
        <Space size="small">
          <Tooltip title="Просмотр">
            <Button
              type="text"
              size="small"
              icon={<EyeOutlined />}
              onClick={() => handlePreview(record)}
            />
          </Tooltip>
          <Tooltip title="Редактировать">
            <Button
              type="text"
              size="small"
              icon={<EditOutlined />}
              onClick={() => handleEdit(record)}
            />
          </Tooltip>
          <Popconfirm
            title="Удалить шаблон?"
            description="Это действие нельзя отменить"
            onConfirm={() => handleDelete(record)}
            okText="Удалить"
            okType="danger"
            cancelText="Отмена"
          >
            <Tooltip title="Удалить">
              <Button type="text" size="small" danger icon={<DeleteOutlined />} />
            </Tooltip>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  return (
    <div>
      {/* Заголовок страницы */}
      <PageHeader
        title="Шаблоны сообщений"
        subtitle={`Всего шаблонов: ${templates.length}`}
        extra={
          <Space>
            <Button 
              icon={<ReloadOutlined spin={loading} />} 
              onClick={loadTemplates}
              loading={loading}
            >
              Обновить
            </Button>
            <Button type="primary" icon={<PlusOutlined />} onClick={handleAdd}>
              Создать шаблон
            </Button>
          </Space>
        }
      />

      {/* Таблица шаблонов */}
      <Card bodyStyle={{ padding: 0 }}>
        <Table
          columns={columns}
          dataSource={templates}
          loading={loading}
          pagination={{
            showSizeChanger: true,
            showTotal: (total, range) => `${range[0]}-${range[1]} из ${total}`,
            pageSizeOptions: ['10', '20', '50'],
          }}
          rowKey="id"
          size="middle"
          locale={{ emptyText: <EmptyState description="Нет шаблонов" /> }}
        />
      </Card>

      {/* Модальное окно создания/редактирования */}
      <Modal
        title={
          <Space>
            <FileTextOutlined />
            <span>{editingTemplate ? 'Редактирование шаблона' : 'Новый шаблон'}</span>
          </Space>
        }
        open={isModalOpen}
        onOk={handleSave}
        onCancel={() => setIsModalOpen(false)}
        width={700}
        okText="Сохранить"
        cancelText="Отмена"
        confirmLoading={saving}
      >
        <Form form={form} layout="vertical" style={{ marginTop: 16 }}>
          <Form.Item
            name="name"
            label="Название шаблона"
            rules={[
              { required: true, message: 'Введите название' },
              { min: 2, message: 'Минимум 2 символа' },
            ]}
          >
            <Input placeholder="Например: Приветственное письмо" />
          </Form.Item>

          <Form.Item
            name="channelType"
            label="Канал доставки"
            rules={[{ required: true, message: 'Выберите канал' }]}
          >
            <Select options={channelOptions} placeholder="Выберите канал" />
          </Form.Item>

          <Form.Item 
            name="subject" 
            label="Тема сообщения"
            tooltip="Обязательно для Email, опционально для других каналов"
          >
            <Input placeholder="Тема письма или заголовок" />
          </Form.Item>

          <Form.Item
            name="body"
            label="Текст сообщения"
            rules={[{ required: true, message: 'Введите текст сообщения' }]}
            extra={
              <span style={{ fontSize: 12 }}>
                <CodeOutlined /> Используйте <code>{'{{переменная}}'}</code> для динамических значений
              </span>
            }
          >
            <TextArea 
              rows={8} 
              placeholder="Здравствуйте, {{name}}!&#10;&#10;Ваш заказ №{{orderNumber}} оформлен.&#10;&#10;С уважением,&#10;Команда {{companyName}}"
              style={{ fontFamily: 'monospace', fontSize: 13 }}
            />
          </Form.Item>

          <Form.Item
            name="variables"
            label="Переменные шаблона"
            tooltip="Список переменных, которые можно использовать в шаблоне"
            extra="Укажите через запятую: name, email, orderNumber"
          >
            <Input placeholder="name, email, orderNumber, companyName" />
          </Form.Item>

          <Divider style={{ margin: '16px 0' }} />

          <Form.Item 
            name="isActive" 
            label="Активный шаблон" 
            valuePropName="checked"
          >
            <Switch checkedChildren="Да" unCheckedChildren="Нет" />
          </Form.Item>
        </Form>
      </Modal>

      {/* Модальное окно предпросмотра */}
      <Modal
        title={
          <Space>
            <EyeOutlined />
            <span>Шаблон: {previewTemplate?.name}</span>
          </Space>
        }
        open={isPreviewOpen}
        onCancel={() => setIsPreviewOpen(false)}
        footer={[
          <Button
            key="copy"
            icon={<CopyOutlined />}
            onClick={() => previewTemplate && copyTemplateBody(previewTemplate.body)}
          >
            Копировать текст
          </Button>,
          <Button 
            key="edit" 
            type="primary"
            icon={<EditOutlined />}
            onClick={() => {
              setIsPreviewOpen(false)
              previewTemplate && handleEdit(previewTemplate)
            }}
          >
            Редактировать
          </Button>,
        ]}
        width={650}
      >
        {previewTemplate && (
          <div>
            {/* Метаинформация */}
            <Space style={{ marginBottom: 16 }}>
              <ChannelTag channel={previewTemplate.channelType as ChannelType} />
              <Badge 
                status={previewTemplate.isActive ? 'success' : 'default'}
                text={previewTemplate.isActive ? 'Активен' : 'Неактивен'}
              />
            </Space>

            {/* Тема */}
            {previewTemplate.subject && (
              <div style={{ marginBottom: 16 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>Тема:</Text>
                <Paragraph strong style={{ margin: '4px 0 0' }}>
                  {previewTemplate.subject}
                </Paragraph>
              </div>
            )}

            {/* Текст сообщения */}
            <Text type="secondary" style={{ fontSize: 12 }}>Текст сообщения:</Text>
            <Card 
              size="small" 
              style={{ 
                marginTop: 8, 
                background: colors.bgGrey,
                border: `1px solid ${colors.border}`,
              }}
            >
              <pre style={{ 
                whiteSpace: 'pre-wrap', 
                margin: 0,
                fontFamily: 'monospace',
                fontSize: 13,
                lineHeight: 1.6,
              }}>
                {previewTemplate.body}
              </pre>
            </Card>

            {/* Переменные */}
            {previewTemplate.variables?.length > 0 && (
              <div style={{ marginTop: 16 }}>
                <Text type="secondary" style={{ fontSize: 12 }}>
                  Доступные переменные:
                </Text>
                <div style={{ marginTop: 8 }}>
                  <Space wrap>
                    {previewTemplate.variables.map((v) => (
                      <Tooltip key={v} title="Нажмите для копирования">
                        <Tag 
                          color="purple" 
                          style={{ cursor: 'pointer' }}
                          onClick={() => {
                            navigator.clipboard.writeText(`{{${v}}}`)
                            message.success(`{{${v}}} скопировано`)
                          }}
                        >
                          {`{{${v}}}`}
                        </Tag>
                      </Tooltip>
                    ))}
                  </Space>
                </div>
              </div>
            )}

            {/* Даты */}
            <Divider style={{ margin: '16px 0 8px' }} />
            <Space split={<span style={{ color: colors.border }}>|</span>}>
              <Text type="secondary" style={{ fontSize: 11 }}>
                Создан: {dayjs(previewTemplate.createdAt).format('DD.MM.YYYY HH:mm')}
              </Text>
              <Text type="secondary" style={{ fontSize: 11 }}>
                Обновлён: {dayjs(previewTemplate.updatedAt).format('DD.MM.YYYY HH:mm')}
              </Text>
            </Space>
          </div>
        )}
      </Modal>
    </div>
  )
}
