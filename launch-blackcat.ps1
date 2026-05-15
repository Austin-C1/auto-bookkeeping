$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$launcherCmdPath = Join-Path $rootDir 'launch-blackcat.cmd'
$desktopShortcutName = [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String('6Ieq5Yqo5YGa6LSm5ZCv5YqoLmxuaw=='))
$backendScript = Join-Path $rootDir 'start-blackcat-backend.ps1'
$whatsappBridgeScript = Join-Path $rootDir 'start-whatsapp-bridge.ps1'
$whatsappBridgeDir = Join-Path $rootDir 'whatsapp-bridge'
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
$whatsappBridgePort = 18883
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

function Write-Utf8File {
    param(
        [string]$Path,
        [string]$Content
    )

    $utf8NoBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($Path, $Content, $utf8NoBom)
}

function Ensure-WhatsappBridgeFiles {
    New-Item -ItemType Directory -Path $whatsappBridgeDir -Force | Out-Null

    $whatsappStartScript = @'
$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$bridgeDir = Join-Path $rootDir 'whatsapp-bridge'
$localConfig = Join-Path $rootDir 'config\local.env.ps1'

if (Test-Path $localConfig) {
    . $localConfig
}

$env:WHATSAPP_BRIDGE_PORT = if ($env:WHATSAPP_BRIDGE_PORT) { $env:WHATSAPP_BRIDGE_PORT } else { '18883' }

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js runtime not found. Please install Node.js or run this from the development environment."
}

if (-not (Test-Path $bridgeDir)) {
    throw "WhatsApp bridge directory not found: $bridgeDir"
}

Push-Location $bridgeDir
try {
    if (-not (Test-Path (Join-Path $bridgeDir 'node_modules'))) {
        npm install
        if ($LASTEXITCODE -ne 0) {
            throw "WhatsApp bridge npm install failed."
        }
    }

    node server.mjs
}
finally {
    Pop-Location
}
'@

    $whatsappPackageJson = @'
{
  "name": "blackcat-whatsapp-bridge",
  "version": "0.1.0",
  "type": "module",
  "private": true,
  "scripts": {
    "start": "node server.mjs"
  },
  "dependencies": {
    "qrcode-terminal": "^0.12.0",
    "whatsapp-web.js": "^1.26.0"
  }
}
'@

    $whatsappServer = @'
import http from 'node:http'
import whatsappWeb from 'whatsapp-web.js'
import qrcode from 'qrcode-terminal'

const { Client, LocalAuth } = whatsappWeb

const port = Number(process.env.WHATSAPP_BRIDGE_PORT || 18883)
let status = 'starting'
let statusMessage = 'WhatsApp 读取服务启动中'
let lastQr = ''

const sendJson = (response, statusCode, body) => {
  const payload = Buffer.from(JSON.stringify(body), 'utf8')
  response.writeHead(statusCode, {
    'content-type': 'application/json; charset=utf-8',
    'content-length': payload.length,
    'access-control-allow-origin': 'http://127.0.0.1:18882',
    'access-control-allow-methods': 'GET, POST, OPTIONS',
    'access-control-allow-headers': 'content-type',
  })
  response.end(payload)
}

const readJsonBody = (request) =>
  new Promise((resolve, reject) => {
    const chunks = []
    request.on('data', (chunk) => chunks.push(chunk))
    request.on('end', () => {
      if (chunks.length === 0) {
        resolve({})
        return
      }
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8')))
      } catch (error) {
        reject(error)
      }
    })
    request.on('error', reject)
  })

const toNumber = (value, fallback) => {
  const numeric = Number(value)
  return Number.isFinite(numeric) ? numeric : fallback
}

const normaliseTimestampMs = (value) => {
  const numeric = toNumber(value, 0)
  if (numeric <= 0) return 0
  return numeric < 10_000_000_000 ? numeric * 1000 : numeric
}

