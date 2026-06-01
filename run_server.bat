@echo off
title Neuro Server
chcp 65001 >nul

cd /d "%~dp0"

if exist "%~dp0run_server.local.bat" call "%~dp0run_server.local.bat"
if not defined LLM_BASE_URL set LLM_BASE_URL=http://127.0.0.1:1234/v1
if not defined LLM_MODEL set LLM_MODEL=nvidia/nemotron-3-nano-omni
if not defined CONTEXT_TOKENS set CONTEXT_TOKENS=262144
if not defined SERVER_HOST set SERVER_HOST=0.0.0.0
if not defined SERVER_PORT set SERVER_PORT=3510
if not defined PUBLIC_SERVER_URL set PUBLIC_SERVER_URL=http://127.0.0.1:%SERVER_PORT%
set HF_HOME=%~dp0server\.cache\huggingface
set HF_HUB_CACHE=%HF_HOME%\hub
set TRANSFORMERS_CACHE=%HF_HOME%\transformers

echo ============================================
echo   Neuro Server
echo ============================================
echo Server: http://localhost:%SERVER_PORT%
echo Phone:  %PUBLIC_SERVER_URL%
echo LLM:    %LLM_MODEL%
echo API:    %LLM_BASE_URL%
echo Ctx:    %CONTEXT_TOKENS% tokens
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

echo [1/2] Installing dependencies...
"%PYTHON_EXE%" -m pip install -q -r server\requirements.txt
if %errorlevel% neq 0 (
    echo Dependency install failed.
    pause
    exit /b 1
)

echo [2/2] Starting server...
echo Press Ctrl+C to stop.
echo.
"%PYTHON_EXE%" server/main.py

echo.
pause
