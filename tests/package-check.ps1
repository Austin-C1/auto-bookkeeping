$ErrorActionPreference = 'Stop'

$rootDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$startScriptPath = Join-Path $rootDir 'start-blackcat-backend.ps1'
$emptyPackageScriptPath = Join-Path $rootDir 'build-blackcat-empty-package.ps1'

function Assert-Contains {
    param(
        [string]$Content,
        [string]$Needle,
        [string]$Message
    )

    if (-not $Content.Contains($Needle)) {
        throw $Message
    }
}

$startScript = Get-Content -Path $startScriptPath -Raw
$emptyPackageScript = Get-Content -Path $emptyPackageScriptPath -Raw

Assert-Contains `
    -Content $startScript `
    -Needle "Get-ChildItem -Path (Join-Path `$backendDir 'build\libs') -Filter 'auto-bookkeeping-backend-*.jar'" `
    -Message 'start-blackcat-backend.ps1 must discover the packaged backend jar dynamically.'

Assert-Contains `
    -Content $startScript `
    -Needle "`$env:AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_ENABLED" `
    -Message 'start-blackcat-backend.ps1 must enable the packaged default admin account.'

Assert-Contains `
    -Content $startScript `
    -Needle "'123456'" `
    -Message 'start-blackcat-backend.ps1 must configure 123456 as the packaged default username/password.'

Assert-Contains `
    -Content $emptyPackageScript `
    -Needle "5LiA6ZSu5a6J6KOF5ZCv5YqoLmNtZA==" `
    -Message 'build-blackcat-empty-package.ps1 must include a one-click install/start command.'

Assert-Contains `
    -Content $emptyPackageScript `
    -Needle "CreateShortcut" `
    -Message 'The one-click launcher must create a desktop shortcut for opening BlackCat after install.'

Assert-Contains `
    -Content $emptyPackageScript `
    -Needle "6buR54yr5ZCv5YqoLmxuaw==" `
    -Message 'The desktop shortcut must be named 黑猫启动.lnk.'

Assert-Contains `
    -Content $emptyPackageScript `
    -Needle "AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_ENABLED" `
    -Message 'build-blackcat-empty-package.ps1 must preserve the packaged default admin config in generated packages.'

Write-Output 'Package script checks passed.'
