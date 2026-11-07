$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$dir=Join-Path (Split-Path $PSScriptRoot -Parent) 'game-resources/common/assets/nanites/textures/item'
New-Item -ItemType Directory -Force $dir | Out-Null
foreach($name in @('box','reservoir')){
 $path=Join-Path $dir "$name.png"; if(Test-Path $path){throw 'Texture exists; refusing overwrite'}
 $b=New-Object Drawing.Bitmap 16,16
 for($y=2;$y -le 13;$y++){for($x=2;$x -le 13;$x++){
  $c=if($x -eq 2 -or $x -eq 13 -or $y -eq 2 -or $y -eq 13){[Drawing.Color]::FromArgb(255,30,55,65)}elseif(($x+$y)%3 -eq 0){[Drawing.Color]::FromArgb(255,70,230,220)}else{[Drawing.Color]::FromArgb(255,95,125,140)}
  if($name -eq 'reservoir' -and $x -gt 5 -and $x -lt 10){$c=[Drawing.Color]::FromArgb(255,170,110,240)}
  $b.SetPixel($x,$y,$c)
 }}
 $b.Save($path,[Drawing.Imaging.ImageFormat]::Png);$b.Dispose();Write-Output $path
}
