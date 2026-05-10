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
  CloudDownloadOutlined,
  CloudSyncOutlined,
  CloudUploadOutlined,
  DeleteOutlined,
  FileExcelOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import { apiService } from '../services/api'
import SystemUpdate from './SystemUpdate'

const { Title, Text } = Typography

type ApiResponse<T> = { code: number; data: T; msg: string }
type Money = number | string
type WorkspaceType = 'prematch' | 'rolling'
type GroupRole =
  | 'pending'
  | 'upstream'
  | 'downstream'
  | 'company_follow'
  | 'rolling'
  | 'rolling_upstream'
  | 'rolling_downstream'
  | 'rolling_company'
  | 'ignored'
type OrderDirection = Exclude<GroupRole, 'pending' | 'ignored'>
type ReportType =
  | 'daily'
  | 'crown_wagers'
  | 'downstream_orders'
  | 'upstream_orders'
  | 'company_orders'
  | 'prematch_profit'
  | 'prematch_excel'
  | 'rolling_upstream_orders'
  | 'rolling_downstream_orders'
  | 'rolling_group_orders'
  | 'rolling_reconcile'
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
  sourceType?: 'whatsapp' | 'telegram' | string
  sourceChatId?: string
  displayName: string
  chatName: string
  role: GroupRole
  currency: 'USDT' | 'RMB' | string
  exchangeRate: Money
  lastScannedMessageId?: string
  configured?: boolean
  enabled: boolean
}

type TelegramGroup = WhatsappGroup

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
  createdAt?: number
}

type WhatsappOrder = {
  id?: number
  groupId?: number
  sourceType?: 'whatsapp' | 'telegram' | string
  businessDate: string
  orderKey: string
  direction: OrderDirection
  messageTime?: number
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
  telegramGroups: TelegramGroup[]
  crownWagers: CrownWager[]
  whatsappOrders: WhatsappOrder[]
  reconciliationResults: ReconciliationResult[]
  tasks: BookkeepingTask[]
}

