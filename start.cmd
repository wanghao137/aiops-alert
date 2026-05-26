@echo off
setlocal EnableExtensions EnableDelayedExpansion
chcp 65001 >nul
title AIOps Alert - Launcher

REM ============================================================
REM AIOps Alert 一键启动脚本
REM   1) 启动后端 Spring Boot (8090)
REM   2) 启动前端 Vite Dev (5173)
REM   3) 自动打开浏览器
REM 关闭随便一个窗口都不会停其他服务，要全停就关掉所有黑窗口
REM ============================================================

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "FRONTEND=%ROOT%frontend"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"

echo.
echo ============================================================
echo   AIOps Alert  ^|  Local Launcher
echo ============================================================
echo   Backend  : http://localhost:8090/api
echo   Frontend : http://localhost:5173
echo   H2 Console: http://localhost:8090/api/h2  (sa / 空密码)
echo ============================================================
echo.

REM ---------- 1. 检查 JDK17 ----------
if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] JDK 17 未找到: %JAVA_HOME%
  echo         请安装 Eclipse Adoptium JDK 17，或修改本脚本里的 JAVA_HOME
  pause
  exit /b 1
)

REM ---------- 2. 检查 npm ----------
where npm >nul 2>nul
if errorlevel 1 (
  echo [ERROR] 未找到 npm，先安装 Node.js: https://nodejs.org
  pause
  exit /b 1
)

REM ---------- 3. 检查端口占用 ----------
call :CheckPort 8090 "后端 8090"
call :CheckPort 5173 "前端 5173"

REM ---------- 4. 启动后端（独立窗口） ----------
echo [1/3] 启动后端 Spring Boot ...
start "AIOps Backend (8090)" cmd /k ^
  "title AIOps Backend (8090) ^&^& cd /d "%BACKEND%" ^&^& set JAVA_HOME=%JAVA_HOME% ^&^& set PATH=%JAVA_HOME%\bin;%%PATH%% ^&^& mvnw.cmd spring-boot:run"

REM ---------- 5. 等后端起来 ----------
echo     等待后端就绪 (最多 90 秒) ...
set /a tries=0
:WaitBackend
set /a tries+=1
if !tries! gtr 90 (
  echo [WARN] 后端 90 秒内没就绪，前端会先起来，后端窗口看日志
  goto StartFrontend
)
powershell -NoProfile -Command "try { (Invoke-WebRequest -Uri 'http://localhost:8090/api/dashboard' -UseBasicParsing -TimeoutSec 2).StatusCode } catch { 0 }" 2>nul | findstr /c:"200" >nul
if errorlevel 1 (
  <nul set /p=.
  timeout /t 1 /nobreak >nul
  goto WaitBackend
)
echo.
echo     后端已就绪 ✓

:StartFrontend
REM ---------- 6. 安装前端依赖（首次） ----------
if not exist "%FRONTEND%\node_modules" (
  echo [2/3] 安装前端依赖 (首次需要几分钟) ...
  pushd "%FRONTEND%"
  call npm install
  popd
) else (
  echo [2/3] 前端依赖已就绪 ✓
)

REM ---------- 7. 启动前端 dev server（独立窗口） ----------
echo [3/3] 启动前端 Vite ...
start "AIOps Frontend (5173)" cmd /k ^
  "title AIOps Frontend (5173) ^&^& cd /d "%FRONTEND%" ^&^& npm run dev"

REM ---------- 8. 等前端就绪并打开浏览器 ----------
echo     等待前端就绪 ...
set /a tries=0
:WaitFrontend
set /a tries+=1
if !tries! gtr 30 goto Done
powershell -NoProfile -Command "try { (Invoke-WebRequest -Uri 'http://localhost:5173' -UseBasicParsing -TimeoutSec 2).StatusCode } catch { 0 }" 2>nul | findstr /c:"200" >nul
if errorlevel 1 (
  <nul set /p=.
  timeout /t 1 /nobreak >nul
  goto WaitFrontend
)
echo.
echo     前端已就绪 ✓
start "" "http://localhost:5173"

:Done
echo.
echo ============================================================
echo   所有服务已启动
echo   - 关闭对应黑窗口即可停止单个服务
echo   - 或直接运行 stop.cmd 一键关闭全部
echo ============================================================
echo.
echo 按任意键关闭本启动器（不影响后端/前端运行）...
pause >nul
exit /b 0


REM ============================================================
REM 子程序：检查端口是否被占用
REM ============================================================
:CheckPort
set "port=%~1"
set "label=%~2"
netstat -ano | findstr ":%port% " | findstr "LISTENING" >nul
if not errorlevel 1 (
  echo [WARN] %label% 端口已被占用，可能要先 stop.cmd
)
exit /b 0
