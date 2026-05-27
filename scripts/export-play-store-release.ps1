param(
    [string]$Version = "",
    [string]$AabPath = "",
    [string]$DesktopPath = ""
)

$ErrorActionPreference = "Stop"

function Resolve-Version {
    param([string]$ExplicitVersion)

    if ($ExplicitVersion.Trim().Length -gt 0) {
        return $ExplicitVersion.TrimStart("v")
    }

    $buildFile = Join-Path $PSScriptRoot "..\app\build.gradle.kts"
    $versionLine = Select-String -Path $buildFile -Pattern 'versionName\s*=' | Select-Object -First 1
    if ($null -eq $versionLine -or $versionLine.Line -notmatch '"([^"]+)"') {
        throw "Could not resolve versionName from app/build.gradle.kts"
    }

    return $Matches[1]
}

function Resolve-VersionCode {
    $buildFile = Join-Path $PSScriptRoot "..\app\build.gradle.kts"
    $codeLine = Select-String -Path $buildFile -Pattern 'versionCode\s*=' | Select-Object -First 1
    if ($null -eq $codeLine) {
        throw "Could not resolve versionCode from app/build.gradle.kts"
    }
    if ($codeLine.Line -match '\?:\s*(\d+)') {
        return [int]$Matches[1]
    }
    if ($codeLine.Line -match '=\s*(\d+)') {
        return [int]$Matches[1]
    }
    throw "Could not parse versionCode from line: $($codeLine.Line)"
}

function Resolve-DesktopPath {
    param([string]$ExplicitDesktopPath)

    $candidates = @()
    if ($ExplicitDesktopPath.Trim().Length -gt 0) {
        $candidates += $ExplicitDesktopPath
    }
    if ($env:OneDrive) {
        $candidates += (Join-Path $env:OneDrive "바탕 화면")
        $candidates += (Join-Path $env:OneDrive "Desktop")
    }
    $shellDesktop = [Environment]::GetFolderPath("Desktop")
    if ($shellDesktop) {
        $candidates += $shellDesktop
    }
    $candidates += (Join-Path $HOME "Desktop")

    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Container)) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    if ($shellDesktop) {
        New-Item -ItemType Directory -Force -Path $shellDesktop | Out-Null
        return (Resolve-Path -LiteralPath $shellDesktop).Path
    }

    throw "Could not resolve a Desktop path."
}

function Resolve-ArtifactPath {
    param(
        [string]$ExplicitPath,
        [string]$RelativeDir,
        [string]$Filter,
        [string]$BuildCommand
    )

    if ($ExplicitPath.Trim().Length -gt 0) {
        if (-not (Test-Path -LiteralPath $ExplicitPath -PathType Leaf)) {
            throw "Artifact not found: $ExplicitPath"
        }
        return (Resolve-Path -LiteralPath $ExplicitPath).Path
    }

    $dir = Join-Path $PSScriptRoot "..\$RelativeDir"
    $artifact = Get-ChildItem -Path $dir -Filter $Filter -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if ($null -eq $artifact) {
        throw "Release artifact ($Filter) not found in $RelativeDir. Build it first: $BuildCommand"
    }

    return $artifact.FullName
}

$resolvedVersion = Resolve-Version -ExplicitVersion $Version
$resolvedCode = Resolve-VersionCode
$desktop = Resolve-DesktopPath -ExplicitDesktopPath $DesktopPath

# Only the AAB is exported to the desktop. The APK is intentionally NOT
# copied — Play Console upload uses the AAB, and the APK lives only inside
# `app/build/outputs/apk/release/` for ad-hoc sideloading. The Play Console
# release notes file is what users actually need on the desktop.
$sourceAab = Resolve-ArtifactPath -ExplicitPath $AabPath `
    -RelativeDir "app\build\outputs\bundle\release" `
    -Filter "*.aab" `
    -BuildCommand ".\gradlew.bat bundleRelease"

$stem = "flux-hourglass-v$resolvedVersion-vc$resolvedCode"
$targetAab = Join-Path $desktop "$stem.aab"

Copy-Item -LiteralPath $sourceAab -Destination $targetAab -Force

# Release notes must already exist in BCP-47 multi-language form. The file
# is copied verbatim — Play Console will split the <ko-KR>/<en-US> blocks.
$notesPath = Join-Path $PSScriptRoot "..\play_store\release_notes\v$resolvedVersion.txt"
if (-not (Test-Path -LiteralPath $notesPath -PathType Leaf)) {
    throw "Play Store release notes not found: $notesPath. " +
        "Create it with the <ko-KR>...</ko-KR><en-US>...</en-US> format documented in play_store/release_notes/README.md."
}

$notesContent = Get-Content -LiteralPath $notesPath -Raw -Encoding UTF8
if ($notesContent -notmatch '<ko-KR>' -or $notesContent -notmatch '<en-US>') {
    throw "Release notes at $notesPath must contain both <ko-KR>...</ko-KR> and <en-US>...</en-US> blocks. " +
        "See play_store/release_notes/README.md for the required format."
}

$targetNotes = Join-Path $desktop "$stem-release-notes.txt"
Copy-Item -LiteralPath $notesPath -Destination $targetNotes -Force

# Remove any stale APK that an older version of this script left behind.
$staleApk = Join-Path $desktop "$stem.apk"
if (Test-Path -LiteralPath $staleApk -PathType Leaf) {
    Remove-Item -LiteralPath $staleApk -Force
    Write-Host "Removed stale desktop APK: $staleApk"
}

Write-Host "Exported Play Store files:"
Write-Host "- $targetAab"
Write-Host "- $targetNotes"
