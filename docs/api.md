# API 명세서

> REST 약 80개 + WebSocket(STOMP) 실시간 협업 채널. 16개 컨트롤러 기준.

> **Swagger UI (인터랙티브 탐색)** — 백엔드 실행 후 `http://localhost:8080/swagger-ui.html` 접속.
> 컨트롤러는 `@Tag`로 그룹화돼 있고 OpenAPI 스펙은 `/v3/api-docs`에서 제공된다.
> 인증은 `access_token` HttpOnly 쿠키 기반이라, 먼저 `POST /api/auth/login`을 실행해 로그인하면
> 이후 "Try it out" 호출에 브라우저가 쿠키를 자동 전송한다. 본 문서는 그 위의 사람이 읽는 명세다.

---

## 1. 개요

### 1-1. 공통 응답 형식
모든 REST 응답은 `ApiResponse<T>` 래퍼로 감싼다. (`JsonInclude.NON_NULL` — null 필드는 직렬화 생략)

```json
{ "success": true, "data": { }, "message": null, "errorCode": null }
```

| 필드 | 타입 | 설명 |
|------|------|------|
| `success` | boolean | 성공 여부 |
| `data` | T \| null | 성공 시 페이로드 (없으면 생략) |
| `message` | string \| null | 실패 시 사용자 메시지 |
| `errorCode` | string \| null | 실패 시 에러 코드 |

### 1-2. 인증 방식
- **JWT (쿠키 기반)**. 로그인 성공 시 서버가 `access_token`·`refresh_token`을 **HttpOnly 쿠키**로 발급.
- 보호된 요청: `JwtAuthenticationFilter`가 `access_token` 쿠키를 검증 → `memberId`·`role`을 SecurityContext에 적재.
- 토큰 만료 시: `POST /api/auth/refresh` (refresh_token 쿠키 사용)로 재발급.
- 컨트롤러는 `@AuthenticationPrincipal Long memberId`로 현재 회원 식별.
- CORS: `allowCredentials=true`, 메서드 `GET/POST/PUT/DELETE/PATCH/OPTIONS` 허용.

### 1-3. 권한 레벨 (`SecurityConfig` 기준)
| 레벨 | 의미 |
|------|------|
| **공개** | 비로그인 호출 가능 (permitAll) |
| **인증** | 로그인 필요 (anyRequest authenticated) |
| **인증\*** | 보안상 공개 경로지만 `memberId`가 있어야 의미 있는 응답(내 데이터 조회) |
| **ADMIN** | `ROLE_ADMIN` 필요 |

**공개 경로**: `POST /api/auth/{login,signup,refresh,logout,kakao}`, `GET /api/attractions/**`,
`GET /api/posts/**`, `GET /api/notices/**`, `GET /api/trips/*/blocks-summary`, `GET /api/trips/shared/**`,
`GET /uploads/**`, `/ws/**`, Swagger 경로.
**ADMIN 전용**: `/api/admin/**`, `POST·PUT·DELETE /api/notices/**`.

---

## 2. 엔드포인트 카탈로그

### 2-1. 인증 (`/api/auth`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| POST | `/api/auth/signup` | 공개 | 회원가입 | `SignupRequest` |
| POST | `/api/auth/login` | 공개 | 로그인(쿠키 발급) | `LoginRequest` |
| POST | `/api/auth/kakao` | 공개 | 카카오 소셜 로그인 | `{ code }` |
| POST | `/api/auth/refresh` | 공개 | 액세스 토큰 재발급 | refresh_token 쿠키 |
| POST | `/api/auth/logout` | 공개 | 로그아웃(토큰 무효화) | refresh_token 쿠키 |
| GET | `/api/auth/me` | 인증 | 내 정보 조회 | — |

