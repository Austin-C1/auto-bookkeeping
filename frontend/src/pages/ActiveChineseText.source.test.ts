import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const activeFiles = [
  'src/components/Layout.tsx',
  'src/pages/Login.tsx',
  'src/pages/ResetPassword.tsx',
  'src/pages/Bookkeeping.tsx',
]

const mojibakeMarkers = [
  0x95c2,
  0x5a75,
  0x7f02,
  0x95c1,
  0x6fe0,
  0x95bc,
  0x9420,
  0x95b9,
  0x9477,
  0x934b,
  0x6769,
  0x7490,
  0x93b6,
  0x68e3,
  0x7035,
].map((codePoint) => String.fromCharCode(codePoint))

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
      'WhatsApp群聊',
      '皇冠投注中心',
      '赛前对账中心',
      '滚球对账中心',
      'Excel报表',
      '退出登录',
      '免密登录失败',
    ].forEach((label) => expect(combined).toContain(label))
  })
})
