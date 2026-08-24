param(
    [string]$EffectLibrary = ".\out\android-legacy-armv7\libaudiofreedomfx_legacy.so",
    [string]$SourceConfig = ".\diagnostics\portable\sony-xperia-1-iii-files\audio_effects.xml",
    [string]$ProfileName = "reference-hidl32"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $PSScriptRoot "AudioEffectsConfig.psm1") -Force
$source = (Resolve-Path -LiteralPath (Join-Path $projectRoot $SourceConfig)).Path
$library = (Resolve-Path -LiteralPath (Join-Path $projectRoot $EffectLibrary)).Path
$templateRoot = Join-Path $projectRoot "module\package-legacy-direct-bind"
$buildRoot = Join-Path $projectRoot "module\build-legacy-direct-bind"
$packageRoot = Join-Path $buildRoot "audiofreedom-portable-legacy32"
$zipPath = Join-Path $buildRoot "AudioFreedom-portable-legacy32-0.9.0-foundation1-legacy4.zip"

if (Test-Path -LiteralPath $buildRoot) {
    $resolved = [IO.Path]::GetFullPath($buildRoot)
    $expected = [IO.Path]::GetFullPath(
        (Join-Path $projectRoot "module\build-legacy-direct-bind"))
    if ($resolved -ne $expected) {
        throw "Refusing to clean unexpected path: $resolved"
    }
    Remove-Item -LiteralPath $buildRoot -Recurse -Force
}

New-Item -ItemType Directory -Path $packageRoot | Out-Null
Copy-Item -Path (Join-Path $templateRoot "*") -Destination $packageRoot -Recurse
$payload = Join-Path $packageRoot "payload"
New-Item -ItemType Directory -Path $payload | Out-Null
$libraryTarget = Join-Path $payload "libaudiofreedomfx_legacy.so"
$configTarget = Join-Path $payload "audio_effects.xml"
$previousConfigTarget = Join-Path $buildRoot "previous-auto-apply-audio_effects.xml"
Copy-Item -LiteralPath $library -Destination $libraryTarget
New-AudioFreedomEffectConfig -Source $source -Target $configTarget `
    -LibraryPath "libaudiofreedomfx_legacy.so" -IncludeTypeUuid $false `
    -AttachToStream $false
New-AudioFreedomEffectConfig -Source $source -Target $previousConfigTarget `
    -LibraryPath "libaudiofreedomfx_legacy.so" -IncludeTypeUuid $false

$sourceHash = (Get-FileHash -LiteralPath $source -Algorithm SHA256).Hash.ToLowerInvariant()
$patchedHash = (Get-FileHash -LiteralPath $configTarget -Algorithm SHA256).Hash.ToLowerInvariant()
$previousPatchedHash = (Get-FileHash -LiteralPath $previousConfigTarget -Algorithm SHA256).Hash.ToLowerInvariant()
$libraryHash = (Get-FileHash -LiteralPath $libraryTarget -Algorithm SHA256).Hash.ToLowerInvariant()
Remove-Item -LiteralPath $previousConfigTarget -Force
$utf8NoBom = [Text.UTF8Encoding]::new($false)

foreach ($file in Get-ChildItem -LiteralPath $packageRoot -Recurse -File) {
    if ($file.Extension -eq ".sh") {
        $content = [IO.File]::ReadAllText($file.FullName)
        $content = $content.Replace("@EXPECTED_CONFIG_HASH@", $sourceHash)
        $content = $content.Replace("@PATCHED_CONFIG_HASH@", $patchedHash)
        $content = $content.Replace("@PREVIOUS_PATCHED_CONFIG_HASH@", $previousPatchedHash)
        $content = $content.Replace("@EXPECTED_LIBRARY_HASH@", $libraryHash)
        $content = $content.Replace("`r`n", "`n")
        [IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
    }
}

$buildInfo = @(
    "profile=portable-legacy32-hidl"
    "profile_name=$ProfileName"
    "mount_mode=legacy-direct-bind"
    "effect_process_abi=armeabi-v7a"
    "selection=capabilities-plus-config-signature"
    "source_config_sha256=$sourceHash"
    "patched_config_sha256=$patchedHash"
    "previous_auto_apply_config_sha256=$previousPatchedHash"
    "effect_library_sha256=$libraryHash"
    "effect_uuid=2f6e8c10-8d44-4b42-b110-16f3a729ef01"
) -join "`n"
[IO.File]::WriteAllText((Join-Path $packageRoot "build-info.txt"),
    "$buildInfo`n", $utf8NoBom)

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::Open(
    $zipPath, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($file in Get-ChildItem -LiteralPath $packageRoot -Recurse -File) {
        $entryName = $file.FullName.Substring($packageRoot.Length + 1).Replace("\", "/")
        [void][IO.Compression.ZipFileExtensions]::CreateEntryFromFile(
            $archive, $file.FullName, $entryName,
            [IO.Compression.CompressionLevel]::Optimal)
    }
} finally {
    $archive.Dispose()
}

Write-Output $zipPath
