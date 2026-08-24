param(
    [string]$EffectLibrary = ".\out\android-effect-arm64\libaudiofreedomfx.so",
    [string]$ControllerBinary = ""
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot "AudioEffectsConfig.psm1") -Force
$sourceConfig = Join-Path $projectRoot "diagnostics\xuanyuan-eea\sku_sun\audio_effects_config.xml"
$sourceLegacyConfig = Join-Path $projectRoot "diagnostics\xuanyuan-eea\sku_sun\audio_effects.xml"
$templateRoot = Join-Path $projectRoot "module\package"
$buildRoot = Join-Path $projectRoot "module\build"
$packageRoot = Join-Path $buildRoot "audiofreedom-xuanyuan-eea"
$zipPath = Join-Path $buildRoot "AudioFreedom-xuanyuan-eea-0.1.0-dev9.zip"
$expectedHash = "FE7C66C4A94DBB4555A4BEE819B44505B8E6B871C77535B50A36AF7D31D87F5C"
$expectedLegacyHash = "1A8C6E2B33ABC9237BEC744B2C5142A7007EF91ED6DBA4A25435D0E3CD5FFAB4"
$effectUuid = "2f6e8c10-8d44-4b42-b110-16f3a729ef01"

$resolvedLibrary = (Resolve-Path -LiteralPath (Join-Path $projectRoot $EffectLibrary)).Path
$resolvedController = $null
if ($ControllerBinary) {
    $resolvedController = (Resolve-Path -LiteralPath (
        Join-Path $projectRoot $ControllerBinary)).Path
}
$actualHash = (Get-FileHash -LiteralPath $sourceConfig -Algorithm SHA256).Hash
$actualLegacyHash = (Get-FileHash -LiteralPath $sourceLegacyConfig -Algorithm SHA256).Hash
if ($actualHash -ne $expectedHash) {
    throw "Reference config hash changed: $actualHash"
}
if ($actualLegacyHash -ne $expectedLegacyHash) {
    throw "Reference music-chain config hash changed: $actualLegacyHash"
}

if (Test-Path -LiteralPath $buildRoot) {
    $resolvedBuildRoot = [IO.Path]::GetFullPath($buildRoot)
    $expectedBuildRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot "module\build"))
    if ($resolvedBuildRoot -ne $expectedBuildRoot) {
        throw "Refusing to clean unexpected path: $resolvedBuildRoot"
    }
    Remove-Item -LiteralPath $buildRoot -Recurse -Force
}

New-Item -ItemType Directory -Path $packageRoot | Out-Null
Copy-Item -Path (Join-Path $templateRoot "*") -Destination $packageRoot -Recurse

$libraryTarget = Join-Path $packageRoot "system\vendor\lib64\soundfx\libaudiofreedomfx.so"
$configTarget = Join-Path $packageRoot "system\vendor\etc\audio\sku_sun\audio_effects_config.xml"
$legacyConfigTarget = Join-Path $packageRoot "system\vendor\etc\audio\sku_sun\audio_effects.xml"
New-Item -ItemType Directory -Path (Split-Path -Parent $libraryTarget) -Force | Out-Null
New-Item -ItemType Directory -Path (Split-Path -Parent $configTarget) -Force | Out-Null
Copy-Item -LiteralPath $resolvedLibrary -Destination $libraryTarget

if ($resolvedController) {
    $controllerTarget = Join-Path $packageRoot "system\bin\audiofreedom-controller"
    $runtimeTarget = Join-Path $packageRoot "runtime"
    New-Item -ItemType Directory -Path (Split-Path -Parent $controllerTarget) -Force |
        Out-Null
    Copy-Item -LiteralPath $resolvedController -Destination $controllerTarget
    Copy-Item -LiteralPath (Join-Path $projectRoot "module\runtime") -Destination $runtimeTarget -Recurse
}

New-AudioFreedomEffectConfig -Source $sourceConfig -Target $configTarget
New-AudioFreedomEffectConfig -Source $sourceLegacyConfig -Target $legacyConfigTarget

$utf8NoBom = [Text.UTF8Encoding]::new($false)
foreach ($scriptName in @("customize.sh", "service.sh", "action.sh", "uninstall.sh")) {
    $scriptPath = Join-Path $packageRoot $scriptName
    $content = [IO.File]::ReadAllText($scriptPath).Replace("`r`n", "`n")
    [IO.File]::WriteAllText($scriptPath, $content, $utf8NoBom)
}
if ($resolvedController) {
    foreach ($runtimeScript in Get-ChildItem -LiteralPath (Join-Path $packageRoot "runtime") -Filter "*.sh" -File) {
        $content = [IO.File]::ReadAllText($runtimeScript.FullName).Replace("`r`n", "`n")
        [IO.File]::WriteAllText($runtimeScript.FullName, $content, $utf8NoBom)
    }
}

$buildInfo = @(
    "profile=xiaomi-xuanyuan-eea-android16"
    "source_config_sha256=$($actualHash.ToLowerInvariant())"
    "patched_config_sha256=$((Get-FileHash $configTarget -Algorithm SHA256).Hash.ToLowerInvariant())"
    "source_music_chain_sha256=$($actualLegacyHash.ToLowerInvariant())"
    "patched_music_chain_sha256=$((Get-FileHash $legacyConfigTarget -Algorithm SHA256).Hash.ToLowerInvariant())"
    "effect_library_sha256=$((Get-FileHash $libraryTarget -Algorithm SHA256).Hash.ToLowerInvariant())"
    "effect_uuid=$effectUuid"
)
if ($resolvedController) {
    $controllerTarget = Join-Path $packageRoot "system\bin\audiofreedom-controller"
    $buildInfo += "controller_sha256=$((Get-FileHash $controllerTarget -Algorithm SHA256).Hash.ToLowerInvariant())"
}
$buildInfo = $buildInfo -join "`n"
[IO.File]::WriteAllText((Join-Path $packageRoot "build-info.txt"), "$buildInfo`n", $utf8NoBom)

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open($zipPath, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -LiteralPath $packageRoot -Recurse -File) {
        $entryName = $file.FullName.Substring($packageRoot.Length + 1).Replace("\", "/")
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive,
            $file.FullName,
            $entryName,
            [IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    $archive.Dispose()
}
Write-Output $zipPath
