# Changelog

이 프로젝트의 모든 주요 변경 사항을 기록한다.
형식은 [Keep a Changelog](https://keepachangelog.com/ko/1.1.0/)를 따르며, 버전은 [유의적 버전(SemVer)](https://semver.org/lang/ko/)을 준수한다.

## [Unreleased]

### 추가
- **일정 블록 메모 실시간 공유**(개선 01) — 일정 블록에 최대 100자 메모, 협업자에게 STOMP 실시간 반영. `PATCH /api/trips/{id}/blocks/{blockId}/memo`, `BLOCK_MEMO_UPDATED` 이벤트, idle(≈600ms)+blur 자동 저장, grab 잠금 재사용(version 미변경)
- 개선 계획 문서(`docs/improvements/`) 신설 — 01 일정 블록 메모 실시간 공유, 02 대량 장소 확보 + 대용량 처리 성능 벤치마크
- **테스트 기반 구축** — 백엔드 서비스 단위테스트(`TripServiceImplTest`: 메모·grab·시간겹침·낙관적 락·권한, Mockito) + Testcontainers(MySQL 8) 통합테스트 골격(`AbstractIntegrationTest`, `@Tag("integration")`). 기본 `./gradlew test`는 Docker 없이 단위테스트만 실행, `integrationTest` 태스크로 컨테이너 테스트 분리
- 개발 일지 폴더(`docs/logs/`) 신설 — 1인 개발 단계의 날짜별 내러티브 작업 일지(동결 아카이브 `docs/capstone-1.0/logs/`의 관행을 Living 문서로 계승)
- 트러블슈팅 기록 문서(`docs/troubleshooting.md`) 신설 — fix/perf 커밋을 도메인별 증상·원인·해결로 정리

### 변경
- 문서 체계 재편: Living 문서(`docs/*`) + 캡스톤 동결 아카이브(`docs/capstone-1.0/`) 분리, 중복 산출물 정리
- 저장소 GitHub 단일화(Public), 1인 trunk-based 개발 모델로 전환

### 수정
- deploy 자산의 스키마 경로 교정 — `docs/02_design/schema.sql`(구 문서 구조) → `docs/sql/schema.sql`. `deploy/docker-compose.yml`·`deploy/local/mysql.Dockerfile`의 mysql initdb 가 스키마를 찾지 못하던 문제 해결
- `JWT_SECRET` 예시값을 유효한 base64 로 교정 — `JwtTokenProvider`가 `Decoders.BASE64.decode`로 해석하는데 기존 예시(`-` 포함)가 base64 파싱에 실패해 백엔드가 기동 crash-loop 하던 문제. 예시 파일 2종 + 테스트 프로파일 반영

---

## [1.0.0] - 2026-06-25 — 캡스톤 제출본

SSAFY 11기 자율 프로젝트 팀(전진·송정기) 최종 제출본. 태그 `v1.0-capstone`.

### 추가
- **회원·인증**: 회원가입/로그인, JWT(HttpOnly 쿠키) 인증, 카카오 소셜 로그인, 마이페이지
- **관광지**: 한국관광공사 TourAPI 기반 전국 관광지 DB 수집, 지역·카테고리별 조회, 상세 정보
- **여행 일정**: 후보 장소 등록, 드래그앤드롭 일정 확정, Naver Maps 연동
- **이동시간 자동 계산**: ODsay·T Map 연동, 대중교통 구간·도보 경로 지도 시각화, 다층 캐싱
- **실시간 협업**: STOMP 기반 동시 일정 편집, 낙관적 락 + grab 게이트 동시성 제어
- **커뮤니티**: 여행 일정 공유 게시판, 댓글·좋아요, 공지사항
- **AI 챗봇**: Spring AI 기반 관광지 주변 추천
- **프론트엔드**: Vue 3 + Vite 마이그레이션 및 전 도메인 API 연동
- **배포**: Docker Compose(nginx + backend + MySQL) 단일 호스트 배포

[Unreleased]: https://github.com/DreamingGenie/TripCraft/compare/v1.0-capstone...HEAD
[1.0.0]: https://github.com/DreamingGenie/TripCraft/releases/tag/v1.0-capstone
