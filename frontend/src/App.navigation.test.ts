import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

describe('app navigation', () => {
  it('exposes bookkeeping subpages in the left navigation', () => {
    const appSource = readFileSync(join(process.cwd(), 'src', 'App.tsx'), 'utf8')
    const layoutSource = readFileSync(join(process.cwd(), 'src', 'components', 'Layout.tsx'), 'utf8')

    expect(appSource).toContain("import('./pages/Bookkeeping')")
    expect(appSource).toContain('path="/bookkeeping/*"')
    expect(appSource).toContain('<Navigate to="/bookkeeping" replace />')
    expect(layoutSource).toContain('自动做账')
    expect(layoutSource).toContain("key: '/bookkeeping'")
    expect(layoutSource).toContain("key: '/bookkeeping/rolling'")
    expect(layoutSource).toContain('赛前工作台')
    expect(layoutSource).toContain('滚球工作台')
    expect(layoutSource).toContain("key: '/bookkeeping/crown/accounts'")
    expect(layoutSource).toContain("key: '/bookkeeping/whatsapp/groups'")
    expect(layoutSource).toContain("key: '/bookkeeping/telegram/groups'")
    expect(layoutSource).toContain("key: '/bookkeeping/crown/wagers'")
    expect(layoutSource).toContain("key: '/bookkeeping/prematch/reconciliation'")
    expect(layoutSource).toContain("key: '/bookkeeping/rolling/reconciliation'")
    expect(layoutSource).toContain("key: '/bookkeeping/system/update'")
    expect(layoutSource).toContain('皇冠账号管理')
    expect(layoutSource).toContain('WhatsApp 群配置')
    expect(layoutSource).toContain('Telegram 群配置')
    expect(layoutSource).toContain('皇冠投注中心')
    expect(layoutSource).toContain('赛前对账中心')
    expect(layoutSource).toContain('滚球对账中心')
    expect(layoutSource).toContain('系统更新')
    expect(layoutSource).not.toContain('皇冠投注记录')
    expect(layoutSource).not.toContain('WhatsApp 订单中心')

    const pageKeySection = layoutSource.slice(layoutSource.indexOf('const pageKeys'))
    expect(pageKeySection.indexOf("'/bookkeeping/rolling/reconciliation'")).toBeLessThan(pageKeySection.indexOf("'/bookkeeping/rolling',"))
  })

  it('does not expose non-bookkeeping pages from the copied app', () => {
    const appSource = readFileSync(join(process.cwd(), 'src', 'App.tsx'), 'utf8')
    const layoutSource = readFileSync(join(process.cwd(), 'src', 'components', 'Layout.tsx'), 'utf8')
    const combined = `${appSource}\n${layoutSource}`

    expect(combined).not.toContain("import('./pages/OddsMonitor')")
    expect(combined).not.toContain('path="/odds-monitor"')
    expect(combined).not.toContain('/default-tracking')
    expect(combined).not.toContain('/pinnacle-league-filter')
    expect(combined).not.toContain('/crown-league-filter')
    expect(combined).not.toContain('/league-filter')
    expect(combined).not.toContain('/data-sources')
    expect(combined).not.toContain('/alerts')
    expect(combined).not.toContain('/polymarket-query')
    expect(combined).not.toContain('/runtime-logs')
    expect(combined).not.toContain('/system-settings/update')
    expect(combined).not.toContain('/copy-trading')
    expect(combined).not.toContain('/leaders')
    expect(combined).not.toContain('/positions')
    expect(combined).not.toContain('path="/accounts"')
    expect(combined).not.toContain('/announcements')
    expect(combined).not.toContain('/system-settings/rpc-nodes')
  })

  it('uses passwordless local login page without username or password inputs', () => {
    const appSource = readFileSync(join(process.cwd(), 'src', 'App.tsx'), 'utf8')
    const loginSource = readFileSync(join(process.cwd(), 'src', 'pages', 'Login.tsx'), 'utf8')

    expect(loginSource).toContain('localLogin')
    expect(appSource).toContain("state={{ from: `${location.pathname}${location.search}` }}")
    expect(loginSource).toContain("navigate(redirectPath, { replace: true })")
    expect(loginSource).not.toContain('name="username"')
    expect(loginSource).not.toContain('name="password"')
    expect(loginSource).not.toContain('Input.Password')
    expect(loginSource).not.toContain('UserOutlined')
    expect(loginSource).not.toContain('LockOutlined')
  })
})
