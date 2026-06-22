# 일정 공유 (읽기 전용 공유 링크) 구현 계획

> **작성일**: 2026-06-22  
> **작성자**: 전진 (문서 분리·재구성: 송정기, 2026-06-22)  
> **관련 기능**: 일정 읽기 전용 공유 — 원본 `collab_plan.md`에서 분리
>
> 이 공유 링크를 카카오톡으로 배포하는 기능은 [`kakao_plan.md`](kakao_plan.md) 참조.

---

## 목차

1. [개념](#1-개념)
2. [백엔드 구현](#2-백엔드-구현)
3. [프론트엔드 구현](#3-프론트엔드-구현)
4. [공유 링크 UI 배치](#4-공유-링크-ui-배치)
5. [DB 스키마 변경 계획](#5-db-스키마-변경-계획)
6. [조심해야 할 포인트](#6-조심해야-할-포인트)

---

## 1. 개념

일정 소유자가 공유 링크를 생성하면 UUID 토큰이 발급된다.  
해당 링크를 아는 사람은 누구나(비회원 포함) 일정을 **읽기 전용**으로 열람할 수 있다.  
커뮤니티 `is_public` 공유와 다르게, 링크를 모르면 접근할 수 없는 **비공개 공유**다.

```
소유자: [공유 링크 생성] → UUID 토큰 발급
         ↓ 링크 복사·전달
동행자: https://tripcraft.com/trip/shared/{shareToken} 접속
         ↓ 토큰으로 일정 조회 (인증 불필요)
         읽기 전용 뷰 표시 (편집 버튼 없음)
```

---

## 2. 백엔드 구현

**추가할 API**

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/trips/{id}/share` | 공유 링크 생성·재발급 (소유자만) |
| `DELETE` | `/api/trips/{id}/share` | 공유 링크 비활성화 |
| `GET` | `/api/trips/shared/{shareToken}` | 토큰으로 일정 조회 (인증 불필요) |

**`trip` 테이블 컬럼 추가**

```sql
ALTER TABLE trip
    ADD COLUMN share_token  VARCHAR(36) NULL COMMENT '공유 링크 UUID (NULL=비공개)',
    ADD COLUMN share_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '공유 활성화 여부';

CREATE UNIQUE INDEX uq_trip_share_token ON trip(share_token);
```

**주요 로직**

- 공유 링크 생성 시 `UUID.randomUUID()`로 토큰 발급 → DB 저장
- `GET /api/trips/shared/{shareToken}` — SecurityConfig에서 `permitAll()` 처리
- 응답에 편집용 필드(소유자 정보 등) 제외한 읽기 전용 DTO 반환

---

## 3. 프론트엔드 구현

- `/trip/shared/:token` 라우트 추가 (인증 불필요)
- 기존 `ScheduleView`를 `readOnly` prop으로 분기 — 드래그·삭제·저장 버튼 비노출
- 소유자 화면에 "공유 링크 복사" 버튼 추가

---

## 4. 공유 링크 UI 배치

공유 링크 UI는 일정 상세 페이지 헤더 우측에 "공유" 버튼으로 노출한다.

```
[일정 제목]                          [공유 ▼]  [편집]  [삭제]
                                         ↓ 드롭다운
                               ┌──────────────────────────┐
                               │ 링크 공유                 │
                               │ 활성화 [토글 ON]          │
                               │ https://.../{token} [복사] │
                               │ [카카오로 공유]            │ ← kakao_plan.md 참조
                               └──────────────────────────┘
```

- 토글 OFF → `share_enabled = 0`, 기존 토큰 접근 차단 (토큰 값은 유지)
- 토글 재ON → 같은 토큰 재활성화 (새 UUID 발급 안 함)
- 토큰이 없는 경우(최초)에만 `UUID.randomUUID()`로 발급
- 드롭다운 내 `[카카오로 공유]` 버튼 동작은 [`kakao_plan.md`](kakao_plan.md)의 카카오 일정 공유하기 참조

---

## 5. DB 스키마 변경 계획

```sql
-- trip 테이블에 공유 링크 컬럼 추가
ALTER TABLE trip
    ADD COLUMN share_token   VARCHAR(36)  NULL    COMMENT '공유 링크 UUID',
    ADD COLUMN share_enabled TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '공유 활성화 여부';

CREATE UNIQUE INDEX uq_trip_share_token ON trip(share_token);
```

---

## 6. 조심해야 할 포인트

- **토큰 추측 방지**: `UUID.randomUUID()`는 122비트 엔트로피로 충분. `SMALLINT` 시퀀스 ID는 절대 사용 금지.
- **링크 비활성화**: 소유자가 링크를 끄면 기존 토큰으로 접근 불가. `share_enabled = 0`으로 처리 (토큰은 유지, 재활성화 가능).
- **공유 중인 일정 삭제**: 현재 `existsPostByTripId` 로직과 유사하게, 공유 활성화 상태이면 삭제 전 확인 처리 필요.
