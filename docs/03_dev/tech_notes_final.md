# 핵심 기술 노트 — TripCraft (final)

> **버전**: final (2026.06)
> **목적**: 프로젝트의 핵심 알고리즘·설계 결정을 한 문서에서 조망. 발표(PPT "적용 패턴·핵심 알고리즘") 원천.
> **구성**: 주제별 요약 + 발표 포인트 + 상세 원문 링크. 상세 분석은 각 원문 문서가 보유.

---

## 한눈에 — 기술 난이도 지도

| # | 주제 | 핵심 도전 | 적용 패턴 | 원문 |
|---|------|-----------|-----------|------|
| 1 | 멀티모달 이동시간 | 외부 API 4종 조합 + 호출 제한 | 다중 호출 오케스트레이션 + 다층 캐시 | [transit_external_api.md](transit_external_api.md) |
| 2 | 실시간 협업 동시성 | 동시 편집 데이터 유실 방지 | 낙관적 락 + grab 게이트 + 행 직렬화 | [collab_concurrency_decision.md](collab_concurrency_decision.md) |
| 3 | 외부 데이터 동기화 | TourAPI 대량·증분 적재 | 배치 + 증분(modifiedtime) + 폴리라인 캐시 | (본 문서 §3) |
| 4 | 목록 조회 성능 | 프로필 이미지 N+1 | 파생 테이블 LEFT JOIN | [perf_n1_profile_image.md](perf_n1_profile_image.md) |
| 5 | 이미지/첨부 관리 | 다형 참조 + 생명주기 | `attach` target/target_id + draft 정리 | [image_upload_lifecycle.md](image_upload_lifecycle.md) |
| 6 | 인증·보안 | 무상태 인증 + WS 권한 | 쿠키 JWT + STOMP 채널 인터셉터 | (본 문서 §6) |

---

## 1. 멀티모달 이동시간 — 외부 API 오케스트레이션 + 다층 캐싱

**무엇이 어려웠나.** 두 장소 사이 "이동수단별 경로" 하나에 **외부 API 4종**(ODsay 대중교통, T Map 자동차·도보, Kakao Local 검색, Naver Maps 렌더)이 얽힌다. 한 번의 호출로 끝나지 않는다 — 도시간(KTX) 경로는 "역→역"만 주므로 **출발지→역, 역→목적지 접근 구간을 추가 호출로 합성**하고, 지도 폴리라인은 또 별도 호출(ODsay `loadLane`)로 조립한다.

**핵심 알고리즘.**
- **경로 보강**(`enrichPublicTransit`): 도시간 경로마다 로컬 경로를 추가 호출해 `[출발지→역]+[KTX]+[역→목적지]`로 합성, 실패 시 하버사인 거리 추정으로 폴백.
- **폴리라인 3단 폴백**: `loadLane`(실폴리라인) → `passStopList`(정류장 좌표) → 구간 양끝 직선.
- **자동차 4옵션**(T Map): 추천/최단/무료/최소를 각각 호출해 비교.

**다층 캐시(핵심 설계).** 무료 API는 같은 좌표 반복 호출 시 빈 응답(rate limit)을 준다 → ① `transit_cache`(DB, 관광지쌍·시간대·모드 키) ② `lane_polyline`(DB, 노선 형상) ③ in-memory 좌표 캐시(커스텀 장소) ④ in-memory 로컬경로 캐시(보강 중복 제거). **성공 결과만 캐시**(빈 응답 캐시 시 영구 빈값 고착 방지).

**발표 포인트.** ① 단일 API로 안 끝남 → 다중 호출 합성 ② 무료 API의 현실은 rate limit → 8연타 실험으로 규명, 성공만 저장하는 다층 캐시로 해결 ③ 보강 로직을 좌표 기반으로 추출해 관광지·커스텀 장소가 동일 파이프라인 공유.

---

## 2. 실시간 협업 동시성 — 낙관적 락 + grab + 행 직렬화

**무엇이 어려웠나.** 여러 사용자가 같은 일정을 동시에 드래그앤드롭으로 편집한다. "즉시 저장 + STOMP 브로드캐스트"는 동작했지만 편집 경로에 **안전장치가 없어** 같은 블록을 둘이 옮기면 나중 요청이 앞 요청을 조용히 덮어썼다(Last-Write-Wins, 데이터 유실).

**핵심 결정 — "무엇을 충돌로 볼 것인가".** 버전이 다르다고 무조건 막으면 안 된다. 같은 블록 동시 수정은 막되, 서로 다른 블록을 옮기는 정상 동시 작업과 시스템의 transit 재계산은 충돌로 오인하면 안 된다.

**적용 패턴(2층 방어).**
- **낙관적 락**(`trip_block.version`): 사용자 편집은 `WHERE id=? AND version=?` 조건부 UPDATE → 0행이면 409 → 프론트가 재조회 후 재시도. `version`은 같은 row에만 작용해 무관한 블록 편집을 구조적으로 비간섭.
- **transit 분리**: 이동시간 재계산 UPDATE는 **version을 건드리지 않음**(오탐 방지의 전제). 외부 API 호출은 `afterCommit`으로 트랜잭션 밖으로 빼 DB 커넥션 장기 점유 해소.
- **grab 서버 게이트**(소프트 선제): 드래그 중인 블록은 다른 사용자가 아예 못 만지게 차단.
- **시간대 겹침 금지**: 여러 행에 걸친 제약이라 `SELECT ... FROM trip WHERE id=? FOR UPDATE`로 일정 편집을 직렬화해 race 방지.

