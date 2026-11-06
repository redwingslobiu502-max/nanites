param([string]$Java17='C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot',[string]$Java21='C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot')
$ErrorActionPreference='Stop'
Set-Location $PSScriptRoot
$stamp=Get-Date -Format 'yyyyMMdd-HHmmss'
New-Item -ItemType Directory -Force 'validation-logs' | Out-Null
foreach($loader in @('forge','fabric')){
 $env:JAVA_HOME=if($loader -eq 'forge'){$Java17}else{$Java21}
 $log="validation-logs/$stamp-$loader.log"
 & cmd.exe /d /c "gradlew.bat -Ploader=$loader :core:check :shared-game:check :${loader}:build --console=plain > `"$log`" 2>&1"
 if($LASTEXITCODE -ne 0){Get-Content $log -Tail 100;throw "$loader build failed; artifacts NOT published"}
 Write-Output "$loader BUILD PASSED; log=$log"
}
& "$PSScriptRoot/tools/audit.ps1" -ForgeLog "validation-logs/$stamp-forge.log" -FabricLog "validation-logs/$stamp-fabric.log"
