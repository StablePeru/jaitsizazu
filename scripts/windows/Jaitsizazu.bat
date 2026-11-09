@echo off
rem Starts Jaitsizazu from this portable folder. Works from any current
rem directory: %~dp0 is the folder of this file, with a trailing backslash.
setlocal
set "JAITSIZAZU_FFMPEG=%~dp0bin\ffmpeg.exe"
start "" "%~dp0runtime\bin\javaw.exe" -cp "%~dp0app\*" jaitsizazu.desktop.MainKt
endlocal
