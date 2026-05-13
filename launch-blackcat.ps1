$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$launcherCmdPath = Join-Path $rootDir 'launch-blackcat.cmd'
$desktopShortcutName = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('6Ieq5Yqo5YGa6LSm5ZCv5YqoLmxuaw=='))
$backendScript = Join-Path $rootDir 'start-blackcat-backend.ps1'
$frontendDir = Join-Path $rootDir 'frontend'
$frontendUrl = 'http://127.0.0.1:18880'
$frontendAppUrl = "$frontendUrl/bookkeeping"
$frontendApiReadyUrl = "$frontendUrl/api/update/version"
$databasePort = 13307
$databaseContainerName = 'blackcat-v1-mysql'
$databaseImage = 'mysql:8.1'
$databaseVolumeName = 'blackcat-v1-mysql-data'
$databaseName = 'blackcat_v1'
$databasePassword = 'change-me'
$dockerDesktopExe = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
$backendPort = 18001
$backendUrl = "http://127.0.0.1:$backendPort"
$backendReadyUrl = "$backendUrl/api/update/version"
$frontendPort = 18880
$backendStartupTimeoutSeconds = 180
$frontendOutLog = Join-Path $rootDir 'frontend-live.out.log'
$frontendErrLog = Join-Path $rootDir 'frontend-live.err.log'
$frontendDistDir = Join-Path $frontendDir 'dist'
$frontendDistMarker = Join-Path $frontendDistDir '.desktop-runtime.json'
$frontendStaticServerScript = Join-Path $rootDir 'scripts\serve-blackcat-frontend.ps1'
$powershellExe = Join-Path $PSHOME 'powershell.exe'
$localConfig = Join-Path $rootDir 'config\local.env.ps1'

if (Test-Path $localConfig) {
    . $localConfig
}

function Write-Status {
    param([string]$Message)
    Write-Host "[BlackCat] $Message"
}

function Fail-Launch {
    param([string]$Message)

    Write-Host ''
    Write-Host "[BlackCat] Launch failed: $Message" -ForegroundColor Red
    Write-Host "[BlackCat] Logs: $rootDir"
    Write-Host ''
    if ([System.Environment]::UserInteractive -and -not [System.Console]::IsInputRedirected) {
        Write-Host 'Press any key to close...'
        try {
            [void][System.Console]::ReadKey($true)
        }
        catch {
        }
    }
    exit 1
}

function Ensure-DesktopShortcut {
    try {
        if (-not (Test-Path $launcherCmdPath)) {
            return
        }

        $desktopDir = [Environment]::GetFolderPath('Desktop')
        if ([string]::IsNullOrWhiteSpace($desktopDir) -or -not (Test-Path $desktopDir)) {
            return
        }

        $shortcutPath = Join-Path $desktopDir $desktopShortcutName
        $shell = New-Object -ComObject WScript.Shell
        $shortcut = $shell.CreateShortcut($shortcutPath)
        $shortcut.TargetPath = $launcherCmdPath
        $shortcut.WorkingDirectory = $rootDir
        $shortcut.IconLocation = $launcherCmdPath
        $shortcut.Save()
    }
    catch {
        Write-Status "Desktop shortcut skipped: $($_.Exception.Message)"
    }
}

function Invoke-LaunchStep {
    param(
        [string]$Message,
        [scriptblock]$Action
    )

    Write-Status $Message
    try {
        & $Action
    }
    catch {
        Fail-Launch $_.Exception.Message
    }
}

