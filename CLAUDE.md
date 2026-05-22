# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

> **수정 규칙**: 이 파일과 `.claude/*.md` 파일은 수정 전 반드시 변경 내용을 사용자에게 설명하고 승인을 받을 것.

---

## 프로젝트

**TripCraft** — 관광지 탐색 → 후보군 등록 → 드래그 앤 드롭 일정 확정 → ODsay 이동 시간 자동 계산 → 커뮤니티 공유. 개발 2인 (전진·송정기). 현재 백엔드 Auth API 구현 완료, 프론트엔드 UI 초안 작성 중.

## 기술 스택

| Layer | 기술 |
|-------|------|
| Backend | Java 21 · Spring Boot 3.5.0 · MyBatis · MySQL 8.0 · Gradle (Kotlin DSL) |
| Frontend | Vanilla HTML/CSS/JS (추후 Vue.js 3 + Vite 마이그레이션 예정) |
| Auth | Spring Security + JWT |
| External API | 한국관광공사 TourAPI 4.0 · ODsay API · Kakao Maps |

## 글로벌 규칙

- MyBatis는 `#{}` 바인딩만 사용. `${}` 사용 금지 (SQL Injection)
- 권한 검증은 반드시 서버에서 수행. 클라이언트 권한만으로 처리 금지
- API 공통 응답 형식: `{ "success": true/false, "data": {}, "message": null, "errorCode": null }`
- 코드 작성 전 `docs/03_dev/conventions.md` 참조

## Git 컨벤션

브랜치: `main` ← PR from `develop` ← `feature/{도메인-기능명}` / `fix/{이슈명}` / `docs/{내용}`

커밋: Conventional Commits — `feat(member): JWT refresh token 갱신 로직 구현`

타입: `feat` · `fix` · `refactor` · `test` · `docs` · `style` · `chore` · `perf`

## docs 업데이트 정책

- `docs/03_dev/conventions.md`, `docs/01_planning/requirements.md` — 결정 사항 변경 즉시 업데이트 (승인 후)
- 나머지 docs (`wbs.md`, `wireframe_spec.md` 등) — 제출 전 일괄 정리

## 기능별 컨텍스트 (필요할 때 읽을 것)

| 작업 도메인 | 참조 파일 |
|------------|----------|
| 회원·인증·보안 | `.claude/member.md` |
| 관광지 조회·수집 | `.claude/attraction.md` |
| 여행 일정·이동 시간 | `.claude/plan.md` |
| 커뮤니티·공지 | `.claude/community.md` |
