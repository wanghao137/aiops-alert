@echo off
title AIOps Backend (8090)

set "ROOT=%~dp0.."
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"

cd /d "%ROOT%\backend"
echo.
echo ============================================================
echo   AIOps Backend  ^|  Spring Boot 8090
echo   JAVA_HOME = %JAVA_HOME%
echo ============================================================
echo.

call mvnw.cmd spring-boot:run

echo.
echo [Backend exited] Press any key to close window
pause >nul