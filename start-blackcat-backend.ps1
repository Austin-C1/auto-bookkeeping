$ErrorActionPreference = 'Stop'

$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$backendDir = Join-Path $rootDir 'backend'
$javaExe = Join-Path $rootDir '.tools\jdk-17.0.18+8\bin\java.exe'
$outLog = Join-Path $rootDir 'backend-live.out.log'
$errLog = Join-Path $rootDir 'backend-live.err.log'
$localConfig = Join-Path $rootDir 'config\local.env.ps1'
$frontendDistMarker = Join-Path $rootDir 'frontend\dist\.desktop-runtime.json'

function Get-ExpectedBackendVersion {
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

    $packageJsonPath = Join-Path $rootDir 'frontend\package.json'
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

function Get-BackendJarPath {
    param([string]$expectedBackendVersion)

    $libsDir = Join-Path $backendDir 'build\libs'
    if (-not [string]::IsNullOrWhiteSpace($expectedBackendVersion)) {
        $exactJarPath = Join-Path $libsDir "auto-bookkeeping-backend-$expectedBackendVersion.jar"
        if (Test-Path $exactJarPath) {
            return $exactJarPath
        }
    }

    $jarFile = Get-ChildItem -Path $libsDir -Filter 'auto-bookkeeping-backend-*.jar' -File -ErrorAction SilentlyContinue |
        Where-Object { $_.Name -notlike '*-plain.jar' } |
        Sort-Object `
            @{ Expression = {
                $match = [regex]::Match($_.Name, '^auto-bookkeeping-backend-(\d+(?:\.\d+){0,3})\.jar$')
                if ($match.Success) { [version]$match.Groups[1].Value } else { [version]'0.0.0' }
            }; Descending = $true },
            @{ Expression = { $_.LastWriteTime }; Descending = $true } |
        Select-Object -First 1

    if ($jarFile) {
        return $jarFile.FullName
    }

    return Join-Path $libsDir 'auto-bookkeeping-backend.jar'
}

$expectedBackendVersion = Get-ExpectedBackendVersion
$jarPath = Get-BackendJarPath -expectedBackendVersion $expectedBackendVersion

$env:DB_URL = if ($env:DB_URL) { $env:DB_URL } else { 'jdbc:mysql://127.0.0.1:13307/blackcat_v1?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true' }
$env:DB_USERNAME = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' }
$env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { 'change-me' }
$env:ENCRYPTION_KEY = if ($env:ENCRYPTION_KEY) { $env:ENCRYPTION_KEY } else { 'change-me-change-me-change-me-change-me' }
$env:CORS_ALLOWED_ORIGINS = if ($env:CORS_ALLOWED_ORIGINS) { $env:CORS_ALLOWED_ORIGINS } else { 'http://127.0.0.1:18880,http://localhost:18880,http://127.0.0.1:18882,http://localhost:18882' }
$env:SPRING_PROFILES_ACTIVE = 'prod'
$env:SERVER_PORT = '18001'

if (Test-Path $localConfig) {
    . $localConfig
}

function Set-TrimmedEnv {
    param([string]$Name)

    $value = [Environment]::GetEnvironmentVariable($Name, 'Process')
    if ($null -ne $value) {
        [Environment]::SetEnvironmentVariable($Name, $value.Trim(), 'Process')
    }
}

@(
    'DB_URL',
    'DB_USERNAME',
    'DB_PASSWORD',
    'ENCRYPTION_KEY',
    'CORS_ALLOWED_ORIGINS'
) |
    ForEach-Object { Set-TrimmedEnv -Name $_ }

if (-not (Test-Path $javaExe)) {
    throw "Java runtime not found: $javaExe"
}

if (-not (Test-Path $jarPath)) {
    $env:JAVA_HOME = Join-Path $rootDir '.tools\jdk-17.0.18+8'
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
    Push-Location $backendDir
    try {
        & .\gradlew.bat bootJar
        if ($LASTEXITCODE -ne 0) {
            throw "Backend build failed."
        }
    }
    finally {
        Pop-Location
    }

    $jarPath = Get-BackendJarPath -expectedBackendVersion $expectedBackendVersion
}

if (-not (Test-Path $jarPath)) {
    throw "Backend jar not found: $jarPath"
}

Push-Location $backendDir
try {
    $process = Start-Process `
        -FilePath $javaExe `
        -ArgumentList @(
            '-Dfile.encoding=UTF-8',
            '-Dsun.stdout.encoding=UTF-8',
            '-Dsun.stderr.encoding=UTF-8',
            '-jar',
            $jarPath
        ) `
        -RedirectStandardOutput $outLog `
        -RedirectStandardError $errLog `
        -NoNewWindow `
        -Wait `
        -PassThru
    exit $process.ExitCode
}
finally {
    Pop-Location
}
