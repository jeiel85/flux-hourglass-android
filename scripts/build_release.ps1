param(
    [string]$Version = "",
    [int]$VersionCode = 0,
    [switch]$SkipTests,
    [switch]$NoExport
)

$ErrorActionPreference = "Stop"

. (Join-Path $PSScriptRoot "lib\version-defaults.ps1")

$root = Resolve-Path (Join-Path $PSScriptRoot "..")
Push-Location $root

try {
    $resolvedVersion = if ($Version.Trim().Length -gt 0) {
        $Version.TrimStart("v")
    }
    else {
        Resolve-DefaultVersionName -BuildGradleKtsPath (Join-Path $root "app\build.gradle.kts")
    }

    foreach ($var in @("KEYSTORE_PATH", "STORE_PASSWORD", "KEY_ALIAS", "KEY_PASSWORD")) {
        $value = [System.Environment]::GetEnvironmentVariable($var, "Process")
        if ([string]::IsNullOrWhiteSpace($value)) {
            $altMap = @{
                "STORE_PASSWORD" = "RELEASE_STORE_PASSWORD"
                "KEY_ALIAS"      = "RELEASE_KEY_ALIAS"
                "KEY_PASSWORD"   = "RELEASE_KEY_PASSWORD"
            }
            if ($altMap.ContainsKey($var)) {
                $altValue = [System.Environment]::GetEnvironmentVariable($altMap[$var], "Process")
                if (-not [string]::IsNullOrWhiteSpace($altValue)) { continue }
            }
            if ($var -eq "KEYSTORE_PATH") {
                Write-Warning "$var is not set; falling back to my-upload-key.jks at repo root if present."
                continue
            }
            throw "Required environment variable '$var' is not set."
        }
    }

    # PowerShell parses `=` inside native-command args; pass each `-P` argument
    # as one quoted token so the Gradle command line stays intact.
    $gradleArgs = @("clean", "bundleRelease", "assembleRelease", "--console=plain")
    $gradleArgs += "-PVERSION_NAME=$resolvedVersion"
    if ($VersionCode -gt 0) {
        $gradleArgs += "-PVERSION_CODE=$VersionCode"
    }

    if (-not $SkipTests) {
        Write-Host "==> Running unit tests"
        & .\gradlew.bat test
        if ($LASTEXITCODE -ne 0) { throw "Unit tests failed." }
    }

    Write-Host "==> Building release artifacts ($resolvedVersion)"
    Write-Host "    .\gradlew.bat $($gradleArgs -join ' ')"
    $env:VERSION_NAME = $resolvedVersion
    if ($VersionCode -gt 0) { $env:VERSION_CODE = "$VersionCode" }
    & .\gradlew.bat @gradleArgs
    if ($LASTEXITCODE -ne 0) { throw "Gradle release build failed." }

    if (-not $NoExport) {
        Write-Host "==> Exporting release to desktop"
        & (Join-Path $PSScriptRoot "export-play-store-release.ps1") -Version $resolvedVersion -VersionCode $VersionCode
    }
}
finally {
    Pop-Location
}
