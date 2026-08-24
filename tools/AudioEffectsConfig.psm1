function New-AudioFreedomEffectConfig {
    [CmdletBinding()]
    param(
        [Parameter(Mandatory)] [string]$Source,
        [Parameter(Mandatory)] [string]$Target,
        [string]$StreamType = "music",
        [string]$InsertAfterEffect = "",
        [string]$LibraryPath = "libaudiofreedomfx.so",
        [string]$ImplementationUuid = "2f6e8c10-8d44-4b42-b110-16f3a729ef01",
        [string]$TypeUuid = "a7e03c90-7c3d-4f48-9c8d-497c8f1b1201",
        [bool]$IncludeTypeUuid = $true,
        [bool]$AttachToStream = $true
    )

    $document = [Xml.XmlDocument]::new()
    $document.PreserveWhitespace = $false
    $document.Load($Source)

    $root = $document.DocumentElement
    if (-not $root -or $root.LocalName -ne "audio_effects_conf") {
        throw "$Source is not an Android audio effects configuration"
    }
    if ($document.SelectSingleNode("//*[local-name()='effect' and @uuid='$ImplementationUuid']")) {
        throw "AudioFreedom UUID already exists in $Source"
    }
    if ($document.SelectSingleNode("//*[local-name()='library' and @name='audiofreedom']") -or
        $document.SelectSingleNode("//*[local-name()='effect' and @name='audiofreedom']")) {
        throw "AudioFreedom element names already exist in $Source"
    }

    $libraries = $root.SelectSingleNode("*[local-name()='libraries']")
    $effects = $root.SelectSingleNode("*[local-name()='effects']")
    if (-not $libraries -or -not $effects) {
        throw "$Source does not contain libraries and effects sections"
    }

    $namespaceUri = $root.NamespaceURI
    function New-ConfigElement([string]$Name) {
        if ($namespaceUri) {
            return $document.CreateElement($Name, $namespaceUri)
        }
        return $document.CreateElement($Name)
    }

    $musicStream = $null
    if ($AttachToStream) {
        $postprocess = $root.SelectSingleNode("*[local-name()='postprocess']")
        if (-not $postprocess) {
            $postprocess = New-ConfigElement "postprocess"
            $deviceEffects = $root.SelectSingleNode("*[local-name()='deviceEffects']")
            if ($deviceEffects) {
                [void]$root.InsertBefore($postprocess, $deviceEffects)
            } else {
                [void]$root.AppendChild($postprocess)
            }
        }

        $streams = @($postprocess.SelectNodes("*[local-name()='stream' and @type='$StreamType']"))
        if ($streams.Count -gt 1) {
            throw "$Source contains duplicate $StreamType post-process streams"
        }
        if ($streams.Count -eq 0) {
            $musicStream = New-ConfigElement "stream"
            $musicStream.SetAttribute("type", $StreamType)
            [void]$postprocess.AppendChild($musicStream)
        } else {
            $musicStream = $streams[0]
        }
    }

    $libraryNode = New-ConfigElement "library"
    $libraryNode.SetAttribute("name", "audiofreedom")
    $libraryNode.SetAttribute("path", $LibraryPath)
    [void]$libraries.AppendChild($libraryNode)

    $effectNode = New-ConfigElement "effect"
    $effectNode.SetAttribute("name", "audiofreedom")
    $effectNode.SetAttribute("library", "audiofreedom")
    $effectNode.SetAttribute("uuid", $ImplementationUuid)
    if ($IncludeTypeUuid) {
        $effectNode.SetAttribute("type", $TypeUuid)
    }
    [void]$effects.AppendChild($effectNode)

    if ($AttachToStream) {
        $applyNode = New-ConfigElement "apply"
        $applyNode.SetAttribute("effect", "audiofreedom")
        if ($InsertAfterEffect) {
            $anchor = $musicStream.SelectSingleNode(
                "*[local-name()='apply' and @effect='$InsertAfterEffect']")
            if (-not $anchor) {
                throw "$Source does not contain the requested insertion anchor $InsertAfterEffect"
            }
            [void]$musicStream.InsertAfter($applyNode, $anchor)
        } else {
            [void]$musicStream.AppendChild($applyNode)
        }
    }

    $settings = [Xml.XmlWriterSettings]::new()
    $settings.Indent = $true
    $settings.IndentChars = "    "
    $settings.NewLineChars = "`n"
    $settings.NewLineHandling = [Xml.NewLineHandling]::Replace
    $settings.Encoding = [Text.UTF8Encoding]::new($false)
    $writer = [Xml.XmlWriter]::Create($Target, $settings)
    try {
        $document.Save($writer)
    } finally {
        $writer.Dispose()
    }

    $validation = [Xml.XmlDocument]::new()
    $validation.Load($Target)
    $effect = $validation.SelectSingleNode(
        "//*[local-name()='effect' and @uuid='$ImplementationUuid']")
    $apply = $validation.SelectSingleNode(
        "//*[local-name()='stream' and @type='$StreamType']/*[local-name()='apply' and @effect='audiofreedom']")
    if (-not $effect -or ($AttachToStream -and -not $apply) -or
        (-not $AttachToStream -and $apply)) {
        throw "Generated config failed validation: $Target"
    }
}

Export-ModuleMember -Function New-AudioFreedomEffectConfig
