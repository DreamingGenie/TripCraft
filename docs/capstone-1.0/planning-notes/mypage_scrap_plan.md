# 마이페이지 & 북마크 기능 기획안

> **작성일**: 2026-06-10
> **최종 수정**: 2026-06-10 (2차 팀 회의 반영)
> **상태**: 기획 2차 수정 (구현 전)
> **담당**: 전진

---

## 1. 전체 구조

### 네비게이션

로그인 상태에서만 GNB에 사용자 닉네임이 표시된다.  
닉네임 클릭 시 드롭다운 메뉴가 열린다.

```
[닉네임 ▼]  [로그아웃]
  ├── 내 정보 수정      → /mypage/profile
  ├── 내 여행 통계      → /mypage/stats
  ├── 내가 쓴 글        → /mypage/posts
  └── 내가 스크랩한 글  → /mypage/scraps
```

### 라우팅 방식 결정 — 서브경로

탭 전환(단일 `/mypage`) 방식과 비교 검토 후 **서브경로** 채택.

| 항목 | 서브경로 `/mypage/*` | 탭 전환 `/mypage` |
|------|---------------------|-----------------|
| 새로고침 유지 | ✅ | ❌ |
| 뒤로가기 동작 | ✅ | ❌ |
| 특정 탭 직접 링크 | ✅ | ❌ |
| 보안 | 동일 (서버 인증) | 동일 (서버 인증) |

보안은 URL 구조와 무관하게 서버가 JWT로 매 요청마다 검증한다.  
커뮤니티 상세 분리(`/community/:id`)와 같은 이유로 서브경로가 적절하다.

---

## 2. 페이지별 기획

### 2-1. 내 정보 수정 `/mypage/profile`

| 항목 | 내용 |
|------|------|
| 프로필 이미지 | 현재 이미지 미리보기 + 파일 업로드로 교체 |
| 닉네임 | 텍스트 입력 + 중복 확인 |
| 비밀번호 | 현재 비밀번호 확인 → 새 비밀번호 입력 (별도 섹션) |

소셜 로그인 대응 불필요 (현재 이메일 로그인만 지원).

---

### 2-2. 내 여행 통계 `/mypage/stats`

여행 데이터를 기반으로 재미 위주의 통계를 카드 형식으로 제공한다.

#### 확정된 통계 항목

| # | 항목 | 계산 방식 | 비고 |
|---|------|-----------|------|
| 1 | 총 여행 횟수 | `COUNT(trip)` | |
| 2 | 총 여행 일수 | `SUM(DATEDIFF(end_date, start_date) + 1)` | |
| 3 | 방문한 지역 수 | `COUNT(DISTINCT city_code)` — trip_candidate 기준 | |
| 4 | 가장 많이 간 지역 | `city_code` 최빈값 → 지역명으로 변환 | city_code 매핑 필요 |

#### 검토 후 제외된 항목

| 항목 | 제외 이유 |
|------|----------|
| 총 이동 시간 | transit_duration_minutes가 미입력인 경우 많아 신뢰도 낮음 |
| 즐겨 쓰는 이동수단 | 이동수단이 NULL인 블록이 존재할 수 있어 통계 왜곡 가능 |

#### 데이터 API

```
GET /api/members/me/stats
→ {
    tripCount: 5,
    totalDays: 14,
    regionCount: 4,
    favoriteRegion: "부산"
  }
```

---

### 2-3. 내가 쓴 글 `/mypage/posts`

커뮤니티 게시글 목록과 동일한 레이아웃.  
기존 `GET /api/posts` API에 `authorId=me` 조건 추가로 재활용.

```
GET /api/posts?authorId=me
```

페이지네이션 동일하게 적용.

- 카드 클릭 시 `/community/:id` 상세 페이지로 이동 (기존 상세 뷰 그대로 사용)
- 상세 페이지에서 본인 글이므로 수정·삭제 버튼이 노출되어 삭제 가능
- 삭제 후 브라우저 뒤로가기 시 `/mypage/posts`로 복귀

---

### 2-4. 내가 스크랩한 글 `/mypage/scraps`

스크랩한 게시글 목록. 목록 레이아웃은 내가 쓴 글과 동일.

```
GET /api/scraps/me
→ [ PostListItem, ... ]
```

---

## 3. 스크랩 기능 기획

### UX

