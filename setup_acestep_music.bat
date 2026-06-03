@echo off
setlocal enabledelayedexpansion
title Install ACE-Step 1.5 Music Generation
chcp 65001 >nul
cd /d "%~dp0"

call "%~dp0configure_acestep_storage.bat"
set "UV_PYTHON=%ACESTEP_BOOTSTRAP_VENV%\Scripts\python.exe"

echo ============================================
echo   Install official ACE-Step 1.5
echo   Music generation and cover generation
echo ============================================
echo.

py -3.12 -c "import sys; print(sys.version)" >nul 2>&1
if errorlevel 1 (
    echo Python 3.12 is missing.
    echo Install Python 3.12 x64 and run this file again.
    exit /b 1
)

git --version >nul 2>&1
if errorlevel 1 (
    echo Git is missing.
    echo Install Git for Windows and run this file again.
    exit /b 1
)

if /i "%~1"=="--check" (
    echo Check passed.
    echo Python 3.12 and Git are available.
    echo Real installation will clone the official ACE-Step repository,
    echo prepare its isolated environment and download models to:
    echo   %ACESTEP_CHECKPOINTS_DIR%
    echo All ACE-Step caches and temporary files will stay on E: under:
    echo   %ACESTEP_LOCAL_STORAGE%
    exit /b 0
)

if not exist "%~dp0server\vendors" mkdir "%~dp0server\vendors"

if not exist "%ACESTEP_ROOT%\.git" (
    echo [1/5] Cloning official ACE-Step 1.5 source code...
    git clone --depth 1 https://github.com/ace-step/ACE-Step-1.5.git "%ACESTEP_ROOT%"
    if errorlevel 1 goto :error
) else (
    echo [1/5] Updating official ACE-Step 1.5 source code...
    git -C "%ACESTEP_ROOT%" pull --ff-only
    if errorlevel 1 goto :error
)

echo Configuring one shared ACE-Step checkpoints directory...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\ensure_acestep_checkpoint_link.ps1"
if errorlevel 1 goto :error

echo [2/5] Preparing uv package manager...
if not exist "%UV_PYTHON%" (
    py -3.12 -m venv "%ACESTEP_BOOTSTRAP_VENV%"
    if errorlevel 1 goto :error
)
"%UV_PYTHON%" -m pip install --upgrade uv
if errorlevel 1 goto :error

echo [3/5] Creating isolated ACE-Step environment...
"%UV_PYTHON%" -m uv sync --project "%ACESTEP_ROOT%"
if errorlevel 1 goto :error

echo [4/5] Checking legacy NVIDIA GPU compatibility...
pushd "%ACESTEP_ROOT%"
".venv\Scripts\python.exe" -c "import os,sys; sys.path.insert(0, os.getcwd()); from acestep.launcher_compat import legacy_torch_fix_probe_exit_code; raise SystemExit(legacy_torch_fix_probe_exit_code())" >nul 2>&1
set "LEGACY_CHECK=!ERRORLEVEL!"
if "!LEGACY_CHECK!"=="42" (
    echo Pascal-compatible CUDA build is required. Installing it...
    "%UV_PYTHON%" -m uv pip install --python ".venv\Scripts\python.exe" --force-reinstall --index-url https://download.pytorch.org/whl/cu121 torch==2.5.1+cu121 torchvision==0.20.1+cu121 torchaudio==2.5.1+cu121
    if errorlevel 1 (
        popd
        goto :error
    )
    "%UV_PYTHON%" -m uv pip install --python ".venv\Scripts\python.exe" --force-reinstall torchao==0.11.0
    if errorlevel 1 (
        popd
        goto :error
    )
) else (
    if not "!LEGACY_CHECK!"=="0" (
        echo GPU compatibility probe failed with code !LEGACY_CHECK!.
        popd
        goto :error
    )
)
popd

echo [5/5] Downloading official ACE-Step 1.5 models...
echo This download is large. It can be resumed by running this file again.
echo Neuro uses ModelScope directly to avoid duplicate temporary downloads after Hugging Face timeouts.
"%UV_PYTHON%" -m uv run --project "%ACESTEP_ROOT%" --no-sync python "%~dp0server\download_acestep_music_models.py" --checkpoints-dir "%ACESTEP_CHECKPOINTS_DIR%" --source modelscope
if errorlevel 1 goto :error

echo.
echo ACE-Step 1.5 is installed.
echo Start the local music worker with run_acestep_music_worker.bat
pause
exit /b 0

:error
echo.
echo ACE-Step installation failed. Check the connection and run this file again.
echo Existing files and partial downloads will be reused.
pause
exit /b 1
