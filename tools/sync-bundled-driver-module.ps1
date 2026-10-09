param(
    [Parameter(Mandatory = $true)]
    [string]$ModuleZip
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$resolvedZip = (Resolve-Path -LiteralPath $ModuleZip).Path
$assets = Join-Path $projectRoot "app\src\main\assets"
$assetName = "audiofreedom-driver-module.zip"
$assetZip = Join-Path $assets $assetName
$metadataPath = Join-Path $assets "driver-module.properties"
$utf8NoBom = [Text.UTF8Encoding]::new($false)

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [IO.Compression.ZipFile]::OpenRead($resolvedZip)
try {
    $entry = $archive.GetEntry("module.prop")
    if ($null -eq $entry) {
        throw "Module archive has no root-level module.prop"
    }
    $reader = [IO.StreamReader]::new($entry.Open())
    try {
        $moduleProperties = @{}
        foreach ($line in ($reader.ReadToEnd() -split "`r?`n")) {
            $trimmed = $line.Trim()
            if (!$trimmed -or $trimmed.StartsWith("#") -or !$trimmed.Contains("=")) {
                continue
            }
            $name, $value = $trimmed -split "=", 2
            $moduleProperties[$name.Trim()] = $value.Trim()
        }
    } finally {
        $reader.Dispose()
    }
} finally {
    $archive.Dispose()
}

if ($moduleProperties.id -ne "audiofreedom") {
    throw "Refusing to bundle module id '$($moduleProperties.id)'"
}
if (!$moduleProperties.version -or !$moduleProperties.versionCode) {
    throw "Module version metadata is incomplete"
}

New-Item -ItemType Directory -Path $assets -Force | Out-Null
Copy-Item -LiteralPath $resolvedZip -Destination $assetZip -Force
$hash = (Get-FileHash -LiteralPath $resolvedZip -Algorithm SHA256).Hash.ToLowerInvariant()
$metadata = @(
    "asset=$assetName"
    "version=$($moduleProperties.version)"
    "versionCode=$($moduleProperties.versionCode)"
    "sha256=$hash"
) -join "`n"
[IO.File]::WriteAllText($metadataPath, "$metadata`n", $utf8NoBom)

Write-Output $assetZip
