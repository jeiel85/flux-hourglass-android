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

| 파일 | 용도 | 형식 |
|------|------|------|
| `docs/releases/vX.Y.Z.md` | GitHub Release 본문 | Markdown 자유 형식 |
| `play_store/release_notes/vX.Y.Z.txt` | Play Console 입력용 | **BCP-47 다국어 블록 (필수)** |

Play Console 노트는 반드시 다음 형식을 따릅니다. 자세한 규약은
[`play_store/release_notes/README.md`](play_store/release_notes/README.md)에
박제되어 있습니다.

```text
<ko-KR>
vX.Y.Z 한 줄 요약

새로 추가
• 변경 1
</ko-KR>
<en-US>
vX.Y.Z short summary

What's new
• Change 1
</en-US>
```

- **두 언어 블록 모두 필수.** 한국어(`<ko-KR>`) 먼저, 영어(`<en-US>`) 그다음.
- **언어당 500자 이내.** Play Console 자동 잘림.
- **마크다운/HTML 금지.** 평문 + `•` 글머리표만.

`scripts/export-play-store-release.ps1`은 이 두 블록이 없으면 export를
중단합니다. 양식 예시는 `play_store/release_notes/v1.2.0.txt`를 참고하세요.

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
   복사. 파일 이름은 `flux-hourglass-v1.2.0-vc3.aab`,
   `flux-hourglass-v1.2.0-vc3-release-notes.txt` **두 개만**입니다.

**APK는 바탕화면으로 복사하지 않습니다.** Play Console 업로드에 필요한
것은 AAB뿐입니다. 사이드로드용 APK가 필요하면
`app/build/outputs/apk/release/app-release.apk`에서 직접 가져가거나, 태그를
푸시해 GitHub Release에 자동으로 올라온 것을 받아 사용합니다.

원본 AAB와 APK는 `app/build/outputs/bundle/release/app-release.aab`와
`app/build/outputs/apk/release/app-release.apk`에 그대로 남습니다.

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
바탕화면에 떨어진 두 파일만 사용합니다.

- `flux-hourglass-v{ver}-vc{code}.aab` — Play Console Internal/Production
  트랙에 업로드.
- `flux-hourglass-v{ver}-vc{code}-release-notes.txt` — "이번 버전의 새로운
  기능 / What's new in this release" 입력란에 **파일 내용 전체를 그대로**
  붙여넣습니다. Play Console이 `<ko-KR>` / `<en-US>` 블록을 자동 인식해 각
  언어별 노트로 분리합니다.

사이드로드용 APK는 `app/build/outputs/apk/release/app-release.apk` 또는 태그
푸시 후 GitHub Release 페이지에서 받습니다.

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
| Release APK | `app/build/outputs/apk/release/*.apk` (사이드로드 전용) |
| Release AAB | `app/build/outputs/bundle/release/*.aab` (Play Console 업로드용) |
| 바탕화면 | `Desktop/flux-hourglass-vX.Y.Z-vcN.aab` + `...-release-notes.txt` |
| Roborazzi 베이스라인 | `app/src/test/screenshots/*.png` |

**바탕화면에는 AAB와 다국어 release-notes.txt만 떨어집니다.** APK는 일부러
복사하지 않습니다.
