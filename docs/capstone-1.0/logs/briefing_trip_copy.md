# 브리핑 — 일정 복사(가져오기) 기능 추가

> **작성 목적**: 커뮤니티 기능 작업 중 plan 도메인 코드를 수정했습니다.
> 해당 코드를 작성한 팀원에게 변경 내용을 구체적으로 공유하기 위한 문서입니다.
>
> **작업자**: 전진  
> **작업일**: 2026-06-09  
> **브랜치**: `feature/community`

---

## 1. 작업 배경 및 목적

커뮤니티 게시글에 연결된 일정을 다른 사용자가 자신의 일정으로 복사해 갈 수 있는
**"일정 가져오기"** 기능을 추가했습니다.

핵심 설계 결정:
- 원본 일정의 **날짜 간격(Day 구조)을 그대로 유지**하고, 사용자가 입력한 새 시작일로 날짜만 재계산합니다.
- 커뮤니티에 공유된 일정(post.trip_id로 연결된 일정)만 복사를 허용합니다.
- 복사된 일정은 **비공개(isPublic=false)** 로 생성됩니다.
- 기존 Mapper·도메인 클래스는 **전혀 건드리지 않았습니다.** 새 DTO 1개 추가, 기존 Service 인터페이스·구현체·Controller에 메서드 1개씩 추가한 것이 전부입니다.

---

## 2. 변경 파일 목록

| 파일 | 변경 종류 | 한 줄 요약 |
|------|----------|-----------|
| `plan/dto/TripCopyRequest.java` | **신규 생성** | 가져오기 요청 DTO (`newStartDate` 1개 필드) |
| `plan/service/TripService.java` | **메서드 1개 추가** | `copyTrip` 시그니처 선언 |
| `plan/service/TripServiceImpl.java` | **메서드 1개 추가 + import 2개** | `copyTrip` 구현체 |
| `plan/controller/TripController.java` | **엔드포인트 1개 추가 + import 2개** | `POST /api/trips/{tripId}/copy` |
| `frontend/src/api/trip.js` | **함수 1개 추가** | `tripApi.copy()` |

> **건드리지 않은 파일**: `Trip.java`, `TripBlock.java`, `TripCandidate.java`,
> `TripMapper.java`, `TripBlockMapper.java`, `TripCandidateMapper.java`,
> `TripMapper.xml`, `TripBlockMapper.xml`, `TripCandidateMapper.xml`,
> `TripCreateRequest.java`, `TripDetailResponse.java`, `TripSummary.java` 등
> plan 도메인의 모든 기존 파일은 수정하지 않았습니다.

---

## 3. 파일별 변경 상세

---

### 3-1. `TripCopyRequest.java` (신규)

**경로**: `backend/src/main/java/com/tripcraft/plan/dto/TripCopyRequest.java`

```java
package com.tripcraft.plan.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import java.time.LocalDate;

@Getter
public class TripCopyRequest {

    @NotNull(message = "새 여행 시작일을 입력해주세요.")
    private LocalDate newStartDate;
}
```

- 요청 바디는 `{ "newStartDate": "2026-08-20" }` 형태입니다.
- `@NotNull`을 붙여 Controller에서 `@Valid`로 검증합니다.
- Spring의 Jackson이 `"2026-08-20"` 문자열을 `LocalDate`로 자동 역직렬화합니다.
  (`application.yml`에 별도 설정이 없어도 Spring Boot 기본 설정으로 동작합니다.)

---

### 3-2. `TripService.java` (인터페이스에 메서드 1개 추가)

**경로**: `backend/src/main/java/com/tripcraft/plan/service/TripService.java`

**추가된 부분만 발췌:**

```java
// 추가된 import
import com.tripcraft.plan.dto.TripCopyRequest;

// 추가된 메서드 시그니처 (인터페이스 맨 아래에 추가)
/**
 * 공유된 일정을 복사해 내 새 일정으로 저장.
 * 날짜는 newStartDate 기준으로 원본 일정의 Day 간격을 그대로 유지해 재계산.
 * @return 새로 생성된 일정 ID
 */
Long copyTrip(Long sourceTripId, TripCopyRequest request, Long memberId);
```

