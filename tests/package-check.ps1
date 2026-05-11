$ErrorActionPreference = 'Stop'

$rootDir = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$launchScriptPath = Join-Path $rootDir 'launch-blackcat.ps1'
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

function Assert-NotContains {
    param(
        [string]$Content,
        [string]$Needle,
        [string]$Message
    )

    if ($Content.Contains($Needle)) {
        throw $Message
    }
}

$launchScript = Get-Content -Path $launchScriptPath -Raw
$startScript = Get-Content -Path $startScriptPath -Raw
$emptyPackageScript = Get-Content -Path $emptyPackageScriptPath -Raw

Assert-Contains `
    -Content $launchScript `
    -Needle "Ensure-DesktopShortcut" `
    -Message 'launch-blackcat.ps1 must create or repair the desktop shortcut on every start.'

Assert-Contains `
    -Content $launchScript `
    -Needle "CreateShortcut" `
    -Message 'launch-blackcat.ps1 must use a Windows shortcut instead of only opening the browser.'

Assert-Contains `
    -Content $launchScript `
    -Needle "6Ieq5Yqo5YGa6LSm5ZCv5YqoLmxuaw==" `
    -Message 'launch-blackcat.ps1 must name the desktop shortcut 自动做账启动.lnk.'

Assert-NotContains `
    -Content $launchScript `
    -Needle "6buR54yr5ZCv5YqoLmxuaw==" `
    -Message 'launch-blackcat.ps1 must not overwrite 黑猫启动.lnk.'

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
    -Needle "6Ieq5Yqo5YGa6LSm5ZCv5YqoLmxuaw==" `
    -Message 'The desktop shortcut must be named 自动做账启动.lnk.'

Assert-NotContains `
    -Content $emptyPackageScript `
    -Needle "6buR54yr5ZCv5YqoLmxuaw==" `
    -Message 'The package launcher must not overwrite 黑猫启动.lnk.'

Assert-Contains `
    -Content $emptyPackageScript `
    -Needle "AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_ENABLED" `
    -Message 'build-blackcat-empty-package.ps1 must preserve the packaged default admin config in generated packages.'

Write-Output 'Package script checks passed.'
