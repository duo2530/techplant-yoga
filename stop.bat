@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\dev.ps1" -Action Stop
set "result=%errorlevel%"
echo.
pause
exit /b %result%