- 기존 메서드 시그니처는 전혀 변경하지 않았습니다.
- 인터페이스 맨 마지막 줄 `updateDefaultTransitMode(...)` 바로 아래에 추가했습니다.

---

### 3-3. `TripServiceImpl.java` (구현체에 메서드 1개 추가 + import 2개)

**경로**: `backend/src/main/java/com/tripcraft/plan/service/TripServiceImpl.java`

#### 추가된 import (2개)

```java
import java.time.temporal.ChronoUnit;   // 날짜 오프셋 계산용
import java.util.HashMap;               // candidateId 매핑 테이블용
```

기존에 없던 import 2개를 파일 상단에 추가했습니다.

#### 추가된 메서드 (기존 recalculateTransitForDate 메서드 바로 앞에 삽입)

```java
@Override
@Transactional
public Long copyTrip(Long sourceTripId, TripCopyRequest request, Long memberId) {

    // ① 원본 일정 존재 확인
    Trip source = tripMapper.findById(sourceTripId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "일정을 찾을 수 없습니다."));

    // ② 공개 일정(커뮤니티에 연결된 일정)만 복사 허용
    //    기존에 구현된 tripMapper.existsPostByTripId()를 재활용합니다.
    if (!tripMapper.existsPostByTripId(sourceTripId)) {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "공유되지 않은 일정은 가져올 수 없습니다.");
    }

    // ③ 날짜 오프셋 계산
    //    원본 시작일과 새 시작일의 차이(일수)를 구해서
    //    원본의 모든 날짜에 동일하게 더합니다.
    //    예) 원본 3/10~3/13, 새 시작일 8/20 → offset=+163일 → 새 종료일 8/23
    long dayOffset = ChronoUnit.DAYS.between(source.getStartDate(), request.getNewStartDate());
    LocalDate newEndDate = source.getEndDate().plusDays(dayOffset);

    // ④ 새 Trip 생성 (title에 "(가져옴)" 접미사, isPublic=false)
    Trip newTrip = new Trip();
    newTrip.setMemberId(memberId);
    newTrip.setTitle(source.getTitle() + " (가져옴)");
    newTrip.setStartDate(request.getNewStartDate());
    newTrip.setEndDate(newEndDate);
    newTrip.setMemberCount(source.getMemberCount());
    newTrip.setDefaultTransitMode(source.getDefaultTransitMode());
    newTrip.setIsPublic(false);
    tripMapper.insert(newTrip);   // 기존 insert 메서드 그대로 사용

    // ⑤ 후보군(TripCandidate) 복사
    //    원본 candidateId → 새 candidateId 매핑 테이블을 만듭니다.
    //    이 매핑이 없으면 이후 TripBlock 복사 시 FK가 어긋납니다.
    List<TripCandidate> candidates = candidateMapper.findByTripId(sourceTripId);
    Map<Long, Long> candidateIdMap = new HashMap<>();
    for (TripCandidate c : candidates) {
        TripCandidate newC = new TripCandidate();
        newC.setTripId(newTrip.getId());
        newC.setAttractionId(c.getAttractionId());
        newC.setCityCode(c.getCityCode());
        newC.setSource("MANUAL");      // 복사본이므로 출처는 항상 MANUAL
        candidateMapper.insert(newC);  // 기존 insert 메서드 그대로 사용
        candidateIdMap.put(c.getId(), newC.getId());
    }

    // ⑥ 블록(TripBlock) 복사
    //    날짜만 오프셋 적용, 나머지 필드(시간·이동시간 등)는 그대로 복사합니다.
    //    이동 시간(transitDurationMinutes, transitMode)은 출발지/도착지가 동일하므로
    //    재계산 없이 그대로 가져옵니다.
    List<TripBlock> blocks = blockMapper.findByTripId(sourceTripId);
    for (TripBlock b : blocks) {
        Long newCandidateId = candidateIdMap.get(b.getCandidateId());
        if (newCandidateId == null) continue;  // 매핑 누락 방어 처리
        TripBlock newB = new TripBlock();
        newB.setCandidateId(newCandidateId);
        newB.setTripDate(b.getTripDate().plusDays(dayOffset));  // 날짜 재계산
        newB.setDisplayOrder(b.getDisplayOrder());
        newB.setStartTime(b.getStartTime());
        newB.setDurationMinutes(b.getDurationMinutes());
        newB.setTransitDurationMinutes(b.getTransitDurationMinutes());
        newB.setTransitMode(b.getTransitMode());
        blockMapper.insert(newB);   // 기존 insert 메서드 그대로 사용
    }

    log.info("일정 복사: sourceTripId={} → newTripId={}, memberId={}, dayOffset={}",
            sourceTripId, newTrip.getId(), memberId, dayOffset);
    return newTrip.getId();
}
```

