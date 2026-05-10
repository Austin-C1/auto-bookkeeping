import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = join(process.cwd(), '..')

const readRootFile = (path: string) => readFileSync(join(root, path), 'utf8')

describe('packaged BlackCat launcher', () => {
  it('opens bookkeeping through the BlackCat frontend without the password page', () => {
    const launchScript = readRootFile('launch-blackcat.ps1')

    expect(launchScript).toContain("$frontendUrl = 'http://127.0.0.1:18880'")
    expect(launchScript).toContain('$frontendAppUrl = "$frontendUrl/bookkeeping"')
    expect(launchScript).toContain("scripts\\serve-blackcat-frontend.ps1")
    expect(launchScript).toContain("Write-Status 'Opening bookkeeping page'")
    expect(launchScript).toContain('Start-Process $frontendAppUrl')
    expect(launchScript).not.toContain('$frontendLoginUrl')
    expect(launchScript).not.toContain('/login')
    expect(launchScript).not.toContain('serve-odds-frontend.ps1')
  })

  it('builds the packaged frontend against the packaged backend port', () => {
    const buildScript = readRootFile('build-blackcat-desktop-frontend.ps1')
    const updatePackageScript = readRootFile('build-blackcat-update-package.ps1')

    expect(buildScript).toContain("$apiUrl = 'http://127.0.0.1:18001'")
    expect(buildScript).toContain("$wsUrl = 'ws://127.0.0.1:18001'")
    expect(buildScript).not.toContain("127.0.0.1:8000")
    expect(updatePackageScript).toContain('$frontendBuildScript')
    expect(updatePackageScript).toContain('& $frontendBuildScript')
  })

  it('enables the packaged default admin with the property prefix read by AuthService', () => {
    const backendStartScript = readRootFile('start-blackcat-backend.ps1')
    const authService = readRootFile('backend/src/main/kotlin/com/wrbug/polymarketbot/service/auth/AuthService.kt')

    expect(backendStartScript).toContain('AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_ENABLED')
    expect(backendStartScript).toContain('AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_USERNAME')
    expect(backendStartScript).toContain('AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_PASSWORD')
    expect(authService).toContain('auto.bookkeeping.package.default-admin.enabled')
    expect(authService).toContain('auto.bookkeeping.package.default-admin.username')
    expect(authService).toContain('auto.bookkeeping.package.default-admin.password')
  })

  it('packages a frontend quick launcher and passwordless first start instructions', () => {
    const emptyPackageScript = readRootFile('build-blackcat-empty-package.ps1')
    const updatePackageScript = readRootFile('build-blackcat-update-package.ps1')
    const packageGuide = readRootFile('packaging/blackcat-empty-package/package-start-template.md')

    expect(emptyPackageScript).toContain('open-blackcat-frontend.cmd')
    expect(updatePackageScript).toContain('open-blackcat-frontend.cmd')
    expect(packageGuide).toContain('http://127.0.0.1:18880/bookkeeping')
    expect(packageGuide).toContain('本机免密进入')
    expect(packageGuide).not.toContain('http://127.0.0.1:18880/login')
    expect(packageGuide).not.toContain('创建你自己的账号和密码')
  })

  it('renders packaged Chinese guides as UTF-8 instead of mojibake', () => {
    const emptyPackageScript = readRootFile('build-blackcat-empty-package.ps1')

    expect(emptyPackageScript).toContain('Read-Utf8File')
    expect(emptyPackageScript).toContain('[System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)')
    expect(emptyPackageScript).toContain('$content = Read-Utf8File -Path $TemplatePath')
    expect(emptyPackageScript).not.toContain('$content = Get-Content -Path $TemplatePath -Raw')
  })
})
