param(
    [string]$Aidl64Library = ".\out\android-effect-arm64-static\libaudiofreedomfx.so",
    [string]$Aidl64Probe = ".\out\android-effect-arm64\audiofreedom_dlopen_probe",
    [string]$Legacy64Library = ".\out\android-legacy-arm64\libaudiofreedomfx_legacy.so",
    [string]$Legacy64Probe = ".\out\android-legacy-arm64\audiofreedom_legacy_probe",
    [string]$Legacy32Library = ".\out\android-legacy-armv7\libaudiofreedomfx_legacy.so",
    [string]$Legacy32Probe = ".\out\android-legacy-armv7\audiofreedom_legacy_probe"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$templateRoot = Join-Path $projectRoot "module\package-universal"
$buildRoot = Join-Path $projectRoot "module\build-universal"
$packageRoot = Join-Path $buildRoot "audiofreedom-universal"
$zipPath = Join-Path $buildRoot "AudioFreedom-universal-0.9.0.zip"
$utf8NoBom = [Text.UTF8Encoding]::new($false)

$patcher = (& (Join-Path $PSScriptRoot "build-config-patcher.ps1") | Select-Object -Last 1)
$inputs = @{
    Patcher = $patcher
    Aidl64Library = Join-Path $projectRoot $Aidl64Library
    Aidl64Probe = Join-Path $projectRoot $Aidl64Probe
    Legacy64Library = Join-Path $projectRoot $Legacy64Library
    Legacy64Probe = Join-Path $projectRoot $Legacy64Probe
    Legacy32Library = Join-Path $projectRoot $Legacy32Library
    Legacy32Probe = Join-Path $projectRoot $Legacy32Probe
}
foreach ($name in @($inputs.Keys)) {
    $inputs[$name] = (Resolve-Path -LiteralPath $inputs[$name]).Path
}

if (Test-Path -LiteralPath $buildRoot) {
    $resolved = [IO.Path]::GetFullPath($buildRoot)
    $expected = [IO.Path]::GetFullPath((Join-Path $projectRoot "module\build-universal"))
    if ($resolved -ne $expected) {
        throw "Refusing to clean unexpected path: $resolved"
    }
    Remove-Item -LiteralPath $buildRoot -Recurse -Force
}

New-Item -ItemType Directory -Path $packageRoot | Out-Null
Copy-Item -Path (Join-Path $templateRoot "*") -Destination $packageRoot -Recurse
$toolsTarget = Join-Path $packageRoot "payload\tools"
$aidl64Target = Join-Path $packageRoot "payload\backends\aidl64"
$legacy64Target = Join-Path $packageRoot "payload\backends\legacy64"
$legacy32Target = Join-Path $packageRoot "payload\backends\legacy32"
New-Item -ItemType Directory -Path $toolsTarget,$aidl64Target,$legacy64Target,$legacy32Target |
    Out-Null

Copy-Item -LiteralPath $inputs.Patcher -Destination `
    (Join-Path $toolsTarget "audiofreedom-config-patcher.jar")
Copy-Item -LiteralPath $inputs.Aidl64Library -Destination `
    (Join-Path $aidl64Target "libaudiofreedomfx.so")
Copy-Item -LiteralPath $inputs.Aidl64Probe -Destination `
    (Join-Path $aidl64Target "audiofreedom_dlopen_probe")
Copy-Item -LiteralPath $inputs.Legacy64Library -Destination `
    (Join-Path $legacy64Target "libaudiofreedomfx_legacy.so")
Copy-Item -LiteralPath $inputs.Legacy64Probe -Destination `
    (Join-Path $legacy64Target "audiofreedom_legacy_probe")
Copy-Item -LiteralPath $inputs.Legacy32Library -Destination `
    (Join-Path $legacy32Target "libaudiofreedomfx_legacy.so")
Copy-Item -LiteralPath $inputs.Legacy32Probe -Destination `
    (Join-Path $legacy32Target "audiofreedom_legacy_probe")

$replacements = @{
    "@PATCHER_HASH@" = (Get-FileHash $inputs.Patcher -Algorithm SHA256).Hash.ToLowerInvariant()
    "@AIDL64_LIBRARY_HASH@" = (Get-FileHash $inputs.Aidl64Library -Algorithm SHA256).Hash.ToLowerInvariant()
    "@AIDL64_PROBE_HASH@" = (Get-FileHash $inputs.Aidl64Probe -Algorithm SHA256).Hash.ToLowerInvariant()
    "@LEGACY64_LIBRARY_HASH@" = (Get-FileHash $inputs.Legacy64Library -Algorithm SHA256).Hash.ToLowerInvariant()
    "@LEGACY64_PROBE_HASH@" = (Get-FileHash $inputs.Legacy64Probe -Algorithm SHA256).Hash.ToLowerInvariant()
    "@LEGACY32_LIBRARY_HASH@" = (Get-FileHash $inputs.Legacy32Library -Algorithm SHA256).Hash.ToLowerInvariant()
    "@LEGACY32_PROBE_HASH@" = (Get-FileHash $inputs.Legacy32Probe -Algorithm SHA256).Hash.ToLowerInvariant()
}

foreach ($file in Get-ChildItem -LiteralPath $packageRoot -Recurse -File) {
    if ($file.Extension -eq ".sh") {
        $content = [IO.File]::ReadAllText($file.FullName)
        foreach ($entry in $replacements.GetEnumerator()) {
            $content = $content.Replace($entry.Key, $entry.Value)
        }
        $content = $content.Replace("`r`n", "`n")
        [IO.File]::WriteAllText($file.FullName, $content, $utf8NoBom)
    }
}

$unresolved = rg -n "@[A-Z0-9_]+@" $packageRoot
if ($LASTEXITCODE -eq 0) {
    throw "Universal module contains unresolved placeholders:`n$unresolved"
}

$buildInfo = @(
    "profile=universal-audio-stack-0.9.0"
    "selection=live-factory-and-process-abi"
    "config_strategy=device-owned-xml-dom-patch"
    "supported_backends=aidl64,legacy64,legacy32"
    "effect_uuid=2f6e8c10-8d44-4b42-b110-16f3a729ef01"
    "patcher_sha256=$($replacements['@PATCHER_HASH@'])"
    "aidl64_library_sha256=$($replacements['@AIDL64_LIBRARY_HASH@'])"
    "legacy64_library_sha256=$($replacements['@LEGACY64_LIBRARY_HASH@'])"
    "legacy32_library_sha256=$($replacements['@LEGACY32_LIBRARY_HASH@'])"
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
