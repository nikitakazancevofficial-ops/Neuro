@echo off
setlocal
title Neuro Server with FLUX.2 Klein
chcp 65001 >nul
cd /d "%~dp0"

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
start "Neuro FLUX Worker" /min "%~dp0run_flux_worker.bat"
timeout /t 3 /nobreak >nul

echo Starting Neuro chat server...
call "%~dp0run_server.bat"
