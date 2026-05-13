import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

describe('app navigation', () => {
  it('routes directly to the bookkeeping workspace', () => {
    const appSource = readFileSync(join(process.cwd(), 'src', 'App.tsx'), 'utf8')
    const layoutSource = readFileSync(join(process.cwd(), 'src', 'components', 'Layout.tsx'), 'utf8')

    expect(appSource).toContain("import('./pages/Bookkeeping')")
    expect(appSource).toContain('path="/bookkeeping/*"')
    expect(appSource).toContain('<Navigate to="/bookkeeping" replace />')

    expect(layoutSource).toContain("key: '/bookkeeping'")
    expect(layoutSource).toContain("key: '/bookkeeping/rolling'")
    expect(layoutSource).toContain("key: '/bookkeeping/crown/accounts'")
    expect(layoutSource).toContain("key: '/bookkeeping/whatsapp/groups'")
    expect(layoutSource).toContain("key: '/bookkeeping/telegram/groups'")
    expect(layoutSource).toContain("key: '/bookkeeping/crown/wagers'")
    expect(layoutSource).toContain("key: '/bookkeeping/prematch/reconciliation'")
    expect(layoutSource).toContain("key: '/bookkeeping/rolling/reconciliation'")
    expect(layoutSource).toContain("key: '/bookkeeping/system/update'")
  })

  it('selects the most specific bookkeeping page first', () => {
    const layoutSource = readFileSync(join(process.cwd(), 'src', 'components', 'Layout.tsx'), 'utf8')
    const pageKeySection = layoutSource.slice(layoutSource.indexOf('const pageKeys'))

    expect(pageKeySection.indexOf("'/bookkeeping/rolling/reconciliation'")).toBeLessThan(
      pageKeySection.indexOf("'/bookkeeping/rolling',"),
    )
  })
})
