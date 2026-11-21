# 기술 노트 — 실시간 협업 편집의 동시성·트랜잭션 정책 결정

작성일: 2026-06-24 · 갱신: 2026-09-10
작성자: 전진
관련 파일: `TripServiceImpl.java`, `TripMapper.(java|xml)`, `TripBlockMapper.(java|xml)`, `TripPresenceController.java`, `ScheduleBoard.vue`, `migration_collab_optlock_v1.sql`

> **2026-09-10 정정**: 이 문서의 최초본은 커밋 `913a4c1`(시간대 겹침 금지 + `trip` 행 `FOR UPDATE`) 직전 상태를 기준으로 쓰였다. 그래서 §3 선택지 비교가 비관적 락을 "❌ 과함"으로, §4 매트릭스와 §8이 시간대 겹침을 "하드 미보장"으로 남겨 두어 **실제 코드와 어긋나 있었다.** 비관적 락은 블록 단위로는 채택하지 않았고, **부모 `trip` 행 단위로는 채택했다.** 아래 본문은 현재 코드 기준으로 정정한 것이다.

> 여러 사용자가 같은 일정을 동시에 드래그앤드롭으로 편집하는 협업 기능에서, **동시성을 어디까지 어떻게 막을지** 고민하고 결정한 과정과 그 이유를 정리한다. 복기·질문 대비용.

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
| **낙관적 락** (`version`) | 같은 row 동시 수정 | 컬럼 1개 + 조건부 UPDATE | ✅ 채택 (블록 단위) |
| **비관적 락** (`SELECT … FOR UPDATE`) | 여러 row에 걸친 제약 | 락 구간만큼 직렬화 | ✅ 채택 (일정 행 단위로 한정) |
| **비관적 락을 블록마다** | 모든 경합 | 락 경합·데드락 위험, 드래그 UX와 안 맞음 | ❌ 과함 |
| **grab 서버 강제** | 드래그 중 블록 선제 차단 | 기존 인프라 호출만 | ✅ 보조 |
| **CRDT / OT** (자동 병합) | 모든 충돌 자동 해소 | 구현 복잡도 폭발 | ❌ 2인 데모엔 과함 |

결정: **낙관적 락(블록 단위 하드 보장) + 일정 행 `FOR UPDATE`(cross-row 제약 직렬화) + grab 서버 강제(소프트 선제) + transit 분리**. 자동 병합은 하지 않고 **"충돌 시 한쪽이 재조회 후 재시도"** 정책으로 간다. 커서 가시화(고민 5번)가 충돌 빈도 자체를 낮춰주므로, 드물게 발생하는 충돌은 거부 후 재조회로 충분하다고 판단했다.

### 두 락을 어떤 기준으로 나눴나
기준은 "충돌 빈도"가 아니라 **제약이 몇 개의 row에 걸쳐 있는가**다.

- **한 row 안에서 끝나는 제약 → 낙관적 락.** 같은 블록을 둘이 옮기는 경우가 여기다. `version`은 "같은 row"에만 작용하므로 서로 다른 블록은 서로 다른 version → **절대 오탐하지 않는다**(고민 2번을 구조적으로 보장). 우리 환경은 충돌이 드물어(2인 + 커서로 서로 인지) 평소 오버헤드 0이라는 점도 맞았다.
- **여러 row에 걸친 제약 → 부모 행 잠금.** "같은 날짜에 두 블록이 같은 시간대에 겹치면 안 된다"는 **한 행의 값만 봐서는 판정할 수 없다.** 검사 대상이 다른 row이므로 `version`으로는 원천적으로 못 막는다. PostgreSQL이라면 배제 제약(exclusion constraint)을 쓰겠지만 MySQL엔 없다. 그래서 검사 직전 **부모인 `trip` 행을 `SELECT … FOR UPDATE`로 잠가** 같은 일정의 편집을 직렬화했다. 둘이 동시에 겹치는 자리에 놓아도 둘째는 첫째가 커밋된 상태를 보고 검사하므로 한쪽만 통과한다.
- 잠금 구간이 짧은 이유: 외부 API를 부르는 느린 transit 재계산은 이미 `afterCommit`으로 트랜잭션 밖으로 빼 놨다(§5-(3)). 잠금 안에는 겹침 검사와 순서 할당만 남는다.
- 부수 효과로 `display_order` **MAX+1 race**도 이 잠금 안에서 안전해졌다. 원래 별건으로 보류했던 문제가 같은 직렬화 지점에서 함께 해결됐다.

---

## 4. 결정한 충돌 기준 (이 설계의 핵심)