- **위치**: 게시글 **상세 페이지**에만 노출 (목록 카드에는 미제공)
- **동작**: 토글 방식 — 한 번 누르면 스크랩, 한 번 더 누르면 취소
- **비로그인**: 버튼은 노출, 클릭 시 "로그인이 필요합니다" 토스트 (대댓글 답글 버튼과 동일 패턴)

### DB 설계

```sql
CREATE TABLE post_scrap (
    member_id  BIGINT NOT NULL,
    post_id    BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT NOW(),
    PRIMARY KEY (member_id, post_id),
    FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    FOREIGN KEY (post_id)   REFERENCES post (id)   ON DELETE CASCADE
);
```

복합 PK `(member_id, post_id)` — 중복 스크랩 DB 레벨에서 방지.  
게시글·회원 삭제 시 스크랩도 자동 삭제(`ON DELETE CASCADE`).

### API

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/posts/{id}/scrap` | 스크랩 추가 (이미 있으면 409 또는 무시) |
| `DELETE` | `/api/posts/{id}/scrap` | 스크랩 취소 |
| `GET` | `/api/scraps/me` | 내 스크랩 목록 조회 |

게시글 상세 응답(`PostDetail`)에 `scrapped: boolean` 필드 추가해  
페이지 진입 시 버튼 초기 상태를 결정한다.

---

## 4. 구현 순서 (잠정)

```
1. DB — post_scrap 테이블 추가 (schema.sql + migration)
2. 스크랩 백엔드 — ScrapController, ScrapService, ScrapMapper
3. 스크랩 프론트 — 상세 페이지 버튼, /mypage/scraps 페이지
4. 내가 쓴 글 — /api/posts?authorId=me + /mypage/posts 페이지
5. 내 정보 수정 — /mypage/profile 페이지
6. 내 여행 통계 — /api/members/me/stats + /mypage/stats 페이지
7. GNB 드롭다운 — 닉네임 클릭 메뉴
```

통계는 데이터가 어느 정도 쌓인 후 의미 있으므로 가장 마지막에 구현.

---

## 5. 결정 사항

### 5-1. city_code → 지역명 매핑 방식

**고민**: 지역명을 어디서 가져올지 세 가지를 검토했다.
- Java/JS 하드코딩 Map → sigungu가 수백 개라 관리 불가
- DB 코드 테이블 신규 생성 → 이미 동일한 테이블이 존재해 불필요한 중복
- 기존 `sigungu` 테이블 JOIN → 별도 작업 없이 바로 사용 가능

**결정**: 기존 `sigungu(sido_code, sigungu_code, name)` 테이블을 JOIN해서 지역명을 가져온다.  
자세한 내용은 [6. city_code 매핑 결정 내역](#6-city_code-매핑-결정-내역) 참고.

---

### 5-2. 내 여행 통계 UI 디자인

**고민**: 숫자 카드형과 차트형 중 고민했다.
- 차트형(Chart.js 등) → 시각적으로 풍부하지만 라이브러리 추가 필요, 데이터가 4개뿐이라 오버스펙
- 숫자 카드형 → 외부 의존성 없이 구현 가능, 통계 항목 수에 맞게 깔끔하게 표현 가능

**결정**: 숫자 카드형으로 구현한다. 총 4개의 통계 카드를 그리드로 배치한다.

---

### 5-3. 내가 쓴 글 목록에서의 삭제

**고민**: 목록 카드에 삭제 버튼을 직접 넣을지, 상세 페이지로 이동해서 삭제하게 할지 고민했다.
- 목록에서 바로 삭제 → 구현 추가 필요, 실수로 삭제할 위험
- 상세에서 삭제 → 이미 상세 페이지에 삭제 버튼이 있고, 내용을 한 번 확인하고 삭제하는 흐름이 더 자연스러움

**결정**: 카드 클릭 시 `/community/:id` 상세 페이지로 이동하고, 상세에서 기존 삭제 버튼으로 삭제한다.

---

## 6. city_code 매핑 결정 내역

### TourAPI 코드 체계

TourAPI는 두 단계 지역 코드를 사용한다.

| 코드 | 단위 | 예시 |
|------|------|------|
| `sido_code` | 광역시·도 (17개) | 서울=1, 부산=6, 제주=39 |
| `sigungu_code` | 시·군·구 | 강남구, 해운대구 등 |

`trip_candidate.city_code`는 **시군구 코드**(`sigungu_code`)다.

### 검토한 방법

| 방법 | 설명 | 결론 |
|------|------|------|
| 하드코딩 Map | Java/JS에 코드→이름 상수로 관리 | ❌ — sigungu가 수백 개라 비현실적 |
| DB 코드 테이블 신규 생성 | `city_code` 전용 테이블 추가 | ❌ — 이미 테이블이 존재함 |
| **sigungu 테이블 JOIN** | 기존 `sigungu` 테이블 재활용 | ✅ 채택 |

### 확정: sigungu 테이블 JOIN

`sigungu(sido_code, sigungu_code, name)` 테이블이 이미 DB에 존재한다.  
통계 쿼리에서 해당 테이블을 JOIN해 지역명을 직접 가져온다.

```sql
-- 가장 많이 간 지역 조회 예시
SELECT s.name, COUNT(*) AS cnt
FROM trip_candidate tc
JOIN sigungu s ON s.sigungu_code = tc.city_code
WHERE tc.trip_id IN (SELECT id FROM trip WHERE member_id = #{memberId})
GROUP BY tc.city_code, s.name
ORDER BY cnt DESC
LIMIT 1;
```

---

## 7. 2차 팀 회의 결정 사항 (2026-06-10)

### 7-1. 내 여행 통계 페이지 제외

**고민**: 통계로 보여줄 수 있는 데이터(여행 횟수, 일수, 지역 수, 최빈 지역)가 대부분의 사용자에게 매력적이지 않다는 의견이 나왔다. 1년에 수십 번 여행하는 헤비 유저가 아니면 카드 4개짜리 통계 페이지는 의미가 없다.

**결정**: 내 여행 통계 페이지(`/mypage/stats`)를 기획에서 제외한다.

---

### 7-2. 지도 기반 방문 기록 페이지 신설

통계 페이지 대신 **전국 지도에 방문 지역을 시각화**하는 페이지를 추가한다.

**동작 방식**
```
전국 시도 지도
  → 방문한 시도: 초록색 / 미방문: 회색
  → 시도 클릭 시 해당 시도의 시군구 지도로 드릴다운
    → 방문한 시군구: 초록색 / 미방문: 회색
```

**기술 방식**: 한국 행정구역 SVG 파일 활용. 각 시도/시군구 영역에 `sido_code`, `sigungu_code`를 id로 부여하고 Vue에서 방문 데이터 기반으로 색상 동적 제어.

**방문 여부 판단 기준**: `trip_candidate` → `sigungu` JOIN으로 해당 회원이 후보에 올린 관광지의 시도/시군구 코드 집계.

**사진 연동 (2차 기능)**  
방문한 시군구 클릭 시 해당 지역과 연관된 사용자 사진을 보여주는 기능은 attach 테이블 도입 이후 검토한다. 현재 게시글 이미지는 Tiptap HTML 안에 URL로만 저장되어 지역 기반 추출이 불가능하다.

**라우팅**: `/mypage/map`

---

### 7-3. '스크랩' 용어를 '북마크'로 변경

**고민**: 구현 방식이 게시글 ID를 저장해두고 목록을 보여주는 것으로, 실질적으로는 링크를 저장해두는 북마크에 가깝다. "스크랩"은 내용을 복사해 따로 보관하는 느낌이 강해 구현 방식과 맞지 않는다.

**검토한 용어**

| 용어 | 느낌 | 결론 |
|------|------|------|
| 스크랩 | 내용을 떠서 보관 | ❌ 구현 방식과 맞지 않음 |
| 북마크 | 나중에 다시 보기 위해 저장 | ✅ 채택 |
| 저장 | 중립적 | — |
| 찜 | 쇼핑 느낌이 강함 | — |

**결정**: 전체 용어를 스크랩 → **북마크**로 변경한다.
- DB 테이블: `post_scrap` → `post_bookmark`
- API 경로: `/api/posts/{id}/scrap` → `/api/posts/{id}/bookmark`
- 마이페이지 메뉴: "내가 스크랩한 글" → "북마크한 글"
- 라우팅: `/mypage/scraps` → `/mypage/bookmarks`

---

### 7-4. 원본 글 삭제 시 "삭제된 글입니다" 표기

**고민**: `post_bookmark`에 `ON DELETE CASCADE`를 걸면 원본 글이 삭제될 때 북마크도 함께 사라진다. 하지만 북마크 목록에서 해당 항목이 조용히 사라지는 것보다, "삭제된 글입니다"로 표기하는 것이 사용자 경험상 더 낫다.

**결정**: `post` 테이블에 소프트 딜리트를 적용한다.

```sql
ALTER TABLE post ADD COLUMN deleted_at DATETIME NULL COMMENT '삭제일시 (NULL=정상, non-NULL=삭제됨)';
```

- 삭제 요청 시 `DELETE` 대신 `UPDATE post SET deleted_at = NOW()`
- 일반 목록·상세 조회: `WHERE deleted_at IS NULL` 조건 추가
- 북마크 목록 조회: `LEFT JOIN post` 후 `deleted_at IS NOT NULL`이면 "삭제된 글입니다" 표시
- `post_bookmark` FK에서 `ON DELETE CASCADE` 제거 → 북마크 레코드 보존
- 기존 커뮤니티 삭제 API 수정 필요 (`PostServiceImpl.deletePost`)

---

### 7-5. attach 테이블 신설

**배경**: 프로필 이미지 저장을 위한 파일 관리 테이블이 필요하다. 현재 게시글 이미지는 `ImageController`가 UUID 파일명으로 서버에 저장하고 URL만 Tiptap HTML에 삽입하는 방식으로 DB에 별도 관리가 없다.

**확정 스키마**

```sql
CREATE TABLE attach (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '첨부파일 고유 번호',
    name        VARCHAR(128) NOT NULL COMMENT '서버 저장 파일명 (UUID 기반)',
    host_name   VARCHAR(128) NOT NULL COMMENT '원본 파일명',
    size        BIGINT       NOT NULL DEFAULT 0 COMMENT '파일 크기 (bytes)',
    mimetype    VARCHAR(128) NOT NULL COMMENT 'MIME 타입 (image/jpeg 등)',
    host_path   VARCHAR(512) NOT NULL COMMENT '서버 내 저장 경로',
    target      VARCHAR(32)  NOT NULL COMMENT '첨부 대상 구분 (profile / post)',
    target_id   BIGINT       NOT NULL COMMENT '첨부 대상의 레코드 ID',
    created_at  DATETIME     NOT NULL DEFAULT NOW() COMMENT '등록일시'
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
```

**제외한 컬럼 및 이유**

| 컬럼 | 제외 이유 |
|------|----------|
| `admin_idx` | 관리자 개념 없음 |
| `type` (확장자) | `mimetype`으로 대체 가능 |
| `status` | 소프트 딜리트는 post에서 관리, attach는 단순 보관 |

**target 값 정의**

| target | target_id | 용도 |
|--------|-----------|------|
| `profile` | `member.id` | 프로필 이미지 |
| `post` | `post.id` | 게시글 첨부 이미지 (현재는 HTML 임베드 방식, 향후 전환 검토) |

---

### 7-6. 좋아요한 글 모아보기 페이지 신설

북마크와 별도로 좋아요를 누른 글만 모아보는 페이지를 추가한다.

**북마크와의 차이**

| | 북마크 | 좋아요 |
|--|--------|--------|
| 의도 | 나중에 다시 보고 싶어서 저장 | 글이 마음에 들었다는 반응 |
| 행동 | 능동적 저장 | 즉각적 감정 표현 |
| 유사 서비스 | 브라우저 북마크, 인스타 저장 | 인스타 하트, 유튜브 좋아요 |

좋아요 데이터는 이미 `post_like(member_id, post_id)` 테이블로 관리되고 있어 별도 DB 작업 없이 조회 API만 추가하면 된다.

**라우팅**: `/mypage/likes`

---

### 7-7. 수정된 구현 순서

```
0. attach 테이블 생성 + post 소프트 딜리트 마이그레이션  ← 선행 작업
1. 북마크 백엔드 — BookmarkController, BookmarkService, BookmarkMapper
2. 북마크 프론트 — 상세 페이지 버튼, /mypage/bookmarks 페이지
3. 좋아요한 글 — /api/likes/me + /mypage/likes 페이지
4. 내가 쓴 글 — /api/posts?authorId=me + /mypage/posts 페이지
5. 내 정보 수정 — /mypage/profile 페이지 (attach 테이블 활용)
6. 지도 방문 기록 — SVG 지도 + /mypage/map 페이지
7. GNB 드롭다운 — 닉네임 클릭 메뉴
```

**GNB 드롭다운 최종 메뉴 구성**
```
[닉네임 ▼]  [로그아웃]
  ├── 내 정보 수정    → /mypage/profile
  ├── 방문 지도       → /mypage/map
  ├── 내가 쓴 글      → /mypage/posts
  ├── 북마크한 글     → /mypage/bookmarks
  └── 좋아요한 글     → /mypage/likes
```