type DashboardPayload = Partial<Omit<Dashboard, 'summary'>> & {
  summary?: Partial<Summary>
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

type TelegramSyncResult = WhatsappSyncResult
type TelegramStatus = WhatsappStatus
type TelegramScanResult = WhatsappScanResult
type Titan007ScoreFetchResult = {
  businessDate: string
  fetchedCount: number
  sourceUrl: string
  savedPath: string
}
type TelegramApiConfig = {
  apiId: string
  apiHashConfigured: boolean
  sessionConfigured: boolean
  bridgeConfigured: boolean
  message: string
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
  telegramGroups: [],
  crownWagers: [],
  whatsappOrders: [],
  reconciliationResults: [],
  tasks: [],
}

export const normalizeDashboard = (payload?: DashboardPayload | null): Dashboard => ({
  ...emptyDashboard,
  ...(payload ?? {}),
  businessDate: payload?.businessDate || emptyDashboard.businessDate,
  summary: {
    ...emptySummary,
    ...(payload?.summary ?? {}),
  },
  crownAccounts: payload?.crownAccounts ?? [],
  whatsappGroups: payload?.whatsappGroups ?? [],
  telegramGroups: payload?.telegramGroups ?? [],
  crownWagers: payload?.crownWagers ?? [],
  whatsappOrders: payload?.whatsappOrders ?? [],
  reconciliationResults: payload?.reconciliationResults ?? [],
  tasks: payload?.tasks ?? [],
})

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

const emptyText = (value?: ReactNode) => {
  if (value === undefined || value === null || value === '') return '-'
  return value
}

const matchTeamsText = (homeTeam?: string, awayTeam?: string, fallback?: string) => {
  const home = homeTeam?.trim()
  const away = awayTeam?.trim()
  if (home && away) return `${home}v${away}`
  const match = fallback?.trim()
  if (!match) return '-'
  return match.replace(/\s+(vs\.?|v)\s+/gi, 'v')
}

const marketOddsText = (market?: string, selection?: string, oddsValue?: Money) => {
  const marketText = [market, selection].map((item) => item?.trim()).filter(Boolean).join(' ')
  const oddsText = oddsValue === undefined || oddsValue === null || oddsValue === '' ? '' : `@ ${oddsValue}`
  return [marketText, oddsText].filter(Boolean).join(' ') || '-'
}

const settlementOutcome = (value?: string) => {
  const text = value?.trim().toLowerCase()
  if (!text) return undefined
  if (['win', 'won', 'full_win', '赢', '全赢'].includes(text)) return 'win'
  if (['win_half', 'half_win', 'halfwon', '赢半', '半赢'].includes(text)) return 'win_half'
  if (['push', 'draw', 'void', '走水', '和'].includes(text)) return 'push'
  if (['lose_half', 'half_lose', 'halflost', '输半', '半输'].includes(text)) return 'lose_half'
  if (['lose', 'lost', 'loss', 'full_lose', '输', '全输'].includes(text)) return 'lose'
  return undefined
}

const orderProfitText = (order: WhatsappOrder) => {
  const outcome = settlementOutcome(order.settlementResult)
  if (!outcome) return '-'
  const stake = Number(order.amount ?? 0)
  const odds = Number(order.oddsValue ?? 0)
  if (!Number.isFinite(stake) || !Number.isFinite(odds)) return '-'
  if (outcome === 'win') return moneyText(stake * odds)
  if (outcome === 'win_half') return moneyText(stake * odds * 0.5)
  if (outcome === 'push') return moneyText(0)
  if (outcome === 'lose_half') return moneyText(stake * -0.5)
  return moneyText(-stake)
}

const statusColor = (status?: string) => {
  if (status === 'success' || status === 'completed' || status === 'matched' || status === 'parsed') return 'green'
  if (status === 'failed' || status === 'difference') return 'red'
  if (status === 'manual_required' || status === 'running' || status === 'suspicious') return 'gold'
  return 'default'
}

const roleText: Record<string, string> = {
  pending: '待设置',
  upstream: '赛前上游',
  downstream: '赛前下游',
  company_follow: '赛前公司',
  rolling: '滚球下游',
  rolling_upstream: '滚球上游',
  rolling_downstream: '滚球下游',
  rolling_company: '滚球公司',
  ignored: '忽略群',
}

const groupRoleOptions = [
  { value: 'pending', label: '待设置' },
  { value: 'upstream', label: '赛前上游' },
  { value: 'downstream', label: '赛前下游' },
  { value: 'company_follow', label: '赛前公司' },
  { value: 'rolling_upstream', label: '滚球上游' },
  { value: 'rolling_downstream', label: '滚球下游' },
  { value: 'rolling_company', label: '滚球公司' },
  { value: 'ignored', label: '忽略群' },
]

const effectiveRole = (group: WhatsappGroup): GroupRole => {
  if (group.configured === false) return 'pending'
  if (group.role === 'rolling') return 'rolling_downstream'
  return group.role
}

const roleColor = (role: string) => {
  if (role === 'pending') return 'gold'
  if (role === 'upstream' || role === 'rolling_upstream') return 'blue'
  if (role === 'downstream' || role === 'rolling_downstream' || role === 'rolling') return 'green'
  if (role === 'company_follow' || role === 'rolling_company') return 'purple'
  return 'default'
}

const roleMatchesWorkspace = (role: GroupRole, workspaceType: WorkspaceType) => {
  if (workspaceType === 'rolling') {
    return role === 'rolling' || role === 'rolling_upstream' || role === 'rolling_downstream' || role === 'rolling_company'
  }
  return role === 'upstream' || role === 'downstream' || role === 'company_follow'
}

const preMatchReportActions: ReportAction[] = [
  { workspaceType: 'prematch', reportType: 'upstream_orders', label: '赛前上游各群表格' },
  { workspaceType: 'prematch', reportType: 'downstream_orders', label: '赛前下游各群表格' },
  { workspaceType: 'prematch', reportType: 'prematch_profit', label: '公司盈亏表格' },
  { workspaceType: 'prematch', reportType: 'prematch_excel', label: '一键生成' },
]

const rollingReportActions: ReportAction[] = [
  { workspaceType: 'rolling', reportType: 'rolling_upstream_orders', label: '滚球上游各群表格' },
  { workspaceType: 'rolling', reportType: 'rolling_downstream_orders', label: '滚球下游各群表格' },
  { workspaceType: 'rolling', reportType: 'rolling_profit', label: '公司盈亏表格' },
  { workspaceType: 'rolling', reportType: 'rolling_excel', label: '一键生成' },
]

const reportTypeLabels: Record<string, string> = {
  upstream_orders: '赛前上游各群表格',
  downstream_orders: '赛前下游各群表格',
  company_orders: '公司跟单表',
  prematch_profit: '公司盈亏表格',
  prematch_excel: '全部文件',
  rolling_upstream_orders: '滚球上游各群表格',
  rolling_downstream_orders: '滚球下游各群表格',
  rolling_group_orders: '滚球群账单',
  crown_wagers: '皇冠注单表',
  rolling_reconcile: '滚球对账表',
  rolling_profit: '公司盈亏表格',
  rolling_excel: '全部文件',
}

const reportTypeMarkers = Object.keys(reportTypeLabels).sort((a, b) => b.length - a.length)

const getPageKey = (pathname: string) => {
  const clean = pathname.replace(/\/$/, '')
  if (clean.startsWith('/bookkeeping/prematch/reconciliation')) return 'prematchReconciliation'
  if (clean.startsWith('/bookkeeping/rolling/reconciliation')) return 'rollingReconciliation'
  if (clean.startsWith('/bookkeeping/system/update')) return 'systemUpdate'
  if (clean.startsWith('/bookkeeping/rolling')) return 'rolling'
  if (clean.startsWith('/bookkeeping/crown/accounts')) return 'crownAccounts'
  if (clean.startsWith('/bookkeeping/whatsapp/groups')) return 'whatsappGroups'
  if (clean.startsWith('/bookkeeping/telegram/groups')) return 'telegramGroups'
  if (clean.startsWith('/bookkeeping/crown/wagers')) return 'crownWagers'
  if (clean.startsWith('/bookkeeping/whatsapp/orders')) return 'prematchReconciliation'
  if (clean.startsWith('/bookkeeping/reconciliation')) return 'rollingReconciliation'
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
  const [scanningTelegram, setScanningTelegram] = useState<string | null>(null)
  const [fetchingTitan007Scores, setFetchingTitan007Scores] = useState(false)
  const [clearingGeneratedFiles, setClearingGeneratedFiles] = useState(false)
  const [syncingWhatsapp, setSyncingWhatsapp] = useState(false)
  const [syncingTelegram, setSyncingTelegram] = useState(false)
  const [savingTelegramApiConfig, setSavingTelegramApiConfig] = useState(false)
  const [whatsappSyncMessage, setWhatsappSyncMessage] = useState('')
  const [telegramSyncMessage, setTelegramSyncMessage] = useState('')
  const [whatsappStatus, setWhatsappStatus] = useState<WhatsappStatus | null>(null)
  const [telegramStatus, setTelegramStatus] = useState<TelegramStatus | null>(null)
  const [telegramApiConfig, setTelegramApiConfig] = useState<TelegramApiConfig | null>(null)
  const [crownForm] = Form.useForm()
  const [groupForm] = Form.useForm()
  const [telegramForm] = Form.useForm()
  const [telegramApiConfigForm] = Form.useForm()

  const loadDashboard = async (date = businessDate) => {
    const workspaceType: WorkspaceType = pageKey === 'rolling' || pageKey === 'rollingReconciliation' ? 'rolling' : 'prematch'
    setLoading(true)
    try {
      const response = await apiService.bookkeeping.dashboard({ businessDate: date, workspaceType }) as { data: ApiResponse<Dashboard> }
      if (response.data.code === 0 && response.data.data) {
        setDashboard(normalizeDashboard(response.data.data))
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

  const loadTelegramStatus = async () => {
    const response = await apiService.bookkeeping.telegramStatus() as { data: ApiResponse<TelegramStatus> }
    if (response.data.code === 0 && response.data.data) {
      setTelegramStatus(response.data.data)
      return
    }
    setTelegramStatus({ connected: false, status: 'unknown', message: response.data.msg || '读取 Telegram 状态失败' })
  }

  const loadTelegramApiConfig = async () => {
    const response = await apiService.bookkeeping.telegramApiConfig() as { data: ApiResponse<TelegramApiConfig> }
    if (response.data.code === 0 && response.data.data) {
      const config = response.data.data
      setTelegramApiConfig(config)
      telegramApiConfigForm.setFieldsValue({ apiId: config.apiId, apiHash: '' })
      return
    }
    setTelegramApiConfig({
      apiId: '',
      apiHashConfigured: false,
      sessionConfigured: false,
      bridgeConfigured: false,
      message: response.data.msg || '读取 Telegram API 配置失败',
    })
  }

  useEffect(() => {
    if (pageKey === 'whatsappGroups') {
      loadWhatsappStatus()
    }
    if (pageKey === 'telegramGroups') {
      loadTelegramApiConfig()
      loadTelegramStatus()
    }
  }, [pageKey])

  useEffect(() => {
    if (pageKey !== 'telegramGroups' || telegramStatus?.status !== 'qr_required') return
    const timer = window.setInterval(() => {
      loadTelegramStatus()
    }, 10000)
    return () => window.clearInterval(timer)
  }, [pageKey, telegramStatus?.status])

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
    }) as { data: ApiResponse<WhatsappGroup> }
    if (response.data.code === 0) {
      message.success('WhatsApp群已保存')
      groupForm.resetFields()
      await loadDashboard()
      return
    }
    message.error(response.data.msg || '保存失败')
  }

  const saveTelegramGroup = async () => {
    const values = await telegramForm.validateFields()
    const response = await apiService.bookkeeping.saveTelegramGroup({
      ...values,
      exchangeRate: Number(values.exchangeRate ?? 1),
    }) as { data: ApiResponse<TelegramGroup> }
    if (response.data.code === 0) {
      message.success('Telegram群已保存')
      telegramForm.resetFields()
      await loadDashboard()
      return
    }
    message.error(response.data.msg || '保存失败')
  }

  const saveTelegramApiConfig = async () => {
    const values = await telegramApiConfigForm.validateFields()
    setSavingTelegramApiConfig(true)
    try {
      const response = await apiService.bookkeeping.saveTelegramApiConfig(values) as { data: ApiResponse<TelegramApiConfig> }
      if (response.data.code === 0 && response.data.data) {
        const config = response.data.data
        setTelegramApiConfig(config)
        telegramApiConfigForm.setFieldsValue({ apiId: config.apiId, apiHash: '' })
        message.success(config.message || 'Telegram API 已保存')
        await loadTelegramStatus()
        return
      }
      message.error(response.data.msg || '保存失败')
    } finally {
      setSavingTelegramApiConfig(false)
    }
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
      enabled: row.enabled,
    })
  }

  const editTelegramGroup = (row: TelegramGroup) => {
    telegramForm.setFieldsValue({
      id: row.id,
      groupKey: row.groupKey,
      sourceChatId: row.sourceChatId,
      displayName: row.displayName,
      chatName: row.chatName,
      role: effectiveRole(row),
      currency: row.currency || 'USDT',
      exchangeRate: Number(row.exchangeRate ?? 1),
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

  const syncTelegramChats = async () => {
    setSyncingTelegram(true)
    try {
      const response = await apiService.bookkeeping.syncTelegramChats() as { data: ApiResponse<TelegramSyncResult> }
      if (response.data.code === 0 && response.data.data) {
        const result = response.data.data
        setTelegramSyncMessage(result.message)
        setDashboard((current) => ({
          ...current,
          telegramGroups: result.groups || current.telegramGroups,
        }))
        if (result.connected) {
          message.success(result.message || 'Telegram群聊已同步')
        } else {
          message.warning(result.message || '没有读取到当前 Telegram 账号已加入的群聊')
        }
        await loadTelegramStatus()
        await loadDashboard()
        return
      }
      message.error(response.data.msg || '同步失败')
    } finally {
      setSyncingTelegram(false)
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

  const groupNameById = useMemo(
    () => new Map(
      [...dashboard.whatsappGroups, ...dashboard.telegramGroups]
        .filter((group) => group.id !== undefined)
        .map((group) => [group.id as number, group.displayName || group.chatName])
    ),
    [dashboard.whatsappGroups, dashboard.telegramGroups]
  )

  const groupNameForOrder = (order: WhatsappOrder) => {
    if (order.groupId === undefined || order.groupId === null) return '-'
    return groupNameById.get(order.groupId) || '-'
  }

  const prematchReconciliationOrders = useMemo(
    () => dashboard.whatsappOrders.filter((order) => roleMatchesWorkspace(order.direction as GroupRole, 'prematch')),
    [dashboard.whatsappOrders]
  )

  const rollingReconciliationOrders = useMemo(
    () => dashboard.whatsappOrders.filter((order) => roleMatchesWorkspace(order.direction as GroupRole, 'rolling')),
    [dashboard.whatsappOrders]
  )

  const workspaceGroups = (workspaceType: WorkspaceType) => [
    ...dashboard.whatsappGroups,
    ...dashboard.telegramGroups,
  ].filter((group) => group.enabled && roleMatchesWorkspace(effectiveRole(group), workspaceType))

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
  const waterLossAmount = valueOrFallback(dashboard.summary.waterLossAmount, 0)
  const companyNetProfit = valueOrFallback(dashboard.summary.companyNetProfit, Number(dashboard.summary.todayProfit ?? 0))
  const rollingGroupStake = valueOrFallback(dashboard.summary.rollingGroupStake, 0)
  const rollingGroupSettlement = valueOrFallback(dashboard.summary.rollingGroupSettlement, 0)

  const preMatchSummaryItems = useMemo(() => [
    { label: '上游总下注额', value: moneyText(upstreamStake), hint: '所有上游确认订单金额' },
    { label: '下游总投放额', value: moneyText(downstreamStake), hint: '所有下游确认订单金额' },
    { label: '公司跟单额', value: moneyText(companyFollowStake), hint: '下游总投放额减上游总下注额' },
    { label: '盈亏水金额', value: moneyText(waterLossAmount), hint: '正数为赚水，负数为亏水' },
    { label: '公司总盈利', value: moneyText(companyNetProfit), hint: '包含公司跟单和盈亏水' },
  ], [upstreamStake, downstreamStake, companyFollowStake, waterLossAmount, companyNetProfit])

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

  const createGroupColumns = (
    sourceName: string,
    onEdit: (row: WhatsappGroup) => void
  ): ColumnsType<WhatsappGroup> => [
    { title: '群聊', dataIndex: 'displayName' },
    { title: `${sourceName}名称`, dataIndex: 'chatName', ellipsis: true },
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
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (value) => <Tag color={value ? 'green' : 'default'}>{value ? '启用' : '停用'}</Tag>,
    },
    {
      title: '操作',
      width: 90,
      render: (_, row) => <Button size="small" onClick={() => onEdit(row)}>编辑</Button>,
    },
  ]

  const groupColumns = createGroupColumns('WhatsApp', editWhatsappGroup)
  const telegramGroupColumns = createGroupColumns('Telegram', editTelegramGroup)
  const workspaceGroupColumns: ColumnsType<WhatsappGroup> = [
    {
      title: '来源',
      width: 100,
      render: (_, row) => <Tag>{row.sourceType === 'telegram' ? 'Telegram' : 'WhatsApp'}</Tag>,
    },
    { title: '群聊', dataIndex: 'displayName' },
    { title: '群名', dataIndex: 'chatName', ellipsis: true },
    {
      title: '角色',
      width: 120,
      render: (_, row) => {
        const role = effectiveRole(row)
        return <Tag color={roleColor(role)}>{roleText[role] || role}</Tag>
      },
    },
    {
      title: '状态',
      dataIndex: 'enabled',
      width: 90,
      render: (value) => <Tag color={value ? 'green' : 'default'}>{value ? '启用' : '停用'}</Tag>,
    },
  ]

  const wagerColumns: ColumnsType<CrownWager> = [
    { title: '投注时间', dataIndex: 'createdAt', render: timeText },
    { title: '联赛类型', dataIndex: 'leagueName', render: emptyText },
    { title: '比赛队伍', render: (_, row) => matchTeamsText(row.homeTeam, row.awayTeam) },
    { title: '投注盘口及赔率', render: (_, row) => marketOddsText(row.marketType, row.selectionName, row.oddsValue) },
    { title: '投注金额', dataIndex: 'stakeAmount', render: moneyText },
    { title: '赛果', dataIndex: 'status', render: emptyText },
    { title: '盈亏', dataIndex: 'winLossAmount', render: moneyText },
  ]

  const orderBillColumns: ColumnsType<WhatsappOrder> = [
    { title: '投注时间', dataIndex: 'messageTime', render: timeText },
    { title: '联赛类型', dataIndex: 'leagueName', render: emptyText },
    { title: '比赛队伍', render: (_, row) => matchTeamsText(undefined, undefined, row.matchName) },
    { title: '投注盘口及赔率', render: (_, row) => marketOddsText(row.marketText, undefined, row.oddsValue) },
    { title: '投注金额', dataIndex: 'amount', render: moneyText },
    { title: '赛果', dataIndex: 'settlementResult', render: emptyText },
    { title: '盈亏', render: (_, row) => orderProfitText(row) },
    { title: '群名', render: (_, row) => groupNameForOrder(row) },
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

  const scanTelegramMessages = async (workspaceType: WorkspaceType, force = false) => {
    const runningKey = `${workspaceType}:${force ? 'rescan' : 'scan'}`
    setScanningTelegram(runningKey)
    try {
      const response = await apiService.bookkeeping.scanTelegramMessages({
        businessDate,
        workspaceType,
        scanStart: workspaceType === 'prematch' ? scanStart : undefined,
        scanEnd: workspaceType === 'prematch' ? scanEnd : undefined,
        force,
      }) as { data: ApiResponse<TelegramScanResult> }
      if (response.data.code === 0 && response.data.data) {
        const result = response.data.data
        if (result.connected) {
          message.success(result.message || '扫描完成')
        } else {
          message.warning(result.message || 'Telegram 还没有连接')
        }
        await loadDashboard()
        return
      }
      message.error(response.data.msg || '扫描失败')
    } finally {
      setScanningTelegram(null)
    }
  }

  const fetchTitan007Scores = async () => {
    setFetchingTitan007Scores(true)
    try {
      const response = await apiService.bookkeeping.fetchTitan007Scores({ businessDate }) as { data: ApiResponse<Titan007ScoreFetchResult> }
      if (response.data.code === 0 && response.data.data) {
        const result = response.data.data
        if (result.fetchedCount > 0) {
          message.success(`已抓取 ${result.fetchedCount} 场赛果`)
        } else {
          message.warning('未抓到赛果，请确认当前日期已有完场数据')
        }
        await loadDashboard()
        return
      }
      message.error(response.data.msg || '抓取赛果失败')
    } finally {
      setFetchingTitan007Scores(false)
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
          扫描WhatsApp赛前群
        </Button>
        <Button
          icon={<ReloadOutlined />}
          onClick={() => scanWhatsappMessages('prematch', true)}
          loading={scanningWhatsapp === 'prematch:rescan'}
          disabled={scanningWhatsapp !== null && scanningWhatsapp !== 'prematch:rescan'}
        >
          重扫WhatsApp赛前群
        </Button>
        <Button
          icon={<CloudSyncOutlined />}
          onClick={() => scanTelegramMessages('prematch')}
          loading={scanningTelegram === 'prematch:scan'}
          disabled={scanningTelegram !== null && scanningTelegram !== 'prematch:scan'}
        >
          扫描TG赛前群
        </Button>
        <Button
          icon={<ReloadOutlined />}
          onClick={() => scanTelegramMessages('prematch', true)}
          loading={scanningTelegram === 'prematch:rescan'}
          disabled={scanningTelegram !== null && scanningTelegram !== 'prematch:rescan'}
        >
          重扫TG赛前群
        </Button>
        <Button
          icon={<CloudDownloadOutlined />}
          onClick={fetchTitan007Scores}
          loading={fetchingTitan007Scores}
        >
          抓取赛果
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
          扫描WhatsApp滚球群
        </Button>
        <Button
          icon={<CloudSyncOutlined />}
          onClick={() => scanTelegramMessages('rolling')}
          loading={scanningTelegram === 'rolling:scan'}
          disabled={scanningTelegram !== null && scanningTelegram !== 'rolling:scan'}
        >
          扫描TG滚球群
        </Button>
        <Button
          icon={<CloudDownloadOutlined />}
          onClick={fetchTitan007Scores}
          loading={fetchingTitan007Scores}
        >
          抓取赛果
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

  const renderWorkspaceGroupsSection = (workspaceType: WorkspaceType) => {
    const groups = workspaceGroups(workspaceType)
    return renderPanel(
      workspaceType === 'rolling' ? '滚球群配置' : '赛前群配置',
      `${groups.length} 个群`,
      <Table
        size="small"
        loading={loading}
        rowKey={(row) => `${row.sourceType || 'whatsapp'}-${row.id || row.groupKey}`}
        columns={workspaceGroupColumns}
        dataSource={groups}
        pagination={false}
        scroll={{ x: 720 }}
      />
    )
  }

  const renderTelegramApiQr = (value?: string) => {
    if (!value) return null
    if (value.startsWith('data:image')) {
      return (
        <img
          src={value}
          alt="Telegram API 登录二维码"
          style={{ width: 220, height: 220, display: 'block', background: '#fff', borderRadius: 6 }}
        />
      )
    }
    return <QRCode value={value} size={220} />
  }

  const renderDashboardPage = () => (
    <Space direction="vertical" size={20} style={pageContainer}>
      {renderHeader('赛前工作台', '上游订单、下游拆单、公司跟单与赛前结算中心')}
      {renderPreMatchTimeBar()}
      {renderPreMatchActions()}
      {renderWorkspaceGroupsSection('prematch')}
      {renderMetricGrid(preMatchSummaryItems)}
      {renderGeneratedFilesSection('prematch')}
    </Space>
  )

  const renderRollingPage = () => (
    <Space direction="vertical" size={20} style={pageContainer}>
      {renderHeader('滚球工作台', '滚球群订单、皇冠真实注单与滚球盈亏生成中心')}
      {renderRollingTimeBar()}
      {renderRollingActions()}
      {renderWorkspaceGroupsSection('rolling')}
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
      {renderHeader('WhatsApp群聊', '读取本机 WhatsApp 群聊，设置赛前上游、赛前下游、赛前公司、滚球上游、滚球下游、滚球公司或忽略')}

      <div style={pageSurface}>
        <div style={panelTitle}>
          <div>
            <Text strong>群聊同步</Text>
            <div><Text type="secondary">同步后会显示当前可读取的群聊，再给每个群设置角色、货币和汇率。</Text></div>
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
              initialValues={{ enabled: true, role: 'pending', currency: 'USDT', exchangeRate: 1 }}
            >
              <Form.Item name="id" hidden><Input /></Form.Item>
              <Form.Item name="sourceChatId" label="WhatsApp Chat ID"><Input placeholder="同步后自动带入" /></Form.Item>
              <Form.Item name="groupKey" label="群标识" rules={[{ required: true, message: '请输入群标识' }]}><Input placeholder="downstream-orders" /></Form.Item>
              <Form.Item name="displayName" label="显示名称" rules={[{ required: true, message: '请输入显示名称' }]}><Input /></Form.Item>
              <Form.Item name="chatName" label="群聊名称" rules={[{ required: true, message: '请输入群聊名称' }]}><Input /></Form.Item>
              <Form.Item name="role" label="角色" rules={[{ required: true }]}>
                <Select options={groupRoleOptions} />
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

  const renderTelegramGroupsPage = () => (
    <Space direction="vertical" size={16} style={pageContainer}>
      {renderHeader('Telegram群聊', '读取当前 Telegram 账号已加入的群聊，设置赛前上游、赛前下游、赛前公司、滚球上游、滚球下游、滚球公司或忽略')}

      <div style={pageSurface}>
        <div style={panelTitle}>
          <div>
            <Text strong>Telegram API 配置</Text>
            <div><Text type="secondary">填写 my.telegram.org 获取的 API_ID 和 API_HASH，保存后会重启 Telegram 读取服务。</Text></div>
          </div>
          {telegramApiConfig && (
            <Tag color={telegramApiConfig.bridgeConfigured ? 'green' : 'gold'}>
              {telegramApiConfig.bridgeConfigured ? '已配置' : '未配置'}
            </Tag>
          )}
        </div>
        <div style={panelBody}>
          <Form form={telegramApiConfigForm} layout="vertical">
            <section style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <Form.Item name="apiId" label="API_ID" rules={[{ required: true, message: '请输入 API_ID' }]}>
                <Input placeholder="12345678" />
              </Form.Item>
              <Form.Item name="apiHash" label="API_HASH" tooltip="不填则保留原 API_HASH">
                <Input.Password autoComplete="new-password" placeholder="不填则保留原值" />
              </Form.Item>
            </section>
            <Space wrap>
              <Button type="primary" icon={<CloudUploadOutlined />} onClick={saveTelegramApiConfig} loading={savingTelegramApiConfig}>
                保存 API 配置
              </Button>
              {telegramApiConfig?.message && <Text type="secondary">{telegramApiConfig.message}</Text>}
            </Space>
          </Form>
        </div>
      </div>

      <div style={pageSurface}>
        <div style={panelTitle}>
          <div>
            <Text strong>群聊同步</Text>
            <div><Text type="secondary">同步后会显示当前 Telegram 账号已加入且可读取的群聊，再给每个群设置角色、货币和汇率。</Text></div>
          </div>
          <Space wrap>
            <Button onClick={loadTelegramStatus}>刷新登录状态</Button>
            <Button icon={<CloudSyncOutlined />} onClick={syncTelegramChats} loading={syncingTelegram}>
              同步本机群聊
            </Button>
          </Space>
        </div>
        {telegramStatus && (
          <div style={{ padding: '12px 18px', borderBottom: '1px solid #edf1f7' }}>
            <Alert
              type={telegramStatus.connected ? 'success' : telegramStatus.status === 'qr_required' ? 'warning' : 'info'}
              showIcon
              message={telegramStatus.message}
            />
            {telegramStatus.qr && (
              <div style={{ marginTop: 14, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
                {renderTelegramApiQr(telegramStatus.qr)}
                <Text type="secondary">用手机 Telegram 的“连接桌面设备”扫描，登录成功后点“同步本机群聊”。二维码会自动刷新。</Text>
              </div>
            )}
          </div>
        )}
        {telegramSyncMessage && (
          <div style={{ padding: '12px 18px', borderBottom: '1px solid #edf1f7' }}>
            <Alert type="info" showIcon message={telegramSyncMessage} />
          </div>
        )}
      </div>

      <section style={pageSurface}>
        <div style={panelTitle}>
          <div>
            <Text strong>消息扫描</Text>
            <div><Text type="secondary">按当前日期和时间范围扫描已启用、已设置角色的 Telegram 群消息。</Text></div>
          </div>
          <Space wrap>
            <Button
              icon={<CloudSyncOutlined />}
              onClick={() => scanTelegramMessages('prematch')}
              loading={scanningTelegram === 'prematch:scan'}
              disabled={scanningTelegram !== null && scanningTelegram !== 'prematch:scan'}
            >
              扫描TG群聊
            </Button>
            <Button
              icon={<ReloadOutlined />}
              onClick={() => scanTelegramMessages('prematch', true)}
              loading={scanningTelegram === 'prematch:rescan'}
              disabled={scanningTelegram !== null && scanningTelegram !== 'prematch:rescan'}
            >
              重新扫描
            </Button>
            <Button
              icon={<CloudSyncOutlined />}
              onClick={() => scanTelegramMessages('rolling')}
              loading={scanningTelegram === 'rolling:scan'}
              disabled={scanningTelegram !== null && scanningTelegram !== 'rolling:scan'}
            >
              扫描TG滚球群
            </Button>
          </Space>
        </div>
      </section>

      <section style={{ display: 'grid', gridTemplateColumns: '380px minmax(0, 1fr)', gap: 16, alignItems: 'start' }}>
        <div style={pageSurface}>
          <div style={panelTitle}>
            <Text strong>群聊配置</Text>
          </div>
          <div style={panelBody}>
            <Form
              form={telegramForm}
              layout="vertical"
              initialValues={{ enabled: true, role: 'pending', currency: 'USDT', exchangeRate: 1 }}
            >
              <Form.Item name="id" hidden><Input /></Form.Item>
              <Form.Item name="sourceChatId" label="Telegram Chat ID"><Input placeholder="同步后自动带入" /></Form.Item>
              <Form.Item name="groupKey" label="群标识" rules={[{ required: true, message: '请输入群标识' }]}><Input placeholder="telegram-downstream-orders" /></Form.Item>
              <Form.Item name="displayName" label="显示名称" rules={[{ required: true, message: '请输入显示名称' }]}><Input /></Form.Item>
              <Form.Item name="chatName" label="群聊名称" rules={[{ required: true, message: '请输入群聊名称' }]}><Input /></Form.Item>
              <Form.Item name="role" label="角色" rules={[{ required: true }]}>
                <Select options={groupRoleOptions} />
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
              <Form.Item name="enabled" label="启用" valuePropName="checked"><Switch /></Form.Item>
              <Space>
                <Button type="primary" icon={<CloudUploadOutlined />} onClick={saveTelegramGroup}>保存群聊</Button>
                <Button onClick={() => telegramForm.resetFields()}>清空</Button>
              </Space>
            </Form>
          </div>
        </div>

        <div style={pageSurface}>
          <div style={panelTitle}>
            <Text strong>群聊列表</Text>
            <Text type="secondary">{dashboard.telegramGroups.length} 个群</Text>
          </div>
          <div style={panelBody}>
            <Table
              size="middle"
              loading={loading}
              rowKey={(row) => row.id || row.groupKey}
              columns={telegramGroupColumns}
              dataSource={dashboard.telegramGroups}
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
      {renderHeader('皇冠投注中心', '查看皇冠投注明细')}
      <div style={pageSurface}>
        <div style={panelBody}>
          <Table loading={loading} rowKey={(row) => row.id || row.ticketId} columns={wagerColumns} dataSource={dashboard.crownWagers} scroll={{ x: 980 }} />
        </div>
      </div>
    </Space>
  )

  const renderPrematchReconciliationPage = () => (
    <Space direction="vertical" size={16} style={pageContainer}>
      {renderHeader('赛前对账中心', '查看赛前群订单明细')}
      <div style={pageSurface}>
        <div style={panelBody}>
          <Table loading={loading} rowKey={(row) => row.id || row.orderKey} columns={orderBillColumns} dataSource={prematchReconciliationOrders} scroll={{ x: 980 }} />
        </div>
      </div>
    </Space>
  )

  const renderRollingReconciliationPage = () => (
    <Space direction="vertical" size={16} style={pageContainer}>
      {renderHeader('滚球对账中心', '查看滚球群订单明细')}
      <div style={pageSurface}>
        <div style={panelBody}>
          <Table loading={loading} rowKey={(row) => row.id || row.orderKey} columns={orderBillColumns} dataSource={rollingReconciliationOrders} scroll={{ x: 980 }} />
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
  if (pageKey === 'telegramGroups') return renderTelegramGroupsPage()
  if (pageKey === 'crownWagers') return renderCrownWagersPage()
  if (pageKey === 'prematchReconciliation') return renderPrematchReconciliationPage()
  if (pageKey === 'rollingReconciliation') return renderRollingReconciliationPage()
  if (pageKey === 'excel') return renderExcelPage()
  if (pageKey === 'systemUpdate') return <SystemUpdate />
  return renderDashboardPage()
}

export default Bookkeeping
