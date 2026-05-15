# WBS (Work Breakdown Structure) — TripCraft

> **버전**: v0.1 (초안)  
> **기준 일정**: 6주 (총 42일)  
> **개발 인원**: 2인 

---

## 전체 일정 요약

| 단계 | 기간 | 주요 내용 |
|------|------|-----------|
| 1단계 — 기획 | Week 1 | 요구사항 정의, WBS 작성, 협업 환경 세팅 |
| 2단계 — 설계 | Week 2 | ERD, API 명세, UI 와이어프레임, 코딩 컨벤션 |
| 3단계 — 개발 | Week 3 ~ 5 | 기능 구현 (F01 ~ F10) |
| 4단계 — QA·배포 | Week 6 | 통합 테스트, 버그 수정, 배포 |

---

## 1단계 — 기획 (Week 1)

### 1.1 프로젝트 초기 설정
- [ ] GitLab 레포지토리 생성 및 브랜치 전략 초안 작성
- [ ] 디렉토리 구조 생성 (`backend/`, `frontend/`, `docs/`)
- [ ] README 초안 작성
- [ ] 팀 내 역할 분담 확정

### 1.2 요구사항 정의
- [ ] 사용자 스토리(User Story) 작성 (기능별)
- [ ] 상세 요구사항 명세서 작성 (`docs/01_planning/requirements.md`)
- [ ] 기능 우선순위 최종 확인 (Phase 1 / Phase 2)

### 1.3 WBS 작성
- [ ] WBS 및 Gantt Chart 작성 (`docs/01_planning/wbs.md`)
- [ ] 주차별 마일스톤 확정

---

## 2단계 — 설계 (Week 2)

### 2.1 아키텍처 설계
- [ ] 시스템 전체 구조도 작성
- [ ] Use-case Diagram 작성 (`docs/02_design/usecase-diagram.md`)

### 2.2 데이터베이스 설계
- [ ] ERD 초안 작성
  - 회원 (Member)
  - 관광지 (Attraction)
  - 즐겨찾기 (Favorite)
  - 여행 일정 (Trip / TripBlock)
  - 커뮤니티 게시글 (Post)
  - 공지사항 (Notice)
- [ ] ERD 검토 및 확정 (`docs/02_design/erd.md`)

### 2.3 API 명세
- [ ] 회원 API 명세
- [ ] 관광지 API 명세
- [ ] 일정 API 명세
- [ ] 커뮤니티 API 명세
- [ ] Swagger 설정 계획 수립
- [ ] API 명세서 작성 (`docs/02_design/api-spec.md`)

### 2.4 UI/UX 설계
- [ ] 주요 화면 와이어프레임 (Figma 또는 드로잉)
  - 메인 / 관광지 탐색 화면
  - 일정 편집 (블록형 시간표) 화면
  - 커뮤니티 게시판 화면
  - 회원가입 / 로그인 화면

