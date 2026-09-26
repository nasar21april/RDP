@echo off
setlocal
if defined JAVA_HOME set PATH=%JAVA_HOME%\bin;%PATH%
cd /d "%~dp0"
call gradlew.bat assembleDebug --no-daemon
echo BUILD EXIT CODE: %ERRORLEVEL%
endlocal
