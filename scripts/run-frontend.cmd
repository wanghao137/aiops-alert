@echo off
title AIOps Frontend (5173)

set "ROOT=%~dp0.."

cd /d "%ROOT%\frontend"
echo.
echo ============================================================
echo   AIOps Frontend  ^|  Vite Dev 5173
echo ============================================================
echo.

call npm run dev

echo.
echo [Frontend exited] Press any key to close window
pause >nul