function Set-TrimmedEnv {
    param([string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ($null -ne $value) {
        [Environment]::SetEnvironmentVariable($Name, $value.Trim(), 'Process')
    }
}

function Get-TrimmedString {
    param([object]$Value)

    if ($null -eq $Value) {
        return $null
    }

    return ([string]$Value).Trim()
}

function Get-ExpectedVersion {
    if (Test-Path $frontendDistMarker) {
        try {
            $marker = Get-Content -Path $frontendDistMarker -Raw | ConvertFrom-Json
            if (-not [string]::IsNullOrWhiteSpace($marker.version)) {
                return ([string]$marker.version).Trim()
            }
        }
        catch {
        }
    }

    $packageJsonPath = Join-Path $frontendDir 'package.json'
    if (Test-Path $packageJsonPath) {
        try {
            $packageJson = Get-Content -Path $packageJsonPath -Raw | ConvertFrom-Json
            if (-not [string]::IsNullOrWhiteSpace($packageJson.version)) {
                return ([string]$packageJson.version).Trim()
            }
        }
        catch {
        }
    }

    return $null
}

$expectedVersion = Get-ExpectedVersion
$databaseContainerName = Get-TrimmedString $databaseContainerName
$databaseImage = Get-TrimmedString $databaseImage
$databaseVolumeName = Get-TrimmedString $databaseVolumeName
$databaseName = Get-TrimmedString $databaseName
$databasePassword = ([string]$databasePassword).Trim()
@('DB_URL', 'DB_USERNAME', 'DB_PASSWORD', 'ENCRYPTION_KEY') |
    ForEach-Object { Set-TrimmedEnv -Name $_ }

function Test-PortListening {
    param([int]$Port)

    return $null -ne (Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue | Select-Object -First 1)
}

function Wait-PortListening {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 30
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening -Port $Port) {
            return $true
        }
        Start-Sleep -Seconds 1
    }

    return $false
}

function Wait-PortFree {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 20
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (-not (Test-PortListening -Port $Port)) {
            return $true
        }
        Start-Sleep -Seconds 1
    }

    return -not (Test-PortListening -Port $Port)
}

function Wait-HttpReady {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 400) {
                return $true
            }
        }
        catch {
        }

        Start-Sleep -Seconds 1
    }

    return $false
}

function Test-BackendVersionReady {
    param(
        [string]$Url,
        [string]$ExpectedVersion
    )

    try {
        $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 400) {
            return $false
        }

        if ([string]::IsNullOrWhiteSpace($ExpectedVersion)) {
            return $true
        }

        $payload = $response.Content | ConvertFrom-Json
        $actualVersion = ([string]$payload.data.version).Trim()
        return $actualVersion -eq $ExpectedVersion
    }
    catch {
        return $false
    }
}

function Wait-BackendVersionReady {
    param(
        [string]$Url,
        [string]$ExpectedVersion,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-BackendVersionReady -Url $Url -ExpectedVersion $ExpectedVersion) {
            return $true
        }
        Start-Sleep -Seconds 1
    }

    return $false
}

function Test-PostReady {
    param([string]$Url)

    try {
        $response = Invoke-WebRequest `
            -Uri $Url `
            -Method Post `
            -Body '{}' `
            -ContentType 'application/json' `
            -UseBasicParsing `
            -TimeoutSec 5
        return $response.StatusCode -eq 200
    }
    catch {
        return $false
    }
}

function Wait-PostReady {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 60
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PostReady -Url $Url) {
            return $true
        }
        Start-Sleep -Seconds 1
    }

    return $false
}

function Stop-BlackcatFrontendServer {
    $processes = Get-CimInstance Win32_Process |
        Where-Object { $_.CommandLine -like '*serve-blackcat-frontend.ps1*' }

    foreach ($process in $processes) {
        Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
    }

    if ($processes) {
        Start-Sleep -Seconds 2
    }
}

function Stop-BlackcatBackendServer {
    $processes = Get-CimInstance Win32_Process |
        Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -like '*auto-bookkeeping-backend*' }

    foreach ($process in $processes) {
        Stop-Process -Id $process.ProcessId -Force -ErrorAction SilentlyContinue
    }

    if ($processes) {
        Start-Sleep -Seconds 3
    }
}

function Get-NewestWriteTime {
    param([string[]]$Paths)

    $latest = Get-Date '2000-01-01'
    foreach ($path in $Paths) {
        if (-not (Test-Path $path)) {
            continue
        }

        $item = Get-Item $path
        if ($item.PSIsContainer) {
            $candidate = Get-ChildItem -Path $item.FullName -Recurse -File -ErrorAction SilentlyContinue |
                Sort-Object LastWriteTime -Descending |
                Select-Object -First 1
            if ($candidate -and $candidate.LastWriteTime -gt $latest) {
                $latest = $candidate.LastWriteTime
            }
            continue
        }

        if ($item.LastWriteTime -gt $latest) {
            $latest = $item.LastWriteTime
        }
    }

    return $latest
}

