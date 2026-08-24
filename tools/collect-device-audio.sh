#!/system/bin/sh

set -u

OUT="${1:-/sdcard/Download/audiofreedom-audio-diagnostics.txt}"

section() {
    printf '\n===== %s =====\n' "$1" >> "$OUT"
}

: > "$OUT" || exit 1

section "device"
printf 'timestamp=' >> "$OUT"
date -Iseconds >> "$OUT" 2>&1
getprop ro.product.manufacturer >> "$OUT" 2>&1
getprop ro.product.model >> "$OUT" 2>&1
getprop ro.build.version.release >> "$OUT" 2>&1
getprop ro.build.version.sdk >> "$OUT" 2>&1
getprop ro.build.fingerprint >> "$OUT" 2>&1
getprop ro.vendor.build.version.release >> "$OUT" 2>&1
getprop ro.vendor.build.version.sdk >> "$OUT" 2>&1
getprop ro.product.first_api_level >> "$OUT" 2>&1
getprop ro.product.cpu.abi >> "$OUT" 2>&1

section "audio services"
service list 2>&1 | grep -i audio >> "$OUT"

section "audio flinger"
dumpsys media.audio_flinger >> "$OUT" 2>&1

section "audio policy"
dumpsys media.audio_policy >> "$OUT" 2>&1

section "declared effect HAL services"
service list 2>&1 | grep -Ei 'audio.*effect|effect.*audio' >> "$OUT"

section "HIDL audio services"
if command -v lshal >/dev/null 2>&1; then
    lshal 2>&1 | grep -Ei 'android.hardware.audio|audio.effect' >> "$OUT"
else
    echo "lshal unavailable" >> "$OUT"
fi

section "VINTF audio declarations"
grep -R -Ei 'android.hardware.audio|audio.effect' \
    /vendor/etc/vintf /odm/etc/vintf /system/etc/vintf 2>/dev/null >> "$OUT"

section "audio effect configuration files"
find /vendor /odm /system /system_ext /product \
    -type f \( -iname 'audio_effects*.xml' -o -iname 'audio_effects*.conf' \) \
    2>/dev/null >> "$OUT"

section "soundfx libraries"
find /vendor /odm /system /system_ext /product \
    -type f -path '*soundfx*' 2>/dev/null >> "$OUT"

section "audio properties"
getprop 2>&1 | grep -Ei 'audio|a2dp|offload|spatial|dolby|dirac' >> "$OUT"

section "mounts"
mount 2>&1 | grep -E ' /(vendor|odm|system|system_ext|product) ' >> "$OUT"

printf '\nDiagnostics written to %s\n' "$OUT"
