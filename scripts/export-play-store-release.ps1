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

    # Resolves to the user's "Desktop\Build" folder. This project — and the
    # other Android apps on the same machine — collects Play Console-ready
    # AAB and release-notes files under a single Build/ subfolder rather
    # than the desktop root, so that other desktop chrome (icons, daily
    # files) stays uncluttered. The redirected OneDrive desktop is
    # respected via [Environment]::GetFolderPath('Desktop').
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

    $desktopRoot = $null
    foreach ($candidate in $candidates) {
        if ($candidate -and (Test-Path -LiteralPath $candidate -PathType Container)) {
            $desktopRoot = (Resolve-Path -LiteralPath $candidate).Path
            break
        }
    }
    if (-not $desktopRoot -and $shellDesktop) {
        New-Item -ItemType Directory -Force -Path $shellDesktop | Out-Null
        $desktopRoot = (Resolve-Path -LiteralPath $shellDesktop).Path
    }
    if (-not $desktopRoot) {
        throw "Could not resolve a Desktop path."
    }

    $buildDir = Join-Path $desktopRoot "Build"
    if (-not (Test-Path -LiteralPath $buildDir -PathType Container)) {
        New-Item -ItemType Directory -Force -Path $buildDir | Out-Null
    }
    return (Resolve-Path -LiteralPath $buildDir).Path
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

# Convention (locked in — see RELEASE.md §5):
#   Desktop\Build\ receives EXACTLY two files per version:
#     1. flux-hourglass-vX.Y.Z-vcN.aab          ← Play Console upload
#     2. flux-hourglass-vX.Y.Z-vcN-release-notes.txt  ← Play Console notes
#   APKs are NEVER copied to the desktop. They live only inside
#   app/build/outputs/apk/release/ for ad-hoc sideloading or for the
#   GitHub Release page (auto-published by .github/workflows/release.yml).
#   If a stale APK ever lands on the desktop it is sent to the recycle
#   bin a few lines below.
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

# Remove any stale APK that an older script (or a manual mirror) left
# behind. Looks in both Desktop\Build\ (the current target) and the
# Desktop root (where pre-1.3.0 scripts used to drop files). Files are
# sent to the recycle bin so they remain recoverable for a few weeks.
Add-Type -AssemblyName Microsoft.VisualBasic | Out-Null
$desktopRoot = Split-Path -Parent $desktop
$apkCandidates = @(
    (Join-Path $desktop "$stem.apk"),
    (Join-Path $desktopRoot "$stem.apk")
)
foreach ($candidate in $apkCandidates) {
    if (Test-Path -LiteralPath $candidate -PathType Leaf) {
        [Microsoft.VisualBasic.FileIO.FileSystem]::DeleteFile(
            $candidate,
            'OnlyErrorDialogs',
            'SendToRecycleBin'
        )
        Write-Host "Sent stale APK to recycle bin: $candidate"
    }
}

Write-Host "Exported Play Store files:"
Write-Host "- $targetAab"
Write-Host "- $targetNotes"
