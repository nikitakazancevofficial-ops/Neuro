@echo off
setlocal
title Start Neuro
chcp 65001 >nul
cd /d "%~dp0"

echo ============================================
echo   Start Neuro
echo ============================================
echo.

if exist "server\.image_venv\Scripts\python.exe" if exist "server\models\FLUX.2-klein-4B\model_index.json" (
    echo FLUX.2 Klein detected. Starting chat and image generation...
    call "%~dp0run_neuro_with_images.bat"
) else (
    echo Starting chat backend...
    echo Optional image generation can be installed later with setup_flux_klein.bat
    call "%~dp0run_server.bat"
)
