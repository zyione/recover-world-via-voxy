@echo off
setlocal
cd /d "%~dp0"
if not defined JAVA_HOME (
  for /d %%J in ("%ProgramFiles%\Java\jdk-21*" "%ProgramFiles%\Eclipse Adoptium\jdk-21*") do (
    if exist "%%~J\bin\javac.exe" set "JAVA_HOME=%%~J"
  )
)
if not defined JAVA_HOME (
  where java >nul 2>nul
  if errorlevel 1 (
    echo Install a Java 21 JDK and set JAVA_HOME or add Java to PATH.
    pause
    exit /b 1
  )
)
call gradlew.bat desktop --console=plain
if errorlevel 1 pause
