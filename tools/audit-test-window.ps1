param(
    [string]$Serial
)

$ErrorActionPreference = "Stop"

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

function Show-Section([string]$Title, [string]$Command, [switch]$Root) {
    Write-Output ""
    Write-Output "===== $Title ====="
    if ($Root) {
        & adb -s $Serial shell su -c $Command
    } else {
        & adb -s $Serial shell $Command
    }
}

Show-Section "device" "printf 'fingerprint='; getprop ro.build.fingerprint; printf 'sdk='; getprop ro.build.version.sdk; printf 'boot_completed='; getprop sys.boot_completed"
Show-Section "selinux" "getenforce"
Show-Section "companion package" "pm path com.svetlio.audiofreedom 2>/dev/null || echo not-installed"
Show-Section "KernelSU module metadata" 'for path in /data/adb/modules/audiofreedom /data/adb/modules/meta-overlayfs /data/adb/metamodule; do if [ -e "$path" ] || [ -L "$path" ]; then ls -ld "$path"; else echo "absent $path"; fi; done' -Root
Show-Section "relevant mounts" 'mount 2>/dev/null | grep -Ei "KSU|audiofreedom|overlay.* /(system|vendor|product|odm)" || echo none' -Root
Show-Section "active AudioFreedom overlay" 'uuid=2f6e8c10-8d44-4b42-b110-16f3a729ef01; found=0; for file in /vendor/etc/audio/sku_sun/audio_effects_config.xml /vendor/etc/audio/sku_sun/audio_effects.xml; do if [ -r "$file" ]; then sha256sum "$file"; if grep -q "$uuid" "$file"; then echo "audiofreedom-present $file"; found=1; fi; fi; done; [ "$found" -eq 0 ] && echo audiofreedom-not-active' -Root
Show-Section "temporary probe" 'if [ -e /data/local/tmp/audiofreedom-factory-probe ]; then echo TEMP_LEFT; else echo TEMP_CLEAN; fi' -Root