function Test-DesktopFrontendBuildAvailable {
    param([int]$BackendPort)

    if (
        -not (Test-Path $frontendDistDir) `
        -or -not (Test-Path $frontendStaticServerScript) `
        -or -not (Test-Path (Join-Path $frontendDistDir 'index.html'))
    ) {
        return $false
    }

    if (Test-Path $frontendDistMarker) {
        try {
            $marker = Get-Content -Path $frontendDistMarker -Raw | ConvertFrom-Json
            if ($marker.apiUrl -ne "http://127.0.0.1:$BackendPort") {
                return $false
            }
        }
        catch {
            return $false
        }
    }

    $sourceLatest = Get-NewestWriteTime -Paths @(
        (Join-Path $frontendDir 'src'),
        (Join-Path $frontendDir 'public'),
        (Join-Path $frontendDir 'index.html'),
        (Join-Path $frontendDir 'package.json'),
        (Join-Path $frontendDir 'package-lock.json'),
        (Join-Path $frontendDir 'vite.config.ts')
    )
    $buildLatest = Get-NewestWriteTime -Paths @($frontendDistDir)

    return $buildLatest -ge $sourceLatest
}

function Test-DockerAvailable {
    try {
        docker version --format '{{.Server.Version}}' 1>$null 2>$null
        return $LASTEXITCODE -eq 0
    }
    catch {
        return $false
    }
}

function Wait-DockerAvailable {
    param([int]$TimeoutSeconds = 120)

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-DockerAvailable) {
            return $true
        }
        Start-Sleep -Seconds 5
    }

    return $false
}

function Ensure-DatabaseContainer {
    param(
        [string]$ContainerName,
        [int]$Port,
        [string]$Image,
        [string]$RootPassword,
        [string]$DatabaseName,
        [string]$VolumeName
    )

    $databaseContainerExists = docker ps -a --filter "name=^/${ContainerName}$" --format "{{.Names}}"
    if ($databaseContainerExists -contains $ContainerName) {
        $databaseContainerRunning = docker ps --filter "name=^/${ContainerName}$" --format "{{.Names}}"
        if (-not ($databaseContainerRunning -contains $ContainerName)) {
            docker start $ContainerName | Out-Null
        }
        return
    }

    docker run -d `
        --name $ContainerName `
        --restart unless-stopped `
        -p "${Port}:3306" `
        -e "TZ=Asia/Shanghai" `
        -e "MYSQL_ROOT_PASSWORD=$RootPassword" `
        -e "MYSQL_DATABASE=$DatabaseName" `
        -v "${VolumeName}:/var/lib/mysql" `
        $Image `
        --character-set-server=utf8mb4 `
        --collation-server=utf8mb4_unicode_ci | Out-Null

    if ($LASTEXITCODE -ne 0) {
        throw "Database container creation failed: $ContainerName"
    }
}

function Wait-DatabaseReady {
    param(
        [string]$ContainerName,
        [string]$RootPassword,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        try {
            docker exec $ContainerName sh -lc "mysqladmin ping -h 127.0.0.1 -p$RootPassword --silent" 1>$null 2>$null
            if ($LASTEXITCODE -eq 0) {
                return $true
            }
        }
        catch {
        }

        Start-Sleep -Seconds 2
    }

    return $false
}

Ensure-DesktopShortcut

Invoke-LaunchStep 'Checking program files' {
    if (-not (Test-Path $backendScript)) {
        throw "Backend start script not found: $backendScript"
    }

    if (-not (Test-Path $frontendDir)) {
        throw "Frontend directory not found: $frontendDir"
    }
}

$frontendMode = if (Test-DesktopFrontendBuildAvailable -BackendPort $backendPort) { 'static' } else { 'dev' }

