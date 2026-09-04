@echo off
title Custom Controller Server
cd /d "C:\Projects\custom-controller\windows-server"

echo.
echo ==========================================
echo      CUSTOM CONTROLLER SERVER
echo ==========================================
echo.
echo Starting server...
echo.

dotnet run

echo.
echo Server stopped.
pause
