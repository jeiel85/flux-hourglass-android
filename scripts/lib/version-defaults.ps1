function Resolve-DefaultVersionName {
    # Target the findProperty("VERSION_NAME") fallback line specifically —
    # a bare `versionName\s*=` also matches unrelated local variables like
    # `resolvedVersionName = ...` as a substring coincidence, which this
    # helper exists to avoid repeating (and re-breaking) in two scripts.
    param([string]$BuildGradleKtsPath)

    $versionLine = Select-String -Path $BuildGradleKtsPath -Pattern 'findProperty\("VERSION_NAME"\).*\?:\s*"([^"]+)"' | Select-Object -First 1
    if ($null -eq $versionLine) {
        throw "Could not resolve default versionName from $BuildGradleKtsPath"
    }
    return $versionLine.Matches[0].Groups[1].Value
}
