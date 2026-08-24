#!/bin/sh
set -eu

project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
aosp_root=${1:-$(pwd)}
destination="$aosp_root/hardware/interfaces/audio/aidl/default/audiofreedom"

if [ ! -d "$aosp_root/build/soong" ]; then
    echo "Not an AOSP checkout: $aosp_root" >&2
    exit 1
fi

version_file="$aosp_root/build/make/core/version_defaults.mk"
if [ -r "$version_file" ]; then
    sdk_version=$(sed -n \
        's/^[[:space:]]*PLATFORM_SDK_VERSION[[:space:]]*:=[[:space:]]*\([0-9][0-9]*\).*/\1/p' \
        "$version_file" | head -n 1)
    if [ -n "$sdk_version" ] && [ "$sdk_version" != "36" ]; then
        echo "AudioFreedom controller requires Android API 36; found API $sdk_version" >&2
        exit 1
    fi
fi

mkdir -p "$destination"
cp "$project_dir/platform/aidl/AudioFreedomEffect.cpp" "$destination/"
cp "$project_dir/platform/aidl/AudioFreedomEffect.h" "$destination/"
cp "$project_dir/platform/aidl/Android.bp.stage" "$destination/Android.bp"

echo "Staged the AudioFreedom AIDL adapter in: $destination"
echo "The AudioFreedom repository must remain available as vendor/audiofreedom."
echo "Inherit vendor/audiofreedom/platform/rom/audiofreedom_product.mk from the product."
echo "Then build: m libaudiofreedomfx audiofreedom-controller"
