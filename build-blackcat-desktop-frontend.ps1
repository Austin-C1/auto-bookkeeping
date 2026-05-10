$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$frontendDir = Join-Path $rootDir 'frontend'
$distDir = Join-Path $frontendDir 'dist'
$markerPath = Join-Path $distDir '.desktop-runtime.json'
$apiUrl = 'http://127.0.0.1:18001'
$wsUrl = 'ws://127.0.0.1:18001'

if (-not (Test-Path $frontendDir)) {
    throw "Frontend directory not found: $frontendDir"
}

$npmCmd = (Get-Command 'npm.cmd' -ErrorAction Stop).Source

Push-Location $frontendDir
try {
    $env:VITE_API_URL = $apiUrl
    $env:VITE_WS_URL = $wsUrl

    & $npmCmd run build
    if ($LASTEXITCODE -ne 0) {
        throw "Frontend build failed with exit code $LASTEXITCODE."
    }

    if (-not (Test-Path $distDir)) {
        throw "Frontend dist directory not found after build: $distDir"
    }

    $marker = [ordered]@{
        mode = 'desktop-static'
        apiUrl = $apiUrl
        wsUrl = $wsUrl
        builtAt = (Get-Date).ToString('o')
    } | ConvertTo-Json

    Set-Content -Path $markerPath -Value $marker -Encoding UTF8
}
finally {
    Remove-Item Env:VITE_API_URL -ErrorAction SilentlyContinue
    Remove-Item Env:VITE_WS_URL -ErrorAction SilentlyContinue
    Pop-Location
}
