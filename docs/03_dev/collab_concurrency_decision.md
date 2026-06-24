# 기술 노트 — 실시간 협업 편집의 동시성·트랜잭션 정책 결정

작성일: 2026-06-24
작성자: 전진
관련 파일: `TripServiceImpl.java`, `TripBlockMapper.(java|xml)`, `TripPresenceController.java`, `TripAccessVersion.java`, `JwtChannelInterceptor.java`, `TripEvent.java`, `ScheduleBoard.vue`, `migration_collab_optlock_v1.sql`
관련 문서: `docs/01_planning/improvements.md` §9(보강 항목)·§10(CRDT/OT)·§11(다인원 위험)

> 여러 사용자가 같은 일정을 동시에 드래그앤드롭으로 편집하는 협업 기능에서, **동시성을 어디까지 어떻게 막을지** 고민하고 결정한 과정과 그 이유를 정리한다. 복기·면접 대비용.

---

## 1. 출발점 — 기능은 됐는데, 동시에 만지면?

협업 편집은 "즉시 저장 + STOMP 브로드캐스트"로 동작한다. 블록을 옮기면 REST로 저장되고, 서버가 `/topic/trip/{id}`로 변경 이벤트를 뿌리면 다른 참여자가 `loadTrip()`으로 재조회한다. 기능 구현 단계에선 여기까지가 목표였다.

그런데 협업은 본질적으로 동시성 문제다. 코드를 다시 보니 **편집 경로에 안전장치가 사실상 없었다**:

- 모든 편집(`updateBlock`, `removeBlock` 등)은 `@Transactional` 단일 트랜잭션이지만 **락이 없다** → 같은 블록을 둘이 동시에 옮기면 나중 요청이 먼저 요청을 조용히 덮어쓴다(Last-Write-Wins, 데이터 유실).
- 드래그 중 블록을 잠그는 **grab**이 있지만, 알고 보니 `getGrabOwner()`는 정의만 돼 있고 **어디서도 호출되지 않았다**. 즉 grab은 순수 클라이언트 표시용이고 서버는 강제하지 않았다.
- `recalculateTransitForDate`가 외부 API(ODsay/TMap)를 **트랜잭션 안에서** 호출 → 외부 지연 동안 DB 커넥션을 잡고 있고, 동시 편집 시 블록 전체를 덮어써 위치 정보까지 손상될 수 있었다.

---

## 2. 내가 고민한 지점들

단순히 "낙관적 락 걸자"로 끝낼 문제가 아니었다. 충돌의 **기준**을 정확히 세우는 게 핵심이라고 봤다.

1. **같은 블록을 동시에 수정**하는 건 막아야 한다 — 이건 진짜 데이터 유실이다.
2. 하지만 **서로 무관한 블록을 서로 다른 위치로 옮기는 건** 정상적인 동시 작업이다. 절대 충돌로 처리하면 안 된다(협업의 의미가 없어짐).
3. **transit(이동시간) 재계산**은 시스템이 자동으로 블록을 건드린다. 이게 사용자 편집과 충돌로 오인되면 멀쩡한 편집이 거부된다.
4. **서로 다른 블록을 같은 자리에 놓으려는** 경우도 있다. 이건 "같은 블록"이 아니라 "같은 위치" 경합이라 성격이 다르다.
5. 다행히 우리는 **상대방 커서를 실시간으로 본다**. 물리적 락은 아니지만 "지금 누가 어디를 만지는지" 인지시키는 1차 완충이 이미 있다.

결국 핵심 질문은 **"버전이 다르다고 무조건 막으면 안 된다. 무엇을 충돌로 볼 것인가?"** 였다.

---

## 3. 선택지 비교

| 방식 | 막는 것 | 비용 | 판단 |
|------|---------|------|------|
| **비관적 락** (`SELECT … FOR UPDATE`) | 모든 경합 | 락 경합·데드락 위험, 드래그 UX와 안 맞음 | ❌ 과함 |
| **낙관적 락** (`version`) | 같은 row 동시 수정 | 컬럼 1개 + 조건부 UPDATE | ✅ 핵심 |
| **grab 서버 강제** | 드래그 중 블록 선제 차단 | 기존 인프라 호출만 | ✅ 보조 |
| **CRDT / OT** (자동 병합) | 모든 충돌 자동 해소 | 구현 복잡도 폭발 | ❌ 2인 데모엔 과함 |

