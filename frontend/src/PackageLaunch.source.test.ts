import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = join(process.cwd(), '..')

const readRootFile = (path: string) => readFileSync(join(root, path), 'utf8')

describe('packaged BlackCat launcher', () => {
  it('ships the requested 1.1.0 version consistently', () => {
    const frontendPackage = JSON.parse(readRootFile('frontend/package.json')) as { version: string }
    const backendBuild = readRootFile('backend/build.gradle.kts')

    expect(frontendPackage.version).toBe('1.1.0')
    expect(backendBuild).toContain('version = "1.1.0"')
  })

  it('removes the old reset password and first-use backend flow from packaged sources', () => {
    const launchScript = readRootFile('launch-blackcat.ps1')
    const oddsLaunchScript = readRootFile('launch-odds-monitor.ps1')
    const authController = readRootFile('backend/src/main/kotlin/com/wrbug/polymarketbot/controller/auth/AuthController.kt')
    const authService = readRootFile('backend/src/main/kotlin/com/wrbug/polymarketbot/service/auth/AuthService.kt')
    const authDto = readRootFile('backend/src/main/kotlin/com/wrbug/polymarketbot/dto/AuthRequest.kt')
    const authInterceptor = readRootFile('backend/src/main/kotlin/com/wrbug/polymarketbot/config/JwtAuthenticationInterceptor.kt')
    const rateLimitService = readRootFile('backend/src/main/kotlin/com/wrbug/polymarketbot/service/common/RateLimitService.kt')
    const appProperties = readRootFile('backend/src/main/resources/application.properties')
    const combined = [
      launchScript,
      oddsLaunchScript,
      authController,
      authService,
      authDto,
      authInterceptor,
      rateLimitService,
      appProperties
    ].join('\n')

    expect(launchScript).toContain('/api/auth/local-login')
    expect(oddsLaunchScript).toContain('/api/auth/local-login')
    expect(combined).not.toContain('reset-password')
    expect(combined).not.toContain('check-first-use')
    expect(combined).not.toContain('ResetPasswordRequest')
    expect(combined).not.toContain('CheckFirstUseResponse')
    expect(combined).not.toContain('checkResetPasswordRateLimit')
    expect(combined).not.toContain('admin.reset-password')
    expect(combined).not.toContain('rate-limit.reset-password')
  })

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
    expect(buildScript).toContain('$env:VERSION = $version')
    expect(buildScript).toContain('$env:GIT_TAG = "v$version"')
    expect(buildScript).toContain("$env:GITHUB_REPO_URL = 'https://github.com/Austin-C1/auto-bookkeeping'")
    expect(buildScript).not.toContain("127.0.0.1:8000")
    expect(updatePackageScript).toContain('$frontendBuildScript')
    expect(updatePackageScript).toContain('& $frontendBuildScript')
  })

  it('keeps local accounts, group settings, and login sessions out of update packages', () => {
    const updatePackageScript = readRootFile('build-blackcat-update-package.ps1')

    expect(updatePackageScript).toContain("'local login'")
    expect(updatePackageScript).toContain("'crown accounts'")
    expect(updatePackageScript).toContain("'upstream and downstream groups'")
    expect(updatePackageScript).toContain("'WhatsApp session'")
    expect(updatePackageScript).toContain("'Telegram session'")
    expect(updatePackageScript).not.toContain('config\\local.env.ps1')
    expect(updatePackageScript).not.toContain('config\\update.json')
    expect(updatePackageScript).not.toContain('data\\telegram-session.txt')
    expect(updatePackageScript).not.toContain('.wwebjs_auth')
    expect(updatePackageScript).not.toContain('.wwebjs_cache')
  })

  it('builds the blank installer in a staging directory instead of overwriting an installed copy', () => {
    const emptyPackageScript = readRootFile('build-blackcat-empty-package.ps1')

    expect(emptyPackageScript).toContain("$stagingRoot = Join-Path $desktopDir '_auto-bookkeeping-package-build'")
    expect(emptyPackageScript).toContain('$packageDir = Join-Path $stagingRoot $packageDirName')
    expect(emptyPackageScript).not.toContain('$packageDir = Join-Path $desktopDir $packageDirName')
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
    expect(packageGuide).toContain('默认免密进入')
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
