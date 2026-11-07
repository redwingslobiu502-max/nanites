# Offline release gate; never starts Minecraft/model and never overwrites dist.
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
Add-Type -AssemblyName System.IO.Compression.FileSystem
function Assert($ok, $message) { if (-not $ok) { throw $message } }
$src = 'forge/build/libs/nanites-forge-1.20.1-0.3.1.jar'
$reobf = 'forge/build/reobfJar/output.jar'
$dst = 'dist/stage4-armor-20261107/nanites-forge-1.20.1-0.3.1-stage4.jar'
$before = @{}
Get-ChildItem dist -Recurse -File | ForEach-Object { $before[$_.FullName] = @($_.Length, (Get-FileHash $_.FullName -Algorithm SHA256).Hash) }
$hash = (Get-FileHash $src -Algorithm SHA256).Hash
Assert ((Get-Item $src).Length -eq 395018) 'Unexpected build size'
Assert ($hash -eq (Get-FileHash $reobf -Algorithm SHA256).Hash) 'Build differs from reobfJar/output.jar'
Assert ($hash -eq '717388E83D3C735D0D4B94B8658CD40C9C5B10D988FBDDF80DCDAF0E19900B94') 'Build changed since inspection'
$stale = @(Get-ChildItem core/src/main,shared-game/src/main,forge/src/main,game-resources -Recurse -File | Where-Object { $_.LastWriteTimeUtc -gt (Get-Item $src).LastWriteTimeUtc })
Assert ($stale.Count -eq 0) 'Production input newer than jar; rebuild required'
foreach ($log in @('stage1-machines-final','stage2-assimilation-final','stage3-autonomy-final','stage4-armor-final')) {
    $text = Get-Content "validation-logs/$log.log" -Raw
    Assert ($text.Contains('BUILD SUCCESSFUL')) "Build not successful: $log"
    Write-Output "$log : BUILD SUCCESSFUL"
}
$text = Get-Content validation-logs/stage4-armor-final.log -Raw
Assert ($text.Contains('> Task :forge:reobfJar') -and $text.Contains('29 actionable tasks: 17 executed, 12 up-to-date')) 'Missing final reobf/build evidence'
Write-Output 'Wrapper exit code 0 is reported in ETAPA4_ARMADURA_VIVA.md, not inside redirected Gradle log'
$zip = [IO.Compression.ZipFile]::OpenRead((Resolve-Path $src))
function Bytes($name) {
    $e = $zip.GetEntry($name); Assert ($null -ne $e) "Missing entry: $name"
    $s = $e.Open(); $m = New-Object IO.MemoryStream
    try { $s.CopyTo($m); return ,$m.ToArray() } finally { $s.Dispose(); $m.Dispose() }
}
function Text($name) { return [Text.Encoding]::UTF8.GetString((Bytes $name)) }
try {
    $names = @($zip.Entries | ForEach-Object {$_.FullName})
    Assert (($names | Select-Object -Unique).Count -eq $names.Count) 'Duplicate archive entries'
    Assert (@($names | Where-Object {$_ -match '^(com/google/gson/|net/minecraft/|net/minecraftforge/|net/fabricmc/)|Tests?\.class$|fabric.mod.json'}).Count -eq 0) 'Bundled dependencies/test/loader contamination'
    $classes = @($names | Where-Object {$_ -like '*.class'})
    foreach ($n in $classes) {
        $b = Bytes $n
        Assert ($b.Length -ge 8 -and $b[0] -eq 202 -and $b[1] -eq 254 -and $b[2] -eq 186 -and $b[3] -eq 190) "Invalid class $n"
        Assert (($b[6]*256+$b[7]) -eq 61) "Not Java17 bytecode: $n"
    }
    Write-Output "Java17 classfiles: $($classes.Count); no tests/Gson/Minecraft/Forge/Fabric bundled"
    foreach ($n in @('dev/nanites/forge/NanitesForge.class','dev/nanites/forge/ForgeMachines.class','dev/nanites/forge/ForgeAssimilation.class','dev/nanites/forge/ModularArmor.class','dev/nanites/game/GameService.class','dev/nanites/game/CapabilityTransfer.class','dev/nanites/game/MachineAdapters.class','dev/nanites/game/AssimilationLedger.class','dev/nanites/game/AutonomousGoals.class','dev/nanites/game/LivingArmor.class','dev/nanites/game/ProtectionBudget.class','dev/nanites/core/MachineTools.class','dev/nanites/core/AssimilationTools.class','dev/nanites/core/ObjectiveTools.class','dev/nanites/core/LivingArmorTools.class')) {
        [void](Bytes $n); Write-Output "Production: $n"
    }
    $mods = Text 'META-INF/mods.toml'; $manifest = Text 'META-INF/MANIFEST.MF'
    Write-Output "mods.toml:`n$mods"; Write-Output "MANIFEST:`n$manifest"
    Assert ($mods -match 'modId\s*=\s*"nanites"' -and $mods -match 'version\s*=\s*"0.3.1"' -and $mods -match 'javafml' -and $mods -match '1.20.1') 'Incorrect release metadata'
    foreach ($n in @('dev/nanites/forge/ForgeMachines.class','dev/nanites/forge/ModularArmor.class','dev/nanites/forge/NanitesForge.class')) {
        $ct = Text $n; $matches = [regex]::Matches($ct,'m_[0-9]+_')
        Assert ($matches.Count -gt 0) "Missing reobfuscated Minecraft member references: $n"
        Write-Output "Reobfuscated members in $n : $($matches.Count)"
    }
    $fm = (@($names | Where-Object { $_ -like 'dev/nanites/forge/ForgeMachines*.class' } | ForEach-Object { Text $_ }) -join "`n")
    foreach ($token in @('ForgeCapabilities','ITEM_HANDLER','ENERGY','FLUID_HANDLER','extractItem','insertItem','extractEnergy','receiveEnergy','drain','fill')) { Assert ($fm.Contains($token)) "Missing machine production linkage: $token" }
    $gs = Text 'dev/nanites/game/GameService.class'
    foreach ($token in @('LivingArmor','MachineAdapters','AutonomousGoals','armor_repair')) { Assert ($gs.Contains($token)) "Missing GameService linkage: $token" }
    $lt = Text 'dev/nanites/core/LivingArmorTools.class'
    foreach ($token in @('armor_status','armor_configure','armor_reload','armor_repair')) { Assert ($lt.Contains($token)) "Missing tool: $token" }
    $jsons = @($names | Where-Object {$_ -like '*.json' -or $_ -eq 'pack.mcmeta'})
    foreach ($n in $jsons) { [void]((Text $n) | ConvertFrom-Json) }
    foreach ($stem in @('box','reservoir','modular_helmet','modular_chestplate','modular_leggings','modular_boots','module_impact','module_thermal','module_cushioning')) { [void](Bytes "data/nanites/recipes/$stem.json"); [void](Bytes "assets/nanites/models/item/$stem.json") }
    Write-Output "Parsed JSON/resources: $($jsons.Count); nine recipes and item models present"
} finally { $zip.Dispose() }
Assert (-not (Test-Path $dst)) 'Destination already exists; choose a new release subdirectory'
[void][IO.Directory]::CreateDirectory((Join-Path (Get-Location) (Split-Path $dst -Parent)))
[IO.File]::Copy((Join-Path (Get-Location) $src), (Join-Path (Get-Location) $dst), $false)
Assert ((Get-FileHash $dst -Algorithm SHA256).Hash -eq $hash -and (Get-Item $dst).Length -eq (Get-Item $src).Length) 'Copied artifact mismatch'
foreach ($p in $before.Keys) { Assert ((Test-Path $p) -and (Get-Item $p).Length -eq $before[$p][0] -and (Get-FileHash $p -Algorithm SHA256).Hash -eq $before[$p][1]) "Prior dist artifact changed: $p" }
Write-Output "Prior dist files preserved: $($before.Count)"
Write-Output "ARTIFACT: $((Get-Item $dst).FullName)"
Write-Output "SIZE: $((Get-Item $dst).Length) bytes"
Write-Output "SHA256: $hash"
Write-Output 'AUDIT PASSED; no rebuild/version bump/game/model launch'