결정: **낙관적 락(하드 보장) + grab 서버 강제(소프트 선제) + transit 분리**. 자동 병합은 하지 않고 **"충돌 시 한쪽이 재조회 후 재시도"** 정책으로 간다. 커서 가시화(고민 5번)가 충돌 빈도 자체를 낮춰주므로, 드물게 발생하는 충돌은 거부 후 재조회로 충분하다고 판단했다.

### 왜 낙관적 락이 정답이었나
- 우리 환경은 **충돌이 드물다**(2인 편집 + 커서로 서로 인지). 락을 미리 잡는 비관적 방식은 평소에 손해만 본다. 낙관적 락은 **평소 오버헤드 0, 실제 경합 때만 한 명이 거부**된다.
- 무엇보다 **`version`은 "같은 row"에만 작용한다.** 서로 다른 블록은 서로 다른 row → 서로 다른 version → 절대 오탐하지 않는다. 이게 고민 2번(무관한 편집 비간섭)을 구조적으로 보장한다.

---

## 4. 결정한 충돌 기준 (이 설계의 핵심)

| 시나리오 | 감지 메커니즘 | 결과 |
|---|---|---|
| **같은 블록** 동시 이동/리사이즈 | 낙관적 락(version) | 둘째 요청 **409** → 재조회 후 재시도 |
| 한쪽이 드래그 중(grab) + 다른쪽 수정 시도 | grab 서버 게이트(소프트) | 시도 즉시 **409 "○○님이 편집 중"** (선제) |
| **서로 다른** 블록 → 서로 다른 위치 | 독립 row·독립 version | **충돌 없음** (정상 동시 처리) |
| transit 재계산 ↔ 사용자 편집 | transit 전용 UPDATE(version 미검사·미증가) | **충돌 없음** |
| 한쪽 삭제 + 다른쪽 같은 블록 이동 | 이동의 versioned UPDATE가 0행 매칭 | 이동 측 **409** → 재조회 시 삭제 반영 |
| **다른** 블록 → **같은 시간대(겹침)** | 시간 겹침 검사 + `trip` 행 `FOR UPDATE` 직렬화 | **409** "그 시간대엔 이미 다른 일정이 있어요"(둘째 거부) |

핵심 불변식: **transit 전용 UPDATE가 version을 건드리지 않는 것**이 4번 행(오탐 없음)의 전제다. 만약 재계산이 version을 올리면, A의 편집 직후 시스템 재계산이 B의 version을 무효화해 멀쩡한 편집이 거부된다. 그래서 사용자 편집 UPDATE와 transit UPDATE를 **물리적으로 다른 쿼리**로 분리했다.

---

## 5. 구현한 내용

### (1) 낙관적 락
```sql
-- migration_collab_optlock_v1.sql
ALTER TABLE trip_block ADD COLUMN version INT NOT NULL DEFAULT 0;
```
```xml
<!-- 사용자 편집: 그 사이 누가 바꿨으면 0행 -->
<update id="updateWithVersion">
  UPDATE trip_block SET ..., version = version + 1, updated_at = CURRENT_TIMESTAMP
  WHERE id = #{id} AND version = #{version}
</update>
```
```java
int affected = blockMapper.updateWithVersion(block);
if (affected == 0)
    throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 사용자가 먼저 이 블록을 수정했어요");
```
- 클라이언트가 읽을 때 받은 `version`을 편집 요청에 동봉(`BlockItem`·`BlockUpdateRequest` → 프론트 `ev.version`).
- 409를 받으면 프론트는 토스트 안내 + `loadTrip()`으로 최신 상태 재조회(낙관적 UI 자가 복구).
- **삭제는 version 체크 안 함** — 삭제는 종결적이고, "삭제 vs 이동" 경합은 이동 측의 versioned UPDATE가 0행을 만나 자연스럽게 409가 된다(매트릭스 5번).

### (2) grab 서버 게이트
```java
private void assertNotGrabbedByOther(Long tripId, Long blockId, Long memberId) {
    Long owner = presenceController.getGrabOwner(tripId, blockId);
    if (owner != null && !owner.equals(memberId))
        throw new ResponseStatusException(HttpStatus.CONFLICT, "다른 사용자가 이 블록을 편집 중입니다");
}
```
- 이미 있던 `getGrabOwner`를 **호출만** 추가. grab은 5초 stale evict·disconnect로 자동 해제되므로 죽은 보유자는 차단하지 않는다.
- 낙관적 락이 **사후 안전망**이라면, grab은 **사전 차단**이다. 드래그 중인 블록은 아예 건드리지 못하게 해 충돌을 UX 단계에서 줄인다(두 층 방어).