| 시나리오 | 감지 메커니즘 | 결과 |
|---|---|---|
| **같은 블록** 동시 이동/리사이즈 | 낙관적 락(version) | 둘째 요청 **409** → 재조회 후 재시도 |
| 한쪽이 드래그 중(grab) + 다른쪽 수정 시도 | grab 서버 게이트(소프트) | 시도 즉시 **409 "○○님이 편집 중"** (선제) |
| **서로 다른** 블록 → 서로 다른 위치 | 독립 row·독립 version | **충돌 없음** (정상 동시 처리) |
| transit 재계산 ↔ 사용자 편집 | transit 전용 UPDATE(version 미검사·미증가) | **충돌 없음** |
| 한쪽 삭제 + 다른쪽 같은 블록 이동 | 이동의 versioned UPDATE가 0행 매칭 | 이동 측 **409** → 재조회 시 삭제 반영 |
| **다른** 블록 → **같은 시간대 겹침** | `trip` 행 `FOR UPDATE` + 겹침 검사 | 둘째 **409** (cross-row 제약) |

핵심 불변식 (1): **transit 전용 UPDATE가 version을 건드리지 않는 것**이 4번 행(오탐 없음)의 전제다. 만약 재계산이 version을 올리면, A의 편집 직후 시스템 재계산이 B의 version을 무효화해 멀쩡한 편집이 거부된다. 그래서 사용자 편집 UPDATE와 transit UPDATE를 **물리적으로 다른 쿼리**로 분리했다.

핵심 불변식 (2): **6번 행은 낙관적 락으로는 도달할 수 없는 영역이다.** 겹침 여부는 다른 row를 읽어야 판정되므로 `version` 비교로는 감지 자체가 안 된다. 이 한 행 때문에 부모 행 잠금이라는 다른 수단이 필요했다.

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

### (4) 시간대 겹침 금지 + `trip` 행 잠금 (cross-row 제약)
```xml
<!-- 같은 일정 편집을 직렬화하는 부모 행 잠금 -->
<select id="lockTripRow" parameterType="long" resultType="long">
  SELECT id FROM trip WHERE id = #{id} FOR UPDATE
</select>
```
```java
tripMapper.lockTripRow(tripId);  // 같은 일정 동시 편집 직렬화(겹침 검사·순서 할당 정합 보장)
// 이 뒤에서 countOverlapping 검사 → 겹치면 409
```
- 검사 자체는 `countOverlapping`이 `[start, start+duration)` **분 단위 교차**로 판정한다(TIME 자정 넘김 회피).
- 적용 지점은 `placeBlock`과 위치를 바꾸는 `updateBlock` 두 곳. **읽기 경로에는 걸지 않는다.**
- 잠금 대상이 블록이 아니라 **부모 `trip` 행**인 것이 핵심이다. 블록마다 잠그면 "아직 존재하지 않는 겹침 상대"를 잠글 수 없다. 검사와 삽입이 같은 직렬화 구간 안에 들어와야 TOCTOU가 사라진다.
- 잠금 구간 안에 남은 작업은 겹침 검사와 순서 할당뿐이다. 느린 외부 API 재계산은 (3)에서 이미 `afterCommit`으로 빠져 있다.

---

## 6. 의도적으로 보류한 것 (그리고 그 이유)

제출 일정과 충돌 빈도(2인·커서 인지)를 고려해 **비용 대비 효과가 높은 범위만** 적용하고 나머지는 `improvements.md §9`에 등재했다.

- ~~**displayOrder 정합성**~~ — 보류했다가 §5-(4)에서 **해결됐다.** `trip` 행 잠금이 겹침 검사와 순서 할당을 같은 직렬화 구간에 넣으면서 `display_order` MAX+1 race도 함께 안전해졌다. 남은 것은 이미 어긋난 값을 되돌리는 서버 권위 정규화뿐이고, 이건 여전히 후순위다.
- **이벤트 시퀀스 번호** — 브로드캐스트 순서 역전·유실 감지. 재연결 `loadTrip` 전체 재조회로 수렴하므로 데이터 유실은 없음. 후순위.
- **presence in-memory 경합** — `ConcurrentHashMap` 복합 연산 비원자성, 재연결 유령 커서 등. 영향이 **표시 수준(커서 깜빡임)**이지 영속 데이터 손상이 아니라 제외.
- **권한 캐시 무효화** — 협업자 제거 후 세션 캐시가 즉시 안 풀리는 창. presence(커서)에만 영향, 편집은 매 요청 DB 검증이라 안전.

> 판단 기준을 일관되게 "**영속 데이터 유실 = 지금, 표시/성능 = 나중**"으로 잡았다.

---

## 7. 결과

| 영역 | 변경 |
|------|------|
| DB | `trip_block.version` 추가(마이그레이션 + schema.sql) |
| 백엔드 | `updateWithVersion`/`updateTransitById` 매퍼, `updateBlock`/`removeBlock` 락·grab 게이트, `recalculateTransitForDate` afterCommit 분리, `TripPresenceController` 주입 |
| 백엔드(cross-row) | `lockTripRow`(`SELECT … FOR UPDATE`) 매퍼 + `countOverlapping` 겹침 검사, `placeBlock`/`updateBlock` 위치 편집 경로에 적용 |
| DTO | `BlockItem`·`BlockUpdateRequest`에 `version` |
| 프론트 | `buildEvent`에 version, move/resize 요청에 동봉, `isConflict()` 409 처리 |

