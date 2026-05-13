import axios from 'axios'

export interface ApiResponse<T = unknown> {
  code: number
  msg: string
  data: T
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api'

export const apiClient = axios.create({
  baseURL: API_BASE_URL,
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json',
  },
})

apiClient.interceptors.request.use((config) => {
  const savedLanguage = localStorage.getItem('i18n_language') || localStorage.getItem('i18nextLng') || 'zh-CN'
  config.headers['X-Language'] = savedLanguage
  return config
})

export const apiService = {
  bookkeeping: {
    dashboard: (data: { businessDate?: string; workspaceType?: string } = {}) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/dashboard', data),
    listCrownAccounts: () =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/crown/accounts/list', {}),
    saveCrownAccount: (data: unknown) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/crown/accounts/save', data),
    testCrownLogin: (data: { accountId: number }) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/crown/accounts/test-login', data),
    importCrownWagers: (data: unknown) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/crown/wagers/import', data),
    listWhatsappGroups: () =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/whatsapp/groups/list', {}),
    saveWhatsappGroup: (data: unknown) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/whatsapp/groups/save', data),
    whatsappBootstrap: () =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/whatsapp/bootstrap', {}),
    whatsappStatus: () =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/whatsapp/status', {}),
    syncWhatsappChats: () =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/whatsapp/chats/sync', {}),
    scanWhatsappMessages: (data: unknown) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/whatsapp/scan', data),
    importWhatsappOrders: (data: unknown) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/whatsapp/orders/import', data),
    listTelegramGroups: () =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/telegram/groups/list', {}),
    saveTelegramGroup: (data: unknown) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/telegram/groups/save', data),
    telegramApiConfig: () =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/telegram/api-config', {}),
    saveTelegramApiConfig: (data: unknown) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/telegram/api-config/save', data),
    telegramStatus: () =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/telegram/status', {}),
    syncTelegramChats: () =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/telegram/chats/sync', {}),
    scanTelegramMessages: (data: unknown) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/telegram/scan', data),
    fetchTitan007Scores: (data: unknown) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/score-results/titan007/fetch', data),
    runTask: (data: { businessDate: string; workspaceType?: string; reportType?: string }) =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/tasks/run', data),
    clearGeneratedFiles: () =>
      apiClient.post<ApiResponse<unknown>>('/bookkeeping/tasks/generated-files/clear', {}),
    downloadTask: (taskId: number) =>
      apiClient.get(`/bookkeeping/tasks/${taskId}/download`, { responseType: 'blob' }),
  },
}

export default apiService
