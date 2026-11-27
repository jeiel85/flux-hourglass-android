# 전역 스킬 play-publish(~/.claude/skills/play-publish/play-publish.ps1)를 CI에서 쓰려고 복사한 것입니다(.github/workflows/play-release.yml).
# 고칠 때는 원본과 함께 맞춥니다. 비밀값은 담지 않습니다 — 서비스 계정 키는 환경 변수 PLAY_SERVICE_ACCOUNT_JSON으로 받습니다.
#requires -Version 7.0
<#
.SYNOPSIS
    서명된 AAB 를 Google Play Console 트랙에 올리거나(업로드), 트랙 상태를 읽는다(-ShowTracks). 모든 안드로이드 앱 공용.

.DESCRIPTION
    Google Play Developer API v3 를 서드파티 의존성 없이 부른다(OAuth2 JWT → edits → bundles → tracks → commit).
    실패하면 만든 edit 를 지운다. 저장소마다 다른 일(AAB 빌드, 버전 규칙, 출시 노트 작성)은 호출하는 쪽이 한다.

    서비스 계정 키 찾는 순서: -ServiceAccountPath → env PLAY_SERVICE_ACCOUNT_JSON(내용) →
    env PLAY_SERVICE_ACCOUNT_FILE(경로) → %USERPROFILE%\.play\play-service-account.json
    (%USERPROFILE%\.keystore 는 쓰지 않는다 — keytool 이 그 경로를 기본 키스토어 파일로 읽어 모든 keytool 명령이 깨진다.)

.EXAMPLE
    pwsh play-publish.ps1 -PackageName com.jeiel85.doran -ShowTracks