const findChatById = async (chatId) => {
  try {
    return await client.getChatById(chatId)
  } catch {
    const chats = await client.getChats()
    return chats.find((chat) => {
      const id = chat.id?._serialized || chat.id?.user || chat.name
      return id === chatId || chat.name === chatId
    })
  }
}

const fetchMessagesForChats = async ({ chatIds, startTime, endTime, limit }) => {
  const startMs = normaliseTimestampMs(startTime)
  const endMs = normaliseTimestampMs(endTime)
  const fetchLimit = Math.max(1, Math.min(toNumber(limit, 300), 1000))
  const messages = []

  for (const chatId of chatIds) {
    const chat = await findChatById(chatId)
    if (!chat) continue

    const chatMessages = await chat.fetchMessages({ limit: fetchLimit })
    for (const message of chatMessages) {
      const timestampMs = normaliseTimestampMs(message.timestamp)
      if (startMs && timestampMs < startMs) continue
      if (endMs && timestampMs > endMs) continue
      const body = (message.body || '').trim()
      if (!body) continue
      messages.push({
        chatId: chat.id?._serialized || chat.id?.user || chatId,
        chatName: chat.name || chat.formattedTitle || chatId,
        messageId: message.id?._serialized || message.id?.id || `${chatId}-${message.timestamp}`,
        timestamp: timestampMs,
        from: message.from || null,
        author: message.author || message.from || null,
        body,
      })
    }
  }

  return messages.sort((a, b) => a.timestamp - b.timestamp)
}

const client = new Client({
  authStrategy: new LocalAuth({ clientId: 'blackcat-bookkeeping' }),
  puppeteer: {
    headless: true,
    timeout: 90_000,
    protocolTimeout: 90_000,
    args: ['--no-sandbox', '--disable-setuid-sandbox'],
  },
})

client.on('qr', (qr) => {
  status = 'qr_required'
  statusMessage = '需要扫码登录 WhatsApp'
  lastQr = qr
  qrcode.generate(qr, { small: true })
})

client.on('authenticated', () => {
  status = 'authenticated'
  statusMessage = 'WhatsApp 已授权，正在加载群聊'
})

client.on('ready', () => {
  status = 'ready'
  statusMessage = 'WhatsApp 已连接'
})

client.on('disconnected', (reason) => {
  status = 'disconnected'
  statusMessage = `WhatsApp 已断开：${reason}`
})

client.initialize().catch((error) => {
  status = 'failed'
  statusMessage = error?.message || 'WhatsApp 读取服务启动失败'
})

const server = http.createServer(async (request, response) => {
  if (request.method === 'OPTIONS') {
    response.writeHead(204, {
      'access-control-allow-origin': 'http://127.0.0.1:18882',
      'access-control-allow-methods': 'GET, POST, OPTIONS',
      'access-control-allow-headers': 'content-type',
    })
    response.end()
    return
  }

  if (request.url === '/status') {
    sendJson(response, 200, {
      connected: status === 'ready',
      status,
      message: statusMessage,
      qr: lastQr,
    })
    return
  }

  if (request.url === '/groups') {
    if (status !== 'ready') {
      sendJson(response, 200, {
        connected: false,
        status,
        message: statusMessage,
        groups: [],
      })
      return
    }

    try {
      const chats = await client.getChats()
      const groups = chats
        .filter((chat) => chat.isGroup)
        .map((chat) => ({
          id: chat.id?._serialized || chat.id?.user || chat.name,
          name: chat.name || chat.formattedTitle || chat.id?._serialized,
        }))
        .filter((chat) => chat.id && chat.name)

      sendJson(response, 200, {
        connected: true,
        status,
        message: `已读取 ${groups.length} 个 WhatsApp 群聊`,
        groups,
      })
    } catch (error) {
      sendJson(response, 500, {
        connected: false,
        status: 'failed',
        message: error?.message || '读取 WhatsApp 群聊失败',
        groups: [],
      })
    }
    return
  }

  if (request.url === '/messages' && request.method === 'POST') {
    if (status !== 'ready') {
      sendJson(response, 200, {
        connected: false,
        status,
        message: statusMessage,
        messages: [],
      })
      return
    }

    try {
      const body = await readJsonBody(request)
      const chatIds = Array.isArray(body.chatIds)
        ? body.chatIds.map((item) => String(item || '').trim()).filter(Boolean)
        : []
      if (chatIds.length === 0) {
        sendJson(response, 400, {
          connected: true,
          status: 'bad_request',
          message: 'chatIds 不能为空',
          messages: [],
        })
        return
      }

      const messages = await fetchMessagesForChats({
        chatIds,
        startTime: body.startTime,
        endTime: body.endTime,
        limit: body.limit,
      })

      sendJson(response, 200, {
        connected: true,
        status,
        message: `已读取 ${messages.length} 条 WhatsApp 消息`,
        messages,
      })
    } catch (error) {
      sendJson(response, 500, {
        connected: false,
        status: 'failed',
        message: error?.message || '读取 WhatsApp 消息失败',
        messages: [],
      })
    }
    return
  }

  sendJson(response, 404, {
    connected: false,
    status: 'not_found',
    message: 'Not Found',
    groups: [],
  })
})