검증: 백 `compileJava` ✅ / 프론트 `vite build` ✅. **마이그레이션 선적용 필수.** 2계정 동시 편집 E2E는 실사용 검증 예정.

---

## 8. 면접 예상 질문 정리

**Q. 낙관적 락과 비관적 락 중 무엇을 썼나?**
둘 다 썼고, 나눈 기준은 충돌 빈도가 아니라 **제약이 몇 개의 row에 걸쳐 있는가**다. 같은 블록 동시 수정처럼 한 row 안에서 끝나는 제약은 낙관적 락(`version`)으로 막았다. 충돌이 드문 환경(2인 + 커서로 서로 인지)이라 평소 오버헤드가 0인 쪽이 맞았다. 반면 "같은 날짜에 두 블록이 같은 시간대에 겹치면 안 된다"는 **다른 row를 읽어야 판정되는 제약**이라 `version`으로는 감지 자체가 안 된다. 그래서 검사 직전 부모인 `trip` 행을 `SELECT … FOR UPDATE`로 잠가 같은 일정의 편집을 직렬화했다.

**Q. 그럼 왜 블록마다 비관적 락을 걸지 않았나?**
겹침은 "아직 존재하지 않는 상대"와도 발생한다. 내가 넣으려는 블록이 충돌할 대상을 미리 알 수 없으니 잠글 대상도 특정할 수 없다. 검사와 삽입이 같은 직렬화 구간에 들어와야 TOCTOU가 사라지고, 그 구간을 만들 수 있는 공통 조상이 `trip` 행이었다. 블록 단위로 잠그면 락 개수만 늘고 드래그 UX와도 맞지 않는다.

**Q. 부모 행을 잠그면 그 일정 전체가 멈추는 것 아닌가?**
맞다. 그래서 잠금 구간을 짧게 유지하는 게 전제다. 느린 외부 API(ODsay/TMap) 재계산은 이미 `afterCommit`으로 트랜잭션 밖에 빼 뒀고, 잠금 안에는 겹침 검사와 순서 할당만 남는다. 2인 편집 규모에서는 이 비용이 겹침 데이터가 들어가는 비용보다 싸다고 봤다. 편집자가 많아지면 날짜 단위로 잠금을 쪼개는 것이 다음 단계다.

**Q. PostgreSQL이었다면 어떻게 했을 것 같나?**
배제 제약(exclusion constraint)으로 DB가 직접 겹침을 막게 했을 것이다. 그러면 애플리케이션 잠금 없이도 같은 불변식이 성립한다. MySQL엔 그 기능이 없어 검사 시점을 직렬화하는 쪽을 택했다.

**Q. 버전이 다르면 무조건 충돌인가? transit 재계산 때문에 오탐 안 나나?**
안 난다. `version`은 같은 row에만 작용하고, **transit 재계산은 version을 올리지 않는 별도 UPDATE**로 분리했다. 그래서 시스템 재계산이 사용자 편집을 무효화하지 못한다. 서로 다른 블록 편집도 row가 달라 오탐이 없다.

**Q. "다른 블록을 같은 자리에 놓는" 충돌은?**
version(같은 row)으로는 못 막는 영역이라, 처음엔 커서 인지에 맡기고 서버 권위 정규화를 후속 과제로 미뤘다. 이후 `trip` 행 잠금 + 겹침 검사로 **하드 차단으로 승격**했다(§5-(4)). 남은 후속 과제는 이미 어긋난 `display_order`를 되돌리는 정규화뿐이다. 판단 기준은 그대로였다 — **영속 데이터가 어긋나는 것은 지금 막고, 표시·성능 문제는 나중으로.** 겹침은 저장되고 나면 사용자가 직접 지워야 하는 데이터라 앞쪽에 속했다.

**Q. 외부 API를 트랜잭션에서 왜 빼야 하나?**
ODsay/TMap 호출이 수 초 걸릴 수 있는데, `@Transactional` 안에 있으면 그동안 DB 커넥션을 점유한다. 동시 편집이 늘면 커넥션 풀 고갈·락 대기로 번진다. `afterCommit`으로 빼서 트랜잭션은 즉시 커밋하고 외부 호출은 밖에서 하게 했다.

**Q. 충돌이 났을 때 사용자 경험은?**
자동 병합은 하지 않는다. 진 쪽은 "다른 사용자가 먼저 수정했어요" 토스트와 함께 최신 상태로 자동 재조회되고, 보고 다시 시도한다. 2인 협업에선 이 정도 정책이 단순하고 예측 가능하다.

**Q. grab과 낙관적 락은 중복 아닌가?**
역할이 다르다. grab은 **사전**(드래그 중 선제 차단, UX 친화), 낙관적 락은 **사후**(grab을 안 거친 경로·stale 해제 순간까지 잡는 최종 보장). 두 층으로 나눠 충돌 빈도를 줄이면서 정합성을 보장한다.
