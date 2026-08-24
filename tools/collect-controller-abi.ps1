param(
    [string]$Serial,
    [string]$Destination = ".\diagnostics\controller-abi"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot

if (-not $Serial) {
    $connected = @(
        adb devices |
            Select-Object -Skip 1 |
            ForEach-Object {
                if ($_ -match "^(\S+)\s+device$") {
                    $Matches[1]
                }
            }
    )
    if ($connected.Count -ne 1) {
        throw "Expected one connected ADB device; pass -Serial when more than one is present"
    }
    $Serial = $connected[0]
}

$fingerprint = ((& adb -s $Serial shell getprop ro.build.fingerprint) -join "").Trim()
$sdk = ((& adb -s $Serial shell getprop ro.build.version.sdk) -join "").Trim()
$abi = ((& adb -s $Serial shell getprop ro.product.cpu.abi) -join "").Trim()
if ($LASTEXITCODE -ne 0 -or -not $fingerprint) {
    throw "Unable to read the connected device identity"
}

$deviceName = ($fingerprint -replace "[^A-Za-z0-9._-]", "_")
$outputRoot = Join-Path (Join-Path $projectRoot $Destination) $deviceName
New-Item -ItemType Directory -Path $outputRoot -Force | Out-Null

$remoteLibraries = @(
    "/system/lib64/libaudioclient.so",
    "/system/lib64/libbinder.so",
    "/system/lib64/libutils.so",
    "/system/lib64/liblog.so",
    "/system/lib64/libc++.so",
    "/system/lib64/framework-permission-aidl-cpp.so"
)

$manifest = @(
    "serial=$Serial",
    "fingerprint=$fingerprint",
    "sdk=$sdk",
    "abi=$abi"
)

foreach ($remotePath in $remoteLibraries) {
    $readable = ((& adb -s $Serial shell "if [ -r '$remotePath' ]; then echo yes; fi") -join "").Trim()
    if ($readable -ne "yes") {
        $manifest += "missing=$remotePath"
        continue
    }

    $fileName = Split-Path -Leaf $remotePath
    $localPath = Join-Path $outputRoot $fileName
    & adb -s $Serial pull $remotePath $localPath | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to pull $remotePath"
    }
    $hash = (Get-FileHash -LiteralPath $localPath -Algorithm SHA256).Hash.ToLowerInvariant()
    $manifest += "library=$remotePath sha256=$hash"
}

$utf8NoBom = [Text.UTF8Encoding]::new($false)
[IO.File]::WriteAllText(
    (Join-Path $outputRoot "manifest.txt"),
    (($manifest -join "`n") + "`n"),
    $utf8NoBom)

Write-Output $outputRoot
