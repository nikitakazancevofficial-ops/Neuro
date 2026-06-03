@echo off
setlocal
title Start Neuro Services
chcp 65001 >nul
cd /d "%~dp0"

echo This legacy launcher no longer bypasses the system-drive safety check.
echo Forwarding to start_neuro_all.bat...
echo.
call "%~dp0start_neuro_all.bat" %*
