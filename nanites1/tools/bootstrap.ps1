$ErrorActionPreference='Stop'
[Net.ServicePointManager]::SecurityProtocol=[Net.SecurityProtocolType]::Tls12
$root=Split-Path $PSScriptRoot -Parent
New-Item -ItemType Directory -Force "$root/gradle/wrapper" | Out-Null
if(!(Test-Path "$root/gradle/wrapper/gradle-wrapper.jar")){Copy-Item "$root/../jevmod/gradle/wrapper/gradle-wrapper.jar" "$root/gradle/wrapper/gradle-wrapper.jar"}
$u='https://downloads.gradle.org/distributions/gradle-8.8-bin.zip.sha256'
$response=(Invoke-WebRequest -UseBasicParsing -TimeoutSec 90 $u).Content
$hash=if($response -is [byte[]]){[Text.Encoding]::UTF8.GetString($response).Trim()}else{([string]$response).Trim()}
if($hash -notmatch '^[0-9a-f]{64}$'){throw 'Invalid Gradle checksum'}
@("distributionBase=GRADLE_USER_HOME","distributionPath=wrapper/dists","distributionUrl=https\://downloads.gradle.org/distributions/gradle-8.8-bin.zip","distributionSha256Sum=$hash","networkTimeout=90000","validateDistributionUrl=true","zipStoreBase=GRADLE_USER_HOME","zipStorePath=wrapper/dists") | Set-Content "$root/gradle/wrapper/gradle-wrapper.properties" -Encoding ASCII
Write-Output "Gradle8.8 SHA256 $hash"
