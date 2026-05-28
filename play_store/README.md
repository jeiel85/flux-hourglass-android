# Play Store 등록 애셋

Play Console 신규 앱 등록 / 메인 스토어 등록정보를 채우는 데 필요한 모든 텍스트·이미지를 모아둔 폴더입니다.

## 폴더 구조

```
play_store/
├── README.md                       (이 파일)
├── content_rating.md               콘텐츠 등급 설문 답변 + 카테고리 가이드
├── listing/
│   ├── ko-KR/
│   │   ├── title.txt               앱 이름 (≤30자)
│   │   ├── short_description.txt   짧은 설명 (≤80자)
│   │   └── full_description.txt    전체 설명 (≤4000자)
│   └── en-US/
│       ├── title.txt
│       ├── short_description.txt
│       └── full_description.txt
├── graphics/
│   ├── icon-512.png                앱 아이콘 512×512
│   └── feature-graphic.png         피처 그래픽 1024×500
├── screenshots/
│   ├── phone/                      Galaxy S24 캡쳐 (1080×2340, 세로)
│   │   ├── 01-setup.png
│   │   ├── 02-running.png
│   │   ├── 03-time-reveal.png
│   │   ├── 04-paused.png
│   │   └── 05-end.png
│   └── tablet10/                   Lenovo TB320FC 캡쳐 (2560×1600, 가로)
│       ├── 01-setup.png
│       ├── 02-preset-5m.png
│       ├── 03-time-reveal.png
│       └── 04-paused.png
├── privacy_policy/
│   ├── privacy_policy_ko.md
│   └── privacy_policy_en.md
└── release_notes/                  (기존) 버전별 다국어 릴리즈 노트
    ├── README.md
    └── vX.Y.Z.txt …
```

## Play Console 입력 매핑

Play Console 메뉴 → 입력란 → 이 폴더의 파일 매핑.

### 메인 스토어 등록정보 (Main store listing)

| Play Console 필드 | 파일 | 비고 |
| --- | --- | --- |
| 앱 이름 (App name) | `listing/<lang>/title.txt` | 한국어 / 영어 각각 등록 |
| 짧은 설명 (Short description) | `listing/<lang>/short_description.txt` | 80자 |
| 자세한 설명 (Full description) | `listing/<lang>/full_description.txt` | 4000자 |
| 앱 아이콘 (App icon) | `graphics/icon-512.png` | 512×512 PNG |
| 그래픽 추천 이미지 (Feature graphic) | `graphics/feature-graphic.png` | 1024×500 PNG |
| 휴대전화 스크린샷 | `screenshots/phone/*.png` | 최소 2장, 추천 5–8장 |
| 7인치 태블릿 스크린샷 | (없음) | 선택 — 10인치만 등록 |
| 10인치 태블릿 스크린샷 | `screenshots/tablet10/*.png` | 선택, 4장 |

### 정책 & 신고

| Play Console 필드 | 파일 |
| --- | --- |
| 개인정보처리방침 URL | `privacy_policy/privacy_policy_<lang>.md` 를 GitHub Pages 등 공개 URL에 게시한 뒤 그 URL 입력 |
| 콘텐츠 등급 설문 | `content_rating.md` 의 답변 그대로 입력 |
| 데이터 보안 (Data safety) | `content_rating.md` 의 "데이터 보안" 섹션 참고 |
| 광고 포함 여부 | 없음 |
| 타겟 연령 | 13세 이상 / 전체 이용가 |

### 앱 카테고리

`content_rating.md` 참고. 권장: **도구(Tools)**.

## 개인정보처리방침 호스팅

Play Console은 개인정보처리방침의 **공개 URL**을 요구합니다. 마크다운 파일 그대로는 등록할 수 없으므로 다음 중 하나의 방법으로 호스팅합니다.

권장: 이 저장소의 GitHub Pages(`docs/`)에 HTML로 게시.

1. `privacy_policy/privacy_policy_ko.md` 와 `privacy_policy_en.md` 를 HTML로 변환해 `docs/privacy.html` 로 추가
2. GitHub Pages가 재배포되기를 기다림 (`https://jeiel85.github.io/flux-hourglass-android/privacy.html`)
3. 그 URL을 Play Console → 앱 콘텐츠 → 개인정보처리방침에 입력

## 첫 출시 절차 요약

1. Play Console → 앱 만들기 → 기본 정보 입력
2. 정책 & 신고 — 개인정보처리방침 URL, 콘텐츠 등급 설문, 데이터 보안 작성
3. 메인 스토어 등록정보 — 이 폴더의 `listing/`, `graphics/`, `screenshots/` 업로드
4. 프로덕션 트랙 → 새 릴리즈 만들기 → 바탕화면의 `flux-hourglass-vX.Y.Z-vcN.aab` 업로드
5. `release_notes/vX.Y.Z.txt` 를 "이번 버전의 새로운 기능" 에 그대로 붙여넣기 (Play Console이 `<ko-KR>` / `<en-US>` 자동 분리)
6. 검토 → 출시
