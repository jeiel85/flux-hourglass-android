# Flux Hourglass — 새 버전 만들기 절차

이 문서는 이 저장소에서 **새 버전을 만드는 정해진 절차**입니다. zephyr-sky-android,
markscene-android, nightseed-survivor-android 등 같은 머신에 있는 다른
앱들의 운영 패턴을 그대로 따릅니다.

원칙은 단순합니다.

1. `app/build.gradle.kts`의 `versionName` / `versionCode`가 단일 진실 공급원
   (single source of truth).
2. 릴리즈 키스토어는 `~/.keystore/flux-hourglass-upload.jks`에 보관, base64와
   비밀번호도 같은 폴더에 별도 파일로 백업.
3. 태그(`vX.Y.Z`)를 푸시하면 GitHub Actions가 자동으로 서명된 APK + AAB를
   빌드해서 GitHub Release를 만들고, 같은 산출물을 `scripts/build_release.ps1`
   로 로컬에서 만들면 `scripts/export-play-store-release.ps1`이 바탕화면에
   `flux-hourglass-v{ver}-vc{code}.aab` 이름으로 옮겨 줍니다. 옆 프로젝트의
   `markleaf-v2.16.0-vc41.aab`, `BrioDo-v1.5.2-vc38.aab`, `PulpitInk-v1.4.0-vc6.aab`
   와 같은 명명 규칙입니다.

## 1. 사전 준비 (최초 1회)

### 1.1 Android SDK 위치
프로젝트 루트의 `local.properties`(gitignore됨)에 SDK 경로를 박아 둡니다.

```properties
sdk.dir=C\:\\Users\\<USER>\\AppData\\Local\\Android\\Sdk
```

### 1.2 디버그 키스토어 복원
저장소에 동봉된 `debug.keystore.base64`를 한 번만 디코드하면 됩니다.

```powershell
certutil -decode debug.keystore.base64 debug.keystore
```

### 1.3 릴리즈 키스토어
`stargaze-explorer-upload.jks`와 같은 방식으로 한 번만 만들어 두고
백업합니다.

```powershell
$keystoreDir = Join-Path $HOME ".keystore"
New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null
$keystorePath = Join-Path $keystoreDir "flux-hourglass-upload.jks"

keytool -genkey -v `
    -keystore $keystorePath `
    -alias upload `
    -keyalg RSA -keysize 2048 -validity 10000 `
    -dname "CN=Flux Hourglass, OU=Apps, O=jeiel, L=Seoul, ST=Seoul, C=KR" `
    -storepass <STORE_PASSWORD> -keypass <KEY_PASSWORD>
```

비밀번호와 alias는 `~/.keystore/flux-hourglass-upload.credentials.txt`에
같이 적어두고, base64 인코딩본은 `~/.keystore/flux-hourglass-upload.jks.base64`
에 함께 백업합니다. GitHub Actions에는 secret 4개로 등록합니다.

| Secret | 값 |
|--------|----|
| `RELEASE_KEYSTORE_BASE64` | `flux-hourglass-upload.jks.base64` 내용 |
| `RELEASE_STORE_PASSWORD` | keystore 비밀번호 |
| `RELEASE_KEY_ALIAS` | `upload` |
| `RELEASE_KEY_PASSWORD` | key 비밀번호 |

## 2. 새 버전 만들기 — 단계별

새 버전을 만들 때는 **순서대로** 다음을 진행합니다.

### 2.1 변경 사항 구현 + 테스트 통과

```powershell
.\gradlew.bat assembleDebug test
```

UI 변경이 있으면 Roborazzi 베이스라인을 재기록합니다.

```powershell
.\gradlew.bat recordRoborazziDebug
```

### 2.2 버전 번호 올리기
`app/build.gradle.kts`에서 두 줄만 바꿉니다.

```kotlin
versionCode = (findProperty("VERSION_CODE") as String?)?.toIntOrNull() ?: <새 코드>
versionName = (findProperty("VERSION_NAME") as String?) ?: "<X.Y.Z>"
```

- `versionCode`는 직전 코드 + 1.
- `versionName`은 의미 있는 변경이면 `+0.1.0`, 패치면 `+0.0.1`.

### 2.3 릴리즈 노트 작성
같은 버전명으로 두 파일을 만듭니다.

| 파일 | 용도 |
|------|------|
| `docs/releases/vX.Y.Z.md` | GitHub Release 본문 |
| `play_store/release_notes/vX.Y.Z.txt` | Play Console 입력용 |

`docs/releases/v1.1.0.md`와 `play_store/release_notes/v1.1.0.txt`가 양식 예시입니다.

### 2.4 CHANGELOG 업데이트
`CHANGELOG.md` 최상단에 새 버전 섹션을 추가합니다.

### 2.5 로컬 릴리즈 빌드 + 데스크톱 export
환경변수에 키스토어 자격을 넣고 한 줄 스크립트를 실행하면 됩니다.

```powershell
$env:KEYSTORE_PATH = "$HOME\.keystore\flux-hourglass-upload.jks"
$env:STORE_PASSWORD = "<STORE_PASSWORD>"
$env:KEY_ALIAS = "upload"
$env:KEY_PASSWORD = "<KEY_PASSWORD>"

