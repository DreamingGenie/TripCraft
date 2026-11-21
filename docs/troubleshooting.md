# 트러블슈팅 기록 — TripCraft

개발 중 만난 **문제(증상) → 원인 → 해결**을 도메인별로 정리한 Living 문서.
흩어진 `fix`/`perf` 커밋과 작업 일지에 묻힌 해결 경험을 검색 가능한 한 곳으로 응집한다.

## 이 문서의 위치

| 문서 | 관점 | 질문 |
|------|------|------|
| `CHANGELOG.md` | 무엇이 바뀌었나 | "이번 릴리스에 뭐가 추가됐지?" |
| `docs/logs/` 개발 일지 | 왜 그렇게 설계했나 | "이 결정의 배경/트레이드오프는?" |
| **이 문서** | 무엇이 고장났고 왜·어떻게 고쳤나 | "이 증상 전에 본 것 같은데 원인이 뭐였지?" |

**범위**: 저장소 소유자(전진)의 커밋(`DreamingGenie`/`jin`/`전진` 신원) 중 문제 해결이 뚜렷한 것.
각 항목은 근거 커밋 해시를 링크한다. 팀 협업 시절 팀원(송정기)의 fix는 여기서 제외한다.

**엔트리 형식**: 증상 / 원인 / 해결 / 커밋 / (교훈).

---

## 1. 실시간 협업 (collab)

STOMP over WebSocket 기반 동시 편집. 트랜잭션 타이밍·낙관적 락·커서 좌표계에서 문제가 집중됐다.

### 1.1 편집 내용이 다른 사용자에게 한 템포 늦게 반영

