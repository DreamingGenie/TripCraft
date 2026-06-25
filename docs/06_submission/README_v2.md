# TripCraft — 최종 제출 산출물 (v2)

> 팀원: 전진 · 송정기 · 2026.06
> 마크다운/Mermaid 정본 + 변환 스크립트로 PDF·PPTX를 생성한다.
>
> **v2 변경점**(원본 `README.md`·`build.sh`·`04_screen/화면설계서.md` 는 그대로 보존):
> - `05_reference/API명세.md` 추가 — 정식 변환 대상(`build_v2.sh`)에 포함.
> - `05_reference/시연시나리오.md` 추가.
> - `02_diagrams/er-diagram.dbml` 추가 — ERD 원본(dbdiagram.io 입력).
> - `04_screen/화면설계서_v2.md` 추가 — 핵심 5종 **와이어프레임**(`04_screen/wireframes/*.svg`) 임베드.
> - `05_reference/기술심화-*.md` 2종 추가 — **초안**(작업자 직접 완성 예정, 현재 변환 대상 제외).

---

## 제출 묶음 매핑

### ① 설계 문서

| # | 산출물 | 파일 |
|---|--------|------|
| 1 | 요구사항 정의서 | [`01_requirements/요구사항정의서.md`](./01_requirements/요구사항정의서.md) |
| 2 | Use-Case 다이어그램 | [`02_diagrams/usecase.md`](./02_diagrams/usecase.md) (+ `usecase.svg`) |
| 3 | 클래스 다이어그램 | [`02_diagrams/class-diagram.md`](./02_diagrams/class-diagram.md) |
| 4 | ER 다이어그램 | [`02_diagrams/er-diagram.md`](./02_diagrams/er-diagram.md) (+ `er-diagram.dbml`) |
| 5 | WBS & 간트 차트 | [`03_wbs/WBS_간트.md`](./03_wbs/WBS_간트.md) (+ `gantt.html`) |
| 6 | 화면 설계서 | [`04_screen/화면설계서_v2.md`](./04_screen/화면설계서_v2.md) (와이어프레임 포함, 원본 `화면설계서.md` 보존) |
| 7 | 기타 참고 문서 | [`05_reference/기타참고문서.md`](./05_reference/기타참고문서.md) |
| 7-1 | API 명세 | [`05_reference/API명세.md`](./05_reference/API명세.md) |
| 7-2 | 시연 시나리오 | [`05_reference/시연시나리오.md`](./05_reference/시연시나리오.md) |
| 7-3 | 기술 심화 — 경로 최적화·지도 시각화 | [`05_reference/기술심화-경로최적화및지도시각화.md`](./05_reference/기술심화-경로최적화및지도시각화.md) · ⚠️ 초안(송정기 완성 예정) |
| 7-4 | 기술 심화 — 실시간 협업·동시성 | [`05_reference/기술심화-실시간협업및동시성.md`](./05_reference/기술심화-실시간협업및동시성.md) · ⚠️ 초안(전진 완성 예정) |

### ② 소스 코드 (zip 구성)

| 항목 | 경로 |
|------|------|
| DB Schema.sql (테이블·초기데이터) | `docs/02_design/schema.sql` |
| Spring Boot 프로젝트 | `backend/` |
| Vue.js 프로젝트 | `frontend/` |
| 활용 데이터셋 | 한국관광공사 TourAPI 수집 데이터(`attraction*` 테이블) · `docs/05_sql/*.sql` 테스트 데이터 |

### ③ 최종 완료 보고서

| 산출물 | 파일 |
|--------|------|
| 발표용 PPT | [`ppt/slides.md`](./ppt/slides.md) → PDF / PPTX |
| AI 사용 보고서 | [`ai-report/AI사용보고서.md`](./ai-report/AI사용보고서.md) → PDF |

---

## 변환 방법 (Markdown → PDF / PPTX)

> 시스템에 별도 변환기 설치 없이 **Chrome + Node/npx**로 변환한다.
> npx가 최초 실행 시 패키지를 내려받으므로 **네트워크 연결 필요**.

### 한 번에 빌드 (v2)
```bash
cd docs/06_submission
./build_v2.sh      # API명세 포함, 화면설계서_v2(와이어프레임) 변환. 원본 build.sh 도 그대로 사용 가능
```

> 기술심화 2종(`기술심화-*.md`)은 초안이라 `build_v2.sh` 변환 대상에서 제외돼 있다. 작업자 완성 후 DOCS 배열에 추가하면 된다.

### 뷰어로 바로 보기
- `.md`의 Mermaid 다이어그램은 GitLab/GitHub·VS Code(Markdown Preview Mermaid)·Typora에서 즉시 렌더된다.
- `gantt.html`·`usecase.svg`·`04_screen/wireframes/*.svg`는 브라우저에서 바로 열람 가능.

---

## 디렉터리 (v2 기준)

```
06_submission/
├── README.md  · README_v2.md (이 파일)
├── build.sh   · build_v2.sh           ← API명세 포함 변환
├── 01_requirements/요구사항정의서.md
├── 02_diagrams/
│   ├── usecase.md · usecase.svg
│   ├── class-diagram.md
│   ├── er-diagram.md · er-diagram.dbml   ← dbml 원본 추가
├── 03_wbs/WBS_간트.md · gantt.html
├── 04_screen/
│   ├── 화면설계서.md (원본 보존)
│   ├── 화면설계서_v2.md                   ← 와이어프레임 임베드
│   └── wireframes/                        ← screen-M/A/B/C/D *.svg
├── 05_reference/
│   ├── 기타참고문서.md
│   ├── API명세.md                         ← 추가
│   ├── 시연시나리오.md                    ← 추가
│   ├── 기술심화-경로최적화및지도시각화.md  ← 초안
│   └── 기술심화-실시간협업및동시성.md      ← 초안
├── ppt/slides.md
├── ai-report/AI사용보고서.md
└── assets/                                ← 렌더된 이미지(gitignore)
```
