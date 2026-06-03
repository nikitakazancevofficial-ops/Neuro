@echo off
setlocal

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0server\check_neuro_system_drive.ps1"
if errorlevel 1 (
    echo.
    echo Heavy FLUX and ACE-Step workers were not started.
    echo The basic Neuro chat backend can still run safely.
    echo There is no software override while Windows reports storage-related crashes.
    echo After the system SSD is checked or replaced, start_neuro.bat will start every service together.
    exit /b 1
)

exit /b 0
