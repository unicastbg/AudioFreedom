param(
    [string]$Serial,
    [string]$Label = "android-device",
    [string]$Destination = ".\diagnostics\portable"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$collector = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "collect-device-audio.sh")).Path
$remoteCollector = "/data/local/tmp/audiofreedom-collect-audio.sh"
$remoteReport = "/data/local/tmp/audiofreedom-audio-diagnostics.txt"
$adbCommand = (Get-Command adb -ErrorAction SilentlyContinue).Source
if (-not $adbCommand) {
    $sdkAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (-not (Test-Path -LiteralPath $sdkAdb)) {
        throw "adb was not found in PATH or the default Android SDK"
    }
    $adbCommand = $sdkAdb
}

if (-not $Serial) {
    $connected = @(
        & $adbCommand devices |
            Select-Object -Skip 1 |
            ForEach-Object {
                if ($_ -match "^(\S+)\s+device(?:\s|$)") {
                    $Matches[1]
                }
            }
    )
    if ($connected.Count -ne 1) {
        throw "Expected one connected ADB device; pass -Serial when more than one is present"
    }
    $Serial = $connected[0]
}

$safeLabel = $Label -replace "[^A-Za-z0-9._-]", "-"
$timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$destinationRoot = Join-Path $projectRoot $Destination
New-Item -ItemType Directory -Path $destinationRoot -Force | Out-Null
$localReport = Join-Path $destinationRoot "$safeLabel-$timestamp.txt"

try {
    & $adbCommand -s $Serial push $collector $remoteCollector
    if ($LASTEXITCODE -ne 0) { throw "Failed to push the diagnostic collector" }

    & $adbCommand -s $Serial shell chmod 0755 $remoteCollector
    if ($LASTEXITCODE -ne 0) { throw "Failed to make the collector executable" }

    & $adbCommand -s $Serial shell $remoteCollector $remoteReport
    if ($LASTEXITCODE -ne 0) { throw "Device diagnostic collection failed" }

    & $adbCommand -s $Serial pull $remoteReport $localReport
    if ($LASTEXITCODE -ne 0) { throw "Failed to pull the diagnostic report" }

    Write-Output $localReport
} finally {
    & $adbCommand -s $Serial shell rm -f $remoteCollector $remoteReport | Out-Null
}
