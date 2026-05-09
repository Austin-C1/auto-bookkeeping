import { describe, expect, it } from 'vitest'
import fs from 'node:fs'
import path from 'node:path'

const root = path.resolve(__dirname, '..')

describe('legacy league filter source', () => {
  it('is not exposed by bookkeeping app navigation', () => {
    const appSource = fs.readFileSync(path.join(root, 'App.tsx'), 'utf8')
    const layoutSource = fs.readFileSync(path.join(root, 'components', 'Layout.tsx'), 'utf8')

    expect(appSource).not.toContain('const LeagueFilter')
    expect(appSource).not.toContain('const DefaultTracking')
    expect(appSource).not.toContain('const PinnacleLeagueFilter')
    expect(appSource).not.toContain('const CrownLeagueFilter')
    expect(appSource).not.toContain('path="/league-filter"')
    expect(appSource).not.toContain('path="/default-tracking"')
    expect(appSource).not.toContain('path="/pinnacle-league-filter"')
    expect(appSource).not.toContain('path="/crown-league-filter"')
    expect(layoutSource).not.toContain("key: '/league-filter'")
    expect(layoutSource).not.toContain("key: '/default-tracking'")
    expect(layoutSource).not.toContain("key: '/pinnacle-league-filter'")
    expect(layoutSource).not.toContain("key: '/crown-league-filter'")
    expect(layoutSource).not.toContain("label: '联赛筛选'")
    expect(layoutSource).not.toContain("label: '默认追踪'")
    expect(layoutSource).not.toContain("label: '平博比赛选择'")
    expect(layoutSource).not.toContain("label: '皇冠比赛选择'")
  })
})
