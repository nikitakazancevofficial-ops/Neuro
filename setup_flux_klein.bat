@echo off
setlocal
title Install FLUX.2 Klein 4B
chcp 65001 >nul
cd /d "%~dp0"

set IMAGE_VENV=server\.image_venv
set PYTHON_EXE=%IMAGE_VENV%\Scripts\python.exe
set HF_HOME=%~dp0server\.cache\huggingface
set HF_HUB_CACHE=%HF_HOME%\hub
set TRANSFORMERS_CACHE=%HF_HOME%\transformers

echo ============================================
echo   Install official FLUX.2 Klein 4B
echo   Model: black-forest-labs/FLUX.2-klein-4B
echo ============================================
echo.

if not exist "%PYTHON_EXE%" (
    echo [1/4] Creating Python 3.10 image environment...
    py -3.10 -m venv "%IMAGE_VENV%"
    if errorlevel 1 goto :error
) else (
    echo [1/4] Image environment already exists.
)

echo [2/4] Installing CUDA PyTorch...
"%PYTHON_EXE%" -m pip install --upgrade pip
"%PYTHON_EXE%" -m pip install torch==2.8.0 torchvision==0.23.0 --index-url https://download.pytorch.org/whl/cu126
if errorlevel 1 goto :error

echo [3/4] Installing FLUX worker dependencies...
"%PYTHON_EXE%" -m pip install -r server\image_requirements.txt
if errorlevel 1 goto :error

echo [4/4] Downloading FLUX.2 Klein 4B weights...
"%PYTHON_EXE%" server\download_flux_klein.py
if errorlevel 1 goto :error

echo.
echo FLUX.2 Klein is installed.
echo Start the worker with run_flux_worker.bat
pause
exit /b 0

:error
echo.
echo FLUX installation failed. Check the connection and run this file again.
echo Existing downloads will be reused.
pause
exit /b 1
