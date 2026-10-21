@echo off
setlocal
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\dev.ps1" -Action Start
set "result=%errorlevel%"
echo.
pause
exit /b %result%