### 2.5 개발 환경 및 컨벤션 확정
- [ ] 코딩 컨벤션 문서 작성 (`docs/03_dev/conventions.md`)
  - Java 패키지 구조 및 네이밍 규칙
  - Vue.js 컴포넌트 구조
  - Git 커밋 메시지 규칙
  - GitLab 브랜치 전략 확정 (main / develop / feature/*)
- [ ] 백엔드 로컬 개발 환경 세팅 (Spring Boot, MySQL)
- [ ] 프론트엔드 로컬 개발 환경 세팅 (Vue.js 3, Vite)

---

## 3단계 — 개발 (Week 3 ~ 7)

### 3.0 공통 기반 작업 (Week 3 초)
- [ ] Spring Boot 프로젝트 초기화 (Gradle, Java 21)
- [ ] JPA, MySQL, Spring Security 의존성 설정
- [ ] Vue.js 3 프로젝트 초기화 (Vite)
- [ ] Kakao Maps API 키 발급 및 연동 테스트
- [ ] ODsay API 키 발급 및 연동 테스트
- [ ] 한국관광공사 API 키 발급

---

### F10 — 회원 관리 (Week 3)

**Backend**
- [ ] Member 엔티티 및 Repository 작성
- [ ] Spring Security + JWT 설정
- [ ] 회원가입 API (`POST /api/members`)
- [ ] 로그인 API + JWT 발급 (`POST /api/auth/login`)
- [ ] 로그아웃 처리 (`POST /api/auth/logout`)
- [ ] 회원 정보 수정 API (`PUT /api/members/{id}`)
- [ ] 회원 탈퇴 API (`DELETE /api/members/{id}`)

**Frontend**
- [ ] 회원가입 페이지 UI 구현
- [ ] 로그인 페이지 UI 구현
- [ ] JWT 저장 및 Axios 인터셉터 설정
- [ ] 마이페이지(정보수정·탈퇴) UI 구현
- [ ] 헤더 컴포넌트 (로그인 상태 반영)

---

### F01 · F02 · F03 — 관광지 DB 구축 및 조회 (Week 4)

**Backend**
- [ ] 한국관광공사 API 데이터 수집 스크립트 작성
- [ ] Attraction 엔티티 및 Repository 작성
- [ ] 초기 데이터 적재 (주요 시도·카테고리 기준)
- [ ] 지역별 조회 API (`GET /api/attractions?areaCode=&sigunguCode=`)
- [ ] 카테고리별 조회 API (`GET /api/attractions?contentTypeId=`)
- [ ] 관광지 상세 조회 API (`GET /api/attractions/{id}`)
- [ ] Swagger 문서화

**Frontend**
- [ ] 관광지 탐색 페이지 레이아웃 구현
- [ ] 지역 / 카테고리 필터 컴포넌트 구현
- [ ] 관광지 목록 카드 컴포넌트 구현
- [ ] 관광지 상세 모달 / 페이지 구현
- [ ] Kakao Maps 지도 마커 연동

---

### F04 — 즐겨찾기 (Week 4, 관광지 조회와 병행)

**Backend**
- [ ] Favorite 엔티티 및 Repository 작성
- [ ] 즐겨찾기 추가 API (`POST /api/favorites`)
- [ ] 즐겨찾기 삭제 API (`DELETE /api/favorites/{id}`)
- [ ] 즐겨찾기 목록 조회 API (`GET /api/favorites`)

**Frontend**
- [ ] 관광지 카드 즐겨찾기 토글 버튼 구현
- [ ] 즐겨찾기 목록 페이지 구현

---

### F05 · F06 · F07 — 일정 (블록형 시간표 · 이동시간 · 저장) (Week 5 ~ 6)

**Backend**
- [ ] Trip 엔티티 및 Repository 작성
- [ ] TripBlock 엔티티 (날짜, 시작시간, 장소, 순서) 및 Repository 작성
- [ ] 여행 일정 생성 API (`POST /api/trips`)
- [ ] 여행 일정 상세 조회 API (`GET /api/trips/{id}`)
- [ ] 여행 일정 목록 조회 API (`GET /api/trips` — 내 일정)
- [ ] 블록 추가 API (`POST /api/trips/{id}/blocks`)
- [ ] 블록 순서·시간 수정 API (`PUT /api/trips/{id}/blocks/{blockId}`)
- [ ] 블록 삭제 API (`DELETE /api/trips/{id}/blocks/{blockId}`)
- [ ] ODsay API 연동 — 두 장소 간 이동시간 조회 서비스 레이어
- [ ] 이동 시간 자동 계산 API (`POST /api/transit/time`)
- [ ] 여행 일정 저장/불러오기 (상태 유지)

**Frontend**
- [ ] 일정 편집 페이지 레이아웃 구현
- [ ] 날짜별 탭 / 패널 구조 구현
- [ ] vue-draggable을 활용한 블록형 시간표 구현
- [ ] 드래그 앤 드롭으로 장소 블록 추가 / 순서 변경
- [ ] 이동 시간 블록 자동 삽입 UI
- [ ] 장소 추가 시 이동 시간 자동 계산 API 연동
- [ ] 일정 저장 / 불러오기 연동

---

### F08 · F09 — 커뮤니티 (공유 게시판 · 공지사항) (Week 7)

**Backend**
- [ ] Post 엔티티 및 Repository 작성
- [ ] Notice 엔티티 및 Repository 작성
- [ ] 일정 공유 게시글 작성 API (`POST /api/posts`)
- [ ] 게시글 목록 조회 API (`GET /api/posts`)
- [ ] 게시글 상세 조회 API (`GET /api/posts/{id}`)
- [ ] 게시글 수정 API (`PUT /api/posts/{id}`)
- [ ] 게시글 삭제 API (`DELETE /api/posts/{id}`)
- [ ] 공지사항 CRUD API (`/api/notices`)

**Frontend**
- [ ] 커뮤니티 게시판 목록 페이지 구현
- [ ] 게시글 상세 페이지 구현 (공유된 일정 뷰어 포함)
- [ ] 일정 공유 기능 (게시글 작성 시 Trip 선택) 구현
- [ ] 공지사항 목록 / 상세 페이지 구현
- [ ] 관리자 공지 작성 UI (권한 처리 포함)

---

## 4단계 — QA · 배포 (Week 8)

### 4.1 통합 테스트
- [ ] 전체 기능 흐름 E2E 테스트 (회원 → 관광지 탐색 → 일정 작성 → 공유)
- [ ] API 단위 테스트 보완
- [ ] 이동 시간 자동 계산 정확도 검증
- [ ] 크로스 브라우저 테스트 (Chrome, Safari)

### 4.2 버그 수정 및 UI 개선
- [ ] 테스트 중 발견된 버그 수정
- [ ] 반응형 레이아웃 점검 (모바일/태블릿)
- [ ] 로딩/에러 상태 처리 보완

### 4.3 배포
- [ ] 배포 환경 결정 및 서버 세팅
- [ ] 백엔드 빌드 및 배포
- [ ] 프론트엔드 빌드 및 정적 파일 배포
- [ ] 환경 변수 및 API 키 보안 처리
- [ ] 최종 동작 확인

### 4.4 문서 마무리
- [ ] README 최종 업데이트
- [ ] API 명세서 최종 정리
- [ ] 회고 및 Phase 2 기능 리스트 정리

---

## 마일스톤 요약

| 마일스톤 | 목표 시점 | 완료 기준 |
|----------|-----------|-----------|
| M1 — 설계 완료 | Week 2 末 | ERD, API 명세, 와이어프레임 확정 |
| M2 — 회원 + 관광지 구현 | Week 4 末 | 로그인·회원가입·관광지 조회·즐겨찾기 동작 |
| M3 — 일정 기능 구현 | Week 6 末 | 블록형 시간표·이동시간 자동 계산·저장 동작 |
| M4 — MVP 완성 | Week 7 末 | 커뮤니티 포함 전 Phase 1 기능 동작 |
| M5 — 배포 완료 | Week 8 末 | 실서비스 환경 배포 및 최종 QA 완료 |

---

## 리스크 및 대응

| 리스크 | 영향도 | 대응 방안 |
|--------|--------|-----------|
| 한국관광공사 API 할당량 초과 | 중 | 초기 데이터 일괄 적재 후 DB 캐싱, API 직접 호출 최소화 |
| ODsay API 응답 지연 | 중 | 이동 시간 조회 결과 캐싱, 타임아웃 처리 |
| 블록형 시간표 드래그 구현 복잡도 | 높 | Week 5 초에 프로토타입 검증, 필요 시 단순 순서 변경으로 축소 |
| 실시간 공동 편집 (G01) 범위 크리프 | 높 | Phase 2로 엄격히 분리, MVP에서 미포함 |
| 2인 개발 일정 지연 | 중 | 주간 진행 상황 체크, 기능 우선순위 재조정 |

---

*본 문서는 기획 초안으로, 설계·개발 진행 과정에서 지속적으로 업데이트됩니다.*
