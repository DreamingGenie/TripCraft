# 기술 노트 — 인증·보안 (쿠키 JWT + STOMP 채널 권한)

REST는 무상태 쿠키 JWT로, WebSocket(STOMP) 협업 채널은 프레임 단계 권한 검증으로 보호한다.
관련 코드는 모두 `backend/.../global/security/`.

---

## 1. 토큰 — `JwtTokenProvider`

- HMAC-SHA 서명(`jwt.secret`, BASE64 디코드 키). access/refresh 만료는 `jwt.access-token-expiry`·`jwt.refresh-token-expiry`(ms).
- 클레임: `subject = memberId`, `role`. 검증 실패(`JwtException`/`IllegalArgumentException`)는 `validate()`가 `false` 반환(예외 누출 없음).

## 2. REST 인증 흐름

1. **로그인**(`AuthService.login`) 성공 시 `access_token`·`refresh_token`을 **HttpOnly 쿠키**로 발급.
2. **요청마다** `JwtAuthenticationFilter`(OncePerRequest, `UsernamePasswordAuthenticationFilter` 앞)가
   `access_token` 쿠키를 꺼내 `validate()` → `memberId`·`role`을 꺼내
   `UsernamePasswordAuthenticationToken(memberId, null, ROLE_*)`로 `SecurityContext`에 적재.
3. 컨트롤러는 `@AuthenticationPrincipal Long memberId`로 현재 회원을 받는다(쿠키 없으면 `null`).
4. **만료 시** `POST /api/auth/refresh`(refresh 쿠키)로 access 재발급.

> 토큰이 없거나 무효여도 필터는 통과시키고 인증만 비우므로, 공개 경로(permitAll)는 비로그인도 동작하고
> 보호 경로만 `AuthEntryPoint`(401)로 막힌다.

## 3. 인가 — `SecurityConfig`

- **무상태**(`SessionCreationPolicy.STATELESS`), CSRF 비활성(쿠키지만 SPA·동일 오리진 전제), CORS `allowCredentials=true`.
- 경로 규칙(요약):
  - **공개(permitAll)**: `POST /api/auth/{login,signup,refresh,logout,kakao}`, `GET /api/{attractions,posts,notices}/**`,
    `GET /api/trips/*/blocks-summary`, `GET /api/trips/shared/**`, `GET /uploads/**`, `/ws/**`, Swagger, `/error`.
  - **ADMIN**: `/api/admin/**`, `POST·PUT·DELETE /api/notices/**`.
  - **그 외**: 인증 필요(`anyRequest().authenticated()`).
- 예외 처리: 미인증 → `AuthEntryPoint`(401), 권한 부족 → `AccessDeniedHandlerImpl`(403). 모두 `ApiResponse.fail` 형식.
- 비밀번호: `BCryptPasswordEncoder`.

## 4. WebSocket(STOMP) 채널 권한

REST 필터는 WS 프레임에 적용되지 않으므로 별도 2단 방어.

### 4-1. 핸드셰이크 — `JwtHandshakeInterceptor`
`/ws` SockJS 핸드셰이크(HTTP) 단계에서 `access_token` 쿠키를 검증해 `memberId`를 **세션 속성**(`MEMBER_ID_ATTR`)에 저장.
미인증도 연결은 허용(관전 목적) — 차단은 다음 단계에서.

### 4-2. 프레임 검증 — `JwtChannelInterceptor` (`preSend`)
| 프레임 | 처리 |
|--------|------|
| `CONNECT` | 세션의 `memberId`로 `Principal` 설정(없으면 익명 연결 유지) |
| `SUBSCRIBE /topic/trip/{id}` | **조회 권한** 확인 — 소유자 ∨ 협업자 ∨ 공유(비 PRIVATE)면 허용, 아니면 403 |
| `SEND /app/trip/{id}/...` | **미인증 차단(401)** 후 동일 조회 권한 확인 — 관전자(익명)는 발행 불가 |

판정식(`canView`): `trip.memberId == me` ∨ `tripCollaborator(tripId, me)` ∨ `shareAccess != PRIVATE`.

### 4-3. 권한 캐시 무효화 — `TripAccessVersion`
매 프레임 DB 조회는 비싸므로, 검증 결과를 **세대(generation) 번호와 함께 세션에 캐시**한다(`tripAccess:{tripId}`).
협업자 제거·역할 변경·공유 설정 변경 시 `bump(tripId)`로 세대를 올리면, 캐시 세대와 어긋난 세션이
**다음 프레임에서 자동 재검증**된다(즉시 무효화). 같은 세대면 캐시 적중 → DB 조회 생략.

## 5. 관련 코드
`global/security/`: `JwtTokenProvider` · `JwtAuthenticationFilter` · `SecurityConfig` · `AuthEntryPoint` ·
`AccessDeniedHandlerImpl` · `JwtHandshakeInterceptor` · `JwtChannelInterceptor` · `TripAccessVersion`.
실시간 협업 동시성(낙관적 락·grab)은 [realtime-collab.md](realtime-collab.md) 참조.
