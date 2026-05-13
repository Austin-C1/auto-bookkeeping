@echo off
setlocal

for %%I in ("%~dp0.") do set "ROOT_DIR=%%~fI"
set "BACKEND_DIR=%ROOT_DIR%\backend"
set "JAVA_EXE=%ROOT_DIR%\.tools\jdk-17.0.18+8\bin\java.exe"
set "OUT_LOG=%ROOT_DIR%\backend-live.out.log"
set "ERR_LOG=%ROOT_DIR%\backend-live.err.log"

set "DB_URL=jdbc:mysql://127.0.0.1:13307/blackcat_v1?useSSL=false&serverTimezone=UTC&characterEncoding=utf8&allowPublicKeyRetrieval=true"
set "DB_USERNAME=root"
set "DB_PASSWORD=change-me"
set "ENCRYPTION_KEY=change-me-change-me-change-me-change-me"
set "SPRING_PROFILES_ACTIVE=prod"
set "SERVER_PORT=8000"

if not exist "%JAVA_EXE%" (
  echo Java runtime not found: "%JAVA_EXE%"
  exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%ROOT_DIR%\start-blackcat-backend.ps1"
exit /b %ERRORLEVEL%