### 2-2. 회원 (`/api/members`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| PATCH | `/api/members/me/nickname` | 인증 | 닉네임 변경(중복 검사) | `UpdateNicknameRequest` |
| PATCH | `/api/members/me/password` | 인증 | 비밀번호 변경(현재 PW 확인) | `UpdatePasswordRequest` |
| GET | `/api/members/me/profile-image` | 인증 | 프로필 이미지 URL 조회 | — |
| POST | `/api/members/me/profile-image` | 인증 | 프로필 이미지 업로드(교체) | `multipart: file` |
| DELETE | `/api/members/me/profile-image` | 인증 | 프로필 이미지 삭제 | — |
| GET | `/api/members/me/visited-regions` | 인증 | 방문 시도 코드 목록(내 지도) | — |
| GET | `/api/members/search?q=` | 인증 | 협업자 초대용 회원 검색(닉네임·이메일) | query `q` |
| DELETE | `/api/members/me` | 인증 | 회원 탈퇴(비밀번호 확인, 하드 딜리트) | `WithdrawRequest` |

#### 방문 지도 (후기 사진 기반)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| GET | `/api/members/me/map` | 인증 | 시도별 방문/예정·표지·후기 수 일괄 조회 | — |
| GET | `/api/members/me/map/regions/{sidoCode}/stories` | 인증 | 표지 선택용 여행이야기(글) 목록 | path |
| GET | `/api/members/me/map/regions/{sidoCode}/posts/{postId}/images` | 인증 | 글 사진(커버·본문) 목록 | path |
| PUT | `/api/members/me/map/cover` | 인증 | 지역 표지 지정(후보 사진 선택) | `CoverImageRequest` |
| POST | `/api/members/me/map/cover/upload` | 인증 | 지역 표지 지정(직접 업로드) | `multipart: regionCode,file` |
| PATCH | `/api/members/me/map/cover/crop` | 인증 | 표지 crop(초점/확대) 갱신 | `CoverCropRequest` |
| DELETE | `/api/members/me/map/cover/{sidoCode}` | 인증 | 지역 표지 해제(기본값 복귀) | path |

### 2-3. 관광지 (`/api/attractions`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| GET | `/api/attractions/regions` | 공개 | 시도+시군구 트리 조회 | — |
| GET | `/api/attractions` | 공개 | 관광지 검색(페이지) | query: `keyword,region,sigungu,category,page,size` |
| GET | `/api/attractions/{id}` | 공개 | 관광지 상세(detail* 포함) | path `id` |

### 2-4. 관광지 AI 챗봇 (`/api/attractions`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| POST | `/api/attractions/{id}/chat` | 인증 | 관광지 컨텍스트 멀티턴 Q&A(Spring AI) | `AttractionChatRequest{ message, conversationId }` |

### 2-5. 장소 검색 / 내 장소 (`/api/places`, `/api/my-places`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| GET | `/api/places/search?query=` | 인증 | Kakao Local 키워드 장소 검색 | query `query` |
| GET | `/api/my-places` | 인증 | 내 커스텀 장소 목록 | — |
| POST | `/api/my-places` | 인증 | 내 커스텀 장소 등록 | `MemberPlaceRequest` |
| DELETE | `/api/my-places/{id}` | 인증 | 내 커스텀 장소 삭제 | path `id` |

### 2-6. 여행 일정 (`/api/trips`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| GET | `/api/trips` | 인증 | 내 일정 목록 | — |
| GET | `/api/trips/collaborating` | 인증 | 내가 협업자인 일정 목록 | — |
| POST | `/api/trips` | 인증 | 일정 생성 | `TripCreateRequest` |
| GET | `/api/trips/{id}` | 인증 | 일정 상세(소유자·협업자) | path `id` |
| DELETE | `/api/trips/{id}` | 인증 | 일정 삭제 | path `id` |
| PUT | `/api/trips/{id}/share` | 인증 | 공유 링크 접근레벨 설정 → `{access,token}` | `{ access }` |
| GET | `/api/trips/shared/{token}` | 공개 | 공유 토큰으로 일정 조회 | path `token` |
| POST | `/api/trips/{tripId}/copy` | 인증 | 공유 일정 내 일정으로 복제(날짜 재계산) | `TripCopyRequest` |
| PATCH | `/api/trips/{tripId}/default-transit-mode` | 인증 | 기본 이동수단 변경 | `{ mode }` |
| GET | `/api/trips/{id}/blocks-summary` | 공개 | 블록 요약(공유 미리보기) | path `id` |

