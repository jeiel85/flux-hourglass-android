param(
    [string]$Version = "",
    [string]$AabPath = "",
    [string]$ApkPath = "",
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
$desktop = Resolve-DesktopPath -ExplicitDesktopPath $DesktopPath

$sourceAab = Resolve-ArtifactPath -ExplicitPath $AabPath `
    -RelativeDir "app\build\outputs\bundle\release" `
    -Filter "*.aab" `
    -BuildCommand ".\gradlew.bat bundleRelease"

$sourceApk = Resolve-ArtifactPath -ExplicitPath $ApkPath `
    -RelativeDir "app\build\outputs\apk\release" `
    -Filter "*.apk" `
    -BuildCommand ".\gradlew.bat assembleRelease"

$targetAab = Join-Path $desktop "flux-hourglass-v$resolvedVersion.aab"
$targetApk = Join-Path $desktop "flux-hourglass-v$resolvedVersion.apk"

Copy-Item -LiteralPath $sourceAab -Destination $targetAab -Force
Copy-Item -LiteralPath $sourceApk -Destination $targetApk -Force

# Local mirror in repo so committers can quickly verify the build sizes
$buildOutputs = Join-Path $PSScriptRoot "..\.build-outputs"
New-Item -ItemType Directory -Force -Path $buildOutputs | Out-Null
Copy-Item -LiteralPath $sourceAab -Destination (Join-Path $buildOutputs "flux-hourglass-v$resolvedVersion.aab") -Force
Copy-Item -LiteralPath $sourceApk -Destination (Join-Path $buildOutputs "flux-hourglass-v$resolvedVersion.apk") -Force

$notesPath = Join-Path $PSScriptRoot "..\play_store\release_notes\v$resolvedVersion.txt"
if (Test-Path -LiteralPath $notesPath -PathType Leaf) {
    $targetNotes = Join-Path $desktop "flux-hourglass-v$resolvedVersion-release-notes.txt"
    Copy-Item -LiteralPath $notesPath -Destination $targetNotes -Force
    Write-Host "Exported Play Store release notes: $targetNotes"
}

Write-Host "Exported Play Store files:"
Write-Host "- $targetAab"
Write-Host "- $targetApk"