Invoke-LaunchStep 'Checking database' {
    if (-not (Test-PortListening -Port $databasePort)) {
        if (-not (Test-DockerAvailable)) {
            if (-not (Test-Path $dockerDesktopExe)) {
                throw "Docker Desktop not found: $dockerDesktopExe"
            }

            Write-Status 'Starting Docker Desktop'
            Start-Process -FilePath $dockerDesktopExe | Out-Null
        }

        if (-not (Wait-DockerAvailable -TimeoutSeconds 180)) {
            throw 'Docker did not become available.'
        }

        Ensure-DatabaseContainer `
            -ContainerName $databaseContainerName `
            -Port $databasePort `
            -Image $databaseImage `
            -RootPassword $databasePassword `
            -DatabaseName $databaseName `
            -VolumeName $databaseVolumeName

        if (-not (Wait-PortListening -Port $databasePort -TimeoutSeconds 90)) {
            throw "Database did not start on port $databasePort."
        }

        if (-not (Wait-DatabaseReady -ContainerName $databaseContainerName -RootPassword $databasePassword -TimeoutSeconds 120)) {
            throw "Database did not become ready inside container $databaseContainerName."
        }
    }
}

Invoke-LaunchStep 'Checking backend service' {
    if ((Test-PortListening -Port $backendPort) -and -not (Test-BackendVersionReady -Url $backendReadyUrl -ExpectedVersion $expectedVersion)) {
        Write-Status 'Backend port is occupied, unhealthy, or outdated; restarting backend'
        Stop-BlackcatBackendServer
        if (-not (Wait-PortFree -Port $backendPort -TimeoutSeconds 20)) {
            throw "Backend port $backendPort is still occupied."
        }
    }

    if (-not (Test-PortListening -Port $backendPort)) {
        Start-Process -FilePath $powershellExe `
            -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $backendScript) `
            -WorkingDirectory $rootDir `
            -WindowStyle Hidden | Out-Null
    }

    if (-not (Wait-BackendVersionReady -Url $backendReadyUrl -ExpectedVersion $expectedVersion -TimeoutSeconds $backendStartupTimeoutSeconds)) {
        throw "Backend did not become ready with version $expectedVersion at $backendReadyUrl."
    }
}

Invoke-LaunchStep 'Checking frontend service' {
    if ((Test-PortListening -Port $frontendPort) -and -not (Test-BackendVersionReady -Url $frontendApiReadyUrl -ExpectedVersion $expectedVersion)) {
        Write-Status 'Frontend service is outdated or API proxy is unhealthy; restarting frontend'
        Stop-BlackcatFrontendServer
        if (-not (Wait-PortFree -Port $frontendPort -TimeoutSeconds 20)) {
            throw "Frontend port $frontendPort is still occupied."
        }
    }

    if (-not (Test-PortListening -Port $frontendPort)) {
        if ($frontendMode -eq 'static') {
            Start-Process -FilePath $powershellExe `
                -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $frontendStaticServerScript, '-Root', $frontendDistDir, '-ListenHost', '127.0.0.1', '-Port', $frontendPort.ToString(), '-BackendUrl', $backendUrl) `
                -WorkingDirectory $rootDir `
                -RedirectStandardOutput $frontendOutLog `
                -RedirectStandardError $frontendErrLog `
                -WindowStyle Hidden | Out-Null
        }
        else {
            $npmCmd = (Get-Command 'npm.cmd' -ErrorAction Stop).Source
            Start-Process -FilePath $npmCmd `
                -ArgumentList @('run', 'dev', '--', '--host', '127.0.0.1', '--port', "$frontendPort") `
                -WorkingDirectory $frontendDir `
                -RedirectStandardOutput $frontendOutLog `
                -RedirectStandardError $frontendErrLog `
                -WindowStyle Hidden | Out-Null
        }
    }

    if (-not (Wait-PortListening -Port $frontendPort -TimeoutSeconds 60)) {
        throw "Frontend did not start on port $frontendPort."
    }

    if (-not (Wait-HttpReady -Url $frontendAppUrl -TimeoutSeconds 60)) {
        throw "Frontend page did not become available at $frontendAppUrl."
    }

    if (-not (Test-BackendVersionReady -Url $frontendApiReadyUrl -ExpectedVersion $expectedVersion)) {
        throw "Frontend API proxy did not become ready at $frontendApiReadyUrl."
    }
}

Write-Status 'Opening bookkeeping page'
Start-Process $frontendAppUrl | Out-Null
Write-Status 'Ready'