#### 협업자
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| GET | `/api/trips/{id}/collaborators` | 인증 | 협업자 목록 | path `id` |
| POST | `/api/trips/{id}/collaborators` | 인증 | 협업자 초대 | `{ memberId, role=EDITOR\|VIEWER }` |
| DELETE | `/api/trips/{id}/collaborators/{targetMemberId}` | 인증 | 협업자 제거 | path |

#### 후보군(보관함)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| POST | `/api/trips/{id}/candidates` | 인증 | 관광지 후보 추가 | `CandidateAddRequest{ attractionId }` |
| POST | `/api/trips/{id}/candidates/custom` | 인증 | 커스텀 장소 후보 추가 | `CustomCandidateRequest` |
| POST | `/api/trips/{id}/candidates/from-place/{placeId}` | 인증 | 내 장소에서 후보 추가 | path |
| DELETE | `/api/trips/{id}/candidates/{candidateId}` | 인증 | 후보 삭제(블록 있으면 RESTRICT) | path |

#### 타임라인 블록
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| POST | `/api/trips/{id}/blocks` | 인증 | 블록 배치(이동시간 자동 계산) | `BlockCreateRequest` |
| PUT | `/api/trips/{id}/blocks/{blockId}` | 인증 | 블록 수정(시간·순서·메모, 낙관적 락) | `BlockUpdateRequest` |
| DELETE | `/api/trips/{id}/blocks/{blockId}` | 인증 | 블록 삭제 | path |

### 2-7. 이동 시간 (`/api/transit`) — ODsay·T Map
| Method | Path | 권한 | 설명 | 요청(query) |
|--------|------|------|------|------|
| GET | `/api/transit` | 인증 | 두 관광지 간 이동시간 | `fromId,toId,hour,mode` |
| GET | `/api/transit/by-coords` | 인증 | 좌표 기반 이동시간(커스텀) | `fromLat,fromLng,toLat,toLng,hour,mode` |
| GET | `/api/transit/detail` | 인증 | 대중교통 경로 단계 상세 | `fromId,toId,hour` |
| GET | `/api/transit/by-coords/detail` | 인증 | 좌표 기반 경로 단계 상세 | 좌표 4 + `hour` |
| GET | `/api/transit/driving-options` | 인증 | 자동차 단일 옵션 조회 | `fromId,toId,hour,optionIndex` |
| GET | `/api/transit/by-coords/driving-options` | 인증 | 좌표 기반 자동차 옵션 | 좌표 4 + `hour,optionIndex` |
| POST | `/api/transit/select` | 인증 | 대중교통 경로 선택(pathIndex) | `fromId,toId,hour,pathIndex` |
| POST | `/api/transit/select-driving` | 인증 | 자동차 옵션 적용 | `fromId,toId,hour,optionIndex` |
| GET | `/api/transit/route-segments` | 인증 | 경로 구간(색·도보·역마커) | `fromId,toId,hour` |
| GET | `/api/transit/by-coords/route-segments` | 인증 | 좌표 기반 경로 구간 | 좌표 4 + `hour` |
| GET | `/api/transit/walking-coords` | 인증 | 도보 경로 좌표 | `startLat,startLng,endLat,endLng` |

> `mode` = `PUBLIC_TRANSIT`(기본) · `DRIVING` · `WALKING`. `hour` 기본 9(출발 시각, 캐시 키).

### 2-8. 커뮤니티 게시글 (`/api/posts`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| GET | `/api/posts` | 공개 | 게시글 목록(정렬·검색·페이지) | query: `page,size,sort=latest\|popular,keyword` |
| GET | `/api/posts/me` | 인증\* | 내 게시글 목록 | query: `page,size` |
| POST | `/api/posts` | 인증 | 일정 공유 게시글 작성 | `PostCreateRequest` |
| GET | `/api/posts/{id}` | 공개 | 게시글 상세(공유 일정 뷰어 포함) | path `id` |
| PATCH | `/api/posts/{id}` | 인증 | 게시글 수정 | `PostUpdateRequest` |
| DELETE | `/api/posts/{id}` | 인증 | 게시글 삭제(소프트 딜리트) | path `id` |
| POST | `/api/posts/{id}/likes` | 인증 | 좋아요 토글 | path `id` |

