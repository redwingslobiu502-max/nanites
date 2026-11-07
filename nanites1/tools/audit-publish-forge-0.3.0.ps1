param([string]$JavaHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot', [switch]$Publish)
# Forge-only gate: rerun deterministic tests/build/reobf, inspect release bytecode/resources, then publish.
# No Minecraft/AI, no dual audit, no Fabric build, no config/secret/world reads or cache cleaning.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$root = Split-Path $PSScriptRoot -Parent
$started = [DateTime]::UtcNow
$oldEnv = @($env:JAVA_HOME,$env:PATH,$env:TEMP,$env:TMP)
function Hash([string]$path) { (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash }
function Manifest($files) { @($files | Sort-Object FullName | ForEach-Object { $_.FullName.Substring($root.Length+1) + ' SHA256=' + (Hash $_.FullName) }) -join "`n" }
$legacy = @{
    'nanites-forge-1.20.1-0.2.0.jar' = 'E86FE74A2BACEEE435EB7915DC79BDE24A0C82A99492CAA5578D533B8001A1FD'
    'nanites-fabric-1.21.1-0.2.0.jar' = '6A3A34CEF07CF67D082BD6BACAD0FAA82DAD10CDE2B2D0866B1BD47B52381731'
}
foreach($name in $legacy.Keys) { if((Hash (Join-Path $root "dist\$name")) -ne $legacy[$name]) { throw "Legacy artifact differs: $name" } }
$fabricFiles = @(Get-ChildItem (Join-Path $root 'fabric\src') -Recurse -File) + @(Get-Item (Join-Path $root 'fabric\build.gradle'))
$fabricBefore = Manifest $fabricFiles
$distBefore = @{}; Get-ChildItem (Join-Path $root 'dist') -File | Where-Object { $_.Name -ne 'nanites-forge-1.20.1-0.3.0.jar' -and $_.Name -ne 'VALIDATION-FORGE-0.3.0.txt' } | ForEach-Object { $distBefore[$_.FullName] = Hash $_.FullName }
$inputs = @(Get-ChildItem (Join-Path $root 'core\src'),(Join-Path $root 'shared-game\src'),(Join-Path $root 'forge\src'),(Join-Path $root 'game-resources\common') -Recurse -File) + @(Get-Item (Join-Path $root 'build.gradle'),(Join-Path $root 'settings.gradle'),(Join-Path $root 'gradle.properties'),(Join-Path $root 'core\build.gradle'),(Join-Path $root 'shared-game\build.gradle'),(Join-Path $root 'forge\build.gradle'))
$inputBefore = Manifest $inputs
$logDir = Join-Path $root 'validation-logs'
New-Item -ItemType Directory -Force $logDir | Out-Null
$buildLog = Join-Path $logDir 'forge-0.3.0-audit-build.log'
$bytecodeLog = Join-Path $logDir 'forge-0.3.0-audit-srg.log'
$manifestLog = Join-Path $logDir 'forge-0.3.0-audit-inputs.txt'
Push-Location $root
try {
    $env:JAVA_HOME=$JavaHome; $env:PATH="$JavaHome\bin;"+$env:PATH
    $env:TEMP=Join-Path $root 'build\review-temp'; $env:TMP=$env:TEMP
    New-Item -ItemType Directory -Force $env:TEMP | Out-Null
    $java = & cmd /d /c 'java -version 2>&1' | Out-String
    if($LASTEXITCODE -ne 0 -or $java -notmatch 'version "17\.') { throw 'Installed Java 17 required' }
    # Tee stdout only; stderr is redirected to stdout by cmd to avoid PowerShell native-stderr exceptions.
    & cmd /d /c '.\gradlew.bat -Ploader=forge :core:check :shared-game:check :forge:build --rerun-tasks --offline --console=plain 2>&1' | Tee-Object -FilePath $buildLog
    if($LASTEXITCODE -ne 0) { throw 'Forge build/check failed; nothing published' }
    $buildText=Get-Content $buildLog -Raw
    if($buildText -notmatch 'BUILD SUCCESSFUL' -or $buildText -notmatch '> Task :forge:reobfJar\s') { throw 'Missing successful real reobf execution' }
    $counts=@{}; foreach($suite in @('CoreTests','ExpansionTests','GameTests','RuntimeTests','ForgeAttributeTests')) {
        $pattern=switch($suite){'CoreTests' {'RESULT:\s*(\d+) tests passed'} 'ExpansionTests' {'EXPANSION_TESTS=(\d+) PASS'} default {[regex]::Escape($suite)+':\s*(\d+)'}}
        $match=[regex]::Match($buildText,$pattern)
        if(-not $match.Success) { throw "Missing executed suite: $suite" }; $counts[$suite]=[int]$match.Groups[1].Value
    }
    $jar=Join-Path $root 'forge\build\libs\nanites-forge-1.20.1-0.3.0.jar'
    $resources=& (Join-Path $PSScriptRoot 'verify-forge-base.ps1') | Out-String
    $resources | Set-Content (Join-Path $logDir 'forge-0.3.0-audit-resources.log') -Encoding UTF8
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip=[System.IO.Compression.ZipFile]::OpenRead($jar)
    try {
        $classCount=0
        foreach($entry in $zip.Entries) {
            if($entry.FullName -match '(?i)(Tests(?:\$|\.class)|(^|/)test[s]?/|org/junit/|fabric\.mod\.json|^com/google/gson/)') { throw "Release contamination: $($entry.FullName)" }
            if($entry.FullName.EndsWith('.class')) { $classCount++; $stream=$entry.Open();try { $header=New-Object byte[] 8; if($stream.Read($header,0,8) -ne 8 -or $header[0] -ne 202 -or $header[1] -ne 254 -or ($header[6]*256+$header[7]) -ne 61) { throw "Invalid/non-Java17 release class: $($entry.FullName)" } } finally { $stream.Dispose() } }
        }
        foreach($entryName in @('dev/nanites/core/ControlledTools.class','dev/nanites/core/MultiTurnAgent.class','dev/nanites/game/GameService.class','dev/nanites/game/RuntimeInterventions.class','dev/nanites/game/ProtectionBudget.class','dev/nanites/forge/ForgeRuntime.class','dev/nanites/forge/ModularArmor.class','META-INF/mods.toml','pack.mcmeta','data/nanites/recipes/box.json','data/nanites/recipes/reservoir.json')) { if($null -eq $zip.GetEntry($entryName)) { throw "Missing release entry: $entryName" } }
        $stream=New-Object IO.StreamReader($zip.GetEntry('pack.mcmeta').Open());try { $pack=$stream.ReadToEnd() | ConvertFrom-Json; if($pack.pack.pack_format -ne 15){throw 'Wrong Forge resource pack format'} }finally{$stream.Dispose()}
    } finally { $zip.Dispose() }
    $srg=@();foreach($type in @('ForgeRuntime','ModularArmor','NanitesForge','NanitesForge$1','NanitesForge$Box')) {
        $dump=& "$JavaHome\bin\javap.exe" -classpath $jar -verbose -p "dev.nanites.forge.$type" 2>&1 | Out-String
        if($LASTEXITCODE -ne 0){throw "javap failed: $type"}
        if($dump -notmatch 'net/minecraft/[^\r\n]*\.m_\d+_') {throw "Missing real SRG methods: $type"}
        if($dump -match 'net/minecraft/[^\r\n]*\.(getAttribute|removeModifier|addTransientModifier|blockPosition|serverLevel|isAlive|getPlayerList|getTickCount|MOVEMENT_SPEED|ATTACK_DAMAGE|ARMOR|KNOCKBACK_RESISTANCE):') {throw "Named Minecraft release reference: $type"}
        $srg += $dump
    }
    if($srg[0] -notmatch 'net/minecraft/[^\r\n]*\.f_\d+_'){throw 'Missing real SRG vanilla attribute field refs'}
    $srg -join "`n" | Set-Content $bytecodeLog -Encoding UTF8
    if((Manifest $inputs) -ne $inputBefore){throw 'Build inputs changed during audit'}
    if((Manifest $fabricFiles) -ne $fabricBefore){throw 'Fabric sources changed during audit'}
    foreach($path in $distBefore.Keys){if((Hash $path) -ne $distBefore[$path]){throw 'Historical dist changed during audit'}}
    $inputBefore | Set-Content $manifestLog -Encoding UTF8
    $sha=Hash $jar; $item=Get-Item $jar
    $report=@('STATUS=PASS_FORGE_ONLY_STATIC_AND_DETERMINISTIC_NO_LIVE_GAME_OR_AI',"AuditStartedUTC=$($started.ToString('o'))","AuditFinishedUTC=$([DateTime]::UtcNow.ToString('o'))",'Command=gradlew.bat -Ploader=forge :core:check :shared-game:check :forge:build --rerun-tasks --offline --console=plain',($java.Trim()),"BuildJAR=$jar","Bytes=$($item.Length)","SHA256=$sha","BuildJAR_LastWriteUTC=$($item.LastWriteTimeUtc.ToString('o'))","Java17ReleaseClasses=$classCount",'ReobfJar=EXECUTED; actual SRG methods/fields verified with installed javap; named selected Minecraft refs rejected','ReleaseTestsAndBundledGson=ABSENT; Forge metadata=0.3.0; pack_format=15','Resources=30 structural checks (recipes/models/en_us/pt_br/metadata/bytecode)',"InputManifestSHA256=$(Hash $manifestLog)")
    $total=0;foreach($suite in @('CoreTests','ExpansionTests','GameTests','RuntimeTests','ForgeAttributeTests')) { $report+="$suite=$($counts[$suite])";$total+=$counts[$suite] };$report+="TotalJavaChecks=$total"
    $report+=@('ReviewFixes=activation debit compensation; transactional delegated hot-swap and DSL dispatch; queued agent cancellation and main commit linearization; deny unknown tools before envelope executes; shared shield/module/guard cap 8HP/20 server ticks; thermal material gate and impact-fire exclusion; monotonic server timer and armor logout cleanup','GrantFailurePolicy=failed authorized attempts consume grant call/budget but owner debit is compensated; cancel/expiry never refunds committed effects','Scope=PARTIAL other-mod interoperability only: self standard vanilla attributes preserve foreign modifiers; OP loaded radius2 capability presence read-only; no arbitrary external entities/machines/foreign base attribute writes; NOT universal','Limitations=No live Minecraft/LLM/crafting/NBT/network/modpack verification; other LivingHurtEvent listeners may cancel/alter after LOW handler; paid committed damage effects are not a durable atomic transaction with world saves; gameplay QA pending','Fabric=0.2.0 historical only; no new functionality claimed, no Fabric build or source modifications in this audit',"BuildLog=$buildLog","SRGLog=$bytecodeLog","InputManifest=$manifestLog")
    foreach($name in ($legacy.Keys | Sort-Object)){$report+="Preserved=$name SHA256=$($legacy[$name])"}
    if($Publish) {
        $dest=Join-Path $root 'dist\nanites-forge-1.20.1-0.3.0.jar'
        if(Test-Path $dest){if((Hash $dest) -ne $sha){throw 'Refusing to overwrite a different 0.3.0 release'}}else{Copy-Item -LiteralPath $jar -Destination $dest}
        if((Hash $dest) -ne $sha){throw 'Published copy hash mismatch'}
        $published=Get-Item $dest
        $report+=@("PublishedJAR=$dest","PublishedUTC=$([DateTime]::UtcNow.ToString('o'))","PublishedJAR_LastWriteUTC=$($published.LastWriteTimeUtc.ToString('o'))")
        $report | Set-Content (Join-Path $root 'dist\VALIDATION-FORGE-0.3.0.txt') -Encoding UTF8
    }else{$report+='Published=NO (use -Publish only after independent review)'}
    $report | Set-Content (Join-Path $logDir 'forge-0.3.0-audit-result.txt') -Encoding UTF8
    $report
} finally {
    Pop-Location
    $env:JAVA_HOME=$oldEnv[0];$env:PATH=$oldEnv[1];$env:TEMP=$oldEnv[2];$env:TMP=$oldEnv[3]
}