**핵심 설계 포인트:**

| 포인트 | 설명 |
|--------|------|
| Mapper 재활용 | `tripMapper.insert`, `candidateMapper.findByTripId`, `candidateMapper.insert`, `blockMapper.findByTripId`, `blockMapper.insert` 모두 기존 메서드를 그대로 사용했습니다. Mapper에 새 메서드를 추가하거나 XML을 수정하지 않았습니다. |
| candidateIdMap | 원본 candidate.id → 새 candidate.id 매핑을 `HashMap`으로 관리합니다. 블록의 `candidate_id` FK가 새 일정의 candidate를 가리키도록 연결하기 위해 필수입니다. |
| 날짜 재계산 | `ChronoUnit.DAYS.between(원본시작일, 새시작일)`로 일수 차이를 구하고, 블록마다 `b.getTripDate().plusDays(dayOffset)`로 적용합니다. 음수 오프셋(과거 날짜 지정)도 수학적으로는 동작하지만, 프론트에서 `min=today`로 막았습니다. |
| 이동 시간 유지 | `transitDurationMinutes`, `transitMode`는 출발지·도착지가 동일하면 날짜가 바뀌어도 값이 유효합니다. 재계산 API 호출 비용을 아끼기 위해 그대로 복사했습니다. |
| 공개 일정 검증 | `tripMapper.existsPostByTripId()`는 이미 `getBlocksSummary()`에서도 사용 중인 메서드입니다. post.trip_id로 연결된 일정만 복사 가능합니다. |
| `@Transactional` | trip → candidates → blocks 순서로 insert가 이루어집니다. 도중 실패 시 전체 롤백됩니다. |

---

### 3-4. `TripController.java` (엔드포인트 1개 추가 + import 2개)

**경로**: `backend/src/main/java/com/tripcraft/plan/controller/TripController.java`

#### 추가된 import (2개)

```java
import com.tripcraft.plan.dto.TripCopyRequest;
import jakarta.validation.Valid;
```

`@Valid`는 기존 Controller에 없었습니다. `TripCopyRequest`의 `@NotNull` 검증을 활성화하기 위해 추가했습니다.

#### 추가된 엔드포인트

기존 `getBlocksSummary` 엔드포인트와 `updateDefaultTransitMode` 엔드포인트 **사이에** 추가했습니다.

```java
/** 공유된 일정 가져오기 — 시작일 기준으로 날짜 재계산 후 내 일정으로 복제 */
@PostMapping("/{tripId}/copy")
public ResponseEntity<ApiResponse<Long>> copyTrip(
        @PathVariable("tripId") Long tripId,
        @Valid @RequestBody TripCopyRequest request,
        @AuthenticationPrincipal Long memberId) {
    Long newTripId = tripService.copyTrip(tripId, request, memberId);
    return ResponseEntity.status(201).body(ApiResponse.ok(newTripId));
}
```

**API 명세:**

| 항목 | 내용 |
|------|------|
| Method | `POST` |
| URL | `/api/trips/{tripId}/copy` |
| 인증 | 필요 (JWT, `@AuthenticationPrincipal`) |
| Request Body | `{ "newStartDate": "2026-08-20" }` |
| Response (201) | `{ "success": true, "data": 새_일정_ID }` |
| 에러 404 | `tripId`에 해당하는 일정이 없는 경우 |
| 에러 403 | 커뮤니티에 공유되지 않은 일정인 경우 |
| 에러 400 | `newStartDate`가 null인 경우 (`@Valid` 검증 실패) |

