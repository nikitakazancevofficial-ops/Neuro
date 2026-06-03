@echo off
title Neuro FLUX.2 Klein Worker
chcp 65001 >nul
cd /d "%~dp0"

set PYTHON_EXE=server\.image_venv\Scripts\python.exe
set FLUX_MODEL_PATH=%~dp0server\models\FLUX.2-klein-4B
set FLUX_MIN_FREE_VRAM_MB=4096
set FLUX_AUTO_UNLOAD=1
set FLUX_LOCAL_STORAGE=%~dp0server\.local_storage\flux
set TEMP=%FLUX_LOCAL_STORAGE%\tmp
set TMP=%TEMP%
set PIP_CACHE_DIR=%FLUX_LOCAL_STORAGE%\pip-cache
set HF_HOME=%~dp0server\.cache\huggingface
set HF_HUB_CACHE=%HF_HOME%\hub
set TRANSFORMERS_CACHE=%HF_HOME%\transformers
if not exist "%TEMP%" mkdir "%TEMP%"
if not exist "%PIP_CACHE_DIR%" mkdir "%PIP_CACHE_DIR%"

call "%~dp0check_neuro_heavy_start.bat"
if errorlevel 1 exit /b 1

if /i "%~1"=="--check" (
    if not exist "%PYTHON_EXE%" (
        echo FLUX environment is missing.
        echo Run setup_flux_klein.bat first.
        exit /b 1
    )
    if not exist "%FLUX_MODEL_PATH%\model_index.json" (
        echo FLUX.2 Klein model is missing.
        echo Run setup_flux_klein.bat first.
        exit /b 1
    )
    echo FLUX worker launcher check passed.
    echo API: http://127.0.0.1:3511
    echo Model: %FLUX_MODEL_PATH%
    exit /b 0
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\test_local_port.ps1" -Port 3511
if not errorlevel 1 (
    echo FLUX image worker is already listening on port 3511.
    exit /b 0
)

if not exist "%PYTHON_EXE%" (
    echo FLUX environment is missing.
    echo Run setup_flux_klein.bat first.
    pause
    exit /b 1
)

echo ============================================
echo   Neuro FLUX.2 Klein image worker
echo ============================================
echo API:   http://127.0.0.1:3511
echo Model: %FLUX_MODEL_PATH%
echo.
echo Before generation unload the LLM model from LM Studio
echo so FLUX can use the GTX 1080 Ti memory.
echo Logs:  server\flux-worker-current.out.log
echo        server\flux-worker-current.err.log
echo.
"%PYTHON_EXE%" server\image_worker.py 1>>server\flux-worker-current.out.log 2>>server\flux-worker-current.err.log
set "FLUX_EXIT_CODE=%errorlevel%"

echo.
echo FLUX worker stopped with exit code %FLUX_EXIT_CODE%.
echo Read server\flux-worker-current.err.log for details.
pause
exit /b %FLUX_EXIT_CODE%
