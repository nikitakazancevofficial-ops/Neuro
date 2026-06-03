@echo off
title Neuro Server
chcp 65001 >nul

cd /d "%~dp0"

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\test_local_port.ps1" -Port 3510
if not errorlevel 1 (
    echo Neuro chat backend is already listening on port 3510.
    exit /b 0
)

if exist "%~dp0run_server.local.bat" call "%~dp0run_server.local.bat"
if not defined LLM_BASE_URL set LLM_BASE_URL=http://127.0.0.1:1234/v1
if not defined LLM_MODEL set LLM_MODEL=nvidia/nemotron-3-nano-omni
if not defined CONTEXT_TOKENS set CONTEXT_TOKENS=262144
if not defined SERVER_HOST set SERVER_HOST=0.0.0.0
if not defined SERVER_PORT set SERVER_PORT=3510
if not defined MUSIC_WORKER_URL set MUSIC_WORKER_URL=http://127.0.0.1:3512
for /f "usebackq delims=" %%I in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\detect_lan_ip.ps1"`) do set "NEURO_LAN_IP=%%I"
set PUBLIC_SERVER_URL=http://%NEURO_LAN_IP%:%SERVER_PORT%
set RUNTIME_STORAGE=%~dp0server\.local_storage\runtime
set TEMP=%RUNTIME_STORAGE%\tmp
set TMP=%TEMP%
set PIP_CACHE_DIR=%RUNTIME_STORAGE%\pip-cache
set HF_HOME=%~dp0server\.cache\huggingface
set HF_HUB_CACHE=%HF_HOME%\hub
set TRANSFORMERS_CACHE=%HF_HOME%\transformers
if not exist "%TEMP%" mkdir "%TEMP%"
if not exist "%PIP_CACHE_DIR%" mkdir "%PIP_CACHE_DIR%"

echo ============================================
echo   Neuro Server
echo ============================================
echo Server: http://localhost:%SERVER_PORT%
echo Phone:  %PUBLIC_SERVER_URL%
echo Enter in the app: http://%NEURO_LAN_IP%:%SERVER_PORT%
echo LLM:    %LLM_MODEL%
echo API:    %LLM_BASE_URL%
echo Ctx:    %CONTEXT_TOKENS% tokens
echo Music:  %MUSIC_WORKER_URL%
echo.

if not exist "server\.venv\Scripts\python.exe" (
    echo Creating Python 3.10 virtual environment...
    py -3.10 -m venv server\.venv
    if %errorlevel% neq 0 (
        echo Python 3.10 venv creation failed. Install Python 3.10 or check py launcher.
        pause
        exit /b 1
    )
)

set PYTHON_EXE=server\.venv\Scripts\python.exe
set WHISPER_MODEL=%~dp0server\models\faster-whisper-large-v3-turbo

echo [1/2] Checking dependencies...
"%PYTHON_EXE%" -c "import fastapi, uvicorn, pydantic, httpx, multipart, faster_whisper" >nul 2>&1
if errorlevel 1 (
    echo Installing missing dependencies...
    "%PYTHON_EXE%" -m pip install -q -r server\requirements.txt
    if errorlevel 1 (
        echo Dependency install failed.
        pause
        exit /b 1
    )
) else (
    echo Dependencies are ready.
)

echo [2/2] Starting server...
echo Press Ctrl+C to stop.
echo Logs: server\neuro-server-current.out.log
echo       server\neuro-server-current.err.log
echo.
"%PYTHON_EXE%" server/main.py 1>>server\neuro-server-current.out.log 2>>server\neuro-server-current.err.log
set "SERVER_EXIT_CODE=%errorlevel%"

echo.
echo Neuro backend stopped with exit code %SERVER_EXIT_CODE%.
echo Read server\neuro-server-current.err.log for details.
pause
exit /b %SERVER_EXIT_CODE%