응답 코드를 `201 Created`로 설정했습니다. 새 리소스(일정)가 생성되기 때문입니다.

---

### 3-5. `frontend/src/api/trip.js` (함수 1개 추가)

**경로**: `frontend/src/api/trip.js`

기존 `tripApi` 객체 맨 아래에 함수 1개 추가했습니다.

```js
// 추가 전 (마지막 줄)
getBlocksSummary: (tripId) => http.get(`/api/trips/${tripId}/blocks-summary`),

// 추가 후
getBlocksSummary: (tripId) => http.get(`/api/trips/${tripId}/blocks-summary`),

/** 공유된 일정 가져오기 — newStartDate(YYYY-MM-DD) 기준으로 날짜 재계산 후 내 일정으로 복제 */
copy: (tripId, newStartDate) => http.post(`/api/trips/${tripId}/copy`, { newStartDate }),
```

- `http.post`는 기존 프로젝트의 API 래퍼를 그대로 사용합니다.
- `newStartDate`는 `"2026-08-20"` 형태의 문자열로 전달됩니다. (`<input type="date">` 의 `.value`가 이 형식을 반환합니다.)

---

## 4. 기존 코드에 영향을 주지 않음을 확인하는 방법

아래 사항을 확인하면 기존 기능이 정상 동작함을 검증할 수 있습니다.

1. **기존 일정 CRUD** (`GET /api/trips`, `POST /api/trips`, `DELETE /api/trips/{id}`) — 변경 없음.
2. **후보군·블록 CRUD** (`/candidates`, `/blocks`) — 변경 없음.
3. **이동 시간 재계산** (`recalculateTransitForDate`) — 새 메서드에서 호출하지 않음. 완전히 독립.
4. **`getBlocksSummary`** — 변경 없음. `copyTrip`은 별도 메서드.
5. **Mapper XML** — 전혀 건드리지 않음.

---

## 5. 프론트엔드 UI 동작 흐름 (참고)

이 기능의 UI는 `CommunityPostView.vue`(커뮤니티 도메인)에 있어 팀원이 직접 수정하지 않아도 됩니다. 참고용으로 기록합니다.

```
커뮤니티 게시글 상세 페이지
  └── trip card (일정 연결된 게시글에만 표시)
        ├── [📥 가져오기] 버튼  ← 로그인한 사용자에게만 표시
        └── [▼ 일정 보기] 토글

[가져오기] 버튼 클릭
  └── 모달 팝업
        ├── 안내 문구: "내 여행 시작일을 선택하면 날짜 간격을 유지한 채 새 일정으로 저장돼요."
        ├── <input type="date" min=오늘>
        └── [가져오기] 버튼
              └── POST /api/trips/{tripId}/copy { newStartDate }
                    └── 성공: 토스트 "내 일정에 저장됐어요! 일정 페이지에서 확인하세요."
                    └── 실패: 토스트 에러 메시지
```

---

## 6. 질문 가능한 포인트 정리

**Q. `source`를 `"MANUAL"`로 고정한 이유?**  
기존 `TripCandidate.source` 타입은 `ENUM('MANUAL', 'FAVORITE')`입니다.
복사된 일정은 즐겨찾기 자동 연동이 아닌 사용자가 직접 가져온 것이므로 `MANUAL`로 설정했습니다.

**Q. `transitDurationMinutes`와 `transitMode`를 왜 재계산하지 않나?**  
이동 시간은 (출발지, 도착지, 시간대, 이동수단 모드)의 함수이고 날짜와 무관합니다.
기존에 캐시된 값이 있으면 그대로 유효하므로 불필요한 외부 API 호출을 피했습니다.
사용자가 블록을 수정하면 기존 `recalculateTransitForDate` 로직이 정상 동작합니다.

**Q. trip_block 날짜 범위 TRIGGER가 복사 시에도 동작하나?**  
동작합니다. `newEndDate = source.getEndDate().plusDays(dayOffset)`로 먼저 종료일을 계산하고,
블록의 `tripDate`도 동일한 오프셋을 적용하기 때문에 TRIGGER 검증을 항상 통과합니다.