.EXAMPLE
    pwsh play-publish.ps1 -PackageName com.jeiel85.doran -AabPath app\build\outputs\bundle\playRelease\app-play-release.aab `
        -NotesKoFile notes-ko.txt -NotesEnFile notes-en.txt -DryRun
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$PackageName,
    [string]$AabPath = '',
    [string]$Track = 'alpha',
    [ValidateSet('completed', 'draft', 'inProgress', 'halted')][string]$Status = 'completed',
    [ValidateRange(0.0, 1.0)][double]$UserFraction = 0.0,
    [long]$ExpectedVersionCode = 0,
    [string]$ExpectedUploadCertSha256 = '',
    [string]$ReleaseName = '',
    [string]$NotesKo = '',
    [string]$NotesEn = '',
    [string]$NotesKoFile = '',
    [string]$NotesEnFile = '',
    [string]$ServiceAccountPath = '',
    [string]$VersionName = '',
    [switch]$ChangesNotSentForReview,
    [switch]$ReplaceDraft,
    [switch]$ShowTracks,
    [switch]$DryRun
)

$ErrorActionPreference = 'Stop'

$script:GoogleTokenUri = 'https://oauth2.googleapis.com/token'
$script:ApiBase = 'https://androidpublisher.googleapis.com'

function ConvertTo-Base64Url([byte[]]$Bytes) {
    return [Convert]::ToBase64String($Bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

function Resolve-ServiceAccount {
    param([string]$ExplicitPath)
    $path = $null
    if ($ExplicitPath) { $path = $ExplicitPath }
    elseif ($env:PLAY_SERVICE_ACCOUNT_JSON) {
        return [pscustomobject]@{ Source = 'env:PLAY_SERVICE_ACCOUNT_JSON'; Data = ($env:PLAY_SERVICE_ACCOUNT_JSON | ConvertFrom-Json) }
    }
    elseif ($env:PLAY_SERVICE_ACCOUNT_FILE) { $path = $env:PLAY_SERVICE_ACCOUNT_FILE }
    else {
        $path = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.play\play-service-account.json'
    }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "서비스 계정 JSON 키가 없습니다: $path (SKILL.md '키' 절 참고)"
    }
    $data = [System.IO.File]::ReadAllText($path, [Text.Encoding]::UTF8) | ConvertFrom-Json
    return [pscustomobject]@{ Source = $path; Data = $data }
}

function New-ServiceAccountJwt([psobject]$Sa) {
    if ($Sa.type -ne 'service_account') { throw "서비스 계정 JSON 의 type 이 'service_account' 가 아닙니다." }
    if (-not $Sa.client_email -or -not $Sa.private_key) { throw '서비스 계정 JSON 에 client_email 또는 private_key 가 없습니다.' }
    $tokenUri = if ($Sa.token_uri) { [string]$Sa.token_uri } else { $script:GoogleTokenUri }
    if ($tokenUri -ne $script:GoogleTokenUri) { throw "token_uri 가 Google OAuth2 엔드포인트가 아닙니다: $tokenUri" }
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $header = [ordered]@{ alg = 'RS256'; typ = 'JWT' }
    if ($Sa.private_key_id) { $header.kid = [string]$Sa.private_key_id }
    $claims = [ordered]@{
        iss = [string]$Sa.client_email; scope = 'https://www.googleapis.com/auth/androidpublisher'
        aud = $script:GoogleTokenUri; iat = $now; exp = $now + 3600
    }
    $signingInput = (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes(($header | ConvertTo-Json -Compress)))) + '.' +
                    (ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes(($claims | ConvertTo-Json -Compress))))
    $rsa = [System.Security.Cryptography.RSA]::Create()
    try {
        $rsa.ImportFromPem([string]$Sa.private_key)
        $sig = $rsa.SignData([Text.Encoding]::ASCII.GetBytes($signingInput),
            [System.Security.Cryptography.HashAlgorithmName]::SHA256, [System.Security.Cryptography.RSASignaturePadding]::Pkcs1)
        return "$signingInput.$(ConvertTo-Base64Url $sig)"
    }
    finally { $rsa.Dispose() }
}

function Get-ErrorHint([int]$Code, [string]$Body) {
    if ($Body -match 'Only releases with status draft may be created on draft app') { return '아직 한 번도 게시하지 않은 앱입니다. -Status draft 로 올린 뒤 Play Console 에서 검토 제출하세요.' }
    if ($Body -match 'already been used') { return '이 versionCode 는 이미 올라간 적이 있습니다. versionCode 를 올려 다시 빌드하세요.' }
    if ($Body -match 'changesNotSentForReview') { return 'Play Console 에 검토 제출 전 변경이 있습니다. -ChangesNotSentForReview 로 올린 뒤 콘솔에서 함께 제출하세요.' }
    if ($Body -match 'Package not found|applicationNotFound|No application was found') { return "Play Console 에 $PackageName 앱이 없거나 서비스 계정에 이 앱 권한이 없습니다." }
    if ($Code -eq 403 -or $Body -match 'PERMISSION_DENIED|sufficient permissions') { return "Play Console '사용자 및 권한'에서 서비스 계정에 $PackageName 앱 출시 권한이 있는지 확인하세요." }
    if ($Code -eq 401 -or $Body -match 'UNAUTHENTICATED|invalid_grant') { return '서비스 계정 인증 실패 — 키가 폐기됐거나 API 가 꺼졌을 수 있습니다.' }
    return ''
}

function Invoke-Play {
    param([System.Net.Http.HttpClient]$Http, [string]$Method, [string]$Uri, [string]$Token = '', [System.Net.Http.HttpContent]$Content = $null)
    $req = [System.Net.Http.HttpRequestMessage]::new([System.Net.Http.HttpMethod]::new($Method), $Uri)
    try {
        if ($Token) { $req.Headers.Authorization = [System.Net.Http.Headers.AuthenticationHeaderValue]::new('Bearer', $Token) }
        if ($Content) { $req.Content = $Content }
        $resp = $Http.SendAsync($req).GetAwaiter().GetResult()
        try {
            $body = $resp.Content.ReadAsStringAsync().GetAwaiter().GetResult()
            if (-not $resp.IsSuccessStatusCode) {
                $msg = $body
                try { $j = $body | ConvertFrom-Json; if ($j.error.message) { $msg = [string]$j.error.message } elseif ($j.error_description) { $msg = "$($j.error): $($j.error_description)" } } catch { }
                $hint = Get-ErrorHint ([int]$resp.StatusCode) $body
                throw ("Google Play API 실패 (HTTP $([int]$resp.StatusCode), $Method $($Uri -replace '\?.*$','')): $msg" + $(if ($hint) { "`n안내: $hint" } else { '' }))
            }
            if ([string]::IsNullOrWhiteSpace($body)) { return $null }
            return $body | ConvertFrom-Json
        }
        finally { $resp.Dispose() }
    }
    finally { $req.Dispose() }
}

