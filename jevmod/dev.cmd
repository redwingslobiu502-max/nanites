@echo off
rem Dev helper: runs the Gradle wrapper with JDK 21 and keeps every cache/temp file on D:
rem (the C: drive on this machine is full). Usage: dev.cmd build | dev.cmd runClient | dev.cmd test
setlocal
if not defined JEVMOD_JAVA_HOME set "JEVMOD_JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot"
if not defined JEVMOD_CACHE set "JEVMOD_CACHE=D:\jevmod-cache"
set "JAVA_HOME=%JEVMOD_JAVA_HOME%"
set "GRADLE_USER_HOME=%JEVMOD_CACHE%\gradle"
set "TEMP=%JEVMOD_CACHE%\tmp"
set "TMP=%JEVMOD_CACHE%\tmp"
if not exist "%TEMP%" mkdir "%TEMP%"
set "GRADLE_OPTS=-Djava.io.tmpdir=%TEMP%"
cd /d "%~dp0"
call gradlew.bat %* --console=plain