.\scripts\build_release.ps1 -Version 1.2.0 -VersionCode 3
```

이게 내부적으로 하는 일:

1. `./gradlew test`
2. `./gradlew clean bundleRelease assembleRelease -PVERSION_NAME=1.2.0 -PVERSION_CODE=3`
3. `scripts\export-play-store-release.ps1`을 호출해서 결과물을 바탕화면으로
   복사. 파일 이름은 `flux-hourglass-v1.2.0-vc3.aab`, `flux-hourglass-v1.2.0-vc3.apk`,
   `flux-hourglass-v1.2.0-vc3-release-notes.txt`.

원본은 `app/build/outputs/bundle/release/app-release.aab`와
`app/build/outputs/apk/release/app-release.apk`에 그대로 남습니다. 같은
파일이 `/.build-outputs/`에도 거울로 복사되어 빌드 크기를 추적할 수 있습니다.

### 2.6 커밋 + 태그 + 푸시

```powershell
git add -A
git commit -m "feat(vX.Y.Z): <한 줄 요약>"
git tag vX.Y.Z
git push origin main vX.Y.Z
```

`vX.Y.Z` 태그가 푸시되는 순간 `.github/workflows/release.yml`이 발동합니다.
워크플로우는 GitHub Secrets로 다시 한 번 서명된 APK + AAB를 만들고
`docs/releases/vX.Y.Z.md`를 본문으로 GitHub Release를 게시합니다.

## 3. Play Console 업로드
바탕화면에 떨어진 세 파일만 사용합니다.

- `flux-hourglass-v{ver}-vc{code}.aab` — Play Console Internal/Production 트랙에 업로드.
- `flux-hourglass-v{ver}-vc{code}-release-notes.txt` — "이번 버전의 새로운 기능"에 복사 붙여넣기.
- `flux-hourglass-v{ver}-vc{code}.apk` — 사이드로드용(또는 GitHub Release에서 사용자가 직접 다운로드용).

## 4. 트러블슈팅

| 증상 | 원인 / 해결 |
|------|-------------|
| `SDK location not found` | `local.properties`의 `sdk.dir` 누락. |
| `Keystore file not found` | `KEYSTORE_PATH` 환경변수 누락. `~/.keystore/...`까지 풀 경로 지정. |
| `Tag version X does not match versionName Y` (CI) | 태그명과 `app/build.gradle.kts`의 `versionName`이 다를 때. 한쪽을 맞춥니다. |
| Roborazzi 테스트 실패 | UI를 바꾼 뒤 베이스라인을 다시 안 기록한 경우. `recordRoborazziDebug` 실행 후 커밋. |
| `secretsGradlePlugin`이 `.env` 못 찾음 | `.env.example`이 fallback이라 빌드 자체는 통과합니다. 신경 안 써도 됩니다. |

## 5. 산출물 위치 요약

| 산출물 | 경로 |
|--------|------|
| Debug APK | `app/build/outputs/apk/debug/*.apk` |
| Release APK | `app/build/outputs/apk/release/*.apk` |
| Release AAB | `app/build/outputs/bundle/release/*.aab` |
| 로컬 거울 | `.build-outputs/flux-hourglass-vX.Y.Z-vcN.{apk,aab}` |
| 바탕화면 | `Desktop/flux-hourglass-vX.Y.Z-vcN.{apk,aab,release-notes.txt}` |
| Roborazzi 베이스라인 | `app/src/test/screenshots/*.png` |
