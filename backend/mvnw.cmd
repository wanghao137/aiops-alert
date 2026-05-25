@REM ----------------------------------------------------------------------------
@REM Maven Start Up Batch script (lite, for AIOps Alert)
@REM ----------------------------------------------------------------------------
@echo off
setlocal enabledelayedexpansion

set "BASE_DIR=%~dp0"
if "%BASE_DIR:~-1%"=="\" set "BASE_DIR=%BASE_DIR:~0,-1%"

set "WRAPPER_JAR=%BASE_DIR%\.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_LAUNCHER=org.apache.maven.wrapper.MavenWrapperMain"

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo Error: JAVA_HOME is not set or invalid: %JAVA_HOME%
  exit /b 1
)

"%JAVA_HOME%\bin\java.exe" ^
  -classpath "%WRAPPER_JAR%" ^
  "-Dmaven.multiModuleProjectDirectory=%BASE_DIR%" ^
  %WRAPPER_LAUNCHER% %*
