$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $PSScriptRoot
Import-Module (Join-Path $projectRoot "tools\AudioEffectsConfig.psm1") -Force

$source = Join-Path $PSScriptRoot "fixtures\audio_effects_minimal.xml"
$target = Join-Path ([IO.Path]::GetTempPath()) "audiofreedom-config-test-$PID.xml"

try {
    New-AudioFreedomEffectConfig -Source $source -Target $target

    $document = [Xml.XmlDocument]::new()
    $document.Load($target)
    $root = $document.DocumentElement
    $audioFreedomEffect = $document.SelectSingleNode(
        "//*[local-name()='effect' and @uuid='2f6e8c10-8d44-4b42-b110-16f3a729ef01']")
    $musicApply = $document.SelectSingleNode(
        "//*[local-name()='stream' and @type='music']/*[local-name()='apply' and @effect='audiofreedom']")
    if (-not $audioFreedomEffect -or -not $musicApply) {
        throw "AudioFreedom nodes were not generated"
    }

    $sectionNames = @($root.ChildNodes | Where-Object { $_ -is [Xml.XmlElement] } |
        ForEach-Object LocalName)
    if ([Array]::IndexOf($sectionNames, "postprocess") -gt
        [Array]::IndexOf($sectionNames, "deviceEffects")) {
        throw "postprocess was inserted after deviceEffects"
    }

    $duplicateRejected = $false
    try {
        New-AudioFreedomEffectConfig -Source $target -Target "$target.duplicate"
    } catch {
        $duplicateRejected = $_.Exception.Message -like "*UUID already exists*"
    }
    if (-not $duplicateRejected) {
        throw "A duplicate AudioFreedom installation was not rejected"
    }

    $legacyTarget = "$target.legacy"
    New-AudioFreedomEffectConfig -Source $source -Target $legacyTarget `
        -LibraryPath "libaudiofreedomfx_legacy.so" -IncludeTypeUuid $false
    $legacyDocument = [Xml.XmlDocument]::new()
    $legacyDocument.Load($legacyTarget)
    $legacyEffect = $legacyDocument.SelectSingleNode(
        "//*[local-name()='effect' and @name='audiofreedom']")
    if (-not $legacyEffect -or $legacyEffect.HasAttribute("type") -or
        $legacyEffect.GetAttribute("library") -ne "audiofreedom") {
        throw "Legacy effect registration was not generated correctly"
    }

    $registerOnlyTarget = "$target.register-only"
    New-AudioFreedomEffectConfig -Source $source -Target $registerOnlyTarget `
        -LibraryPath "libaudiofreedomfx_legacy.so" -IncludeTypeUuid $false `
        -AttachToStream $false
    $registerOnlyDocument = [Xml.XmlDocument]::new()
    $registerOnlyDocument.Load($registerOnlyTarget)
    $registerOnlyEffect = $registerOnlyDocument.SelectSingleNode(
        "//*[local-name()='effect' and @name='audiofreedom']")
    $registerOnlyApply = $registerOnlyDocument.SelectSingleNode(
        "//*[local-name()='apply' and @effect='audiofreedom']")
    if (-not $registerOnlyEffect -or $registerOnlyApply) {
        throw "Register-only legacy configuration created an automatic stream attachment"
    }

    Write-Output "Audio effects configuration tests passed"
} finally {
    Remove-Item -LiteralPath $target -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath "$target.duplicate" -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath "$target.legacy" -Force -ErrorAction SilentlyContinue
    Remove-Item -LiteralPath "$target.register-only" -Force -ErrorAction SilentlyContinue
}