### (3) transit 분리 + 트랜잭션 위생
```java
// transit 컬럼만 갱신 — version·위치 미변경 (오탐 방지)
blockMapper.updateTransitById(id, durationMin, mode, optionIndex);

// 외부 API 포함 재계산을 변경 트랜잭션 "커밋 후"로 이동 (커넥션 장기 점유 해소)
runAfterCommit(() -> recalculateTransitForDate(tripId, date));
```
```java
private void runAfterCommit(Runnable task) {
    if (TransactionSynchronizationManager.isSynchronizationActive())
        TransactionSynchronizationManager.registerSynchronization(
            new TransactionSynchronization() { public void afterCommit() { task.run(); } });
    else task.run();
}
```
- 편집 트랜잭션은 블록 변경만 하고 **즉시 커밋** → 외부 API는 트랜잭션 밖에서 호출 → DB 커넥션을 외부 지연만큼 잡지 않는다.

### (4) grab을 서버 게이트로 쓰자 드러난 release 버그 (중요)
grab을 **표시용**으로만 쓸 때는 안 보이던 버그가, 서버 편집 게이트로 승격하니 드러났다.
- 증상: 드래그 종료 시 프론트가 `targetBlockId: null`을 보내 **서버 grabMap이 비워지지 않았다.** 게다가 dragger의 keepalive(4초)가 presence를 살려둬 stale evict(5초)도 안 돌아 → **grab이 세션 내내 잔존.** 이 상태에서 grab을 강제하면 *다른 사용자가 그 블록을 영영 못 만지는* 잠금이 된다.
- 수정: ① 프론트가 종료 시 **잡았던 블록 id**를 실어 보내 명시적 해제 ② 백엔드는 `ConcurrentMap.remove(key, value)`로 **본인 소유 grab만** 원자적 제거(남이 방금 잡은 잠금을 덮어 지우지 않게).
- 교훈: **"표시용 기능을 권위적 게이트로 승격할 때는 생명주기(획득·해제·만료)를 전수 재점검해야 한다."** 해제 경로가 약해도 표시용일 땐 무해하지만 게이트가 되는 순간 가용성 사고가 된다.

### (5) 시간대 겹침 금지 + 동시성 직렬화
한 날짜에 두 블록이 같은 시간대에 겹치지 못하게 막는다(예: 1~5시 블록 위에 3~7시 배치 금지).
- **겹침 정의**: 같은 trip·날짜에서 `[start, start+duration)` 구간이 교차. 분 단위(0~1440) 정수로 비교해 `TIME` 자정 넘김 문제를 피함(`countOverlapping`).
- **검사 위치**: 서버 `placeBlock`·`updateBlock`(위치 편집)에서 권위적으로 검사 → 겹치면 409. 프론트는 드롭/리사이즈 전에 `overlapsExisting`(top/height)로 선검사해 즉시 피드백 + 낙관적 UI 되돌림.
- **동시성**: 단순 검사만으론 두 명이 동시에 겹치는 자리에 놓을 때(각자 스냅샷엔 빈자리) race가 남는다. 그래서 검사 직전 **`SELECT id FROM trip WHERE id=? FOR UPDATE`(`lockTripRow`)로 그 일정 편집을 직렬화** → 둘째 트랜잭션은 첫째 커밋까지 대기 후 최신 상태로 검사 → 한쪽만 성공.
- **왜 `trip` 행 잠금인가**: 겹침은 cross-row 제약이라 단일 행 version으로 못 막는다. MySQL은 Postgres의 배제 제약(exclusion constraint)이 없어, 가장 단순·정확한 직렬화 지점으로 일정(trip) 행을 골랐다. 편집은 사람 손 속도라 잠금 구간(빠른 검사+쓰기, transit은 afterCommit으로 이미 분리)이 짧아 비용이 작다. **부수 효과로 9-1 `display_order` MAX+1 race도 이 잠금 안에서 안전**해진다.
- **한계**: in-JVM이 아니라 DB 행 잠금이라 다중 인스턴스에서도 동작. 단 잠금 단위가 "일정 전체"라 같은 일정의 무관한 편집도 짧게 직렬화된다(인원 많으면 §11 broadcast 부하가 먼저 병목).

