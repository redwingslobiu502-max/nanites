# Read-only development artifact checks; does not publish or launch Minecraft.
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$jar = Join-Path $root 'forge\build\libs\nanites-forge-1.20.1-0.3.0.jar'
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($jar)
$checks = 0
function Read-Entry([string]$name) {
    $entry = $zip.GetEntry($name)
    if ($null -eq $entry) { throw "Missing JAR entry: $name" }
    $reader = New-Object System.IO.StreamReader($entry.Open())
    try { return $reader.ReadToEnd() } finally { $reader.Dispose() }
}
try {
    $ids = @('modular_helmet','modular_chestplate','modular_leggings','modular_boots','module_cushioning','module_impact','module_thermal')
    $known = @('nanites:box','nanites:reservoir') + @($ids | ForEach-Object { 'nanites:' + $_ })
    foreach ($id in $ids) {
        $recipe = (Read-Entry "data/nanites/recipes/$id.json") | ConvertFrom-Json
        if ($recipe.type -ne 'minecraft:crafting_shapeless' -or $recipe.result.item -ne "nanites:$id" -or $recipe.ingredients.Count -lt 1 -or $recipe.ingredients.Count -gt 9) { throw "Bad recipe: $id" }
        foreach ($ingredient in $recipe.ingredients) { if (-not ($ingredient.item.StartsWith('minecraft:') -or $known -contains $ingredient.item)) { throw "Unknown ingredient: $id" } }
        $model = (Read-Entry "assets/nanites/models/item/$id.json") | ConvertFrom-Json
        if ($model.parent -ne 'minecraft:item/generated' -or -not $model.textures.layer0.StartsWith('minecraft:item/')) { throw "Bad vanilla model: $id" }
        foreach ($language in @('en_us','pt_br')) {
            $lang = (Read-Entry "assets/nanites/lang/$language.json") | ConvertFrom-Json
            if ($null -eq $lang.PSObject.Properties["item.nanites.$id"]) { throw "Missing translation: $language/$id" }
        }
        $checks += 4
    }
    $metadata = Read-Entry 'META-INF/mods.toml'
    if ($metadata -notmatch 'version="0.3.0"') { throw 'Forge metadata version mismatch' }
    $checks++
    foreach ($entry in $zip.Entries) {
        if ($entry.FullName.EndsWith('.class')) {
            $stream = $entry.Open()
            try { $header = New-Object byte[] 8; if ($stream.Read($header,0,8) -ne 8) { throw 'Truncated class' }; $major = $header[6] * 256 + $header[7]; if ($major -gt 61) { throw "Non-Java17 class: $($entry.FullName)" } } finally { $stream.Dispose() }
        }
    }
    if ($null -ne $zip.GetEntry('fabric.mod.json')) { throw 'Unexpected Fabric metadata in Forge JAR' }
    $checks++
    Write-Output "Forge base resource/bytecode verification: $checks checks; no game or LLM execution."
} finally { $zip.Dispose() }
$item = Get-Item $jar
Write-Output "Development JAR: $($item.FullName) bytes=$($item.Length) SHA256=$((Get-FileHash $jar -Algorithm SHA256).Hash)"
Get-ChildItem (Join-Path $root 'dist') -Filter '*.jar' | ForEach-Object { Write-Output "Preserved dist: $($_.Name) bytes=$($_.Length) SHA256=$((Get-FileHash $_.FullName -Algorithm SHA256).Hash)" }
