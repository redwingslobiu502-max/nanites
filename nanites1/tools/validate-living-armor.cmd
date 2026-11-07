@echo off
setlocal
for %%I in ("%~dp0..") do set "ROOT=%%~fI"
cd /d "%ROOT%"
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
if not exist "%JAVA_HOME%\bin\java.exe" (echo Java 17 installation missing. & exit /b 2)
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "GRADLE_USER_HOME=%ROOT%\build\review-gradle-home"
set "TEMP=%ROOT%\build\review-temp"
set "TMP=%TEMP%"
if not exist "%GRADLE_USER_HOME%\caches\forge_gradle" (echo Existing offline Forge cache missing. No download attempted. & exit /b 2)
if not exist "%TEMP%" mkdir "%TEMP%"
set "JAVA_TOOL_OPTIONS=-Djava.io.tmpdir=%TEMP:\=/% -XX:ActiveProcessorCount=2"
set "GRADLE_EXE="
for /r "%USERPROFILE%\.gradle\wrapper\dists\gradle-8.8-bin" %%G in (gradle.bat) do if exist "%%G" set "GRADLE_EXE=%%G"
if not defined GRADLE_EXE (echo Existing Gradle 8.8 distribution missing. No download attempted. & exit /b 2)
rem Seed exact existing Maven metadata and artifacts; no network or stand-in dependencies.
set "MERGE_REPO=%ROOT%\build\offline-maven\net\minecraftforge\mergetool\1.1.5"
if not exist "%MERGE_REPO%" mkdir "%MERGE_REPO%"
for /r "%USERPROFILE%\.gradle\caches\modules-2\files-2.1\net.minecraftforge\mergetool\1.1.5" %%G in (*) do copy /Y "%%G" "%MERGE_REPO%\" >nul
if not exist "%MERGE_REPO%\mergetool-1.1.5-api.jar" (echo Existing cached merge API missing. No download attempted. & exit /b 2)
call "%GRADLE_EXE%" -I "%ROOT%\tools\offline-merge-api.init.gradle" -Ploader=forge :core:check :shared-game:check :forge:build --offline --no-daemon --max-workers=2 --console=plain > "%ROOT%\validation-logs\stage4-armor-final.log" 2>&1
set "RESULT=%ERRORLEVEL%"
echo Offline validation exit code: %RESULT%
exit /b %RESULT%
