$rootDir = (Resolve-Path (Split-Path -Parent $MyInvocation.MyCommand.Path)).Path
$backendDir = Join-Path $rootDir 'backend'
$javaExe = Join-Path $rootDir '.tools\jdk-17.0.18+8\bin\java.exe'
$jarFile = Get-ChildItem -Path (Join-Path $backendDir 'build\libs') -Filter 'auto-bookkeeping-backend-*.jar' -File |
    Where-Object { $_.Name -notlike '*-plain.jar' } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1
$jarPath = if ($jarFile) { $jarFile.FullName } else { Join-Path $backendDir 'build\libs\auto-bookkeeping-backend.jar' }
$outLog = Join-Path $rootDir 'backend-live.out.log'
$errLog = Join-Path $rootDir 'backend-live.err.log'
$localConfig = Join-Path $rootDir 'config\local.env.ps1'

$env:DB_URL = if ($env:DB_URL) { $env:DB_URL } else { 'jdbc:mysql://127.0.0.1:13307/blackcat_v1?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true' }
$env:DB_USERNAME = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { 'root' }
$env:DB_PASSWORD = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { 'change-me' }
$env:JWT_SECRET = if ($env:JWT_SECRET) { $env:JWT_SECRET } else { 'change-me-change-me-change-me-change-me' }
$env:ENCRYPTION_KEY = if ($env:ENCRYPTION_KEY) { $env:ENCRYPTION_KEY } else { 'change-me-change-me-change-me-change-me' }
$env:AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_ENABLED = if ($env:AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_ENABLED) { $env:AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_ENABLED } else { 'true' }
$env:AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_USERNAME = if ($env:AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_USERNAME) { $env:AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_USERNAME } else { '123456' }
$env:AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_PASSWORD = if ($env:AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_PASSWORD) { $env:AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_PASSWORD } else { '123456' }
$env:CORS_ALLOWED_ORIGINS = if ($env:CORS_ALLOWED_ORIGINS) { $env:CORS_ALLOWED_ORIGINS } else { 'http://127.0.0.1:18880,http://localhost:18880,http://127.0.0.1:18882,http://localhost:18882' }
$env:WEBSOCKET_ALLOWED_ORIGINS = if ($env:WEBSOCKET_ALLOWED_ORIGINS) { $env:WEBSOCKET_ALLOWED_ORIGINS } else { 'http://127.0.0.1:18880,http://localhost:18880,http://127.0.0.1:18882,http://localhost:18882' }
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
    'JWT_SECRET',
    'ENCRYPTION_KEY',
    'AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_ENABLED',
    'AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_USERNAME',
    'AUTO_BOOKKEEPING_PACKAGE_DEFAULT_ADMIN_PASSWORD',
    'CORS_ALLOWED_ORIGINS',
    'WEBSOCKET_ALLOWED_ORIGINS'
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

    $jarFile = Get-ChildItem -Path (Join-Path $backendDir 'build\libs') -Filter 'auto-bookkeeping-backend-*.jar' -File |
        Where-Object { $_.Name -notlike '*-plain.jar' } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1
    $jarPath = if ($jarFile) { $jarFile.FullName } else { Join-Path $backendDir 'build\libs\auto-bookkeeping-backend.jar' }
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




