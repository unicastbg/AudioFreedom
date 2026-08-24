param(
    [string]$EffectLibrary = ".\out\android-legacy-armv7\libaudiofreedomfx_legacy.so",
    [string]$SourceConfig = ".\diagnostics\portable\sony-xperia-1-iii-files\audio_effects.xml"
)

Write-Warning "build-sony-legacy-module.ps1 is deprecated; building the portable legacy32 profile."
& (Join-Path $PSScriptRoot "build-portable-legacy-module.ps1") `
    -EffectLibrary $EffectLibrary `
    -SourceConfig $SourceConfig `
    -ProfileName "sony-xperia-1-iii-reference"