---

## 6. 적용 범위의 변화 — 1차(견고) → 2차(보강)

처음엔 **영속 데이터 유실 = 지금 / 표시·성능 = 나중** 기준으로 견고 범위만 잡았다. 이후 같은 기준으로 `improvements.md §9`의 높음·중간 항목을 2차로 보강했다.

**2차로 추가 적용**
- **9-1 displayOrder 서버 권위 할당** — `placeBlock`·다른 날짜 이동 시 `nextDisplayOrder`(MAX+1)로 서버가 순서를 정함. 클라 값 불신. *잔여*: `MAX+1` 동시 읽기 race는 `trip_id` 컬럼+UNIQUE가 있어야 완전 차단(스키마 확장이라 보류).
- **9-2 removeCandidate TOCTOU** — 선제 체크와 삭제 사이 창을 FK(RESTRICT) 위반 캐치로 막아 동일 409로 변환.
- **9-3 이벤트 seq** — `TripEvent.seq`(일정별 단조 증가)를 broadcast에 스탬프, 프론트가 역전·중복 무시. (유실분 재조회 엔드포인트는 아직, reconnect 전체 재조회로 수렴)
- **9-5 권한 캐시 무효화** — `TripAccessVersion` 세대 카운터. 협업자 제거/공유 변경 시 `bump` → 인터셉터가 다음 프레임에 재검증.

**여전히 보류 (이유와 함께)**
- **9-4 presence in-memory 경합** — 재검증 결과 핵심 주장("`computeIfAbsent` 복합 연산 비원자성")이 **오진**이었다. `ConcurrentHashMap.computeIfAbsent`는 원자적이라 동시 호출자가 같은 맵을 받는다. 남은 항목(재연결 유령 커서 등)은 자가 회복·표시 수준이라, **커서 이동마다 도는 핫 패스에 락을 거는 비용이 이득보다 커서 일부러 두었다.**
- **broadcast 부하·블록 이벤트 부분 패치** — 성능·UX 영역. 인원 확장(§아래) 시 우선순위 상승.

> 의사결정 일관성: "지금 막을 가치가 있는가(데이터 유실?) + 막는 비용이 합리적인가"를 항목마다 따졌고, **잘못 진단된 race는 기계적으로 구현하지 않고 정정**했다.

### 인원 확장(2~5인+) 관점
테스트는 2인이었지만 여행은 다인원이다. 동시 편집 쌍이 ≈ nC2로 늘어 "충돌이 드물다"는 전제가 약해진다. 다만 **데이터 정합성은 인원이 늘어도 낙관적 락+grab+권한세대로 지켜지고, 먼저 무너지는 건 성능·UX**(409 거부율↑, presence broadcast ≈ O(n²), 재계산 중복)다. → 다음 1순위 보강은 broadcast 효율·재계산 디바운스. (상세 `improvements.md §11`)

### 자동 병합(CRDT/OT)은?
"거부 대신 자동 병합"이 가능하나 블록 모델 재설계 + op 로그 영속 등 **수 주 규모 아키텍처 전환**이고, **의미 충돌(A는 9시·B는 14시)은 CRDT도 못 없앤다**(정합성만 보장). 현 규모엔 ROI가 낮아 조건부 후보로 남겼다. (상세 `improvements.md §10`)

---

## 7. 결과

| 영역 | 변경 |
|------|------|
| DB | `trip_block.version` 추가(마이그레이션 + schema.sql) |
| 백엔드(1차) | `updateWithVersion`/`updateTransitById` 매퍼, `updateBlock`/`removeBlock` 락·grab 게이트, `recalculateTransitForDate` afterCommit 분리 |
| 백엔드(2차) | `nextDisplayOrder`(9-1), removeCandidate FK 캐치(9-2), `TripEvent.seq`(9-3), `TripAccessVersion`+인터셉터 재검증(9-5) |
| grab 수정 | 종료 시 명시 해제 + 본인 소유만 원자적 제거 |
| 겹침 금지 | `countOverlapping`+`lockTripRow`(trip 행 FOR UPDATE) 서버 직렬화, 프론트 `overlapsExisting` 선검사 |
| DTO | `BlockItem`·`BlockUpdateRequest`에 `version` |
| 프론트 | `buildEvent`에 version, move/resize 요청에 동봉, `isConflict()` 409 처리, 이벤트 seq dedup |

