$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$frontendDir = Join-Path $rootDir 'frontend'
$distDir = Join-Path $frontendDir 'dist'
$markerPath = Join-Path $distDir '.desktop-runtime.json'
$frontendPackageJsonPath = Join-Path $frontendDir 'package.json'
$backendBuildFilePath = Join-Path $rootDir 'backend\build.gradle.kts'
$apiUrl = 'http://127.0.0.1:18001'
$wsUrl = 'ws://127.0.0.1:18001'

function Get-VersionFromBuildFiles {
    $frontendVersion = (Get-Content -Path $frontendPackageJsonPath -Raw | ConvertFrom-Json).version
    $backendBuildFileContent = Get-Content -Path $backendBuildFilePath -Raw
    $backendVersionMatch = [regex]::Match($backendBuildFileContent, '(?m)^version\s*=\s*"([^"]+)"')
    if (-not $backendVersionMatch.Success) {
        throw "Unable to read backend version from $backendBuildFilePath"
    }

    $backendVersion = $backendVersionMatch.Groups[1].Value.Trim()
    if ($frontendVersion.Trim() -ne $backendVersion) {
        throw "Frontend version '$frontendVersion' does not match backend version '$backendVersion'."
    }

    return $backendVersion
}

if (-not (Test-Path $frontendDir)) {
    throw "Frontend directory not found: $frontendDir"
}

$npmCmd = (Get-Command 'npm.cmd' -ErrorAction Stop).Source
$version = Get-VersionFromBuildFiles

Push-Location $frontendDir
try {
    $env:VITE_API_URL = $apiUrl
    $env:VITE_WS_URL = $wsUrl
    $env:VERSION = $version
    $env:GIT_TAG = "v$version"
    $env:GITHUB_REPO_URL = 'https://github.com/Austin-C1/auto-bookkeeping'

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
        version = $version
        gitTag = "v$version"
        githubRepoUrl = 'https://github.com/Austin-C1/auto-bookkeeping'
        builtAt = (Get-Date).ToString('o')
    } | ConvertTo-Json

    Set-Content -Path $markerPath -Value $marker -Encoding UTF8
}
finally {
    Remove-Item Env:VITE_API_URL -ErrorAction SilentlyContinue
    Remove-Item Env:VITE_WS_URL -ErrorAction SilentlyContinue
    Remove-Item Env:VERSION -ErrorAction SilentlyContinue
    Remove-Item Env:GIT_TAG -ErrorAction SilentlyContinue
    Remove-Item Env:GITHUB_REPO_URL -ErrorAction SilentlyContinue
    Pop-Location
}
