# Play Console release notes

Google Play Console의 "이번 버전의 새로운 기능 / What's new in this release"
입력란에 그대로 붙여넣는 다국어 릴리즈 노트를 보관합니다.

GitHub Release 본문(`docs/releases/`)과는 형식 제약이 완전히 다르므로 별도
파일로 관리합니다.

## 파일명

- 태그명과 정확히 일치: `vX.Y.Z.txt`
- 예: `v1.0.0.txt`, `v1.2.0.txt`

## 형식 — BCP-47 다국어 블록

각 언어 노트를 BCP-47 언어 태그로 감쌉니다. `<ko-KR>` 한국어, `<en-US>` 영어
**두 블록 모두 필수**이며 순서는 한국어 먼저, 영어 그다음입니다. 이 순서는
프로젝트 전체에서 통일합니다.

```text
<ko-KR>
vX.Y.Z 한 줄 요약

새로 추가
• 변경 1
• 변경 2

개선
• 변경 3
</ko-KR>
<en-US>
vX.Y.Z short summary

What's new
• Change 1
• Change 2

Improved
• Change 3
</en-US>
```

## 제약

- **언어당 최대 500자.** Play Console이 잘라냅니다. 한국어가 영어보다 길게
  잡히는 경우가 많으니 한국어 쪽을 먼저 다듬어 500자에 맞춥니다.
- **마크다운/HTML 금지.** Play Console은 평문만 렌더링합니다. 강조는
  `•` 글머리표와 공백으로만.
- **BCP-47 태그 그대로 유지.** `<ko-KR>`, `<en-US>` 외 다른 태그 추가 시
  Play Console이 인식하지 못합니다.
- **두 언어 모두 첫 줄은 `vX.Y.Z` 버전 + 한 줄 요약.** 다른 프로젝트
  (zephyr-sky, nightseed-survivor 등)와 동일한 규약입니다.

## 바탕화면 내보내기

`scripts/export-play-store-release.ps1`이 이 폴더의 `vX.Y.Z.txt`를 그대로
바탕화면으로 복사합니다.

```powershell
.\scripts\export-play-store-release.ps1 -Version 1.2.0
```

결과 파일 (바탕화면):

```text
flux-hourglass-vX.Y.Z-vcN.aab
flux-hourglass-vX.Y.Z-vcN-release-notes.txt
```

스크립트는 다음을 강제합니다.

1. `play_store/release_notes/vX.Y.Z.txt`가 없으면 export 중단.
2. 그 파일에 `<ko-KR>`과 `<en-US>` 블록이 둘 다 없으면 export 중단.
3. APK는 바탕화면으로 복사하지 않습니다. Play Console 업로드에 필요한 것은
   AAB뿐이며, APK는 `app/build/outputs/apk/release/`에 남아 있는 것을 그대로
   사이드로드용으로 쓰면 됩니다.

## 업로드 절차

1. Play Console → 프로덕션/내부 테스트 → 새 릴리즈
2. `flux-hourglass-vX.Y.Z-vcN.aab` 업로드
3. `flux-hourglass-vX.Y.Z-vcN-release-notes.txt`를 통째로 "이번 버전의 새로운
   기능"에 붙여넣기. Play Console이 `<ko-KR>` / `<en-US>` 블록을 자동
   분리합니다.
4. 분리가 안 되면 언어 태그 오타나 줄바꿈 누락을 의심합니다.