### 2-9. 댓글 (`/api/posts/{postId}/comments`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| GET | `/api/posts/{postId}/comments` | 공개 | 댓글·대댓글 목록 | path `postId` |
| POST | `/api/posts/{postId}/comments` | 인증 | 댓글/대댓글 등록 | `CommentCreateRequest{ content, parentId? }` |
| DELETE | `/api/posts/{postId}/comments/{commentId}` | 인증 | 댓글 삭제(본인/ADMIN) | path |

### 2-10. 좋아요·북마크 (`/api/likes`, `/api/posts`, `/api/bookmarks`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| GET | `/api/likes/me` | 인증\* | 내가 좋아요한 글 목록 | query: `page,size` |
| POST | `/api/posts/{postId}/bookmark` | 인증 | 북마크 토글 | path `postId` |
| GET | `/api/bookmarks/me` | 인증\* | 내 북마크 목록(삭제글 표시) | query: `page,size` |

### 2-11. 공지사항 (`/api/notices`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| GET | `/api/notices` | 공개 | 최신 공지 5건(사이드바) | — |

> 공지 작성/수정/삭제(`POST·PUT·DELETE /api/notices/**`)는 ADMIN 전용으로 보안 설정됨.

### 2-12. 이미지 (`/api/images`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| POST | `/api/images/upload` | 인증 | 게시글 이미지/대표사진 업로드(draft) | `multipart: file`, query `type=cover?` |
| DELETE | `/api/images/cover-draft` | 인증 | 임시 대표사진 정리 | — |

### 2-13. 관리자 — 관광지 동기화 (`/api/admin/attractions`)
| Method | Path | 권한 | 설명 | 요청 |
|--------|------|------|------|------|
| POST | `/api/admin/attractions/sync/regions` | ADMIN | 시도·시군구 참조 동기화(TourAPI) | — |
| POST | `/api/admin/attractions/sync` | ADMIN | 전체 관광지 수집(수 분 소요) | — |
| POST | `/api/admin/attractions/sync/partial` | ADMIN | 부분 수집(테스트) | query: `areaCode,contentTypeId` |

---

## 3. WebSocket 실시간 협업 (STOMP)

`WebSocketConfig` 기준. 일정 공동 편집의 커서/아바타·블록 변경 실시간 동기화.

| 항목 | 값 |
|------|-----|
| 핸드셰이크 엔드포인트 | `/ws` (SockJS) — JWT 핸드셰이크 인터셉터 인증 |
| 앱(발행) prefix | `/app` |
| 브로커(구독) prefix | `/topic`, `/queue` |
| 발행 — 커서/프레즌스 | `SEND /app/trip/{tripId}/pointer` |
| 구독 — 프레즌스 업데이트 | `SUBSCRIBE /topic/trip/{tripId}/presence` |
| 구독 — 일정 변경 이벤트 | `SUBSCRIBE /topic/trip/{tripId}` (블록 추가·수정·삭제 등 `TripEvent`, `seq` 포함) |

- **권한**: `JwtChannelInterceptor`가 SUBSCRIBE 시 조회 권한, SEND 시 편집 권한을 검증(세션 캐시).
- **프레즌스**: 좌표는 절대 픽셀이 아닌 zone 기반 비율 좌표. stale(기본 5s) 경과 시 evict. `leave`/`keepalive` 페이로드 지원.

---

## 4. 대표 요청/응답 예시

### 4-1. 회원가입 — `POST /api/auth/signup`
```json
// Request
{ "email": "user@example.com", "password": "password123", "nickname": "여행자" }
// Response 201
{ "success": true }
```

### 4-2. 로그인 — `POST /api/auth/login`
```json
// Request
{ "email": "user@example.com", "password": "password123" }
// Response 200 — Set-Cookie: access_token, refresh_token (HttpOnly)
{ "success": true }
```