**발표 포인트.** ① 충돌 기준을 행렬로 정의(같은 블록=하드 차단, 다른 블록=비간섭, transit=무간섭) ② 낙관적 락(사후) + grab(사전) 2층 ③ 표시용 grab을 권위적 게이트로 승격하며 드러난 release 누락 버그 → "생명주기 전수 재점검" 교훈 ④ CRDT/OT는 의미 충돌을 못 없애고 ROI가 낮아 거부·재시도 정책 채택.

---

## 3. 외부 데이터 동기화 — TourAPI 배치·증분 + 참조 데이터

**핵심.** 한국관광공사 TourAPI `areaBasedList2`로 전국 관광지를 **배치 수집**해 `attraction` 테이블에 적재(조회 시 외부 API 미호출). 6개 콘텐츠 타입(관광지·문화시설·레포츠·숙박·쇼핑·음식점) × 지역으로 순회.

**증분 동기화.** 각 행에 `api_modified_at`(TourAPI `modifiedtime`)을 저장하고 `idx_api_modified` 인덱스로 변경분만 갱신. 시도·시군구 참조(`sido`/`sigungu`)는 `areaCode2`로 동기화하되 표시용 `alias`는 보존.

**상세 데이터 지연 로딩.** 상세(`detailCommon/Intro/Image/Info`)는 별도 테이블로 분리, `intro_data`·`room_data`는 콘텐츠 타입별 가변 필드라 JSON 컬럼으로 저장(검색 필요 시 컬럼 승격).

**엔드포인트.** `POST /api/admin/attractions/sync`(전체)·`/sync/partial`(부분)·`/sync/regions`(참조) — ADMIN 전용.

---

## 4. 목록 조회 성능 — 프로필 이미지 N+1 제거

**문제.** 게시글 목록 5개 쿼리가 작성자 프로필 이미지를 **상관 서브쿼리**로 가져와, `size=10`이면 항상 11회 DB 왕복(MySQL은 Dependent Subquery로 처리, 자동 JOIN 최적화 안 함).

**해결(채택안).** `attach`를 **파생 테이블 LEFT JOIN**으로 한 번만 읽고 해시 조인.
```sql
LEFT JOIN (SELECT target_id, name FROM attach WHERE target='profile') pa ON pa.target_id = m.id
```
→ 11회 → **1회**. `EXPLAIN`이 `DEPENDENT SUBQUERY` → `DERIVED`+`ref`로 변경. MyBatis는 `profileImageCol`/`profileImageJoin` fragment로 분리해 5개 쿼리(cross-namespace include 포함)에 재사용.

**남은 N+1.** 댓글 수 집계는 전체 `post_comment` GROUP BY 비용이 있어 게시글-댓글 비율을 보고 조건부 적용으로 보류.

---

## 5. 이미지/첨부 관리 — 다형 참조 + draft 생명주기

**다형 참조.** `attach` 테이블이 `target`(`profile`·`post`·`post_draft`·`post_cover_draft`) + `target_id`로 회원/게시글 이미지를 **DB FK 없이** 통합 관리. 파일은 디스크(UUID 파일명), DB엔 메타데이터(경로·크기·MIME)만.

**구현된 생명주기.** 업로드 시 `post_draft`/`post_cover_draft`(target_id=memberId)로 임시 등록 → 게시글 확정 시 `post`(target_id=post.id)로 승격 → 대표사진은 회원당 1장 유지(업로드 시 기존 draft 정리). 프로필 이미지도 동일 패턴(업로드 시 기존 삭제 후 교체).

> 참고: 원문 `image_upload_lifecycle.md`는 초기 MVP 시점 작성이라 "컨트롤러 직접 I/O·DB 미추적"으로 기술돼 있으나, **현재 코드는 `FileStorageService` 위임 + `attach` draft 추적이 적용된 상태**다. 남은 과제: magic bytes 검증, 고아 파일 정리 스케줄러.

---

## 6. 인증·보안

- **무상태 쿠키 JWT.** 로그인 시 `access_token`·`refresh_token`을 HttpOnly 쿠키로 발급, `JwtAuthenticationFilter`가 access 쿠키 검증 → SecurityContext에 `memberId`·`role` 적재. 만료 시 refresh로 재발급.
- **서버 권한 검증 원칙.** 모든 권한은 서버에서 검증(`SecurityConfig` 경로 규칙 + 서비스 레이어). 클라이언트 권한만으로 처리 금지.
- **WebSocket 채널 권한.** `JwtChannelInterceptor`가 SUBSCRIBE(`/topic/trip/{id}`)는 조회 권한, SEND(`/app/trip/{id}/...`)는 편집 권한을 프레임 단계에서 검증(세션 캐시). 협업자 제거/공유 변경 시 `TripAccessVersion` 세대 카운터를 bump해 다음 프레임에 재검증.
- **SQL Injection 방지.** MyBatis `#{}` 바인딩만 사용(`${}` 금지).

---

## 캐시 정밀도 설정 (`system_config`)

이동시간 캐시는 `transit_cache_level`(1~5)로 시간대 정밀도를 조절한다 — 레벨1(시간 무시) ~ 레벨5(시간별). 러시아워 경계·레벨별 대표 시간을 `system_config`에 두어 관리자 화면에서 조정 가능. 캐시 키의 `departure_hour`가 레벨에 따라 대표 시간으로 정규화된다.
