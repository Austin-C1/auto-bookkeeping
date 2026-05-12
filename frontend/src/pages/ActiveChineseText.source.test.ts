import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const activeFiles = [
  'src/components/Layout.tsx',
  'src/pages/Bookkeeping.tsx',
]

const mojibakeMarkers = [
  '鑷',
  '璧',
  '婊',
  '鐨',
  '瀵',
  '绯',
  '閫',
]

describe('active page Chinese text', () => {
  it('does not contain mojibake in active bookkeeping pages', () => {
    const combined = activeFiles
      .map((file) => readFileSync(join(process.cwd(), file), 'utf8'))
      .join('\n')

    mojibakeMarkers.forEach((marker) => {
      expect(combined).not.toContain(marker)
    })
  })

  it('keeps visible labels readable', () => {
    const combined = activeFiles
      .map((file) => readFileSync(join(process.cwd(), file), 'utf8'))
      .join('\n')

    ;[
      '自动做账',
      '赛前工作台',
      '滚球工作台',
      '皇冠账号',
      'WhatsApp',
      'Telegram',
      '皇冠投注中心',
      '赛前对账中心',
      '滚球对账中心',
      '系统更新',
    ].forEach((label) => expect(combined).toContain(label))
  })
})
