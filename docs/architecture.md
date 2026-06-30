# 아키텍처 — TripCraft

시스템 전체 구성을 조망한다. 개별 기술의 상세 분석은 [features/](features/) 심화 노트로 분리했고,
이 문서는 그 지도 역할을 한다.

---

## 1. 시스템 컨텍스트

```
        ┌─────────────┐   REST(/api) · WS(/ws, STOMP)   ┌──────────────────┐
        │  Vue 3 SPA  │ ───────────────────────────────▶│  Spring Boot     │
        │  (브라우저) │ ◀─── ApiResponse<T> · TripEvent ─│  :8080           │
        └─────────────┘                                  └───────┬──────────┘
                                                                 │ MyBatis
                                                         ┌───────▼──────────┐
                                                         │  MySQL 8.0       │
                                                         └──────────────────┘
   외부 연동: 한국관광공사 TourAPI(관광지 수집) · ODsay·T Map(이동시간·경로)
             Naver Maps(지도 렌더) · Kakao(Local 검색·OAuth) · gms(Spring AI 챗봇)
```

조회 트래픽이 외부 API로 직접 가지 않도록, 관광지는 **사전 수집해 DB 적재**하고 이동시간은 **다층 캐싱**한다.

## 2. 기술 스택 · 레이어링

| Layer | 기술 |
|-------|------|
| Frontend | Vue 3 · Vite · Pinia · Vue Router → [frontend.md](frontend.md) |
| Backend | Java 21 · Spring Boot 3.5 · Spring Security(JWT) · MyBatis · Gradle(Kotlin DSL) |
| 실시간 | WebSocket · STOMP (in-memory broker) |
| AI | Spring AI (gms, OpenAI 호환 프록시) |
| DB | MySQL 8.0 |

요청 흐름: **Controller → Service(@Transactional) → MyBatis Mapper(XML) → MySQL**.
모든 REST 응답은 `ApiResponse<T>`로 감싸고, 예외는 `GlobalExceptionHandler`가 동일 형식으로 변환한다.

## 3. 백엔드 도메인 패키지

`com.tripcraft.*` — 도메인별 수직 분할(각 도메인 내부에 controller·service·mapper·dto·domain).

| 패키지 | 책임 |
|--------|------|
| `member` | 회원·인증(쿠키 JWT)·프로필·방문 지도 |
| `attraction` | 관광지 조회·검색·상세 + TourAPI 수집(`client`·`batch`·`service`) |
| `chat` | 관광지 AI 챗봇 (Spring AI, 주변 추천) |
| `place` | 장소 검색(Kakao Local)·내 커스텀 장소 |
| `plan` | 여행 일정·후보군·타임라인 블록·이동시간(`transit`)·실시간 협업(presence) |
| `community` | 게시글·댓글·좋아요·북마크·공지 |
| `global` | 횡단 관심사(아래 §4) |
| `common` | 공용 설정(`system_config` 등) |

## 4. 횡단 관심사 (`global/`)

| 영역 | 구성 | 비고 |
|------|------|------|
| 보안 | `security/` — `SecurityConfig`·`JwtAuthenticationFilter`·`Jwt*Interceptor`·`TripAccessVersion` | → [features/auth-security.md](features/auth-security.md) |
| 공통 응답 | `response/ApiResponse` | `{success,data,message,errorCode}` |
| 예외 | `exception/GlobalExceptionHandler` (`@RestControllerAdvice`) | HTTP·errorCode 매핑 → [api.md](api.md) §5 |
| WebSocket | `config/WebSocketConfig` | `/ws`(SockJS) · `/app`·`/topic`·`/queue` |
| 파일 | `storage/FileStorageService` + `attach/` | 다형 첨부(`target/target_id`), 디스크 저장 |
| 스케줄러 | `scheduler/OrphanImageCleanupScheduler` · `config/SchedulingConfig` | 고아 이미지 정리 |
| 정적 자원 | `config/WebMvcConfig` | `/uploads/**` 매핑 |

## 5. 캐싱 · 외부 호출 보호

- **이동시간 다층 캐시**: `transit_cache`(DB)·`lane_polyline`(DB)·in-memory 좌표/경로 캐시. 성공 결과만 캐시.
- **TourAPI 일일 한도**: `TourApiCallLimiter`가 `system_config`로 호출량을 추적·차단.
- 정밀도 설정: `transit_cache_level`(1~5)로 시간대 캐시 키 정규화.

## 6. 배포

호스트 nginx에 서브도메인으로 합류하는 단일 인스턴스(Docker Compose). 쿠키 `Secure` → HTTPS 필수,
STOMP in-memory broker → 백엔드 단일 인스턴스. 상세는 [setup.md](setup.md).

---

## 7. 핵심 기술 하이라이트

프로젝트의 어려운 문제와 해결을 한눈에. 상세는 각 심화 노트.

| # | 주제 | 한 줄 요약 | 심화 |
|---|------|-----------|------|
| 1 | 멀티모달 이동시간 | 외부 API 4종 합성 + 성공만 저장하는 다층 캐시 | [features/transit-routing.md](features/transit-routing.md) |
| 2 | 실시간 협업 동시성 | 낙관적 락 + grab 게이트 + 행 직렬화로 동시 편집 유실 방지 | [features/realtime-collab.md](features/realtime-collab.md) |
| 3 | 외부 데이터 동기화 | TourAPI 배치·증분(modifiedtime) + 일일 호출 한도 | [features/external-data-sync.md](features/external-data-sync.md) |
| 4 | 목록 조회 성능 | 프로필 이미지 N+1 → 파생 테이블 LEFT JOIN | [features/perf-profile-image.md](features/perf-profile-image.md) |
| 5 | 이미지 생명주기 | 다형 `attach` + draft 승격 + 고아 정리 | [features/image-lifecycle.md](features/image-lifecycle.md) |
| 6 | 인증·보안 | 무상태 쿠키 JWT + STOMP 프레임 권한·세대 캐시 | [features/auth-security.md](features/auth-security.md) |

## 8. 문서 지도
[api.md](api.md) · [database.md](database.md) · [frontend.md](frontend.md) · [setup.md](setup.md) ·
[conventions.md](conventions.md) · [design-system.md](design-system.md) · [features/](features/)
