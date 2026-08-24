param(
    [string]$JavaHome = "C:\Program Files\Eclipse Adoptium\jdk-21.0.8.9-hotspot",
    [string]$AndroidSdk = "$env:LOCALAPPDATA\Android\Sdk"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$sourceRoot = Join-Path $PSScriptRoot "config-patcher\src"
$source = Join-Path $sourceRoot `
    "com\svetlio\audiofreedom\tools\AudioEffectsConfigPatcher.java"
$buildRoot = Join-Path $projectRoot "out\config-patcher"
$classes = Join-Path $buildRoot "classes"
$dex = Join-Path $buildRoot "dex"
$classesJar = Join-Path $buildRoot "audiofreedom-config-patcher-classes.jar"
$outputJar = Join-Path $buildRoot "audiofreedom-config-patcher.jar"
$javac = Join-Path $JavaHome "bin\javac.exe"
$java = Join-Path $JavaHome "bin\java.exe"
$jar = Join-Path $JavaHome "bin\jar.exe"
$d8 = Get-ChildItem -LiteralPath (Join-Path $AndroidSdk "build-tools") -Directory |
    Sort-Object { [Version]$_.Name } -Descending |
    ForEach-Object { Join-Path $_.FullName "d8.bat" } |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1

foreach ($required in @($source, $javac, $java, $jar, $d8)) {
    if (-not $required -or -not (Test-Path -LiteralPath $required)) {
        throw "Required config-patcher build input is missing: $required"
    }
}

if (Test-Path -LiteralPath $buildRoot) {
    $resolved = [IO.Path]::GetFullPath($buildRoot)
    $expected = [IO.Path]::GetFullPath((Join-Path $projectRoot "out\config-patcher"))
    if ($resolved -ne $expected) {
        throw "Refusing to clean unexpected path: $resolved"
    }
    Remove-Item -LiteralPath $buildRoot -Recurse -Force
}
New-Item -ItemType Directory -Path $classes,$dex | Out-Null

& $javac -encoding UTF-8 --release 17 -d $classes $source
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
& $jar --create --file $classesJar -C $classes .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }
$previousJavaHome = $env:JAVA_HOME
try {
    $env:JAVA_HOME = $JavaHome
    & $d8 --min-api 35 --output $dex $classesJar
    if ($LASTEXITCODE -ne 0) { throw "d8 failed" }
} finally {
    $env:JAVA_HOME = $previousJavaHome
}
& $jar --create --file $outputJar -C $dex classes.dex
if ($LASTEXITCODE -ne 0) { throw "Dex jar packaging failed" }

$fixture = Join-Path $projectRoot "tests\fixtures\audio_effects_minimal.xml"
$legacyOutput = Join-Path $buildRoot "legacy-test.xml"
$aidlOutput = Join-Path $buildRoot "aidl-test.xml"
& $java -cp $classes `
    com.svetlio.audiofreedom.tools.AudioEffectsConfigPatcher `
    $fixture $legacyOutput legacy libaudiofreedomfx_legacy.so
if ($LASTEXITCODE -ne 0) { throw "Legacy host patcher test failed" }
& $java -cp $classes `
    com.svetlio.audiofreedom.tools.AudioEffectsConfigPatcher `
    $fixture $aidlOutput aidl libaudiofreedomfx.so
if ($LASTEXITCODE -ne 0) { throw "AIDL host patcher test failed" }

foreach ($test in @(
    @{ Path = $legacyOutput; Backend = "legacy"; Library = "libaudiofreedomfx_legacy.so" },
    @{ Path = $aidlOutput; Backend = "aidl"; Library = "libaudiofreedomfx.so" }
)) {
    $document = [Xml.XmlDocument]::new()
    $document.Load($test.Path)
    $effect = $document.SelectSingleNode(
        "//*[local-name()='effect' and @uuid='2f6e8c10-8d44-4b42-b110-16f3a729ef01']")
    $library = $document.SelectSingleNode(
        "//*[local-name()='library' and @name='audiofreedom']")
    $apply = $document.SelectSingleNode(
        "//*[local-name()='apply' and @effect='audiofreedom']")
    if (-not $effect -or -not $library -or
        $library.GetAttribute("path") -ne $test.Library) {
        throw "$($test.Backend) host patcher validation failed"
    }
    if (($test.Backend -eq "aidl") -ne [bool]$apply) {
        throw "$($test.Backend) music attachment validation failed"
    }
    $hasType = $effect.GetAttribute("type") -eq "a7e03c90-7c3d-4f48-9c8d-497c8f1b1201"
    if (($test.Backend -eq "aidl") -ne $hasType) {
        throw "$($test.Backend) type UUID validation failed"
    }
}

Write-Output $outputJar
