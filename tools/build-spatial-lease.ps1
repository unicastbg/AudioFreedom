param(
    [string]$JavaHome = 'C:/Program Files/Eclipse Adoptium/jdk-21.0.8.9-hotspot',
    [string]$AndroidSdk = "$env:LOCALAPPDATA/Android/Sdk"
)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$out = Join-Path $root 'out/spatial-lease'
$classes = Join-Path $out 'classes'
$dex = Join-Path $out 'dex'
New-Item -ItemType Directory -Path $classes,$dex -Force | Out-Null
$androidJar = Join-Path $AndroidSdk 'platforms/android-36/android.jar'
& "$JavaHome/bin/javac.exe" --release 17 -cp $androidJar -d $classes (Join-Path $PSScriptRoot 'spatial-lease/AudioFreedomSpatialLease.java')
if ($LASTEXITCODE) { throw 'Spatial lease javac failed' }
$jar = Join-Path $out 'classes.jar'
& "$JavaHome/bin/jar.exe" --create --date=1980-01-01T00:00:02Z --file $jar -C $classes .
if ($LASTEXITCODE) { throw 'Spatial lease jar failed' }
$saved = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    & "$AndroidSdk/build-tools/36.0.0/d8.bat" --min-api 35 --lib $androidJar --output $dex $jar
    if ($LASTEXITCODE) { throw 'Spatial lease d8 failed' }
} finally { $env:JAVA_HOME = $saved }
$asset = Join-Path $root 'app/src/main/assets/audiofreedom-spatial-lease.jar'
& "$JavaHome/bin/jar.exe" --create --date=1980-01-01T00:00:02Z --file $asset -C $dex classes.dex
if ($LASTEXITCODE) { throw 'Spatial lease packaging failed' }
Write-Output $asset
