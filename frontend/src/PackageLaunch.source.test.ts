import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const root = join(process.cwd(), '..')

const readRootFile = (path: string) => readFileSync(join(root, path), 'utf8')

describe('packaged BlackCat launcher', () => {
  it('ships the requested 1.1.3 version consistently', () => {
    const frontendPackage = JSON.parse(readRootFile('frontend/package.json')) as { version: string }
    const backendBuild = readRootFile('backend/build.gradle.kts')

    expect(frontendPackage.version).toBe('1.1.3')
    expect(backendBuild).toContain('version = "1.1.3"')
  })

  it('opens the bookkeeping workspace directly through the BlackCat frontend', () => {
    const launchScript = readRootFile('launch-blackcat.ps1')

    expect(launchScript).toContain("$frontendUrl = 'http://127.0.0.1:18880'")
    expect(launchScript).toContain('$frontendAppUrl = "$frontendUrl/bookkeeping"')
    expect(launchScript).toContain('$frontendApiReadyUrl = "$frontendUrl/api/update/version"')
    expect(launchScript).toContain('$expectedVersion = Get-ExpectedVersion')
    expect(launchScript).toContain('Test-BackendVersionReady')
    expect(launchScript).toContain("scripts\\serve-blackcat-frontend.ps1")
    expect(launchScript).toContain("Write-Status 'Opening bookkeeping page'")
    expect(launchScript).toContain('Start-Process $frontendAppUrl')
  })

  it('builds the packaged frontend against the packaged backend port', () => {
    const buildScript = readRootFile('build-blackcat-desktop-frontend.ps1')
    const updatePackageScript = readRootFile('build-blackcat-update-package.ps1')

    expect(buildScript).toContain("$apiUrl = 'http://127.0.0.1:18001'")
    expect(buildScript).toContain('$env:VERSION = $version')
    expect(buildScript).toContain('$env:GIT_TAG = "v$version"')
    expect(buildScript).toContain("$env:GITHUB_REPO_URL = 'https://github.com/Austin-C1/auto-bookkeeping'")
    expect(updatePackageScript).toContain('$frontendBuildScript')
    expect(updatePackageScript).toContain('& $frontendBuildScript')
  })

  it('keeps local accounts, group settings, and local data out of update packages', () => {
    const updatePackageScript = readRootFile('build-blackcat-update-package.ps1')

    expect(updatePackageScript).toContain("'local configuration'")
    expect(updatePackageScript).toContain("'crown accounts'")
    expect(updatePackageScript).toContain("'upstream and downstream groups'")
    expect(updatePackageScript).toContain("'WhatsApp session'")
    expect(updatePackageScript).toContain("'Telegram session'")
  })

  it('builds the blank installer in a staging directory', () => {
    const emptyPackageScript = readRootFile('build-blackcat-empty-package.ps1')

    expect(emptyPackageScript).toContain("$stagingRoot = Join-Path $desktopDir '_auto-bookkeeping-package-build'")
    expect(emptyPackageScript).toContain('$packageDir = Join-Path $stagingRoot $packageDirName')
  })

  it('starts the packaged backend with only bookkeeping runtime settings', () => {
    const backendStartScript = readRootFile('start-blackcat-backend.ps1')

    expect(backendStartScript).toContain('Get-ExpectedBackendVersion')
    expect(backendStartScript).toContain('auto-bookkeeping-backend-$expectedBackendVersion.jar')
    expect(backendStartScript).toContain('DB_URL')
    expect(backendStartScript).toContain('ENCRYPTION_KEY')
    expect(backendStartScript).toContain('CORS_ALLOWED_ORIGINS')
  })

  it('starts the WhatsApp bridge from the packaged launcher', () => {
    expect(existsSync(join(root, 'start-whatsapp-bridge.ps1'))).toBe(true)

    const launchScript = readRootFile('launch-blackcat.ps1')
    const whatsappStartScript = readRootFile('start-whatsapp-bridge.ps1')
    const emptyPackageScript = readRootFile('build-blackcat-empty-package.ps1')
    const updatePackageScript = readRootFile('build-blackcat-update-package.ps1')

    expect(launchScript).toContain("$whatsappBridgeScript = Join-Path $rootDir 'start-whatsapp-bridge.ps1'")
    expect(launchScript).toContain('$whatsappBridgePort = 18883')
    expect(launchScript).toContain('Ensure-WhatsappBridgeFiles')
    expect(launchScript).toContain("whatsapp-web.js")
    expect(launchScript).toContain("Invoke-LaunchStep 'Checking WhatsApp service'")
    expect(launchScript).toContain("'-File', $whatsappBridgeScript")
    expect(whatsappStartScript).toContain("$bridgeDir = Join-Path $rootDir 'whatsapp-bridge'")
    expect(whatsappStartScript).toContain('$env:WHATSAPP_BRIDGE_PORT')
    expect(emptyPackageScript).toContain("'start-whatsapp-bridge.ps1'")
    expect(emptyPackageScript).toContain("'whatsapp-bridge\\server.mjs'")
    expect(updatePackageScript).not.toContain("Copy-RequiredFile -Source (Join-Path $rootDir 'start-whatsapp-bridge.ps1')")
    expect(updatePackageScript).not.toContain("Copy-RequiredFile -Source (Join-Path $rootDir 'whatsapp-bridge\\server.mjs')")
    expect(updatePackageScript).not.toContain('AutoBookkeeping-v$version-install-and-use.md')
  })

  it('allows the WhatsApp browser enough time to start for QR login', () => {
    const whatsappServer = readRootFile('whatsapp-bridge/server.mjs')

    expect(whatsappServer).toContain('timeout: 90_000')
    expect(whatsappServer).toContain('protocolTimeout: 90_000')
  })

  it('packages the frontend quick launcher and current start guide', () => {
    const emptyPackageScript = readRootFile('build-blackcat-empty-package.ps1')
    const updatePackageScript = readRootFile('build-blackcat-update-package.ps1')
    const packageGuide = readRootFile('packaging/blackcat-empty-package/package-start-template.md')

    expect(emptyPackageScript).toContain('open-blackcat-frontend.cmd')
    expect(updatePackageScript).toContain('open-blackcat-frontend.cmd')
    expect(packageGuide).toContain('http://127.0.0.1:18880/bookkeeping')
  })

  it('renders packaged Chinese guides as UTF-8', () => {
    const emptyPackageScript = readRootFile('build-blackcat-empty-package.ps1')

    expect(emptyPackageScript).toContain('Read-Utf8File')
    expect(emptyPackageScript).toContain('[System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)')
    expect(emptyPackageScript).toContain('$content = Read-Utf8File -Path $TemplatePath')
  })
})
