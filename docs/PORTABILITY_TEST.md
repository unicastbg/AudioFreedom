# Sony and Redmi portability diagnostics

Do not install the Xiaomi 15 Ultra module on another model. Its paths and source-file
hash guards are intentionally device-specific.

For each phone:

1. Enable USB debugging and connect it with ADB.
2. Run `tools/collect-device-audio.ps1 -Serial <serial> -Label <short-name>`.
3. Record Android version, ROM build, root solution, and whether the phone uses stock or
   custom firmware.
4. Do not mount or replace vendor files until the collected configuration and active
   Effects HAL have been reviewed.
5. After a device-specific module exists, test speaker, wired headset, USB DAC,
   Bluetooth SBC/AAC/LDAC, local playback, streaming playback, and call bypass.

The collector only reads diagnostics, writes temporary files under `/data/local/tmp`,
pulls the report, and removes its temporary files. Root is not required for the first
pass, although some vendor paths may be hidden on an unrooted build.