### 4-3. 관광지 검색 — `GET /api/attractions?region=1&category=12&page=0&size=20`
```json
// Response 200 (AttractionPageResponse — 요약)
{ "success": true, "data": {
  "content": [ { "id": 101, "title": "경복궁", "contentTypeId": 12,
                 "addr1": "서울 종로구 ...", "firstImage": "https://...",
                 "latitude": 37.5796, "longitude": 126.9770, "favorite": false } ],
  "page": 0, "size": 20, "totalElements": 134, "totalPages": 7 } }
```

### 4-4. 일정 생성 — `POST /api/trips`
```json
// Request (TripCreateRequest)
{ "title": "부산 2박3일", "startDate": "2026-07-01", "endDate": "2026-07-03",
  "memberCount": 2, "defaultTransitMode": "PUBLIC_TRANSIT" }
// Response 201 — 생성된 tripId
{ "success": true, "data": 42 }
```

### 4-5. 블록 배치 — `POST /api/trips/42/blocks`
```json
// Request (BlockCreateRequest)
{ "candidateId": 555, "tripDate": "2026-07-01", "startTime": "10:00",
  "durationMinutes": 90, "displayOrder": 1 }
// Response 201 — 생성된 blockId
{ "success": true, "data": 777 }
```

### 4-6. 이동시간 — `GET /api/transit?fromId=101&toId=102&hour=9&mode=PUBLIC_TRANSIT`
```json
// Response 200 (TransitResponse)
{ "success": true, "data": {
  "durationMinutes": 38, "transportMode": "SUBWAY,BUS", "transferCount": 1,
  "fare": 1500, "totalWalkM": 420, "totalDistanceM": 9200, "label": "추천" } }
```

### 4-7. 게시글 작성 — `POST /api/posts`
```json
// Request (PostCreateRequest)
{ "title": "부산 다녀왔어요", "content": "...", "tripId": 42 }
// Response 201
{ "success": true, "data": 88 }
```

---

## 5. 공통 에러 응답

`GlobalExceptionHandler`(`@RestControllerAdvice`)가 예외를 `ApiResponse.fail(message, errorCode)`로 변환.

**errorCode 규칙**
- `ResponseStatusException` → `errorCode` = **HTTP 상태 문자열**(예: `"401 UNAUTHORIZED"`, `"409 CONFLICT"`), `message` = 던질 때의 reason.
- `DataIntegrityViolationException`(DB UNIQUE 위반 등) → HTTP **409**, `errorCode` = `"CONFLICT"`, 메시지는 위반 키별 안내.
- 그 외 미처리 예외 → HTTP **500**, `errorCode` = `"INTERNAL_SERVER_ERROR"`.

```json
// 401 — 인증 필요 (ResponseStatusException)
{ "success": false, "message": "로그인이 필요합니다.", "errorCode": "401 UNAUTHORIZED" }
// 409 — 보관함 중복 (DataIntegrityViolation: uq_candidate_place)
{ "success": false, "message": "이미 보관함에 있는 장소예요.", "errorCode": "CONFLICT" }
// 500 — 서버 오류
{ "success": false, "message": "서버 오류가 발생했습니다.", "errorCode": "INTERNAL_SERVER_ERROR" }
```

| 상황 | HTTP | errorCode | 비고 |
|------|------|-----------|------|
| 인증 누락/만료 | 401 | `401 UNAUTHORIZED` | `AuthEntryPoint` |
| 권한 부족(ADMIN 등) | 403 | `403 FORBIDDEN` | `AccessDeniedHandlerImpl` |
| 검증 실패(`@Valid`) | 400 | `400 BAD_REQUEST` | 필드 메시지 |
| 닉네임 중복 등 비즈니스 충돌 | 409 | `409 CONFLICT` | `ResponseStatusException(CONFLICT)` |
| DB 무결성 위반(보관함·내 장소 중복) | 409 | `CONFLICT` | 키별 안내 메시지 |
| 미처리 예외 | 500 | `INTERNAL_SERVER_ERROR` | |
