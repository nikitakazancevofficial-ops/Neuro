@echo off
setlocal
title Neuro Server with FLUX.2 Klein
chcp 65001 >nul
cd /d "%~dp0"

call "%~dp0check_neuro_heavy_start.bat"
if errorlevel 1 (
    if /i "%~1"=="--check" (
        echo Neuro image launcher check passed. Safe mode will use the basic chat backend.
        exit /b 0
    )
    echo Starting Neuro chat server without FLUX...
    call "%~dp0run_server.bat"
    exit /b %errorlevel%
)

if /i "%~1"=="--check" (
    echo Neuro image launcher check passed. FLUX is allowed.
    exit /b 0
)

if not exist "server\.image_venv\Scripts\python.exe" (
    echo FLUX environment is missing.
    echo Run setup_flux_klein.bat first.
    pause
    exit /b 1
)

if not exist "server\models\FLUX.2-klein-4B\model_index.json" (
    echo FLUX.2 Klein model is missing.
    echo Run setup_flux_klein.bat first.
    pause
    exit /b 1
)

echo Starting FLUX image worker in a separate window...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\test_local_port.ps1" -Port 3511
if errorlevel 1 (
    start "Neuro FLUX Worker" /min "%~dp0run_flux_worker.bat"
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\wait_local_port.ps1" -Port 3511 -TimeoutSeconds 75
    if errorlevel 1 (
        echo FLUX image worker failed to start.
        pause
        exit /b 1
    )
) else (
    echo FLUX image worker is already listening on port 3511.
)

echo Starting Neuro chat server...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\test_local_port.ps1" -Port 3510
if not errorlevel 1 (
    echo Neuro chat backend is already listening on port 3510.
    exit /b 0
)
start "Neuro Server" "%~dp0run_server.bat"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\wait_local_port.ps1" -Port 3510 -TimeoutSeconds 75
if errorlevel 1 (
    echo Neuro chat backend failed to start.
    echo Open the Neuro Server window to read the error.
    pause
    exit /b 1
)
echo Neuro chat backend is ready.
exit /b 0
