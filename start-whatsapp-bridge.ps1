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