- **증상**: A가 블록을 옮기면 B 화면에는 한 박자 늦게(다음 이벤트가 올 때) 반영됐다.
- **원인**: `@Transactional` 서비스 메서드 안에서 `broadcast()`가 **커밋 전에** 호출됐다. 수신측 B는 이벤트를 받고 `loadTrip()`(별도 트랜잭션)으로 재조회하는데, 그 시점엔 A의 변경이 아직 커밋되지 않아 **미커밋 데이터를 못 보고** 옛 상태를 읽었다.
- **해결**: `broadcast`를 `runAfterCommit`으로 감싸 **커밋 이후 전송**. 이벤트 `seq`도 afterCommit에서 부여해 **seq 순서 = 커밋 순서 = 전송 순서**로 맞춰 동시 편집 시 순서 역전·이벤트 누락도 함께 방지.
- **커밋**: [`fad7388`](https://github.com/DreamingGenie/TripCraft/commit/fad7388)
- **교훈**: 트랜잭션 안에서 외부(브로드캐스트·메시징)로 나가는 호출은 **커밋 이후**로 미뤄야 수신측 재조회와 정합이 맞는다.

### 1.2 단일 창에서 수정해도 "다른 사용자가 먼저 수정했어요"(409) 오탐

- **증상**: 혼자(단일 브라우저) 편집 중인데도 블록 리사이즈 후 다시 수정하면 낙관적 락 충돌 409가 떴다.
- **원인**: `onResizeEnd` 성공 시 `loadTrip` 재조회를 하지 않아 클라이언트의 `ev.version`이 갱신되지 않았다. 다음 수정에서 **옛 version**으로 `updateWithVersion` → 0행 매칭 → 충돌로 오판. (move는 성공 시 `loadTrip`로 이미 갱신되어 문제없었음.)
- **해결**: resize 성공 시 `ev.version = (ev.version ?? 0) + 1`로 서버의 version+1과 클라이언트를 동기화.
- **커밋**: [`aba94a8`](https://github.com/DreamingGenie/TripCraft/commit/aba94a8)
- **교훈**: 낙관적 락을 쓰면 **성공 응답 후 로컬 version을 반드시 갱신**해야 다음 요청이 stale 값으로 자기 자신과 충돌하지 않는다.

### 1.3 드래그를 오래 하면 상대 ghost가 한참 늦게 따라옴(랙)

- **증상**: 협업자가 블록을 오래 드래그한 뒤 손을 떼도, 관전자 화면의 ghost가 한참 동안 천천히 따라왔다.
- **원인**: `onDragOver`에서 ghost 포인터를 **매 dragover마다 전송** → 백로그 누적. 또한 커서 보간 시간(`cursorSmoothMs`)이 전송 간격(`throttle`)보다 커서 전환이 누적되며 랙이 가중됐다.
- **해결**: ghost 포인터 전송을 `cursorThrottleMs`로 throttle(로컬 `dragPreview`는 매 이벤트 갱신해 본인 화면은 부드럽게 유지). `cursorSmoothMs` 기본 80→45로 낮춰 **smooth ≤ throttle** 관계 유지.
- **커밋**: [`e37cbc6`](https://github.com/DreamingGenie/TripCraft/commit/e37cbc6)
- **교훈**: 실시간 좌표 전송은 throttle이 필수이고, **보간 시간이 전송 간격보다 크면** 오히려 지연이 쌓인다.

### 1.4 협업 커서/ghost가 시간표 밖(헤더·GNB)까지 침범

- **증상**: 수신자가 아래로 스크롤한 상태에서 송신자가 시야 위쪽 시간에 커서를 두면, 상대 커서가 타임테이블 헤더·`plan-header`·GNB 위에 그려졌다. 또 헤더·시간축 거터·사이드바 등 협업과 무관한 영역에서도 커서가 보였다.
- **원인**: 커서 좌표 `y = dayCols.top + contentY`가 시간표 wrapper 위로 계산될 수 있는데, 커서를 `body`에 teleport하여 **클리핑이 없어** 격자 밖으로 삐져나갔다. zone 판정도 wrapper 전체를 기준으로 해 헤더/거터를 포함했다.
- **해결**: 보이는 격자 세로 범위를 계산하는 `gridViewport()`를 도입해 범위 밖이면 커서/ghost 숨김(걸치면 일부만 노출). zone 판정을 `.day-cols` 포함 여부로 한정하고, 보드 밖으로 나가면 `zone:'other'`를 전송해 커서를 즉시 숨김(이전엔 마지막 위치에 얼어붙음).
- **커밋**: [`165d282`](https://github.com/DreamingGenie/TripCraft/commit/165d282), [`7eb8299`](https://github.com/DreamingGenie/TripCraft/commit/7eb8299)
- **교훈**: teleport로 렌더하는 오버레이는 부모 클리핑이 사라지므로 **가시 영역을 직접 계산해 클리핑**해야 한다.

### 1.5 연결 해제 시 `ReferenceError` (미선언 변수 참조)

- **증상**: 협업 세션 종료(disconnect) 시 콘솔에 `ReferenceError`.
- **원인**: 앞선 리팩터([`5230285`](https://github.com/DreamingGenie/TripCraft/commit/5230285))에서 `let activeTripId` 선언을 제거했으나, `disconnect()` 안의 `activeTripId = null` 대입 줄이 남아 ES 모듈 strict mode에서 미선언 변수 참조로 터졌다.
- **해결**: 잔존한 대입 줄 삭제.
- **커밋**: [`07e8b2c`](https://github.com/DreamingGenie/TripCraft/commit/07e8b2c)
- **교훈**: 변수 선언 제거 리팩터 후에는 **모든 참조 지점**을 확인해야 한다(strict mode는 런타임에야 드러남). ESLint `no-undef`로 사전 차단 가능.

---

## 2. 관광지 · API (attraction / api)

### 2.1 IDE로 실행하면 관광지 조회가 500 (파라미터 이름 인식 실패)

- **증상**: Gradle이 아니라 **IDE 자체 컴파일러(IntelliJ/VSCode ECJ)**로 실행하면 관광지 조회 등 여러 API가 500을 냈다. Gradle `bootRun`에서는 정상.
- **원인**: Gradle 빌드는 `-parameters` 플래그로 파라미터 이름을 바이트코드에 남기지만, IDE 컴파일러는 이 플래그를 적용하지 않는다. Spring MVC가 리플렉션으로 `@RequestParam`/`@PathVariable`의 **이름을 인식하지 못해** 바인딩 실패.
- **해결**: 모든 `@RequestParam`/`@PathVariable`에 **명시적 `name` 지정**. (Attraction/AttractionSync/Post/Transit/Comment/Trip 컨트롤러 전반.)
- **커밋**: [`0547293`](https://github.com/DreamingGenie/TripCraft/commit/0547293), [`44972f0`](https://github.com/DreamingGenie/TripCraft/commit/44972f0)
- **교훈**: 컴파일러/실행 경로에 의존하지 않으려면 애너테이션에 이름을 **명시**하는 편이 안전하다.

### 2.2 Spring Batch Step 빈 충돌로 앱 기동 실패

- **증상**: 앱이 기동하다 `Step` 빈이 2개로 ambiguous 하다며 실패.
- **원인**: `TourApiSyncJobConfig`에서 Job이 주입받는 Step 빈이 여러 개라 어떤 것을 쓸지 모호.
- **해결**: `tourApiSyncJob` 파라미터에 `@Qualifier`로 대상 Step 명시.
- **커밋**: [`44972f0`](https://github.com/DreamingGenie/TripCraft/commit/44972f0)

---

## 3. 커뮤니티 · 회원 (community / member)

### 3.1 게시글 목록의 댓글 수가 항상 0

- **증상**: 목록에서 댓글 수가 실제와 무관하게 0으로 표시.
- **원인**: `PostListItem`에 `@Setter`가 없어, MyBatis가 `resultType` 매핑 시 `commentCount` 필드에 값을 **주입하지 못했다**(setter 부재 → 기본값 0 유지).
- **해결**: `PostListItem`에 `@Setter` 추가.
- **커밋**: [`fe81f91`](https://github.com/DreamingGenie/TripCraft/commit/fe81f91)
- **교훈**: MyBatis `resultType` POJO는 setter가 있어야 값이 채워진다. 계산 컬럼이 0/ null로만 나오면 setter부터 의심.

### 3.2 로그아웃 후에도 수정·삭제 버튼 노출

- **증상**: 세션 만료(로그아웃) 뒤에도 게시글 상세의 수정/삭제 버튼이 보였다.
- **원인**: `postDetail.mine` 값이 **캐시된 상태로 남아** 로그인 여부와 무관하게 버튼이 노출.
- **해결**: 버튼 조건을 `mine && auth.isLoggedIn` **이중 체크**로 강화.
- **커밋**: [`fe81f91`](https://github.com/DreamingGenie/TripCraft/commit/fe81f91)

### 3.3 이미지 업로드가 여러 지점에서 실패

- **증상**: 게시글 이미지 업로드 시 서버 예외, 개발 서버에서는 이미지 깨짐.
- **원인**(복합):
  1. `MultipartFile.transferTo()`가 **상대 경로**를 Tomcat 임시 디렉토리 기준으로 해석 → `FileNotFoundException`.
  2. `application.yml`에 `spring` 키가 중복 정의되어 SnakeYAML `DuplicateKeyException`.
  3. Vite 개발 서버(5173)가 `/uploads/**`를 백엔드(8080)로 프록시하지 않아 이미지 URL이 끊김.
- **해결**: (1) `toAbsolutePath()`로 절대 경로 확정, (2) `servlet.multipart`를 기존 `spring` 블록으로 병합해 중복 제거, (3) `vite.config.js`에 `/uploads` 프록시 추가.
- **커밋**: [`e4f80dc`](https://github.com/DreamingGenie/TripCraft/commit/e4f80dc)
- **교훈**: 파일 저장은 항상 **절대 경로**로. dev 프록시는 API뿐 아니라 **정적 업로드 경로**도 포함해야 한다.

### 3.4 회원 검색 시 NPE

- **증상**: 회원 검색 결과 응답에서 NullPointerException.
- **원인**: `Map.of(...)`는 **null 값을 허용하지 않아**, 프로필 이미지 등 null 필드가 있으면 터졌다.
- **해결**: 응답 맵을 `HashMap`으로 교체(null 허용).
- **커밋**: [`588904a`](https://github.com/DreamingGenie/TripCraft/commit/588904a)
- **교훈**: null이 들어갈 수 있는 응답에는 `Map.of`/`List.of`(불변·null 불가) 대신 null 허용 컬렉션을 쓴다.

### 3.5 [perf] 프로필 이미지 N+1 쿼리

- **증상**: 게시글 목록 10개를 부르면 프로필 이미지 서브쿼리가 행마다 실행돼 **엔진 내부 실행이 11회**(메인 1 + 서브쿼리 10)가 된다. ⚠ 왕복이 11회라는 뜻이 아니다 — SQL 문장은 하나라 **클라이언트↔DB 왕복은 1회**다.
- **원인**: 프로필 이미지를 **상관 서브쿼리**로 행마다 조회(N+1). MySQL은 이를 `DEPENDENT SUBQUERY`로 처리하고 JOIN으로 자동 변환하지 않는다.
- **해결**: 상관 서브쿼리를 **파생 테이블 LEFT JOIN**으로 교체 → attach를 한 번만 읽는다. `profileImageSubquery` fragment를 `profileImageCol` + `profileImageJoin`으로 분리해 목록/상세/마이페이지 쿼리에 공통 적용.
- **커밋**: [`36f7bd0`](https://github.com/DreamingGenie/TripCraft/commit/36f7bd0)
- **참고**: 기술 노트 [`docs/features/perf-profile-image.md`](features/perf-profile-image.md).
- **교훈**: 목록 API에서 행별 부가정보는 서브쿼리보다 **JOIN 한 번**으로 모으는 편이 반복 실행을 없앤다. 다만 **비용의 단위를 정확히 불러야 한다** — 여기서 줄인 것은 왕복 수가 아니라 행마다 반복되던 내부 실행이고, 그 차이는 `EXPLAIN`으로 확인할 일이지 짐작할 일이 아니다.
- ⚠ **남은 위험**: 교체한 파생 테이블에는 기존 서브쿼리의 `LIMIT 1`이 없다. 한 회원에게 `target='profile'` 행이 둘 이상이면 게시글 행이 증식한다(현재는 앱 레이어가 중복 삽입 전에 삭제한다는 전제에 의존). 기술 노트 §방법 2 참조.

### 3.6 일정 공유 경고 팝업 UX·동작 버그

- **증상**: 커뮤니티 글쓰기에서 일정 공유 경고 팝업이 백드랍 이중 표시, 잘못된 시점 노출, 취소 후 재시도 시 경고 미표시 등으로 어색하게 동작.
- **원인**: 경고가 별도 오버레이로 떠 모달 백드랍과 겹쳤고, 트리거 시점이 드롭다운 선택 시점이라 흐름과 안 맞았다.
- **해결**: 경고를 모달 내부 인라인으로 이동(백드랍 이중 제거), 트리거를 **등록 버튼 클릭 시점**으로 변경, `submitPost`/`doSubmitPost` 분리로 확인 즉시 등록·취소 시 상태 유지.
- **커밋**: [`9c69886`](https://github.com/DreamingGenie/TripCraft/commit/9c69886)

---

## 4. UI · 랜딩

### 4.1 랜딩 페이지 헤더가 두 개

- **증상**: 랜딩 페이지에 헤더가 중복 렌더.
- **원인**: `App.vue`의 공용 `AppHeader`와 `LandingView`의 `landing-gnb`가 동시에 렌더링. 역할이 완전히 중복.
- **해결**: 중복인 `landing-gnb` 제거.
- **커밋**: [`d40fbaa`](https://github.com/DreamingGenie/TripCraft/commit/d40fbaa)

---

## 5. 배포 · 설정 (deploy)

### 5.1 JWT_SECRET 예시값이 무효 base64 → 백엔드 crash-loop

- **증상**: 로컬 compose 스택 기동 시 백엔드가 `DecodingException`으로 기동 실패(crash-loop).
- **원인**: `JwtTokenProvider`가 `Decoders.BASE64.decode(secret)`로 서명키를 해석하는데, 예시값(`local-dev-secret-...`, `change-me-...`)에 `-`가 포함되어 **base64 파싱 실패**.
- **해결**: 예시 시크릿을 **유효한 base64 플레이스홀더**로 교정하고 `openssl rand -base64 48` 생성 안내 주석 추가. `application-test.yml`의 테스트용 `jwt.secret`도 유효 base64로 교정(Testcontainers `contextLoads` 동일 문제 방지).
- **커밋**: [`e914d38`](https://github.com/DreamingGenie/TripCraft/commit/e914d38)
- **교훈**: 형식이 강제되는 시크릿(base64·PEM 등)은 **예시값도 그 형식을 지켜야** 첫 실행이 깨지지 않는다.

---

> 새 트러블슈팅이 생기면 해당 도메인 섹션에 같은 형식(증상/원인/해결/커밋)으로 추가한다.
