@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
"%JAVA_HOME%\bin\java.exe" -jar "%~dp0target\aiops-alert-backend.jar"
