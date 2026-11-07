param([string]$ForgeLog='build-forge-6.log',[string]$FabricLog='build-fabric-final.log')
$ErrorActionPreference='Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
Add-Type -AssemblyName System.IO.Compression.FileSystem
foreach($log in @($ForgeLog,$FabricLog)){if(!(Test-Path $log) -or !(Select-String -Path $log -SimpleMatch 'BUILD SUCCESSFUL' -Quiet)){throw "Successful build required: $log"}}
$report=New-Object Collections.Generic.List[string]
function EntryBytes($z,$name){$e=$z.GetEntry($name);if($null -eq $e){throw "Missing JAR entry: $name"};$s=$e.Open();$m=New-Object IO.MemoryStream;try{$s.CopyTo($m);return ,$m.ToArray()}finally{$s.Dispose();$m.Dispose()}}
$files=@('forge/build/libs/nanites-forge-1.20.1-0.2.0.jar','fabric/build/libs/nanites-fabric-1.21.1-0.2.0.jar')
foreach($file in $files){
 $z=[IO.Compression.ZipFile]::OpenRead((Resolve-Path $file));try{
  $names=@($z.Entries | ForEach-Object FullName)
  if(($names|Group-Object|Where-Object Count -gt 1)){throw 'Duplicate JAR entries'}
  if($names -match '^com/google/gson/'){throw 'Gson must not be bundled'}
  foreach($entry in @('dev/nanites/core/NaniteSession.class','dev/nanites/core/LocalChatClient.class','dev/nanites/core/SourceCache.class','dev/nanites/game/GameService.class','dev/nanites/game/Materials.class','dev/nanites/game/SourceSupport.class','assets/nanites/textures/item/box.png','assets/nanites/textures/item/reservoir.png','assets/nanites/models/item/box.json','assets/nanites/lang/pt_br.json','pack.mcmeta')){[void](EntryBytes $z $entry)}
  $b=EntryBytes $z 'dev/nanites/core/NaniteSession.class';if($b[6]*256+$b[7] -ne 61){throw 'Core must be Java17 bytecode'}
  if($file -like 'forge/*'){
   [void](EntryBytes $z 'META-INF/mods.toml');[void](EntryBytes $z 'data/nanites/recipes/box.json');
   $b=EntryBytes $z 'dev/nanites/forge/NanitesForge.class';if($b[6]*256+$b[7] -ne 61){throw 'Forge must be Java17'}
   $text=[Text.Encoding]::UTF8.GetString($b);if($text -notmatch 'm_\d+_'){throw 'Forge SRG method mapping not found; inspect reobf'}
   $report.Add('Forge descriptor, recipe, bytecode61 and SRG references verified')
  }else{
   [void](EntryBytes $z 'fabric.mod.json');[void](EntryBytes $z 'nanites.mixins.json');[void](EntryBytes $z 'data/nanites/recipe/box.json');
   $b=EntryBytes $z 'dev/nanites/fabric/NanitesFabric.class';if($b[6]*256+$b[7] -ne 65){throw 'Fabric must be Java21'}
   $text=[Text.Encoding]::UTF8.GetString($b);if($text -notmatch 'net/minecraft/class_'){throw 'Fabric intermediary references not found'}
   $mix=[Text.Encoding]::UTF8.GetString((EntryBytes $z 'dev/nanites/fabric/DamageMixin.class'));if($mix -notmatch 'class_1657'){throw 'Player mixin target missing'}
   $cfg=[Text.Encoding]::UTF8.GetString((EntryBytes $z 'nanites.mixins.json')) | ConvertFrom-Json
   if($cfg.refmap -ne 'fabric-refmap.json'){throw 'Mixin refmap config missing'}
   $refs=[Text.Encoding]::UTF8.GetString((EntryBytes $z $cfg.refmap)) | ConvertFrom-Json
   if($refs.mappings.'dev/nanites/fabric/DamageMixin'.actuallyHurt -ne 'Lnet/minecraft/class_1657;method_6074(Lnet/minecraft/class_1282;F)V'){throw 'Player damage injection production mapping missing'}
   $report.Add('Fabric descriptor, singular recipe path, bytecode65, intermediary Player mixin and actuallyHurt refmap verified')
  }
  $report.Add("$file entries=$($names.Count) bytes=$((Get-Item $file).Length) SHA256=$((Get-FileHash $file -Algorithm SHA256).Hash)")
 }finally{$z.Dispose()}
}
New-Item -ItemType Directory -Force 'dist'|Out-Null
foreach($file in $files){Copy-Item $file (Join-Path 'dist' (Split-Path $file -Leaf))}
$report.Add("Build logs: $ForgeLog ; $FabricLog")
$report.Add('No Minecraft runtime/game launched. No LLM server connected. Source decompiler transport compiled; actual configured Vineflower execution not tested in game.')
$report | Set-Content 'dist/VALIDATION.txt' -Encoding UTF8
$report | Write-Output
