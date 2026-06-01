@echo off
title Neuro FLUX.2 Klein Worker
chcp 65001 >nul
cd /d "%~dp0"

set PYTHON_EXE=server\.image_venv\Scripts\python.exe
set FLUX_MODEL_PATH=%~dp0server\models\FLUX.2-klein-4B
set FLUX_MIN_FREE_VRAM_MB=4096
set FLUX_AUTO_UNLOAD=1
set HF_HOME=%~dp0server\.cache\huggingface
set HF_HUB_CACHE=%HF_HOME%\hub
set TRANSFORMERS_CACHE=%HF_HOME%\transformers

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
echo.
"%PYTHON_EXE%" server\image_worker.py

echo.
pause
