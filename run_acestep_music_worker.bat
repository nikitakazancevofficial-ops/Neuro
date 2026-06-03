@echo off
setlocal
title Neuro ACE-Step 1.5 Music Worker
chcp 65001 >nul
cd /d "%~dp0"

call "%~dp0configure_acestep_storage.bat"
set "ACESTEP_NO_INIT=true"
set "ACESTEP_INIT_LLM=false"
set "ACESTEP_CONFIG_PATH=acestep-v15-turbo"
set "ACESTEP_LM_MODEL_PATH=acestep-5Hz-lm-0.6B"
set "ACESTEP_LM_BACKEND=pt"
if not defined ACESTEP_DTYPE set "ACESTEP_DTYPE=float32"
set "ACESTEP_OFFLOAD_DIT_TO_CPU=true"

if /i "%~1"=="--check" (
    if not exist "%ACESTEP_ROOT%\.venv\Scripts\acestep-api.exe" (
        echo ACE-Step environment is missing. Run setup_acestep_music.bat first.
        exit /b 1
    )
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\ensure_acestep_checkpoint_link.ps1" -CheckOnly
    if errorlevel 1 (
        echo ACE-Step checkpoints link is missing. Run setup_acestep_music.bat once.
        exit /b 1
    )
    echo ACE-Step worker launcher check passed.
    echo API: http://127.0.0.1:3512
    exit /b 0
)

call "%~dp0check_neuro_heavy_start.bat"
if errorlevel 1 exit /b 1

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\test_local_port.ps1" -Port 3512
if not errorlevel 1 (
    echo ACE-Step music worker is already listening on port 3512.
    exit /b 0
)

if not exist "%ACESTEP_ROOT%\.venv\Scripts\acestep-api.exe" (
    echo ACE-Step environment is missing.
    echo Run setup_acestep_music.bat first.
    pause
    exit /b 1
)

"%ACESTEP_ROOT%\.venv\Scripts\python.exe" "%~dp0server\patch_acestep_legacy_cuda.py"
if errorlevel 1 (
    echo ACE-Step CUDA compatibility patch failed.
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\ensure_acestep_checkpoint_link.ps1" -CheckOnly
if errorlevel 1 (
    echo ACE-Step checkpoints link is missing.
    echo Run setup_acestep_music.bat once to configure the shared model directory.
    pause
    exit /b 1
)

if not exist "%ACESTEP_CHECKPOINTS_DIR%\vae" (
    echo ACE-Step models are missing.
    echo Run setup_acestep_music.bat first.
    pause
    exit /b 1
)

echo ============================================
echo   Neuro ACE-Step 1.5 music worker
echo ============================================
echo API:    http://127.0.0.1:3512
echo Models: %ACESTEP_CHECKPOINTS_DIR%
echo DiT:    %ACESTEP_CONFIG_PATH%
echo LM:     %ACESTEP_LM_MODEL_PATH% ^(%ACESTEP_LM_BACKEND%^)
echo.
echo Models load lazily on the first music request.
echo Close other GPU-heavy models if generation runs out of VRAM.
echo.
pushd "%ACESTEP_ROOT%"
".venv\Scripts\acestep-api.exe" --host 127.0.0.1 --port 3512 --no-init
popd
