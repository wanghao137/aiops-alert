@echo off
setlocal EnableExtensions EnableDelayedExpansion
title AIOps Alert - Stop

echo.
echo ============================================================
echo   AIOps Alert  ^|  停止所有服�?
echo ============================================================
echo.

call :KillByPort 8090 "后端"
call :KillByPort 5173 "前端 dev"
call :KillByPort 4173 "前端 preview"

echo.
echo 完成�?
timeout /t 2 >nul
exit /b 0


:KillByPort
set "port=%~1"
set "label=%~2"
set "found=0"
for /f "tokens=5" %%P in ('netstat -ano ^| findstr ":%port% " ^| findstr "LISTENING"') do (
  if not "%%P"=="0" (
    echo [STOP] %label% 端口 %port% 进程 PID=%%P
    taskkill /F /PID %%P >nul 2>nul
    set "found=1"
  )
)
if "!found!"=="0" (
  echo [SKIP] %label% 端口 %port% 没有运行
)
exit /b 0