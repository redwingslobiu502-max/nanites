# Scoped disk-space workaround: copy existing Gradle cache to D, never delete global files.
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot -Parent
Set-Location $root
$cacheHome=Join-Path $root 'build/review-gradle-home'
$temp=Join-Path $root 'build/review-temp'
New-Item -ItemType Directory -Force $cacheHome,$temp | Out-Null
$copyLog=Join-Path $root 'validation-logs/review-cache-copy.log'
& robocopy (Join-Path $env:USERPROFILE '.gradle') $cacheHome /E /R:0 /W:0 /XJ /XF '*.lock' /XD 'daemon' '.tmp' 'workers' 'undefined-build' 'kotlin-profile' 'notifications' 'jdks' /NFL /NDL /NP /LOG:$copyLog
if($LASTEXITCODE -ge 8){throw "Existing cache copy failed: $copyLog"}
$env:GRADLE_USER_HOME=$cacheHome
$env:TEMP=$temp
$env:TMP=$temp
$env:JAVA_TOOL_OPTIONS='"-Djava.io.tmpdir='+($temp -replace '\\','/')+'"'
& "$root/test.ps1" -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot' *> "$root/validation-logs/review-core-jdk17.log"
& "$root/test.ps1" -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot' *> "$root/validation-logs/review-core-jdk21.log"
& "$root/build.ps1"
