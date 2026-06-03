@echo off
setlocal
title Start Neuro
chcp 65001 >nul
cd /d "%~dp0"

echo ============================================
echo   Start Neuro
echo ============================================
echo.

set "NEURO_START_HEAVY=1"
call "%~dp0check_neuro_heavy_start.bat"
if errorlevel 1 set "NEURO_START_HEAVY=0"

if /i "%~1"=="--check" (
    if "%NEURO_START_HEAVY%"=="1" (
        echo Neuro launcher check passed. Heavy workers are allowed.
    ) else (
        echo Neuro launcher check passed. Safe mode will start only the basic chat backend.
    )
    exit /b 0
)

if "%NEURO_START_HEAVY%"=="0" goto :start_basic

if exist "server\vendors\ACE-Step-1.5\.venv\Scripts\acestep-api.exe" if exist "server\models\ACE-Step-1.5\vae" (
    powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\test_local_port.ps1" -Port 3512
    if errorlevel 1 (
        echo ACE-Step 1.5 detected. Starting music generation worker...
        start "Neuro ACE-Step Music Worker" /min "%~dp0run_acestep_music_worker.bat"
        powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\wait_local_port.ps1" -Port 3512 -TimeoutSeconds 75
        if errorlevel 1 (
            echo ACE-Step music worker failed to start.
            pause
            exit /b 1
        )
    ) else (
        echo ACE-Step music worker is already listening on port 3512.
    )
) else (
    echo Optional music generation can be installed with setup_acestep_music.bat
)

if exist "server\.image_venv\Scripts\python.exe" if exist "server\models\FLUX.2-klein-4B\model_index.json" (
    echo FLUX.2 Klein detected. Starting chat and image generation...
    call "%~dp0run_neuro_with_images.bat"
    if errorlevel 1 exit /b %errorlevel%
    goto :ready
)

:start_basic
echo Starting chat backend without heavy FLUX or ACE-Step workers...
echo Optional image generation can be installed later with setup_flux_klein.bat
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\test_local_port.ps1" -Port 3510
if not errorlevel 1 (
    echo Neuro chat backend is already listening on port 3510.
    goto :ready
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
:ready
for /f "usebackq delims=" %%I in (`powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\detect_lan_ip.ps1"`) do set "NEURO_LAN_IP=%%I"
echo.
echo ============================================
echo   NEURO IS READY
echo ============================================
echo Enter this address in the Android app:
echo.
echo   http://%NEURO_LAN_IP%:3510
echo.
echo Settings - PC connection - Server address
echo Logs: server\neuro-server-current.out.log
echo       server\neuro-server-current.err.log
echo.
echo The main Neuro Server console stays open in a separate window.
echo Neuro services keep running if this information window is closed.
if /i "%~1"=="--no-wait" exit /b 0
echo Press any key to close this information window...
pause >nul
exit /b 0
