@echo off
setlocal
title Start All Neuro Services
chcp 65001 >nul
cd /d "%~dp0"

echo ============================================
echo   Start Neuro services
echo ============================================
echo.
echo Neuro will start the chat backend, FLUX and ACE-Step together when the
echo system-drive preflight passes. If Windows reports storage-related crashes,
echo Neuro will start the lightweight chat backend only.
echo.

call "%~dp0start_neuro.bat" %*
