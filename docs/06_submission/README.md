# TripCraft — 최종 제출 산출물

> 팀원: 전진 · 송정기 · 2026.06
> 마크다운/Mermaid 정본 + 변환 스크립트로 PDF·PPTX를 생성한다.

---

## 제출 묶음 매핑

### ① 설계 문서

| # | 산출물 | 파일 |
|---|--------|------|
| 1 | 요구사항 정의서 | [`01_requirements/요구사항정의서.md`](./01_requirements/요구사항정의서.md) |
| 2 | Use-Case 다이어그램 | [`02_diagrams/usecase.md`](./02_diagrams/usecase.md) (+ `usecase.svg`) |
| 3 | 클래스 다이어그램 | [`02_diagrams/class-diagram.md`](./02_diagrams/class-diagram.md) |
| 4 | ER 다이어그램 | [`02_diagrams/er-diagram.md`](./02_diagrams/er-diagram.md) |
| 5 | WBS & 간트 차트 | [`03_wbs/WBS_간트.md`](./03_wbs/WBS_간트.md) (+ `gantt.html`) |
| 6 | 화면 설계서 | [`04_screen/화면설계서.md`](./04_screen/화면설계서.md) |
| 7 | 기타 참고 문서 | [`05_reference/기타참고문서.md`](./05_reference/기타참고문서.md) |

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

### 한 번에 빌드
```bash
cd docs/06_submission
./build.sh
```

### 개별 변환
```bash
# 1) 다이어그램 Mermaid → SVG (선택: 정적 이미지가 필요할 때)
npx -y @mermaid-js/mermaid-cli -i 02_diagrams/er-diagram.md -o assets/er.svg

# 2) 설계 문서 → PDF (Mermaid 코드펜스 자동 렌더)
npx -y md-to-pdf 01_requirements/요구사항정의서.md
npx -y md-to-pdf 02_diagrams/er-diagram.md
#   ... 나머지 .md 동일

# 3) 발표자료 → PDF + PPTX (편집 가능)
npx -y @marp-team/marp-cli ppt/slides.md --pdf  --allow-local-files
npx -y @marp-team/marp-cli ppt/slides.md --pptx --allow-local-files
```

생성물은 각 문서 옆(또는 `out/`)에 `*.pdf` / `*.pptx`로 떨어진다.

### 뷰어로 바로 보기
- `.md`의 Mermaid 다이어그램은 GitLab/GitHub·VS Code(Markdown Preview Mermaid)·Typora에서 즉시 렌더된다.
- `gantt.html`은 브라우저에서 바로 열람 가능, `usecase.svg`도 동일.

---

## 디렉터리

```
06_submission/
├── README.md                  ← (이 파일)
├── build.sh                   ← 전체 변환 스크립트
├── 01_requirements/요구사항정의서.md
├── 02_diagrams/
│   ├── usecase.md  · usecase.svg
│   ├── class-diagram.md
│   └── er-diagram.md
├── 03_wbs/WBS_간트.md · gantt.html
├── 04_screen/화면설계서.md
├── 05_reference/기타참고문서.md
├── ppt/slides.md              ← Marp (→ PDF/PPTX)
├── ai-report/AI사용보고서.md
└── assets/                    ← 렌더된 다이어그램 이미지
```
