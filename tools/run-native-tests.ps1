param(
    [string]$Serial,
    [string]$TestBinary = ".\out\android-tests-arm64\tests\audiofreedom_tests"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
$binaryPath = (Resolve-Path -LiteralPath (Join-Path $projectRoot $TestBinary)).Path
$remotePath = "/data/local/tmp/audiofreedom-native-tests"

if (-not $Serial) {
    $connected = @(
        adb devices |
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

try {
    & adb -s $Serial push $binaryPath $remotePath
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to push the native tests"
    }

    & adb -s $Serial shell chmod 0755 $remotePath
    if ($LASTEXITCODE -ne 0) {
        throw "Failed to make the native tests executable"
    }

    $command = "$remotePath; test_exit=`$?; " +
               "echo AUDIOFREEDOM_TEST_EXIT=`$test_exit; exit `$test_exit"
    & adb -s $Serial shell $command
    if ($LASTEXITCODE -ne 0) {
        throw "Native tests failed"
    }
} finally {
    & adb -s $Serial shell rm -f $remotePath | Out-Null
    $cleanup = & adb -s $Serial shell "test ! -e $remotePath && echo TEMP_CLEAN || echo TEMP_LEFT"
    Write-Output $cleanup
}
