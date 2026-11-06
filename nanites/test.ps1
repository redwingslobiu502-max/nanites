param(
    [string]$JavaHome = $env:JAVA_HOME,
    [string]$GsonJar = (Join-Path $env:USERPROFILE '.gradle/caches/minecraft/libraries/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar')
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($JavaHome)) { throw 'Informe -JavaHome com um JDK 17 ou superior; Java 8 nao serve.' }
$javac = Join-Path $JavaHome 'bin/javac.exe'
$java = Join-Path $JavaHome 'bin/java.exe'
$jar = Join-Path $JavaHome 'bin/jar.exe'
foreach ($tool in @($javac, $java, $jar)) { if (!(Test-Path $tool)) { throw "Ferramenta ausente: $tool" } }
& $java -version
if ($LASTEXITCODE -ne 0) { throw 'Falha ao identificar JDK' }
$build = Join-Path $PSScriptRoot ('build/run-' + [guid]::NewGuid().ToString('N'))
$mainOut = Join-Path $build 'main'
$testOut = Join-Path $build 'test'
New-Item -ItemType Directory -Path $mainOut,$testOut -Force | Out-Null
$main = @(Get-ChildItem (Join-Path $PSScriptRoot 'core/src/main/java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
$tests = @(Get-ChildItem (Join-Path $PSScriptRoot 'core/src/test/java') -Recurse -Filter '*.java' | ForEach-Object { $_.FullName })
if (!(Test-Path $GsonJar -PathType Leaf)) { throw 'Gson 2.10.1 local ausente; informe -GsonJar. Este script nao baixa dependencias.' }
$GsonJar = (Resolve-Path $GsonJar).Path
Write-Output "JSON_DEPENDENCY=$GsonJar"
& $javac --release 17 -encoding UTF-8 -Xlint:all -Werror -cp $GsonJar -d $mainOut @main
if ($LASTEXITCODE -ne 0) { throw 'Compilacao do core falhou' }
& $javac --release 17 -encoding UTF-8 -Xlint:all -Werror -cp "$mainOut;$GsonJar" -d $testOut @tests
if ($LASTEXITCODE -ne 0) { throw 'Compilacao dos testes falhou' }
& $java -cp "$mainOut;$testOut;$GsonJar" dev.nanites.core.CoreTests
if ($LASTEXITCODE -ne 0) { throw 'Testes falharam' }
& $java -cp "$mainOut;$testOut;$GsonJar" dev.nanites.core.ExpansionTests
if ($LASTEXITCODE -ne 0) { throw 'Testes de expansao falharam' }
& $java -cp $mainOut dev.nanites.core.Demo
if ($LASTEXITCODE -ne 0) { throw 'Demo falhou' }
Copy-Item (Join-Path $PSScriptRoot 'examples') -Destination $mainOut -Recurse
& $java -cp "$mainOut;$GsonJar" dev.nanites.core.ProgrammingDemo
if ($LASTEXITCODE -ne 0) { throw 'Demo de programacao falhou' }
$artifact = Join-Path $build 'nanites-core.jar'
& $jar --create --file $artifact --main-class dev.nanites.core.Demo -C $mainOut .
if ($LASTEXITCODE -ne 0) { throw 'Empacotamento falhou' }
Write-Output "ARTIFACT=$artifact"
Write-Output 'VALIDATION=PASS (Java 17 bytecode; core only, NOT a Minecraft mod)'
