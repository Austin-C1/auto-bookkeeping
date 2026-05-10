/* @vitest-environment jsdom */
import { describe, expect, it, vi } from 'vitest'

vi.stubGlobal('localStorage', {
  getItem: () => null,
  setItem: () => undefined,
  removeItem: () => undefined,
  clear: () => undefined,
})

describe('bookkeeping dashboard normalization', () => {
  it('fills telegram groups when older backend responses omit telegram fields', async () => {
    const { normalizeDashboard } = await import('./Bookkeeping')
    const dashboard = normalizeDashboard({
      businessDate: '2026-05-09',
      summary: {
        whatsappOrderCount: 3,
      },
    } as any)

    expect(dashboard.businessDate).toBe('2026-05-09')
    expect(dashboard.summary.whatsappOrderCount).toBe(3)
    expect(dashboard.summary.crownAccountTotal).toBe(0)
    expect(dashboard.whatsappGroups).toEqual([])
    expect(dashboard.telegramGroups).toEqual([])
    expect(dashboard.crownAccounts).toEqual([])
  }, 20000)
})
