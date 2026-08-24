#!/system/bin/sh

EXPECTED_CONFIG_HASH="@EXPECTED_CONFIG_HASH@"
PATCHED_CONFIG_HASH="@PATCHED_CONFIG_HASH@"
PREVIOUS_PATCHED_CONFIG_HASH="@PREVIOUS_PATCHED_CONFIG_HASH@"
CONFIG="/vendor/etc/audio_effects.xml"
VENDOR_SOUNDFX="/vendor/lib/soundfx"
AUDIOFREEDOM_COMPATIBILITY_ERROR=""
AUDIOFREEDOM_FACTORY_PID=""
AUDIOFREEDOM_FACTORY_INTERFACE=""
AUDIOFREEDOM_CONFIG_HASH=""

compatibility_error() {
    AUDIOFREEDOM_COMPATIBILITY_ERROR="$1"
    return 1
}

audiofreedom_check_legacy32() {
    abilist="$(getprop ro.product.cpu.abi),$(getprop ro.product.cpu.abilist),$(getprop ro.product.cpu.abilist32)"
    echo "$abilist" | grep -q "armeabi-v7a" || {
        compatibility_error "this package requires the armeabi-v7a compatibility runtime"
        return 1
    }
    [ -d "$VENDOR_SOUNDFX" ] || {
        compatibility_error "vendor soundfx directory is missing"
        return 1
    }
    [ -r "$CONFIG" ] || {
        compatibility_error "active vendor effects configuration is missing"
        return 1
    }
    command -v nsenter >/dev/null 2>&1 || {
        compatibility_error "Android nsenter is unavailable"
        return 1
    }
    command -v mount >/dev/null 2>&1 || {
        compatibility_error "Android mount is unavailable"
        return 1
    }
    command -v lshal >/dev/null 2>&1 || {
        compatibility_error "lshal is required to identify the Effects Factory"
        return 1
    }

    factory_line=$(lshal 2>/dev/null |
        grep -m 1 'android.hardware.audio.effect@[0-9][.][0-9]::IEffectsFactory/default')
    [ -n "$factory_line" ] || {
        compatibility_error "classic HIDL Effects Factory was not found"
        return 1
    }
    AUDIOFREEDOM_FACTORY_INTERFACE=$(echo "$factory_line" | awk '{
        for (field = 1; field <= NF; field++) {
            if ($field ~ /IEffectsFactory\/default/) { print $field; exit }
        }
    }')
    AUDIOFREEDOM_FACTORY_PID=$(echo "$factory_line" | awk '{
        for (field = NF; field >= 1; field--) {
            if ($field ~ /^[0-9]+$/) { print $field; exit }
        }
    }')
    [ -n "$AUDIOFREEDOM_FACTORY_PID" ] || {
        compatibility_error "could not identify the Effects Factory process"
        return 1
    }
    [ -r "/proc/$AUDIOFREEDOM_FACTORY_PID/maps" ] || {
        compatibility_error "Effects Factory process map is unavailable"
        return 1
    }
    if grep -q ' /vendor/lib64/' "/proc/$AUDIOFREEDOM_FACTORY_PID/maps"; then
        compatibility_error "the active Effects Factory is 64-bit; install a legacy64 profile"
        return 1
    fi
    grep -q ' /vendor/lib/' "/proc/$AUDIOFREEDOM_FACTORY_PID/maps" || {
        compatibility_error "could not verify a 32-bit vendor Effects Factory"
        return 1
    }

    AUDIOFREEDOM_CONFIG_HASH=$(sha256sum "$CONFIG" 2>/dev/null | awk '{print $1}')
    case "$AUDIOFREEDOM_CONFIG_HASH" in
        "$EXPECTED_CONFIG_HASH"|"$PATCHED_CONFIG_HASH"|"$PREVIOUS_PATCHED_CONFIG_HASH") ;;
        *)
            compatibility_error "vendor effects configuration is not in this signed profile"
            return 1
            ;;
    esac
    return 0
}

if [ "$1" = "check" ]; then
    if audiofreedom_check_legacy32; then
        echo "compatible=legacy32-hidl"
        echo "factory_interface=$AUDIOFREEDOM_FACTORY_INTERFACE"
        echo "factory_pid=$AUDIOFREEDOM_FACTORY_PID"
        echo "config_sha256=$AUDIOFREEDOM_CONFIG_HASH"
        exit 0
    fi
    echo "compatible=no"
    echo "reason=$AUDIOFREEDOM_COMPATIBILITY_ERROR"
    exit 1
fi