검증: 백 `compileJava` ✅ / 프론트 `vite build` ✅. **마이그레이션(`migration_collab_optlock_v1.sql`) 선적용 필수.** 2계정(및 다인원) 동시 편집 E2E는 실사용 검증 예정.

---

## 8. 면접 예상 질문 정리

**Q. 왜 비관적 락이 아니라 낙관적 락인가?**
충돌이 드문 환경(2인 + 실시간 커서로 서로 인지)이기 때문. 비관적 락은 평소에도 락을 잡아 손해지만, 낙관적 락은 평소 오버헤드가 없고 실제 경합 시에만 한 명을 거부한다. 충돌 빈도가 낮을수록 낙관적 락이 유리하다.

**Q. 버전이 다르면 무조건 충돌인가? transit 재계산 때문에 오탐 안 나나?**
안 난다. `version`은 같은 row에만 작용하고, **transit 재계산은 version을 올리지 않는 별도 UPDATE**로 분리했다. 그래서 시스템 재계산이 사용자 편집을 무효화하지 못한다. 서로 다른 블록 편집도 row가 달라 오탐이 없다.

**Q. "다른 블록을 같은 자리에 놓는" 충돌은?**
두 가지를 구분한다. ① **시간대 겹침**(1~5시 위 3~7시)은 후속으로 **하드 차단**했다 — 서버 시간 겹침 검사 + `trip` 행 `FOR UPDATE` 직렬화(위 §5-(5)). ② `display_order` 같은 값 중복(순수 정렬 tiebreaker)은 위치 표시에 영향이 없어 서버 MAX+1 할당으로 완화만 했다. "막을 가치가 있는 것(시간 겹침)은 하드, 영향 없는 것(순서 tiebreaker)은 소프트"로 나눈 판단이다.

**Q. 외부 API를 트랜잭션에서 왜 빼야 하나?**
ODsay/TMap 호출이 수 초 걸릴 수 있는데, `@Transactional` 안에 있으면 그동안 DB 커넥션을 점유한다. 동시 편집이 늘면 커넥션 풀 고갈·락 대기로 번진다. `afterCommit`으로 빼서 트랜잭션은 즉시 커밋하고 외부 호출은 밖에서 하게 했다.

**Q. 충돌이 났을 때 사용자 경험은?**
자동 병합은 하지 않는다. 진 쪽은 "다른 사용자가 먼저 수정했어요" 토스트와 함께 최신 상태로 자동 재조회되고, 보고 다시 시도한다. 2인 협업에선 이 정도 정책이 단순하고 예측 가능하다.

**Q. grab과 낙관적 락은 중복 아닌가?**
역할이 다르다. grab은 **사전**(드래그 중 선제 차단, UX 친화), 낙관적 락은 **사후**(grab을 안 거친 경로·stale 해제 순간까지 잡는 최종 보장). 두 층으로 나눠 충돌 빈도를 줄이면서 정합성을 보장한다.

**Q. 기능을 그대로 두지 않고 게이트로 승격하면서 생긴 문제가 있었나?**
있었다. grab을 표시용으로만 쓸 땐 보이지 않던 **release 누락 버그**가 드러났다. 종료 시 블록 id를 안 보내 서버 grabMap이 안 비워졌고, keepalive가 presence를 살려 stale evict도 안 돌아 잠금이 세션 내내 남았다. 게이트가 되니 그게 곧 "남이 그 블록을 못 만지는" 가용성 사고였다. **표시용 기능을 권위적 게이트로 바꿀 땐 생명주기(획득·해제·만료)를 전수 재점검해야 한다**는 교훈.

**Q. 백로그 항목을 다 구현했나? 안 한 게 있다면 왜?**
중간 우선순위 중 "presence in-memory 경합"은 구현하지 않고 **분석을 정정**했다. 1차 진단의 핵심("`computeIfAbsent` 복합 연산이 비원자적")이 틀렸기 때문이다. 그건 원자적이고, 남은 이슈는 표시 수준에서 자가 회복된다. **커서 이동마다 도는 핫 패스에 없는 버그를 막겠다고 락을 넣으면 성능만 깎는다.** 백로그를 기계적으로 소화하기보다, 항목마다 "실제 위험인가·막는 비용이 합리적인가"를 재검증한 사례다.

