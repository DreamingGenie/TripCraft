# 📐 개발 컨벤션 & 협업 규칙

> TripCraft 프로젝트의 코딩 컨벤션, Git 브랜치 전략, 커밋 메시지 규칙을 정의합니다.  
> 두 사람이 일관된 방식으로 협업하기 위한 최소한의 약속입니다.

---

## 1. Git 브랜치 전략

**GitHub Flow** 방식을 기반으로 단순화하여 사용합니다.

```
main
└── develop
    ├── feature/attraction-api          # 기능 개발
    ├── feature/plan-dragdrop
    ├── fix/member-login-error          # 버그 수정
    └── docs/add-erd                    # 문서 작업
```

### 브랜치 규칙

| 브랜치 | 용도 | 병합 대상 |
|--------|------|---------|
| `main` | 릴리즈 버전 (배포 가능 상태) | PR from `develop` |
| `develop` | 통합 개발 브랜치 | PR from `feature/*`, `fix/*` |
| `feature/{기능명}` | 단위 기능 개발 | → `develop` |
| `fix/{이슈명}` | 버그 수정 | → `develop` |
| `docs/{내용}` | 문서 작업 | → `develop` |

### 브랜치 이름 규칙

```
feature/도메인-기능명
예) feature/attraction-search
    feature/plan-block-dragdrop
    feature/member-jwt-auth
    fix/plan-time-calculation-bug
    docs/update-erd
```

---

## 2. 커밋 메시지 규칙

**Conventional Commits** 형식을 따릅니다.

```
<type>(<scope>): <subject>

[body — 선택사항]

[footer — 선택사항]
```

### type 목록

| type | 사용 상황 |
|------|---------|
| `feat` | 새로운 기능 추가 |
| `fix` | 버그 수정 |
| `refactor` | 기능 변화 없는 코드 개선 |
| `test` | 테스트 코드 추가·수정 |
| `docs` | 문서 작성·수정 |
| `style` | 포매팅, 세미콜론 등 코드 의미 변화 없음 |
| `chore` | 빌드 설정, 패키지 수정 등 |
| `perf` | 성능 개선 |

### 예시

```
feat(attraction): 한국관광공사 API 데이터 수집 배치 구현

- 시도·시군구·콘텐츠타입 필터로 전체 관광지 수집
- API 호출 실패 시 재시도 로직 (3회) 추가

feat(plan): 드래그 앤 드롭 블록 시간표 컴포넌트 추가
fix(member): JWT 만료 후 refresh token 갱신 오류 수정
docs: ERD 초안 추가 및 README 기술스택 업데이트
test(attraction): AttractionService 단위 테스트 작성
```

### 규칙 요약

- subject는 **한국어 또는 영어** 통일 (팀 내 합의)
- subject는 50자 이내, 마침표 없이
- body는 **왜(Why)** 변경했는지 위주로 작성
- 1커밋 = 1논리 단위 (한 커밋에 여러 기능 혼재 금지)

---

## 3. PR (Pull Request) 규칙

### PR 생성 기준

- `feature/*` 브랜치의 작업이 완료되면 `develop`으로 PR 생성
- PR 제목은 커밋 타입 형식과 동일하게 작성

### PR 템플릿

```markdown
## 작업 내용
- [ ] 구현한 기능/수정 사항 요약

## 변경 사유
왜 이 변경이 필요한지 간략히 작성

## 테스트 방법
어떻게 동작을 확인했는지

## 관련 이슈
Closes #이슈번호 (있는 경우)
```

### 리뷰 규칙

- 상대방이 최소 1회 리뷰 후 Merge
- `main` 브랜치 직접 Push 금지
- Merge 전 빌드·테스트 통과 확인

---

## 4. 백엔드 코딩 컨벤션 (Java / Spring Boot)

### 패키지 구조 (도메인 중심)

```
com.tripcraft/
├── attraction/
│   ├── AttractionController.java
│   ├── AttractionService.java
│   ├── AttractionServiceImpl.java
│   ├── AttractionMapper.java
│   └── dto/
│       ├── AttractionDto.java
│       └── AttractionSearchDto.java
├── plan/
├── member/
├── community/
└── global/
    ├── config/          # Spring 설정 클래스
    ├── exception/        # 전역 예외 처리
    └── util/
```

### 네이밍 규칙

| 대상 | 규칙 | 예시 |
|------|------|------|
| 클래스 | PascalCase | `AttractionServiceImpl` |
| 메서드·변수 | camelCase | `findByRegion`, `sidoCode` |
| 상수 | UPPER_SNAKE_CASE | `MAX_RETRY_COUNT` |
| DB 컬럼 | snake_case | `content_type_id` |
| DTO 필드 | camelCase (Jackson 자동 변환) | `contentTypeId` |
| URL | kebab-case | `/trip-plans/{id}` |

### REST API 설계 원칙

```
GET    /api/attractions           # 관광지 목록 조회
GET    /api/attractions/{id}      # 관광지 단건 조회
GET    /api/trip-plans            # 내 일정 목록
POST   /api/trip-plans            # 일정 생성
PATCH  /api/trip-plans/{id}       # 일정 수정 (일부)
DELETE /api/trip-plans/{id}       # 일정 삭제
```

- 복수형 명사 사용
- 동사 URL 금지 (행위는 HTTP 메서드로 표현)
- 계층 구조는 `/` 로 표현

### 응답 형식 통일

```json
{
  "success": true,
  "data": { ... },
  "message": null
}
```

오류 시:
```json
{
  "success": false,
  "data": null,
  "message": "해당 관광지를 찾을 수 없습니다.",
  "errorCode": "ATTRACTION_NOT_FOUND"
}
```

---

## 5. 프론트엔드 컨벤션 (Vue.js 3)

### 파일 네이밍

| 대상 | 규칙 | 예시 |
|------|------|------|
| 컴포넌트 | PascalCase | `TripPlanBlock.vue` |
| 뷰(페이지) | PascalCase + View | `AttractionListView.vue` |
| composable | camelCase + use 접두사 | `useTripPlan.js` |
| store | camelCase | `attractionStore.js` |

### 컴포넌트 구조 순서

```vue
<script setup>
// 1. import
// 2. props / emits 정의
// 3. store, router 사용
// 4. ref, computed, reactive
// 5. 함수 정의
// 6. lifecycle hooks
</script>

<template>
  <!-- 템플릿 -->
</template>

<style scoped>
/* 스타일 */
</style>
```

---

## 6. 데이터베이스 컨벤션

- 테이블·컬럼명: `snake_case`
- PK: `id BIGINT AUTO_INCREMENT`
- 생성·수정 시각: `created_at`, `updated_at` (TIMESTAMP)
- 논리 삭제가 필요한 경우: `deleted_at TIMESTAMP NULL` (소프트 삭제)
- FK 컬럼명: 참조 테이블 단수형 + `_id` (예: `member_id`, `attraction_id`)

---

## 7. 주간 동기화 규칙

- **주 1회** (매주 월요일 또는 금요일) 진행상황 공유
- 각자 작업 브랜치 상태, 블로커(막히는 것), 다음 주 목표 공유
- 주요 설계 변경(DB 스키마, API 명세, 아키텍처)은 반드시 상대방과 사전 합의 후 진행

---

*본 문서는 팀 합의에 따라 언제든지 수정될 수 있습니다.*
