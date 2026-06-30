# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **수정 규칙**: 이 파일과 `.claude/*.md` 파일은 수정 전 반드시 변경 내용을 사용자에게 설명하고 승인을 받을 것.

---

## 프로젝트

**TripCraft** — 관광지 탐색 → 후보군 등록 → 드래그 앤 드롭 일정 확정 → ODsay 이동 시간 자동 계산 → 커뮤니티 공유. 개발 2인 (전진·송정기). **현재 상태**: 백엔드 전 도메인 API 구현 완료(Auth·Attraction·Plan·Community·Transit·Chat), 프론트엔드 Vue 3 마이그레이션 및 API 연동 완료, TourAPI 관광지 DB 수집 완료, Naver Maps 연동 완료, ODsay 이동 시간 자동 계산 완료, 대중교통 구간·도보 경로 지도 시각화 완료, 관광지 AI 챗봇(Spring AI·주변 추천) 완료.

## 기술 스택

| Layer | 기술 |
|-------|------|
| Backend | Java 21 · Spring Boot 3.5.0 · MyBatis · MySQL 8.0 · Gradle (Kotlin DSL) |
| Frontend | Vue.js 3 + Vite · Pinia · Vue Router (마이그레이션 완료) |
| Auth | Spring Security + JWT |
| External API | 한국관광공사 TourAPI 4.0 · ODsay API · Naver Maps · T Map · gms(OpenAI 호환 프록시, Spring AI) |

## 글로벌 규칙

- MyBatis는 `#{}` 바인딩만 사용. `${}` 사용 금지 (SQL Injection)
- 권한 검증은 반드시 서버에서 수행. 클라이언트 권한만으로 처리 금지
- API 공통 응답 형식: `{ "success": true/false, "data": {}, "message": null, "errorCode": null }`
- 코드 작성 전 `docs/conventions.md` 참조

## 저장소 · 개발 모델

- **원격**: origin = GitHub `DreamingGenie/TripCraft` (SSAFY GitLab 미러 종료). **Public** 포트폴리오 저장소.
- **개발 모델**: 캡스톤(2인) 종료 후 **1인 추가 개발** 단계. `master` 단일 소스(코드+문서)를 기준으로
  **trunk-based** — 작업은 짧은 `feature/*` 브랜치에서 하고 `master`로 병합. (팀 시절 `main←develop` GitFlow 폐기)
- **버전 경계**: `v1.0-capstone` 태그 = 팀 캡스톤 최종 제출본 = 1인 개발 분기점. 히스토리는 rewrite 금지(기여 이력 보존).

## Git 컨벤션

브랜치: `master` ← `feature/{도메인-기능명}` / `fix/{이슈명}` / `docs/{내용}`

커밋: Conventional Commits — `feat(member): JWT refresh token 갱신 로직 구현`

타입: `feat` · `fix` · `refactor` · `test` · `docs` · `style` · `chore` · `perf`

## MR 설명 컨벤션

제목: `feat(도메인): 기능 요약` (커밋 컨벤션과 동일)

본문 구조:
1. **개요** — 이번 MR에서 해결한 문제/목적 1~2문장
2. **변경 내용** — 기능 단위로 섹션 구분
   - 섹션 제목: 어떤 기능인지
   - 섹션 내용: 해당 기능과 관련된 파일 목록 + 각 파일에서 한 작업 (`| 파일 | 작업 |` 표 형식)

기능 중심으로 묶는 이유: 파일 중심으로 나열하면 나중에 "이 파일이 왜 바뀌었는지"를 역추적해야 하지만,
기능 중심으로 묶으면 변경의 의도와 범위를 한눈에 파악할 수 있다.

## docs 정책

- **Living 문서**(`docs/*.md`, `docs/features/`, `docs/sql/`) — 코드 변경에 맞춰 유지보수. `docs/conventions.md` 등 결정 사항 변경 즉시 업데이트(승인 후).
- **동결 아카이브**(`docs/capstone-1.0/`) — 캡스톤 제출 상태(as-submitted) 보존. **수정 금지.**
- 변경 이력은 루트 `CHANGELOG.md`(Keep a Changelog) + GitHub Releases.

## 기능별 컨텍스트 (필요할 때 읽을 것)

| 작업 도메인 | 참조 파일 |
|------------|----------|
| 회원·인증·보안 | `.claude/member.md` |
| 관광지 조회·수집 | `.claude/attraction.md` |
| 여행 일정·이동 시간 | `.claude/plan.md` |
| 커뮤니티·공지 | `.claude/community.md` |
| AI 챗봇·주변 추천 | `.claude/chat.md` |
