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
- 기본 브랜치 이름을 `master`에서 `main`으로 변경(2026-11-22) — 기존 clone은 `git branch -m master main && git fetch origin && git branch -u origin/main main && git remote set-head origin -a`
- AI 챗봇 엔드포인트를 환경변수 `GMS_BASE_URL`로 분리 — `application.yml`의 base-url 하드코딩 제거. 기존 환경은 `.env`·`backend-secrets.env`에 값 추가 필요
- 공개 전환 전 저장소 이력 1회 재작성(2026-10-06) — 교육 기관 식별 문자열 정리. 작성자·날짜는 보존, 이전 커밋 SHA는 모두 변경됨
- **이동시간 재계산을 요청 스레드 밖으로 분리** — 편집 응답·DB 커넥션이 외부 이동시간 API를 기다리지 않는다. 일정별 순서 큐(`TripTaskQueue`)에서 실행하고 결과는 `TRANSIT_RECALCULATED`로 편집자 본인을 포함한 참가자에게 전달. 계산에 실패한 블록은 `failedBlockIds`로 알린다

### 보안
- HTTP API의 CORS 허용 origin을 설정값(`cors.allowed-origins`, 환경변수 `CORS_ALLOWED_ORIGINS`)으로 변경 — 이전에는 임의 origin + 자격증명 허용으로 고정돼 있었다. 미설정 시 기존처럼 전체 허용이므로 **운영 환경에는 값을 넣어야 한다**
- 권한을 잃은 세션의 실시간 구독을 서버가 끊음 — 협업자 제거·공유 해제 뒤에도 이미 맺은 구독으로 편집 이벤트를 계속 받던 문제. 구독 자격을 기억해 두었다가 변경이 커밋되면 다시 판정해 STOMP `ERROR`로 연결 종료(`TripSubscriptionGuard`)
- 실시간 채널 구독에 공유 링크 토큰 요구 — 소유자·협업자가 아니면(익명 포함) 공유 일정이라도 `share-token` 헤더가 일정의 토큰과 일치해야 구독된다. 이전에는 일정 id만으로 구독 가능했다
- 실시간 구독 경로를 허용 목록으로 제한 — `/topic/trip/{id}`·`/topic/trip/{id}/presence` 외의 구독은 거부. 이전에는 검사 밖 경로를 그대로 통과시켜, `/topic/**` 같은 와일드카드 구독으로 토큰·권한 없이 모든 일정의 편집 이벤트를 받을 수 있었다
- 인증 쿠키에 `SameSite=Lax` 지정
- WebSocket 엔드포인트 허용 origin을 설정값(`cors.allowed-origins`, 환경변수 `CORS_ALLOWED_ORIGINS`)으로 변경 — 미설정 시 기존처럼 전체 허용

### 수정
- **이동시간 API 호출 실패가 "경로 없음"으로 굳던 문제** — ODsay·TMap 호출이 실패해도(키 오류·한도·네트워크) 결과를 NONE으로 돌려주고, 관광지 간 구간은 그 NONE을 `transit_cache`에 저장해 장애가 끝나도 계속 "경로 없음"이었다. 재계산도 실패를 알지 못해 `failedBlockIds`가 비었다. ODsay 오류 코드 중 문서상 "경로 없음"(3·4·5·6·-98·-99)만 NONE으로, 나머지와 TMap 실패는 `TransitLookupException`으로 구분해 캐시하지 않고 재계산 실패 블록으로 알린다. REST `/api/transit`·`/by-coords` 응답은 예전처럼 NONE. 이미 저장된 NONE 캐시 행은 그대로 남는다
- **관광지 동기화 배치가 저장 실패 한 건으로 중단되던 문제** — 청크 UPSERT가 DB 오류로 실패하면 Step 전체가 FAILED로 끝나 그 뒤 지역이 수집되지 않았다. 쓰기 실패는 문제 행만 건너뛰도록 fault tolerant skip 적용(한도 100건). Processor javadoc의 "Skip" 표기를 실제 동작(filter)으로 정정
- **글 목록·협업자 목록의 프로필 이미지 조인이 행을 늘리던 문제** — 프로필 이미지 행이 둘 이상이면 같은 글·협업자가 그 수만큼 나왔다. 회원당 최신 1장만 붙이도록 조인 조건 수정
- **글 목록 정렬 인덱스 추가**(`idx_post_created_at`, `idx_post_popular`) — 목록 조회마다 전체 행을 정렬하던 것을 인덱스 스캔으로. 게시글 50,000건 기준 최신순 13.1ms → 0.06ms(`docs/features/perf-profile-image.md` 2026-11-21 실측). 기존 DB는 `docs/sql/migration_post_list_index_v1.sql` 적용 필요
- **블록 메모 덮어쓰기 방지** — grab 게이트는 다른 사람이 잡고 있을 때만 막아, 아무도 잡지 않은 순간에는 나중 저장이 앞선 저장을 덮어쓸 수 있었다. 편집 시작 때 본 메모(`baseMemo`)와 현재 메모가 다르면 409로 거부하는 조건부 갱신 추가(`version`은 그대로 미변경). blur 시 저장 완료 뒤에 grab을 반납하도록 순서 수정
- 게시글 수정 제목 길이 검증을 DB 컬럼(`post.title` VARCHAR(100))에 맞춤 — 101~200자 제목이 검증을 통과해 DB 오류로 이어지던 문제. 게시글 이미지 생명주기 통합테스트(`PostImageLifecycleIntegrationTest`) 추가
- **동시 배치 시 시간대 겹침·순서 중복** — 일정 행 `FOR UPDATE` 뒤의 겹침 검사·순서 할당이 REPEATABLE READ 스냅샷 때문에 먼저 커밋된 블록을 보지 못하던 문제. `placeBlock`·`updateBlock`을 `READ_COMMITTED`로 실행
- **이동시간 재계산 완료 알림(`TRANSIT_RECALCULATED`) 미발신** — afterCommit 콜백 안에서 콜백을 다시 등록해 전송되지 않던 문제
- Testcontainers 통합테스트가 Docker Engine 29에서 `400 BadRequest`로 기동 실패하던 문제 — Spring Boot가 고정한 관리 버전(1.21.0)을 1.21.4로 올림. 실 MySQL 통합테스트 `TripCollabIntegrationTest`(동시 배치·낙관적 락·afterCommit 알림) 추가
- deploy 자산의 스키마 경로 교정 — `docs/02_design/schema.sql`(구 문서 구조) → `docs/sql/schema.sql`. `deploy/docker-compose.yml`·`deploy/local/mysql.Dockerfile`의 mysql initdb 가 스키마를 찾지 못하던 문제 해결
- `JWT_SECRET` 예시값을 유효한 base64 로 교정 — `JwtTokenProvider`가 `Decoders.BASE64.decode`로 해석하는데 기존 예시(`-` 포함)가 base64 파싱에 실패해 백엔드가 기동 crash-loop 하던 문제. 예시 파일 2종 + 테스트 프로파일 반영

---

## [1.0.0] - 2026-06-25 — 캡스톤 제출본

2인 팀(전진·송정기) 캡스톤 최종 제출본. 태그 `v1.0-capstone`.

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
