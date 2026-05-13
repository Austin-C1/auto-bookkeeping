import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

describe('bookkeeping frontend source', () => {
  const app = readFileSync(join(process.cwd(), 'src', 'App.tsx'), 'utf8')
  const layout = readFileSync(join(process.cwd(), 'src', 'components', 'Layout.tsx'), 'utf8')
  const page = readFileSync(join(process.cwd(), 'src', 'pages', 'Bookkeeping.tsx'), 'utf8')
  const api = readFileSync(join(process.cwd(), 'src', 'services', 'api.ts'), 'utf8')

  it('registers the bookkeeping workspace and menu entries', () => {
    expect(app).toContain("const Bookkeeping = lazy(() => import('./pages/Bookkeeping'))")
    expect(app).toContain('path="/bookkeeping/*"')
    expect(layout).toContain("key: '/bookkeeping'")
    expect(layout).toContain("key: '/bookkeeping/rolling'")
    expect(layout).toContain("key: '/bookkeeping/crown/accounts'")
    expect(layout).toContain("key: '/bookkeeping/whatsapp/groups'")
    expect(layout).toContain("key: '/bookkeeping/telegram/groups'")
    expect(layout).toContain("key: '/bookkeeping/crown/wagers'")
    expect(layout).toContain("key: '/bookkeeping/prematch/reconciliation'")
    expect(layout).toContain("key: '/bookkeeping/rolling/reconciliation'")
    expect(layout).toContain("key: '/bookkeeping/excel'")
    expect(layout).toContain("key: '/bookkeeping/system/update'")
  })

  it('keeps system update inside the bookkeeping workspace', () => {
    expect(page).toContain("import SystemUpdate from './SystemUpdate'")
    expect(page).toContain("if (clean.startsWith('/bookkeeping/system/update')) return 'systemUpdate'")
    expect(page).toContain("if (pageKey === 'systemUpdate') return <SystemUpdate />")
  })

  it('keeps prematch and rolling workspaces in one bookkeeping page', () => {
    expect(page).toContain("if (clean.startsWith('/bookkeeping/rolling')) return 'rolling'")
    expect(page).toContain('preMatchSummaryItems')
    expect(page).toContain('rollingSummaryItems')
    expect(page).toContain('preMatchReportActions')
    expect(page).toContain('rollingReportActions')
    expect(page).toContain('renderDashboardPage')
    expect(page).toContain('renderCrownAccountsPage')
    expect(page).toContain('renderGeneratedFilesSection')
  })

  it('wires WhatsApp, Telegram, Crown, score, report, and file APIs', () => {
    expect(api).toContain('bookkeeping:')
    expect(api).toContain('/bookkeeping/dashboard')
    expect(api).toContain('/bookkeeping/crown/accounts/list')
    expect(api).toContain('/bookkeeping/crown/accounts/save')
    expect(api).toContain('/bookkeeping/crown/accounts/test-login')
    expect(api).toContain('/bookkeeping/crown/wagers/import')
    expect(api).toContain('/bookkeeping/whatsapp/bootstrap')
    expect(api).toContain('/bookkeeping/whatsapp/status')
    expect(api).toContain('/bookkeeping/whatsapp/chats/sync')
    expect(api).toContain('/bookkeeping/whatsapp/scan')
    expect(api).toContain('/bookkeeping/whatsapp/orders/import')
    expect(api).toContain('/bookkeeping/telegram/groups/list')
    expect(api).toContain('/bookkeeping/telegram/groups/save')
    expect(api).toContain('/bookkeeping/telegram/api-config')
    expect(api).toContain('/bookkeeping/telegram/api-config/save')
    expect(api).toContain('/bookkeeping/telegram/status')
    expect(api).toContain('/bookkeeping/telegram/chats/sync')
    expect(api).toContain('/bookkeeping/telegram/scan')
    expect(api).toContain('/bookkeeping/score-results/titan007/fetch')
    expect(api).toContain('/bookkeeping/tasks/run')
    expect(api).toContain('/bookkeeping/tasks/generated-files/clear')
    expect(api).toContain('/bookkeeping/tasks/${taskId}/download')
  })
})
