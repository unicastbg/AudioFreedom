# Device profiles

Profiles record confirmed integration facts for rooted stock ROMs. They are installer
inputs, not scripts, and cannot modify a device by themselves.

The installer must still validate every match and path at installation time. An OTA can
change an effects factory, active XML path, library ABI, or existing processing chain
without changing the marketing device name. A failed validation must stop installation
without restarting audio services or modifying an overlay.

Source-built AOSP and LineageOS targets do not require a stock-ROM profile; they should
integrate the backend through product makefiles and device-owned effects configuration.