server.listen(port, '127.0.0.1', () => {
  console.log(`WhatsApp bridge listening at http://127.0.0.1:${port}`)
})
'@

    Write-Utf8File -Path $whatsappBridgeScript -Content $whatsappStartScript
    Write-Utf8File -Path (Join-Path $whatsappBridgeDir 'package.json') -Content $whatsappPackageJson
    Write-Utf8File -Path (Join-Path $whatsappBridgeDir 'server.mjs') -Content $whatsappServer
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

function Stop-BlackcatWhatsappBridgeServer {
    $processIds = Get-NetTCPConnection -State Listen -LocalPort $whatsappBridgePort -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique

    foreach ($processId in $processIds) {
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
    }

    if ($processIds) {
        Start-Sleep -Seconds 2
    }
}

function Test-WhatsappBridgeHealthy {
    try {
        $response = Invoke-WebRequest -Uri "http://127.0.0.1:$whatsappBridgePort/status" -UseBasicParsing -TimeoutSec 5
        if ($response.StatusCode -lt 200 -or $response.StatusCode -ge 400) {
            return $false
        }

        $payload = $response.Content | ConvertFrom-Json
        return $payload.status -ne 'failed'
    }
    catch {
        return $false
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
    Ensure-WhatsappBridgeFiles

    if (-not (Test-Path $backendScript)) {
        throw "Backend start script not found: $backendScript"
    }

    if (-not (Test-Path $whatsappBridgeScript)) {
        throw "WhatsApp bridge start script not found: $whatsappBridgeScript"
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

Invoke-LaunchStep 'Checking WhatsApp service' {
    if ((Test-PortListening -Port $whatsappBridgePort) -and -not (Test-WhatsappBridgeHealthy)) {
        Write-Status 'WhatsApp service is unhealthy; restarting WhatsApp bridge'
        Stop-BlackcatWhatsappBridgeServer
        if (-not (Wait-PortFree -Port $whatsappBridgePort -TimeoutSeconds 20)) {
            throw "WhatsApp bridge port $whatsappBridgePort is still occupied."
        }
    }

    if (-not (Test-PortListening -Port $whatsappBridgePort)) {
        Start-Process -FilePath $powershellExe `
            -ArgumentList @('-NoProfile', '-ExecutionPolicy', 'Bypass', '-File', $whatsappBridgeScript) `
            -WorkingDirectory $rootDir `
            -WindowStyle Hidden | Out-Null
    }

    if (-not (Wait-PortListening -Port $whatsappBridgePort -TimeoutSeconds 60)) {
        throw "WhatsApp bridge did not start on port $whatsappBridgePort."
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




