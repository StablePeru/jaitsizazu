@echo off
rem Starts Jaitsizazu from this portable folder. Works from any current
rem directory: %~dp0 is the folder of this file, with a trailing backslash.
rem
rem The JVM output goes to %LOCALAPPDATA%\Jaitsizazu\launch.log, overwritten
rem on each launch, so a crash at startup leaves a trace. start /b keeps the
rem redirected handles for javaw: without /b, start gives the new process its
rem own console handles. javaw opens no console, so this window still closes.
rem If the log cannot be opened, cmd skips that start command, and the second
rem start command, after the "or" operator, starts the app without a log.
setlocal
set "JAITSIZAZU_FFMPEG=%~dp0bin\ffmpeg.exe"
set "JAVAW=%~dp0runtime\bin\javaw.exe"
set "APP_JARS=%~dp0app\*"
set "LOG_DIR=%LOCALAPPDATA%\Jaitsizazu"
if not defined LOCALAPPDATA set "LOG_DIR=%TEMP%\Jaitsizazu"
if not exist "%LOG_DIR%\" mkdir "%LOG_DIR%" >nul 2>&1
start "" /b "%JAVAW%" -cp "%APP_JARS%" jaitsizazu.desktop.MainKt >"%LOG_DIR%\launch.log" 2>&1 || start "" /b "%JAVAW%" -cp "%APP_JARS%" jaitsizazu.desktop.MainKt
endlocal
