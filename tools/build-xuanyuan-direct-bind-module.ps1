param(
    [string]$EffectLibrary = ".\out\android-effect-arm64-static\libaudiofreedomfx.so"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot "AudioEffectsConfig.psm1") -Force
$sourceConfig = Join-Path $projectRoot "diagnostics\xuanyuan-eea\sku_sun\audio_effects_config.xml"
$sourceLegacyConfig = Join-Path $projectRoot "diagnostics\xuanyuan-eea\sku_sun\audio_effects.xml"
$templateRoot = Join-Path $projectRoot "module\package-direct-bind"
$buildRoot = Join-Path $projectRoot "module\build-direct-bind"
$packageRoot = Join-Path $buildRoot "audiofreedom-xuanyuan-eea-direct-bind"
$zipPath = Join-Path $buildRoot "AudioFreedom-xuanyuan-eea-0.9.0-foundation1-directbind.zip"
$expectedHash = "FE7C66C4A94DBB4555A4BEE819B44505B8E6B871C77535B50A36AF7D31D87F5C"
$expectedLegacyHash = "1A8C6E2B33ABC9237BEC744B2C5142A7007EF91ED6DBA4A25435D0E3CD5FFAB4"
$expectedLibraryHash = "E27B334F81C1DD8405894F5852757EAAEDB65690D98697222B45140CD598B30D"
$effectUuid = "2f6e8c10-8d44-4b42-b110-16f3a729ef01"

$resolvedLibrary = (Resolve-Path -LiteralPath (Join-Path $projectRoot $EffectLibrary)).Path
$actualHash = (Get-FileHash -LiteralPath $sourceConfig -Algorithm SHA256).Hash
$actualLegacyHash = (Get-FileHash -LiteralPath $sourceLegacyConfig -Algorithm SHA256).Hash
$actualLibraryHash = (Get-FileHash -LiteralPath $resolvedLibrary -Algorithm SHA256).Hash
if ($actualHash -ne $expectedHash) {
    throw "Reference config hash changed: $actualHash"
}
if ($actualLegacyHash -ne $expectedLegacyHash) {
    throw "Reference music-chain config hash changed: $actualLegacyHash"
}
if ($actualLibraryHash -ne $expectedLibraryHash) {
    throw "Refusing to package an unverified driver: $actualLibraryHash"
}

if (Test-Path -LiteralPath $buildRoot) {
    $resolvedBuildRoot = [IO.Path]::GetFullPath($buildRoot)
    $expectedBuildRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot "module\build-direct-bind"))
    if ($resolvedBuildRoot -ne $expectedBuildRoot) {
        throw "Refusing to clean unexpected path: $resolvedBuildRoot"
    }
    Remove-Item -LiteralPath $buildRoot -Recurse -Force
}

New-Item -ItemType Directory -Path $packageRoot | Out-Null
Copy-Item -Path (Join-Path $templateRoot "*") -Destination $packageRoot -Recurse

$payloadRoot = Join-Path $packageRoot "payload"
$libraryTarget = Join-Path $payloadRoot "libaudiofreedomfx.so"
$configTarget = Join-Path $payloadRoot "audio_effects_config.xml"
$legacyConfigTarget = Join-Path $payloadRoot "audio_effects.xml"
New-Item -ItemType Directory -Path $payloadRoot -Force | Out-Null
Copy-Item -LiteralPath $resolvedLibrary -Destination $libraryTarget
New-AudioFreedomEffectConfig -Source $sourceConfig -Target $configTarget
New-AudioFreedomEffectConfig -Source $sourceLegacyConfig -Target $legacyConfigTarget

$utf8NoBom = [Text.UTF8Encoding]::new($false)
foreach ($script in Get-ChildItem -LiteralPath $packageRoot -Filter "*.sh" -File) {
    $content = [IO.File]::ReadAllText($script.FullName).Replace("`r`n", "`n")
    [IO.File]::WriteAllText($script.FullName, $content, $utf8NoBom)
}

$buildInfo = @(
    "profile=xiaomi-xuanyuan-eea-android16"
    "mount_mode=direct-bind"
    "source_config_sha256=$($actualHash.ToLowerInvariant())"
    "patched_config_sha256=$((Get-FileHash $configTarget -Algorithm SHA256).Hash.ToLowerInvariant())"
    "source_music_chain_sha256=$($actualLegacyHash.ToLowerInvariant())"
    "patched_music_chain_sha256=$((Get-FileHash $legacyConfigTarget -Algorithm SHA256).Hash.ToLowerInvariant())"
    "effect_library_sha256=$($actualLibraryHash.ToLowerInvariant())"
    "effect_uuid=$effectUuid"
) -join "`n"
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
