param(
    [string]$Serial,
    [string]$Probe = ".\out\android-effect-arm64\audiofreedom_factory_probe"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$probePath = (Resolve-Path -LiteralPath (Join-Path $projectRoot $Probe)).Path
$remotePath = "/data/local/tmp/audiofreedom-factory-probe"

if (-not $Serial) {
    $connected = @(
        adb devices |
            Select-Object -Skip 1 |
            ForEach-Object { ($_ -split "\s+")[0] } |
            Where-Object { $_ }
    )
    if ($connected.Count -ne 1) {
        throw "Expected one connected ADB device; pass -Serial when more than one is present"
    }
    $Serial = $connected[0]
}

try {
    & adb -s $Serial push $probePath $remotePath
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to push the factory probe"
    }

    & adb -s $Serial shell su -c "chmod 0755 $remotePath"
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to make the factory probe executable"
    }

    $command = "su -c '$remotePath'; probe_exit=`$?; " +
               "echo AUDIOFREEDOM_PROBE_EXIT=`$probe_exit; exit `$probe_exit"
    & adb -s $Serial shell $command
} finally {
    & adb -s $Serial shell su -c "rm -f $remotePath" | Out-Null
    $cleanup = & adb -s $Serial shell su -c "test ! -e $remotePath && echo TEMP_CLEAN || echo TEMP_LEFT"
    Write-Output $cleanup
}