function New-JsonContent([string]$Json) { [System.Net.Http.StringContent]::new($Json, [Text.Encoding]::UTF8, 'application/json') }

function Read-Notes([string]$Text, [string]$File, [string]$Lang) {
    if ($File) {
        if (-not (Test-Path -LiteralPath $File -PathType Leaf)) { throw "$Lang 노트 파일이 없습니다: $File" }
        $Text = [System.IO.File]::ReadAllText($File, [Text.Encoding]::UTF8)
    }
    $Text = ($Text -replace "`r`n", "`n").Trim()
    if ($Text.Length -gt 500) { throw "$Lang 출시 노트가 500자를 넘습니다: $($Text.Length)자" }
    return $Text
}

function Test-Aab([string]$Path, [string]$ExpectedSha) {
    if (-not (Test-Path -LiteralPath $Path -PathType Leaf)) { throw "AAB 파일이 없습니다: $Path" }
    $size = (Get-Item -LiteralPath $Path).Length
    if ($size -le 0 -or $size -gt 150MB) { throw "AAB 크기가 올바르지 않습니다: $size bytes" }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($Path)
    try {
        $names = @($zip.Entries | ForEach-Object { $_.FullName.Replace('\', '/') })
        foreach ($req in 'BundleConfig.pb', 'base/manifest/AndroidManifest.xml') {
            if ($names -notcontains $req) { throw "올바른 .aab 가 아닙니다('$req' 없음): $Path" }
        }
    }
    finally { $zip.Dispose() }

    $keytool = (Get-Command keytool -ErrorAction SilentlyContinue).Source
    if (-not $keytool -and $env:JAVA_HOME) { $keytool = Join-Path $env:JAVA_HOME 'bin' ($IsWindows ? 'keytool.exe' : 'keytool') }
    if (-not $keytool -or -not (Test-Path -LiteralPath $keytool)) { throw 'keytool 을 찾을 수 없습니다(JDK 필요).' }
    $out = (& $keytool '-J-Duser.language=en' '-printcert' '-jarfile' $Path 2>&1) -join "`n"
    $m = [regex]::Match($out, 'SHA256:\s*([0-9A-Fa-f]{2}(?::[0-9A-Fa-f]{2}){31})')
    if (-not $m.Success) { throw 'AAB 서명 지문을 읽지 못했습니다(서명 안 된 번들일 수 있음).' }
    $sha = $m.Groups[1].Value.ToUpperInvariant()
    if ($ExpectedSha -and $sha -ne $ExpectedSha.ToUpperInvariant()) { throw "AAB 서명 인증서가 기대한 업로드 키와 다릅니다: $sha" }
    return [pscustomobject]@{ Path = (Resolve-Path -LiteralPath $Path).Path; Size = $size; CertSha256 = $sha }
}

if ($MyInvocation.InvocationName -eq '.') { return }

$prevEnc = [Console]::OutputEncoding
[Console]::OutputEncoding = [Text.UTF8Encoding]::new($false)
$handler = [System.Net.Http.HttpClientHandler]::new(); $handler.AllowAutoRedirect = $false; $handler.UseCookies = $false
$http = [System.Net.Http.HttpClient]::new($handler); $http.Timeout = [TimeSpan]::FromMinutes(10)
$token = ''; $editId = $null; $committed = $false
try {
    if ($PackageName -notmatch '^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$') { throw "패키지명 형식이 아닙니다: $PackageName" }
    if ($Track -notmatch '^[A-Za-z0-9 _.-]{1,64}$') { throw "트랙 이름이 올바르지 않습니다: $Track" }
    if ($Status -eq 'inProgress' -and ($UserFraction -le 0 -or $UserFraction -ge 1)) { throw 'inProgress 는 0 < UserFraction < 1 이 필요합니다.' }
    if (($Status -in @('completed', 'draft')) -and $UserFraction -gt 0) { throw "$Status 에는 UserFraction 을 줄 수 없습니다." }

    $sa = Resolve-ServiceAccount $ServiceAccountPath
    $jwt = New-ServiceAccountJwt $sa.Data

    $aab = $null; $ko = ''; $en = ''
    # -AabPath 없이 -ExpectedVersionCode 만 주면 이미 올라간 번들을 다른 트랙에 붙인다(업로드 없음).
    $assignOnly = (-not $ShowTracks) -and (-not $AabPath) -and $ExpectedVersionCode -gt 0
    if (-not $ShowTracks) {
        if (-not $AabPath -and -not $assignOnly) { throw '-AabPath 가 필요합니다(이미 올린 번들을 트랙에 붙이려면 -ExpectedVersionCode 만, 조회는 -ShowTracks).' }
        if (-not $assignOnly) { $aab = Test-Aab $AabPath $ExpectedUploadCertSha256 }
        $ko = Read-Notes $NotesKo $NotesKoFile 'ko-KR'
        $en = Read-Notes $NotesEn $NotesEnFile 'en-US'
        if ($DryRun -and $assignOnly) {
            Write-Host "[DryRun] $PackageName versionCode $ExpectedVersionCode 를 트랙 $Track ($Status)에 붙임(업로드 없음). 노트 ko $($ko.Length)자 / en $($en.Length)자. 서비스 계정 JWT 서명 OK."
            return
        }
        if ($DryRun) {
            Write-Host "[DryRun] $PackageName → 트랙 $Track ($Status)"
            Write-Host "  AAB: $($aab.Path) ($([math]::Round($aab.Size / 1MB, 2)) MB)"
            Write-Host "  서명 SHA-256: $($aab.CertSha256)$(if ($ExpectedUploadCertSha256) { ' [일치]' } else { ' (기대값 미지정)' })"
            Write-Host "  서비스 계정: $($sa.Data.client_email) (JWT 서명 OK, 키: $($sa.Source))"
            Write-Host "  노트 ko-KR ($($ko.Length)자): $($ko -replace "`n", ' / ')"
            Write-Host "  노트 en-US ($($en.Length)자): $($en -replace "`n", ' / ')"
            Write-Host '  (DryRun 은 Play API 를 부르지 않습니다. 권한·트랙 확인은 -ShowTracks)'
            return
        }
    }

    $fields = [System.Collections.Generic.Dictionary[string, string]]::new()
    $fields.Add('grant_type', 'urn:ietf:params:oauth:grant-type:jwt-bearer')
    $fields.Add('assertion', $jwt)
    $form = [System.Net.Http.FormUrlEncodedContent]::new($fields)
    try { $token = [string](Invoke-Play $http 'POST' $script:GoogleTokenUri -Content $form).access_token } finally { $form.Dispose() }
    if (-not $token) { throw 'OAuth2 토큰 응답에 access_token 이 없습니다.' }

    $appBase = "$($script:ApiBase)/androidpublisher/v3/applications/$([Uri]::EscapeDataString($PackageName))"
    $c = New-JsonContent '{}'
    try { $edit = Invoke-Play $http 'POST' "$appBase/edits" $token $c } finally { $c.Dispose() }
    $editId = [Uri]::EscapeDataString([string]$edit.id)

    if ($ShowTracks) {
        $tracks = Invoke-Play $http 'GET' "$appBase/edits/$editId/tracks" $token
        Write-Host "[$PackageName] 트랙 상태 (서비스 계정: $($sa.Data.client_email))"
        foreach ($t in @($tracks.tracks)) {
            $rels = @($t.releases | Where-Object { $_ -and $_.versionCodes })
            if ($rels.Count -eq 0) { Write-Host "  $($t.track): (버전 없음)"; continue }
            foreach ($r in $rels) {
                $codes = @($r.versionCodes) -join ','
                $frac = if ($r.userFraction) { " $([math]::Round($r.userFraction * 100, 1))%" } else { '' }
                Write-Host "  $($t.track): $($r.name) [versionCode $codes] $($r.status)$frac"
            }
        }
        return
    }

    # 트랙 PUT 은 그 트랙의 버전 목록을 통째로 바꾼다 — 콘솔에서 만들어 둔 초안이 있으면 말없이 사라지므로 먼저 막는다.
    $current = $null
    try { $current = Invoke-Play $http 'GET' "$appBase/edits/$editId/tracks/$([Uri]::EscapeDataString($Track))" $token } catch { if ($_.Exception.Message -notmatch 'HTTP 404') { throw } }
    $drafts = @($current.releases | Where-Object { $_ -and $_.status -eq 'draft' -and $_.versionCodes })
    if ($drafts.Count -gt 0 -and -not $ReplaceDraft) {
        $d = ($drafts | ForEach-Object { "$($_.name) [versionCode $(@($_.versionCodes) -join ',')]" }) -join ', '
        throw "트랙 $Track 에 초안 버전이 있습니다: $d. 올리면 이 초안이 사라집니다. 초안을 버려도 되면 -ReplaceDraft 를 붙이세요."
    }

    if ($assignOnly) {
        $bundles = Invoke-Play $http 'GET' "$appBase/edits/$editId/bundles" $token
        if (-not (@($bundles.bundles) | Where-Object { [long]$_.versionCode -eq $ExpectedVersionCode })) {
            throw "versionCode $ExpectedVersionCode 번들이 Play 에 없습니다. -AabPath 로 먼저 올리세요."
        }
        $versionCode = $ExpectedVersionCode
    }
    else {
        $bytes = [System.Net.Http.ByteArrayContent]::new([System.IO.File]::ReadAllBytes($aab.Path))
        $bytes.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new('application/octet-stream')
        try {
            $bundle = Invoke-Play $http 'POST' "$($script:ApiBase)/upload/androidpublisher/v3/applications/$([Uri]::EscapeDataString($PackageName))/edits/$editId/bundles?uploadType=media" $token $bytes
        }
        finally { $bytes.Dispose() }
        $versionCode = [long]$bundle.versionCode
        if ($ExpectedVersionCode -gt 0 -and $versionCode -ne $ExpectedVersionCode) {
            throw "업로드한 AAB 의 versionCode($versionCode)가 기대값($ExpectedVersionCode)과 다릅니다."
        }
    }

    $release = [ordered]@{ versionCodes = @([string]$versionCode); status = $Status }
    # 콘솔 수동 업로드와 같은 "versionCode (versionName)" 꼴. 둘 다 없으면 Play 가 versionName 으로 붙인다.
    if ($ReleaseName) { $release.name = $ReleaseName }
    elseif ($VersionName) { $release.name = "$versionCode ($VersionName)" }
    if ($Status -eq 'inProgress' -or ($Status -eq 'halted' -and $UserFraction -gt 0)) { $release.userFraction = [math]::Round($UserFraction, 6) }
    $notes = @()
    if ($ko) { $notes += [ordered]@{ language = 'ko-KR'; text = $ko } }
    if ($en) { $notes += [ordered]@{ language = 'en-US'; text = $en } }
    if ($notes.Count) { $release.releaseNotes = $notes }
    $c = New-JsonContent ([ordered]@{ track = $Track; releases = @($release) } | ConvertTo-Json -Depth 6 -Compress)
    try { [void](Invoke-Play $http 'PUT' "$appBase/edits/$editId/tracks/$([Uri]::EscapeDataString($Track))" $token $c) } finally { $c.Dispose() }

    $commitUri = "$appBase/edits/$editId`:commit" + $(if ($ChangesNotSentForReview) { '?changesNotSentForReview=true' } else { '' })
    $c = New-JsonContent ''
    try { [void](Invoke-Play $http 'POST' $commitUri $token $c) } finally { $c.Dispose() }
    $committed = $true
    Write-Host "Play $(if ($assignOnly) { '트랙 지정' } else { '업로드' }) 완료: $PackageName versionCode $versionCode → 트랙 $Track ($Status)$(if ($ChangesNotSentForReview) { ' — 검토 미제출, 콘솔에서 제출 필요' })"
}
finally {
    if ($editId -and -not $committed -and $token) {
        try { [void](Invoke-Play $http 'DELETE' "$appBase/edits/$editId" $token) } catch { }
    }
    $token = ''
    $http.Dispose()
    [Console]::OutputEncoding = $prevEnc
}