**Q. 인원이 5인, 10인으로 늘면?**
정합성은 유지된다(낙관적 락·grab·권한 세대는 인원 무관). 먼저 무너지는 건 **성능·UX**다: 동시 편집 쌍이 ≈ nC2로 늘어 409 거부율이 오르고, presence가 매 커서 이동마다 전체 목록을 전원에게 재전송해 트래픽이 ≈ O(n²)로 커진다. 그래서 인원 확장 시 1순위는 락 강화가 아니라 **broadcast 효율화(델타·coalescing)와 transit 재계산 디바운스**다.

**Q. 같은 시간대에 두 블록이 겹치는 걸 막을 때 동시성은 어떻게 처리했나?**
겹침은 한 행의 문제가 아니라 **여러 행에 걸친 제약**이라 블록 version으로는 못 막는다. MySQL엔 Postgres의 배제 제약도 없다. 그래서 검사 직전에 **그 일정(`trip`) 행을 `FOR UPDATE`로 잠가** 같은 일정 편집을 직렬화했다. 두 명이 동시에 겹치는 자리에 놓아도 둘째는 첫째 커밋까지 대기했다가 최신 상태로 검사 → 한쪽만 통과한다. 잠금 구간은 빠른 검사+쓰기뿐이고(느린 transit 재계산은 afterCommit으로 이미 분리) 사람 손 속도라 비용이 작다. 프론트도 드롭 전에 선검사해 즉시 피드백을 준다(서버가 최종 권위).

**Q. 구글 닥스처럼 자동 병합(CRDT/OT)은 왜 안 했나?**
ROI 때문이다. 블록을 op/CRDT로 재모델링하고 op 로그 영속·재동기화까지 가는 수 주 규모 전환인데, 우리 규모(2~5인, 커서로 상호 인지)엔 과하다. 게다가 CRDT/OT도 **의미 충돌**(같은 블록을 A는 9시·B는 14시로)은 못 없앤다 — 정합성만 보장하지 "사용자가 원한 결과"를 주진 않는다. 그래서 거부·재시도 정책이 더 단순하고 정직하다고 봤다.

---

## 9. 실시간 튜닝 변수 (시연 ↔ 실서비스)

프레임·갱신 타이밍을 환경별로 바꿀 수 있게 **별개 변수로 추출**했다. 현재는 `.env`에 두고(코드는 `${...:기본값}`로 바인딩) 추후 프론트 `application.yml`/`VITE` 모드파일로 이전 예정. 미설정 시 코드 기본값으로 안전 동작.

| 변수 | 위치 | 효과 | 시연 | 실서비스 |
|------|------|------|------|----------|
| `VITE_COLLAB_CURSOR_THROTTLE_MS` | 프론트 `.env` → `config/collab.js` | 커서 전송 주기(작을수록 부드럽고 트래픽↑) | 33 | 80~100 |
| `VITE_COLLAB_CURSOR_SMOOTH_MS` | 〃 (CSS `--collab-smooth`) | 수신 커서/ghost 보간 transition(점프↔글라이드) | 90 | 120 |
| `VITE_COLLAB_KEEPALIVE_MS` | 프론트 `.env` → `stores/collab.js` | presence keepalive 주기 | 4000 | 8000 |
| `VITE_COLLAB_RECONNECT_MS` | 〃 | 끊김 후 재연결 지연 | 2000 | 3000 |
| `COLLAB_PRESENCE_STALE_MS` | 백 `.env` → `TripPresenceController` `@Value` | 유령 커서 제거까지 무신호 허용 시간 | 5000 | 10000 |
| `COLLAB_PRESENCE_EVICT_INTERVAL_MS` | 〃 `@Scheduled(fixedDelayString)` | stale 정리 스케줄 주기 | 2000 | 3000 |

핵심 "부드러움"은 두 축의 조합이다 — **전송 빈도(throttle ↓)** 로 데이터가 자주 오게 하고, **CSS 보간(smooth)** 으로 그 사이를 미끄러지듯 채운다. 인원이 많아지면(§11) throttle·keepalive를 키워 broadcast O(n²) 부하를 누르는 쪽으로 전환한다. 비밀이 아닌 앱 튜닝값이라 정석은 `application.yml`(`collab:`)이며, 지금은 yml 미사용으로 `.env`에 둔 것이라 이전 시 `${}` 플레이스홀더만 그대로 옮기면 된다.
