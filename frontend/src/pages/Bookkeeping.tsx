import { useEffect, useMemo, useState } from 'react'
import type { CSSProperties, ReactNode } from 'react'
import { useLocation } from 'react-router-dom'
import {
  Alert,
  Button,
  Form,
  Input,
  InputNumber,
  message,
  QRCode,
  Select,
  Space,
  Switch,
  Table,
  Tag,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  CheckCircleOutlined,
  CloudSyncOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  FileExcelOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { apiService } from '../services/api'

const { Title, Text } = Typography

type ApiResponse<T> = { code: number; data: T; msg: string }
type Money = number | string
type WorkspaceType = 'prematch' | 'rolling'
type ReportType =
  | 'daily'
  | 'crown_wagers'
  | 'downstream_before_rebate'
  | 'downstream_after_rebate'
  | 'upstream_orders'
  | 'company_orders'
  | 'prematch_profit'
  | 'prematch_excel'
  | 'rolling_group_orders'
  | 'rolling_profit'
  | 'rolling_excel'
type ReportAction = { workspaceType: WorkspaceType; reportType: ReportType; label: string }

type CrownAccount = {
  id?: number
  accountKey: string
  displayName: string
  baseUrl: string
  username: string
  passwordConfigured?: boolean
  enabled: boolean
  timezone?: string
  lastLoginStatus?: string
  lastLoginMessage?: string
  lastLoginAt?: number
}

type WhatsappGroup = {
  id?: number
  groupKey: string
  sourceChatId?: string
  displayName: string
  chatName: string
  role: 'pending' | 'upstream' | 'downstream' | 'company_follow' | 'rolling' | 'ignored'
  currency: 'USDT' | 'RMB' | string
  exchangeRate: Money
  rebatePoints: Money
  lastScannedMessageId?: string
  configured?: boolean
  enabled: boolean
}

type CrownWager = {
  id?: number
  accountId: number
  businessDate: string
  ticketId: string
  leagueName?: string
  homeTeam?: string
  awayTeam?: string
  marketType?: string
  selectionName?: string
  oddsValue?: Money
  stakeAmount: Money
  winLossAmount: Money
  status: string
}

type WhatsappOrder = {
  id?: number
  businessDate: string
  orderKey: string
  direction: 'upstream' | 'downstream' | 'company_follow' | 'rolling'
  senderName?: string
  rawMessage: string
  leagueName?: string
  matchName?: string
  marketText?: string
  oddsValue?: Money
  amount?: Money
  parseStatus: string
  settlementResult?: string
}

type ReconciliationResult = {
  id?: number
  matchStatus: string
  issueType?: string
  crownWagerId?: number
  whatsappOrderId?: number
  amountDiff?: Money
  oddsDiff?: Money
  profitAmount: Money
  notes?: string
}

type BookkeepingTask = {
  id?: number
  taskKey: string
  workspaceType: WorkspaceType
  businessDate: string
  status: string
  resultSummaryJson?: string
  excelPath?: string
  createdAt: number
}

type Summary = {
  crownAccountTotal: number
  crownSuccessCount: number
  crownFailedCount: number
  crownManualCount: number
  crownUntestedCount: number
  crownTurnover: Money
  settledWinLoss: Money
  unsettledAmount: Money
  whatsappOrderCount: number
  upstreamValidCount: number
  downstreamValidCount: number
  companyFollowCount: number
  companyFollowAmount: Money
  suspiciousCount: number
  cancelledCount: number
  differenceCount: number
  todayProfit: Money
  upstreamTotalStake?: Money
  downstreamTotalStake?: Money
  downstreamRebateAmount?: Money
  upstreamCashflow?: Money
  downstreamCashflow?: Money
  waterLossAmount?: Money
  grossProfit?: Money
  companyNetProfit?: Money
  rollingGroupStake?: Money
  rollingGroupSettlement?: Money
  rollingProfitDiff?: Money
}

type Dashboard = {
  businessDate: string
  summary: Summary
  crownAccounts: CrownAccount[]
  whatsappGroups: WhatsappGroup[]
  crownWagers: CrownWager[]
  whatsappOrders: WhatsappOrder[]
  reconciliationResults: ReconciliationResult[]
  tasks: BookkeepingTask[]
}

type WhatsappSyncResult = {
  connected: boolean
  status: string
  message: string
  groups: WhatsappGroup[]
}

type WhatsappStatus = {
  connected: boolean
  status: string
  message: string
  qr?: string
}

type WhatsappScanResult = {
  connected: boolean
  status: string
  message: string
  workspaceType: WorkspaceType
  businessDate: string
  scannedGroupCount: number
  scannedMessageCount: number
  importedCount: number
  updatedCount: number
}

type GeneratedFileRow = {
  key: string
  taskId?: number
  fileType: string
  fileName: string
  createdAt?: number
  status: string
  excelPath?: string
}

type ClearGeneratedFilesResult = {
  deletedFileCount: number
  deletedTaskCount: number
}

const emptySummary: Summary = {
  crownAccountTotal: 0,
  crownSuccessCount: 0,
  crownFailedCount: 0,
  crownManualCount: 0,
  crownUntestedCount: 0,
  crownTurnover: 0,
  settledWinLoss: 0,
  unsettledAmount: 0,
  whatsappOrderCount: 0,
  upstreamValidCount: 0,
  downstreamValidCount: 0,
  companyFollowCount: 0,
  companyFollowAmount: 0,
  suspiciousCount: 0,
  cancelledCount: 0,
  differenceCount: 0,
  todayProfit: 0,
  upstreamTotalStake: 0,
  downstreamTotalStake: 0,
  downstreamRebateAmount: 0,
  upstreamCashflow: 0,
  downstreamCashflow: 0,
  waterLossAmount: 0,
  grossProfit: 0,
  companyNetProfit: 0,
  rollingGroupStake: 0,
  rollingGroupSettlement: 0,
  rollingProfitDiff: 0,
}

const emptyDashboard: Dashboard = {
  businessDate: new Date().toISOString().slice(0, 10),
  summary: emptySummary,
  crownAccounts: [],
  whatsappGroups: [],
  crownWagers: [],
  whatsappOrders: [],
  reconciliationResults: [],
  tasks: [],
}

const pageSurface: CSSProperties = {
  background: '#fff',
  border: '1px solid #dfe5ef',
  borderRadius: 8,
  boxShadow: '0 1px 2px rgba(15, 23, 42, 0.03)',
}

const panelBody: CSSProperties = {
  padding: 18,
}

const panelTitle: CSSProperties = {
  padding: '14px 18px',
  borderBottom: '1px solid #edf1f7',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'space-between',
  gap: 12,
}

const pageContainer: CSSProperties = {
  width: '100%',
  maxWidth: 1480,
  margin: '0 auto',
  overflowX: 'hidden',
}

const moneyText = (value?: Money) => {
  const numeric = Number(value ?? 0)
  return Number.isFinite(numeric)
    ? numeric.toLocaleString('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 })
    : String(value ?? '0.00')
}

const timeText = (value?: number) => value ? new Date(value).toLocaleString('zh-CN') : '-'

const statusColor = (status?: string) => {
  if (status === 'success' || status === 'completed' || status === 'matched' || status === 'parsed') return 'green'
  if (status === 'failed' || status === 'difference') return 'red'
  if (status === 'manual_required' || status === 'running' || status === 'suspicious') return 'gold'
  return 'default'
}

const issueText: Record<string, string> = {
  matched: '正常',
  amount_mismatch: '金额不一致',
  odds_mismatch: '赔率不一致',
  missing_crown: 'Crown缺单',
  missing_whatsapp: 'WhatsApp缺单',
}

const roleText: Record<string, string> = {
  pending: '待设置',
  upstream: '上游群',
  downstream: '下游群',
  company_follow: '公司跟单群',
  rolling: '滚球群',
  ignored: '忽略群',
}

const effectiveRole = (group: WhatsappGroup) => group.configured === false ? 'pending' : group.role

const roleColor = (role: string) => {
  if (role === 'pending') return 'gold'
  if (role === 'upstream') return 'blue'
  if (role === 'downstream') return 'green'
  if (role === 'company_follow') return 'purple'
  if (role === 'rolling') return 'cyan'
  return 'default'
}

const directionText = (direction: string) => {
  if (direction === 'upstream') return '上游'
  if (direction === 'downstream') return '下游'
  if (direction === 'company_follow') return '公司跟单'
  if (direction === 'rolling') return '滚球'
  return direction
}

const preMatchReportActions: ReportAction[] = [
  { workspaceType: 'prematch', reportType: 'upstream_orders', label: '生成上游群账单' },
  { workspaceType: 'prematch', reportType: 'downstream_before_rebate', label: '生成下游群账单（退水前）' },
  { workspaceType: 'prematch', reportType: 'downstream_after_rebate', label: '生成下游群账单（退水后）' },
  { workspaceType: 'prematch', reportType: 'company_orders', label: '生成公司跟单表' },
  { workspaceType: 'prematch', reportType: 'prematch_profit', label: '生成公司总盈亏表' },
  { workspaceType: 'prematch', reportType: 'prematch_excel', label: '一键生成全部文件' },
]

const rollingReportActions: ReportAction[] = [
  { workspaceType: 'rolling', reportType: 'rolling_group_orders', label: '生成滚球群账单' },
  { workspaceType: 'rolling', reportType: 'crown_wagers', label: '生成皇冠注单表' },
  { workspaceType: 'rolling', reportType: 'rolling_profit', label: '生成滚球盈亏表' },
  { workspaceType: 'rolling', reportType: 'rolling_excel', label: '一键生成全部文件' },
]

const reportTypeLabels: Record<string, string> = {
  upstream_orders: '上游群账单',
  downstream_before_rebate: '下游群账单（退水前）',
  downstream_after_rebate: '下游群账单（退水后）',
  company_orders: '公司跟单表',
  prematch_profit: '公司总盈亏表',
  prematch_excel: '全部文件',
  rolling_group_orders: '滚球群账单',
  crown_wagers: '皇冠注单表',
  rolling_profit: '滚球盈亏表',
  rolling_excel: '全部文件',
}

const reportTypeMarkers = Object.keys(reportTypeLabels).sort((a, b) => b.length - a.length)

const getPageKey = (pathname: string) => {
  const clean = pathname.replace(/\/$/, '')
  if (clean.startsWith('/bookkeeping/rolling')) return 'rolling'
  if (clean.startsWith('/bookkeeping/crown/accounts')) return 'crownAccounts'
  if (clean.startsWith('/bookkeeping/whatsapp/groups')) return 'whatsappGroups'
  if (clean.startsWith('/bookkeeping/crown/wagers')) return 'crownWagers'
  if (clean.startsWith('/bookkeeping/whatsapp/orders')) return 'whatsappOrders'
  if (clean.startsWith('/bookkeeping/reconciliation')) return 'reconciliation'
  if (clean.startsWith('/bookkeeping/excel')) return 'excel'
  return 'dashboard'
}

const Bookkeeping = () => {
  const location = useLocation()
  const pageKey = getPageKey(location.pathname)
  const [dashboard, setDashboard] = useState<Dashboard>(emptyDashboard)
  const [businessDate, setBusinessDate] = useState(emptyDashboard.businessDate)
  const [scanStart, setScanStart] = useState('09:00')
  const [scanEnd, setScanEnd] = useState('23:59')
  const [loading, setLoading] = useState(false)
  const [runningReport, setRunningReport] = useState<string | null>(null)
  const [scanningWhatsapp, setScanningWhatsapp] = useState<string | null>(null)
  const [clearingGeneratedFiles, setClearingGeneratedFiles] = useState(false)
  const [syncingWhatsapp, setSyncingWhatsapp] = useState(false)
  const [whatsappSyncMessage, setWhatsappSyncMessage] = useState('')
  const [whatsappStatus, setWhatsappStatus] = useState<WhatsappStatus | null>(null)
  const [crownForm] = Form.useForm()
  const [groupForm] = Form.useForm()

  const loadDashboard = async (date = businessDate) => {
    const workspaceType: WorkspaceType = pageKey === 'rolling' ? 'rolling' : 'prematch'
    setLoading(true)
    try {
      const response = await apiService.bookkeeping.dashboard({ businessDate: date, workspaceType }) as { data: ApiResponse<Dashboard> }
      if (response.data.code === 0 && response.data.data) {
        setDashboard(response.data.data)
      } else {
        message.error(response.data.msg || '读取做账数据失败')
      }
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadDashboard()
  }, [pageKey])

  const loadWhatsappStatus = async () => {
    const response = await apiService.bookkeeping.whatsappStatus() as { data: ApiResponse<WhatsappStatus> }
    if (response.data.code === 0 && response.data.data) {
      setWhatsappStatus(response.data.data)
      return
    }
    setWhatsappStatus({ connected: false, status: 'unknown', message: response.data.msg || '读取 WhatsApp 状态失败' })
  }

  useEffect(() => {
    if (pageKey === 'whatsappGroups') {
      loadWhatsappStatus()
    }
  }, [pageKey])

  const runTask = async (action: ReportAction) => {
    const runningKey = `${action.workspaceType}:${action.reportType}`
    setRunningReport(runningKey)
    try {
      const response = await apiService.bookkeeping.runTask({
        businessDate,
        workspaceType: action.workspaceType,
        reportType: action.reportType,
      }) as { data: ApiResponse<BookkeepingTask> }
      if (response.data.code === 0) {
        message.success(`${action.label}已生成`)
        await loadDashboard()
      } else {
        message.error(response.data.msg || '生成失败')
      }
    } finally {
      setRunningReport(null)
    }
  }

  const saveCrownAccount = async () => {
    const values = await crownForm.validateFields()
    const response = await apiService.bookkeeping.saveCrownAccount(values) as { data: ApiResponse<CrownAccount> }
    if (response.data.code === 0) {
      message.success('Crown账号已保存')
      crownForm.resetFields()
      await loadDashboard()
      return
    }
    message.error(response.data.msg || '保存失败')
  }

  const editCrownAccount = (row: CrownAccount) => {
    crownForm.setFieldsValue({
      id: row.id,
      accountKey: row.accountKey,
      displayName: row.displayName,
      baseUrl: row.baseUrl,
      username: row.username,
      enabled: row.enabled,
      timezone: row.timezone || 'GMT-4',
      password: '',
    })
  }

  const saveWhatsappGroup = async () => {
    const values = await groupForm.validateFields()
    const response = await apiService.bookkeeping.saveWhatsappGroup({
      ...values,
      exchangeRate: Number(values.exchangeRate ?? 1),
      rebatePoints: Number(values.rebatePoints ?? 0),
      rebateRate: 0,
      rebateRule: 'none',
    }) as { data: ApiResponse<WhatsappGroup> }
    if (response.data.code === 0) {
      message.success('WhatsApp群已保存')
      groupForm.resetFields()
      await loadDashboard()
      return
    }
    message.error(response.data.msg || '保存失败')
  }

  const editWhatsappGroup = (row: WhatsappGroup) => {
    groupForm.setFieldsValue({
      id: row.id,
      groupKey: row.groupKey,
      sourceChatId: row.sourceChatId,
      displayName: row.displayName,
      chatName: row.chatName,
      role: effectiveRole(row),
      currency: row.currency || 'USDT',
      exchangeRate: Number(row.exchangeRate ?? 1),
      rebatePoints: Number(row.rebatePoints ?? 0),
      enabled: row.enabled,
    })
  }

  const syncWhatsappChats = async () => {
    setSyncingWhatsapp(true)
    try {
      const response = await apiService.bookkeeping.syncWhatsappChats() as { data: ApiResponse<WhatsappSyncResult> }
      if (response.data.code === 0 && response.data.data) {
        const result = response.data.data
        setWhatsappSyncMessage(result.message)
        setDashboard((current) => ({
          ...current,
          whatsappGroups: result.groups || current.whatsappGroups,
        }))
        if (result.connected) {
          message.success(result.message || 'WhatsApp群聊已同步')
        } else {
          message.warning(result.message || '没有读取到本机WhatsApp群聊')
        }
        await loadWhatsappStatus()
        await loadDashboard()
        return
      }
      message.error(response.data.msg || '同步失败')
    } finally {
      setSyncingWhatsapp(false)
    }
  }

  const testCrownLogin = async (row: CrownAccount) => {
    if (!row.id) return
    const response = await apiService.bookkeeping.testCrownLogin({ accountId: row.id }) as {
      data: ApiResponse<{ ok: boolean; message: string }>
    }
    if (response.data.code === 0 && response.data.data?.ok) {
      message.success('Crown登录成功')
    } else {
      message.error(response.data.data?.message || response.data.msg || 'Crown登录失败')
    }
    await loadDashboard()
  }

  const upstreamOrders = useMemo(
    () => dashboard.whatsappOrders.filter((order) => order.direction === 'upstream'),
    [dashboard.whatsappOrders]
  )

  const downstreamOrders = useMemo(
    () => dashboard.whatsappOrders.filter((order) => order.direction === 'downstream'),
    [dashboard.whatsappOrders]
  )

  const companyFollowOrders = useMemo(
    () => dashboard.whatsappOrders.filter((order) => order.direction === 'company_follow'),
    [dashboard.whatsappOrders]
  )

  const sumOrderAmount = (orders: WhatsappOrder[]) =>
    orders.reduce((total, order) => total + Number(order.amount ?? 0), 0)

  const valueOrFallback = (value: Money | undefined, fallback: number) => {
    const numeric = Number(value)
    return Number.isFinite(numeric) ? numeric : fallback
  }

  const upstreamStake = valueOrFallback(dashboard.summary.upstreamTotalStake, sumOrderAmount(upstreamOrders))
  const downstreamStake = valueOrFallback(
    dashboard.summary.downstreamTotalStake,
    sumOrderAmount(downstreamOrders) + sumOrderAmount(companyFollowOrders)
  )
  const companyFollowStake = valueOrFallback(
    dashboard.summary.companyFollowAmount,
    Math.max(0, downstreamStake - upstreamStake)
  )
  const downstreamRebateAmount = valueOrFallback(dashboard.summary.downstreamRebateAmount, 0)
  const waterLossAmount = valueOrFallback(dashboard.summary.waterLossAmount, 0)
  const companyNetProfit = valueOrFallback(dashboard.summary.companyNetProfit, Number(dashboard.summary.todayProfit ?? 0))
  const rollingGroupStake = valueOrFallback(dashboard.summary.rollingGroupStake, 0)
  const rollingGroupSettlement = valueOrFallback(dashboard.summary.rollingGroupSettlement, 0)

  const preMatchSummaryItems = useMemo(() => [
    { label: '上游总下注额', value: moneyText(upstreamStake), hint: '所有上游确认订单金额' },
    { label: '下游总投放额', value: moneyText(downstreamStake), hint: '所有下游确认订单金额' },
    { label: '公司跟单额', value: moneyText(companyFollowStake), hint: '下游总投放额减上游总下注额' },
    { label: '下游退水金额', value: moneyText(downstreamRebateAmount), hint: '只统计下游赢单和赢半' },
    { label: '盈亏水金额', value: moneyText(waterLossAmount), hint: '正数为赚水，负数为亏水' },
    { label: '公司总盈利', value: moneyText(companyNetProfit), hint: '包含公司跟单和盈亏水' },
  ], [upstreamStake, downstreamStake, companyFollowStake, downstreamRebateAmount, waterLossAmount, companyNetProfit])

  const rollingSummaryItems = useMemo(() => [
    { label: '滚球群总投注额', value: moneyText(rollingGroupStake), hint: '群里要求下注金额' },
    { label: '皇冠总下注额', value: moneyText(dashboard.summary.crownTurnover), hint: '皇冠账号实际下注金额' },
    { label: '滚球群结算盈亏', value: moneyText(rollingGroupSettlement), hint: '按滚球群订单算出的盈亏' },
    { label: '皇冠实际盈亏', value: moneyText(dashboard.summary.settledWinLoss), hint: '皇冠真实注单盈亏' },
    { label: '公司滚球净利润', value: moneyText(companyNetProfit), hint: '皇冠实际盈亏减滚球群结算盈亏' },
  ], [dashboard.summary.crownTurnover, dashboard.summary.settledWinLoss, rollingGroupStake, rollingGroupSettlement, companyNetProfit])

  const generatedFileRows = (workspaceType: WorkspaceType): GeneratedFileRow[] =>
    dashboard.tasks
      .filter((task) => task.workspaceType === workspaceType)
      .flatMap((task) => {
        const source = `${task.taskKey} ${task.excelPath || ''}`
        const reportType = reportTypeMarkers.find((marker) => source.includes(marker))
        if (!reportType) return []
        const fileName = task.excelPath?.split(/[\\/]/).pop() || '-'
        return [{
          key: String(task.id || task.taskKey),
          taskId: task.id,
          fileType: reportTypeLabels[reportType],
          fileName,
          createdAt: task.createdAt,
          status: task.status,
          excelPath: task.excelPath,
        }]
      })

  const downloadGeneratedFile = async (row: GeneratedFileRow) => {
    if (!row.taskId || !row.excelPath) {
      message.warning('暂无可下载文件')
      return
    }
    const response = await apiService.bookkeeping.downloadTask(row.taskId) as { data: Blob }
    const url = URL.createObjectURL(response.data)
    const link = document.createElement('a')
    link.href = url
    link.download = row.fileName || 'bookkeeping.xlsx'
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(url)
  }

  const clearGeneratedFiles = async () => {
    setClearingGeneratedFiles(true)
    try {
      const response = await apiService.bookkeeping.clearGeneratedFiles() as { data: ApiResponse<ClearGeneratedFilesResult> }
      if (response.data.code === 0) {
        const result = response.data.data
        message.success(`已清除 ${result?.deletedFileCount ?? 0} 个文件`)
        await loadDashboard()
        return
      }
      message.error(response.data.msg || '清除生成文件失败')
    } finally {
      setClearingGeneratedFiles(false)
    }
  }

  const crownColumns: ColumnsType<CrownAccount> = [
    { title: '名称', dataIndex: 'displayName' },
    { title: '域名', dataIndex: 'baseUrl', ellipsis: true },
    { title: '账号', dataIndex: 'username' },
    {
      title: '状态',
      width: 180,
      render: (_, row) => (
        <Space>
          <Tag color={row.enabled ? 'green' : 'default'}>{row.enabled ? '启用' : '停用'}</Tag>
          <Tag color={statusColor(row.lastLoginStatus)}>{row.lastLoginStatus || '未测试'}</Tag>
        </Space>
      ),
    },
    { title: '最后测试', dataIndex: 'lastLoginAt', render: timeText },
    {
      title: '操作',
      width: 170,
      render: (_, row) => (
        <Space>
          <Button size="small" onClick={() => editCrownAccount(row)}>编辑</Button>
          <Button size="small" icon={<CheckCircleOutlined />} disabled={!row.id} onClick={() => testCrownLogin(row)}>
            测试
          </Button>
        </Space>
      ),
    },
  ]

  const groupColumns: ColumnsType<WhatsappGroup> = [
    { title: '群聊', dataIndex: 'displayName' },
    { title: 'WhatsApp名称', dataIndex: 'chatName', ellipsis: true },
    {
      title: '角色',
      width: 120,
      render: (_, row) => {
        const role = effectiveRole(row)
        return <Tag color={roleColor(role)}>{roleText[role] || role}</Tag>
      },
    },
    {
      title: '货币',
      dataIndex: 'currency',
      width: 90,
      render: (value) => <Tag color={value === 'RMB' ? 'blue' : 'green'}>{value || 'USDT'}</Tag>,
    },
    { title: '汇率', dataIndex: 'exchangeRate', width: 100, render: (value) => value ?? 1 },
    {
      title: '退水',
      dataIndex: 'rebatePoints',
      width: 130,
      render: (value) => {
        const points = Number(value ?? 0)
        return `${points}格 / 减${(points / 100).toFixed(2)}`
      },
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (value) => <Tag color={value ? 'green' : 'default'}>{value ? '启用' : '停用'}</Tag>,
    },
    {
      title: '操作',
      width: 90,
      render: (_, row) => <Button size="small" onClick={() => editWhatsappGroup(row)}>编辑</Button>,
    },
  ]

  const wagerColumns: ColumnsType<CrownWager> = [
    { title: '订单号', dataIndex: 'ticketId' },
    { title: '联赛', dataIndex: 'leagueName' },
    { title: '主队', dataIndex: 'homeTeam' },
    { title: '客队', dataIndex: 'awayTeam' },
    { title: '盘口', render: (_, row) => [row.marketType, row.selectionName].filter(Boolean).join(' ') },
    { title: '赔率', dataIndex: 'oddsValue' },
    { title: '投注金额', dataIndex: 'stakeAmount', render: moneyText },
    { title: '输赢', dataIndex: 'winLossAmount', render: moneyText },
    { title: '状态', dataIndex: 'status', render: (value) => <Tag color={statusColor(value)}>{value}</Tag> },
  ]

  const orderColumns: ColumnsType<WhatsappOrder> = [
    { title: '订单号', dataIndex: 'orderKey' },
    { title: '方向', dataIndex: 'direction', render: directionText },
    { title: '联赛', dataIndex: 'leagueName' },
    { title: '比赛', dataIndex: 'matchName' },
    { title: '盘口', dataIndex: 'marketText' },
    { title: '赔率', dataIndex: 'oddsValue' },
    { title: '金额', dataIndex: 'amount', render: moneyText },
    { title: '赛果', dataIndex: 'settlementResult', render: (value) => value || '-' },
    { title: '状态', dataIndex: 'parseStatus', render: (value) => <Tag color={statusColor(value)}>{value}</Tag> },
  ]

  const reconciliationColumns: ColumnsType<ReconciliationResult> = [
    {
      title: '问题',
      dataIndex: 'issueType',
      render: (value) => <Tag color={statusColor(value === 'matched' ? 'matched' : 'difference')}>{issueText[value] || value || '未匹配'}</Tag>,
    },
    { title: 'Crown投注ID', dataIndex: 'crownWagerId' },
    { title: 'WhatsApp订单ID', dataIndex: 'whatsappOrderId' },
    { title: '金额差异', dataIndex: 'amountDiff', render: moneyText },
    { title: '赔率差异', dataIndex: 'oddsDiff' },
    { title: '利润', dataIndex: 'profitAmount', render: moneyText },
    { title: '说明', dataIndex: 'notes', ellipsis: true },
  ]

  const taskColumns: ColumnsType<BookkeepingTask> = [
    { title: '日期', dataIndex: 'businessDate' },
    { title: '状态', dataIndex: 'status', render: (value) => <Tag color={statusColor(value)}>{value}</Tag> },
    { title: '生成时间', dataIndex: 'createdAt', render: timeText },
    { title: 'Excel', dataIndex: 'excelPath', ellipsis: true, render: (value) => value || '-' },
  ]

  const fileColumns: ColumnsType<GeneratedFileRow> = [
    { title: '文件类型', dataIndex: 'fileType', width: 160 },
    { title: '文件名', dataIndex: 'fileName', ellipsis: true },
    { title: '生成时间', dataIndex: 'createdAt', width: 190, render: timeText },
    { title: '状态', dataIndex: 'status', width: 110, render: (value) => <Tag color={statusColor(value)}>{value}</Tag> },
    {
      title: '下载',
      width: 100,
      render: (_, row) => (
        <Button
          size="small"
          icon={<FileExcelOutlined />}
          disabled={!row.excelPath}
          onClick={() => downloadGeneratedFile(row)}
        >
          下载
        </Button>
      ),
    },
  ]

  const renderMetricBlock = (item: { label: string; value: string | number; hint: string }, prominent = false) => (
    <div key={item.label} style={{ padding: '10px 0', minWidth: 0 }}>
      <Text type="secondary">{item.label}</Text>
      <div
        style={{
          fontSize: prominent ? 28 : 24,
          lineHeight: prominent ? '36px' : '32px',
          fontWeight: 700,
          marginTop: 6,
          color: prominent ? '#0f172a' : '#1f2937',
          whiteSpace: 'nowrap',
        }}
      >
        {item.value}
      </div>
      <Text type="secondary">{item.hint}</Text>
    </div>
  )

  const renderPanel = (title: string, extra: string | number | ReactNode, children: ReactNode) => (
    <section style={pageSurface}>
      <div style={panelTitle}>
        <Text strong>{title}</Text>
        {typeof extra === 'string' || typeof extra === 'number' ? <Text type="secondary">{extra}</Text> : extra}
      </div>
      <div style={panelBody}>{children}</div>
    </section>
  )

  const renderReportButton = (action: ReportAction) => {
    const runningKey = `${action.workspaceType}:${action.reportType}`
    return (
      <Button
        key={runningKey}
        icon={<FileExcelOutlined />}
        onClick={() => runTask(action)}
        loading={runningReport === runningKey}
        disabled={runningReport !== null && runningReport !== runningKey}
      >
        {action.label}
      </Button>
    )
  }

  const scanWhatsappMessages = async (workspaceType: WorkspaceType, force = false) => {
    const runningKey = `${workspaceType}:${force ? 'rescan' : 'scan'}`
    setScanningWhatsapp(runningKey)
    try {
      const response = await apiService.bookkeeping.scanWhatsappMessages({
        businessDate,
        workspaceType,
        scanStart: workspaceType === 'prematch' ? scanStart : undefined,
        scanEnd: workspaceType === 'prematch' ? scanEnd : undefined,
        force,
      }) as { data: ApiResponse<WhatsappScanResult> }
      if (response.data.code === 0 && response.data.data) {
        const result = response.data.data
        if (result.connected) {
          message.success(result.message || '扫描完成')
        } else {
          message.warning(result.message || 'WhatsApp 还没有连接')
        }
        await loadDashboard()
        return
      }
      message.error(response.data.msg || '扫描失败')
    } finally {
      setScanningWhatsapp(null)
    }
  }

  const noticeWorkInProgress = (label: string) => {
    message.info(`${label}功能还没有连接 WhatsApp 消息读取程序`)
  }

  const renderHeader = (title: string, subtitle: string, actions: ReportAction[] = []) => (
    <section style={{ display: 'flex', justifyContent: 'space-between', gap: 16, flexWrap: 'wrap', alignItems: 'center' }}>
      <div>
        <Space align="center" wrap>
          <Title level={3} style={{ margin: 0 }}>{title}</Title>
          {title === '赛前工作台' && <Tag color="blue">二级赛前工作台</Tag>}
        </Space>
        <Text type="secondary">{subtitle}</Text>
      </div>
      <Space wrap>
        <Input type="date" value={businessDate} onChange={(event) => setBusinessDate(event.target.value)} style={{ width: 160 }} />
        <Button icon={<ReloadOutlined />} onClick={() => loadDashboard()} loading={loading}>刷新</Button>
        {actions.map(renderReportButton)}
      </Space>
    </section>
  )

  const renderPreMatchTimeBar = () => (
    <section style={pageSurface}>
      <div style={{ ...panelBody, display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'end' }}>
        <label style={{ width: 170 }}>
          <Text type="secondary">日期</Text>
          <Input type="date" value={businessDate} onChange={(event) => setBusinessDate(event.target.value)} style={{ marginTop: 6 }} />
        </label>
        <label style={{ width: 150 }}>
          <Text type="secondary">扫描开始</Text>
          <Input type="time" value={scanStart} onChange={(event) => setScanStart(event.target.value)} style={{ marginTop: 6 }} />
        </label>
        <label style={{ width: 150 }}>
          <Text type="secondary">扫描结束</Text>
          <Input type="time" value={scanEnd} onChange={(event) => setScanEnd(event.target.value)} style={{ marginTop: 6 }} />
        </label>
        <Button icon={<ReloadOutlined />} onClick={() => loadDashboard()} loading={loading}>刷新</Button>
      </div>
    </section>
  )

  const renderRollingTimeBar = () => (
    <section style={pageSurface}>
      <div style={{ ...panelBody, display: 'flex', gap: 12, flexWrap: 'wrap', alignItems: 'end' }}>
        <label style={{ width: 170 }}>
          <Text type="secondary">日期</Text>
          <Input type="date" value={businessDate} onChange={(event) => setBusinessDate(event.target.value)} style={{ marginTop: 6 }} />
        </label>
        <Button icon={<ReloadOutlined />} onClick={() => loadDashboard()} loading={loading}>刷新</Button>
      </div>
    </section>
  )

  const renderPreMatchActions = () => (
    <section style={pageSurface}>
      <div style={panelTitle}>
        <Text strong>操作区</Text>
        <Text type="secondary">扫描群聊后生成每个群账单和公司表</Text>
      </div>
      <div style={{ ...panelBody, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Button
          icon={<CloudSyncOutlined />}
          onClick={() => scanWhatsappMessages('prematch')}
          loading={scanningWhatsapp === 'prematch:scan'}
          disabled={scanningWhatsapp !== null && scanningWhatsapp !== 'prematch:scan'}
        >
          扫描群聊
        </Button>
        <Button
          icon={<ReloadOutlined />}
          onClick={() => scanWhatsappMessages('prematch', true)}
          loading={scanningWhatsapp === 'prematch:rescan'}
          disabled={scanningWhatsapp !== null && scanningWhatsapp !== 'prematch:rescan'}
        >
          重新扫描
        </Button>
        {preMatchReportActions.map(renderReportButton)}
      </div>
    </section>
  )

  const renderRollingActions = () => (
    <section style={pageSurface}>
      <div style={panelTitle}>
        <Text strong>操作区</Text>
        <Text type="secondary">扫描滚球群并抓取皇冠注单后生成文件</Text>
      </div>
      <div style={{ ...panelBody, display: 'flex', gap: 12, flexWrap: 'wrap' }}>
        <Button
          icon={<CloudSyncOutlined />}
          onClick={() => scanWhatsappMessages('rolling')}
          loading={scanningWhatsapp === 'rolling:scan'}
          disabled={scanningWhatsapp !== null && scanningWhatsapp !== 'rolling:scan'}
        >
          扫描滚球群
        </Button>
        <Button icon={<CloudUploadOutlined />} onClick={() => noticeWorkInProgress('抓取皇冠注单')}>抓取皇冠注单</Button>
        {rollingReportActions.map(renderReportButton)}
      </div>
    </section>
  )

  const renderMetricGrid = (items: { label: string; value: string | number; hint: string }[]) => (
    <section style={pageSurface}>
      <div style={{ ...panelBody, display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(190px, 1fr))', gap: '8px 28px' }}>
        {items.map((item, index) => renderMetricBlock(item, index === items.length - 1))}
      </div>
    </section>
  )

  const renderGeneratedFilesSection = (workspaceType: WorkspaceType) => {
    const rows = generatedFileRows(workspaceType)
    return renderPanel(
      '文件生成结果',
      <Space wrap>
        <Text type="secondary">{rows.length} 个文件</Text>
        <Button
          danger
          size="small"
          icon={<DeleteOutlined />}
          onClick={clearGeneratedFiles}
          loading={clearingGeneratedFiles}
        >
          清除生成文件
        </Button>
      </Space>,
      <Table
        size="middle"
        loading={loading}
        rowKey={(row) => row.key}
        columns={fileColumns}
        dataSource={rows}
        scroll={{ x: 760 }}
        pagination={{ pageSize: 8 }}
      />
    )
  }

  const renderDashboardPage = () => (
    <Space direction="vertical" size={20} style={pageContainer}>
      {renderHeader('赛前工作台', '上游订单、下游拆单、公司跟单与赛前结算中心')}
      {renderPreMatchTimeBar()}
      {renderPreMatchActions()}
      {renderMetricGrid(preMatchSummaryItems)}
      {renderGeneratedFilesSection('prematch')}
    </Space>
  )

  const renderRollingPage = () => (
    <Space direction="vertical" size={20} style={pageContainer}>
      {renderHeader('滚球工作台', '滚球群订单、皇冠真实注单与滚球盈亏生成中心')}
      {renderRollingTimeBar()}
      {renderRollingActions()}
      {renderMetricGrid(rollingSummaryItems)}
      {renderGeneratedFilesSection('rolling')}
    </Space>
  )

  const renderCrownAccountsPage = () => (
    <Space direction="vertical" size={16} style={pageContainer}>
      {renderHeader('皇冠账号', '单独管理 Crown 账号、登录测试和抓取状态')}

      <section style={{ display: 'grid', gridTemplateColumns: '360px minmax(0, 1fr)', gap: 16, alignItems: 'start' }}>
        <div style={pageSurface}>
          <div style={panelTitle}>
            <Text strong>新增 Crown 账号</Text>
          </div>
          <div style={panelBody}>
            <Form form={crownForm} layout="vertical" initialValues={{ enabled: true, timezone: 'GMT-4' }}>
              <Form.Item name="id" hidden><Input /></Form.Item>
              <Form.Item name="accountKey" label="账号标识" rules={[{ required: true, message: '请输入账号标识' }]}><Input placeholder="crown-main" /></Form.Item>
              <Form.Item name="displayName" label="名称" rules={[{ required: true, message: '请输入名称' }]}><Input /></Form.Item>
              <Form.Item name="baseUrl" label="域名" rules={[{ required: true, message: '请输入域名' }]}><Input placeholder="https://crown.example.com" /></Form.Item>
              <Form.Item name="username" label="账号" rules={[{ required: true, message: '请输入账号' }]}><Input autoComplete="off" /></Form.Item>
              <Form.Item name="password" label="密码"><Input.Password autoComplete="new-password" placeholder="不填写则保留原密码" /></Form.Item>
              <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
              <Space>
                <Button type="primary" icon={<CloudUploadOutlined />} onClick={saveCrownAccount}>保存账号</Button>
                <Button onClick={() => crownForm.resetFields()}>清空</Button>
              </Space>
            </Form>
          </div>
        </div>

        <div style={pageSurface}>
          <div style={panelTitle}>
            <Text strong>账号池</Text>
            <Text type="secondary">{dashboard.crownAccounts.length} 个账号</Text>
          </div>
          <div style={panelBody}>
            <Table
              size="middle"
              loading={loading}
              rowKey={(row) => row.id || row.accountKey}
              columns={crownColumns}
              dataSource={dashboard.crownAccounts}
              scroll={{ x: 760 }}
            />
          </div>
        </div>
      </section>
    </Space>
  )

  const renderWhatsappGroupsPage = () => (
    <Space direction="vertical" size={16} style={pageContainer}>
      {renderHeader('WhatsApp群聊', '读取本机 WhatsApp 群聊，设置上游、下游、公司跟单、滚球或忽略')}

      <div style={pageSurface}>
        <div style={panelTitle}>
          <div>
            <Text strong>群聊同步</Text>
            <div><Text type="secondary">同步后会显示当前可读取的群聊，再给每个群设置角色、货币、汇率和退水。</Text></div>
          </div>
          <Space wrap>
            <Button onClick={loadWhatsappStatus}>刷新登录状态</Button>
            <Button icon={<CloudSyncOutlined />} onClick={syncWhatsappChats} loading={syncingWhatsapp}>
              同步本机群聊
            </Button>
          </Space>
        </div>
        {whatsappStatus && (
          <div style={{ padding: '12px 18px', borderBottom: '1px solid #edf1f7' }}>
            <Alert
              type={whatsappStatus.connected ? 'success' : whatsappStatus.status === 'qr_required' ? 'warning' : 'info'}
              showIcon
              message={whatsappStatus.message}
            />
            {whatsappStatus.qr && (
              <div style={{ marginTop: 14, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
                <QRCode value={whatsappStatus.qr} size={176} />
                <Text type="secondary">用手机 WhatsApp 扫码登录，登录成功后点“同步本机群聊”。</Text>
              </div>
            )}
          </div>
        )}
        {whatsappSyncMessage && (
          <div style={{ padding: '12px 18px', borderBottom: '1px solid #edf1f7' }}>
            <Alert type="info" showIcon message={whatsappSyncMessage} />
          </div>
        )}
      </div>

      <section style={{ display: 'grid', gridTemplateColumns: '380px minmax(0, 1fr)', gap: 16, alignItems: 'start' }}>
        <div style={pageSurface}>
          <div style={panelTitle}>
            <Text strong>群聊配置</Text>
          </div>
          <div style={panelBody}>
            <Form
              form={groupForm}
              layout="vertical"
              initialValues={{ enabled: true, role: 'pending', currency: 'USDT', exchangeRate: 1, rebatePoints: 0 }}
            >
              <Form.Item name="id" hidden><Input /></Form.Item>
              <Form.Item name="sourceChatId" label="WhatsApp Chat ID"><Input placeholder="同步后自动带入" /></Form.Item>
              <Form.Item name="groupKey" label="群标识" rules={[{ required: true, message: '请输入群标识' }]}><Input placeholder="downstream-orders" /></Form.Item>
              <Form.Item name="displayName" label="显示名称" rules={[{ required: true, message: '请输入显示名称' }]}><Input /></Form.Item>
              <Form.Item name="chatName" label="群聊名称" rules={[{ required: true, message: '请输入群聊名称' }]}><Input /></Form.Item>
              <Form.Item name="role" label="角色" rules={[{ required: true }]}>
                <Select options={[
                  { value: 'pending', label: '待设置' },
                  { value: 'upstream', label: '上游群' },
                  { value: 'downstream', label: '下游群' },
                  { value: 'company_follow', label: '公司跟单群' },
                  { value: 'rolling', label: '滚球群' },
                  { value: 'ignored', label: '忽略群' },
                ]} />
              </Form.Item>
              <section style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <Form.Item name="currency" label="货币" rules={[{ required: true }]}>
                  <Select options={[
                    { value: 'USDT', label: 'U / USDT' },
                    { value: 'RMB', label: 'RMB' },
                  ]} />
                </Form.Item>
                <Form.Item name="exchangeRate" label="汇率" rules={[{ required: true, message: '请输入汇率' }]}>
                  <InputNumber min={0.000001} precision={6} style={{ width: '100%' }} />
                </Form.Item>
              </section>
              <Form.Item name="rebatePoints" label="退水（格）" tooltip="6格表示每单赔率减0.06">
                <InputNumber min={0} precision={2} style={{ width: '100%' }} addonAfter="格" />
              </Form.Item>
              <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
              <Space>
                <Button type="primary" icon={<CloudUploadOutlined />} onClick={saveWhatsappGroup}>保存群聊</Button>
                <Button onClick={() => groupForm.resetFields()}>清空</Button>
              </Space>
            </Form>
          </div>
        </div>

        <div style={pageSurface}>
          <div style={panelTitle}>
            <Text strong>群聊列表</Text>
            <Text type="secondary">{dashboard.whatsappGroups.length} 个群</Text>
          </div>
          <div style={panelBody}>
            <Table
              size="middle"
              loading={loading}
              rowKey={(row) => row.id || row.groupKey}
              columns={groupColumns}
              dataSource={dashboard.whatsappGroups}
              scroll={{ x: 1040 }}
              pagination={{ pageSize: 10 }}
            />
          </div>
        </div>
      </section>
    </Space>
  )

  const renderCrownWagersPage = () => (
    <Space direction="vertical" size={16} style={pageContainer}>
      {renderHeader('Crown投注', '查看每个 Crown 账号抓到的投注记录')}
      <div style={pageSurface}>
        <div style={panelBody}>
          <Table loading={loading} rowKey={(row) => row.id || row.ticketId} columns={wagerColumns} dataSource={dashboard.crownWagers} scroll={{ x: 980 }} />
        </div>
      </div>
    </Space>
  )

  const renderWhatsappOrdersPage = () => (
    <Space direction="vertical" size={16} style={pageContainer}>
      {renderHeader('WhatsApp订单', '上游大单和下游小单统一查看')}
      <div style={pageSurface}>
        <div style={panelBody}>
          <Table loading={loading} rowKey={(row) => row.id || row.orderKey} columns={orderColumns} dataSource={dashboard.whatsappOrders} scroll={{ x: 900 }} />
        </div>
      </div>
    </Space>
  )

  const renderReconciliationPage = () => (
    <Space direction="vertical" size={16} style={pageContainer}>
      {renderHeader('对账结果', 'WhatsApp订单、Crown投注和利润差异')}
      <div style={pageSurface}>
        <div style={panelBody}>
          <Table loading={loading} rowKey={(row) => row.id || `${row.issueType}-${row.crownWagerId}-${row.whatsappOrderId}`} columns={reconciliationColumns} dataSource={dashboard.reconciliationResults} scroll={{ x: 900 }} />
        </div>
      </div>
    </Space>
  )

  const renderExcelPage = () => (
    <Space direction="vertical" size={16} style={pageContainer}>
      {renderHeader('Excel报表', '每天生成的完整账目文件')}
      <div style={pageSurface}>
        <div style={panelBody}>
          <Table
            loading={loading}
            rowKey={(row) => row.id || row.taskKey}
            columns={[
              ...taskColumns,
              { title: '文件', dataIndex: 'excelPath', render: (value) => value ? <Tag icon={<FileExcelOutlined />} color="green">已生成</Tag> : <Tag>未生成</Tag> },
            ]}
            dataSource={dashboard.tasks}
            scroll={{ x: 760 }}
          />
        </div>
      </div>
    </Space>
  )

  if (pageKey === 'rolling') return renderRollingPage()
  if (pageKey === 'crownAccounts') return renderCrownAccountsPage()
  if (pageKey === 'whatsappGroups') return renderWhatsappGroupsPage()
  if (pageKey === 'crownWagers') return renderCrownWagersPage()
  if (pageKey === 'whatsappOrders') return renderWhatsappOrdersPage()
  if (pageKey === 'reconciliation') return renderReconciliationPage()
  if (pageKey === 'excel') return renderExcelPage()
  return renderDashboardPage()
}

export default Bookkeeping
