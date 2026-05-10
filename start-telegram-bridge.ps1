$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$bridgeDir = Join-Path $rootDir 'telegram-bridge'
$localConfig = Join-Path $rootDir 'config\local.env.ps1'

if (Test-Path $localConfig) {
    . $localConfig
}

$env:TELEGRAM_BRIDGE_PORT = if ($env:TELEGRAM_BRIDGE_PORT) { $env:TELEGRAM_BRIDGE_PORT } else { '18884' }
$env:TELEGRAM_SESSION_FILE = if ($env:TELEGRAM_SESSION_FILE) {
    $env:TELEGRAM_SESSION_FILE
} else {
    Join-Path $rootDir 'data\telegram-session.txt'
}

if (-not (Get-Command node -ErrorAction SilentlyContinue)) {
    throw "Node.js runtime not found. Please install Node.js or run this from the development environment."
}

if (-not (Test-Path $bridgeDir)) {
    throw "Telegram bridge directory not found: $bridgeDir"
}

Push-Location $bridgeDir
try {
    if (-not (Test-Path (Join-Path $bridgeDir 'node_modules'))) {
        npm install
        if ($LASTEXITCODE -ne 0) {
            throw "Telegram bridge npm install failed."
        }
    }

    node server.mjs
}
finally {
    Pop-Location
}
