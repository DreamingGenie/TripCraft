# 마이페이지 & 북마크 기능 기획안 v2

> **작성일**: 2026-06-10
> **상태**: 기획 확정 (구현 전)
> **담당**: 전진
> **이전 문서**: `mypage_scrap_plan.md` (1차 기획)

---

## 1. 2차 팀 회의 변경 사항 요약

1차 기획(`mypage_scrap_plan.md`) 대비 변경된 내용만 정리한다.

| 항목             | 1차 기획                         | 2차 기획 (현재)                |
| ---------------- | -------------------------------- | ------------------------------ |
| 여행 통계 페이지 | `/mypage/stats` — 숫자 카드 4개  | **제외**                       |
| 방문 지도        | 없음                             | **신설** `/mypage/map`         |
| 스크랩           | "스크랩" 용어 사용               | **북마크**로 변경              |
| 좋아요한 글      | 없음                             | **신설** `/mypage/likes`       |
| 게시글 삭제 방식 | Hard delete (`DELETE FROM post`) | **Soft delete** (`deleted_at`) |
| 파일 관리        | 별도 테이블 없음                 | **attach 테이블** 신설         |

### 변경 이유

- **통계 제외**: 데이터 4개(횟수·일수·지역 수·최빈 지역)로는 대부분의 사용자에게 매력적이지 않다. 여행을 수십 번 다닌 헤비 유저가 아니면 의미가 없다.
- **지도 신설**: 통계보다 직관적이고 재미있는 시각화. 방문한 지역을 지도에서 직접 확인하는 경험이 서비스 특성과 잘 맞는다.
- **스크랩 → 북마크**: 구현 방식이 게시글 ID를 저장해두고 목록을 보여주는 것으로, 내용을 복사해 보관하는 "스크랩"보다 "북마크"가 더 정확하다.
- **좋아요한 글 신설**: 북마크(저장해두고 싶은 글)와 좋아요(마음에 들었던 글)는 사용 의도가 달라 별도 페이지로 분리한다.
- **소프트 딜리트**: 북마크 목록에서 원본 글이 삭제됐을 때 항목이 조용히 사라지는 것보다 "삭제된 글입니다"로 표기하는 것이 더 나은 UX다.
- **attach 테이블**: 프로필 이미지 수정 기능 구현을 위해 파일 메타데이터를 DB에서 관리해야 한다.

---

## 2. 전체 구조

### 네비게이션

로그인 상태에서만 GNB에 사용자 닉네임이 표시된다.
닉네임 클릭 시 드롭다운 메뉴가 열린다.

```
[닉네임 ▼]  [로그아웃]
  ├── 내 정보 수정    → /mypage/profile
  ├── 방문 지도       → /mypage/map
  ├── 내가 쓴 글      → /mypage/posts
  ├── 북마크한 글     → /mypage/bookmarks
  └── 좋아요한 글     → /mypage/likes
```

### 라우팅 방식

서브경로 방식 유지. 새로고침·뒤로가기·직접 링크 모두 자연스럽게 동작한다.  
보안 검증은 URL 구조와 무관하게 서버가 JWT로 매 요청마다 수행한다.

---

## 3. 페이지별 기획

### 3-1. 내 정보 수정 `/mypage/profile`

| 항목          | 내용                                                             |
| ------------- | ---------------------------------------------------------------- |
| 프로필 이미지 | 현재 이미지 미리보기 + 파일 업로드로 교체 (attach 테이블에 저장) |
| 닉네임        | 텍스트 입력 + 중복 확인                                          |
| 비밀번호      | 현재 비밀번호 확인 → 새 비밀번호 입력 (별도 섹션)                |

---

### 3-2. 방문 지도 `/mypage/map`

전국 지도에 방문 지역을 시각화한다.

**동작 방식**

```
전국 시도 지도 (17개 영역)
  → 방문한 시도: 초록색 / 미방문: 회색
  → 시도 클릭 시 해당 시도의 시군구 지도로 드릴다운
    → 방문한 시군구: 초록색 / 미방문: 회색
```

**기술 방식**

한국 행정구역 SVG 파일 활용. 각 시도/시군구 영역에 `sido_code`, `sigungu_code`를 id로 부여하고 Vue에서 방문 데이터 기반으로 색상을 동적 제어한다.

**방문 여부 판단 기준**

`trip_candidate` → `sigungu` JOIN으로 해당 회원의 후보 관광지 시도/시군구 코드를 집계한다.

```
GET /api/members/me/visited-regions
→ {
    sido: [1, 6, 39],           // 방문한 시도 코드 목록
    sigungu: { 6: [4, 7, 12] } // 시도별 방문 시군구 코드 목록
  }
```

**사진 연동 (2차 기능)**

방문한 시군구 클릭 시 해당 지역 게시글의 첨부 이미지를 보여주는 기능은 attach 테이블 정착 이후 검토한다. 현재 게시글 이미지는 Tiptap HTML에 URL로 임베드되어 있어 지역 기반 추출이 불가능하다.

---

### 3-3. 내가 쓴 글 `/mypage/posts`

커뮤니티 게시글 목록과 동일한 레이아웃.

```
GET /api/posts?authorId=me
```

- 카드 클릭 시 `/community/:id` 상세 페이지로 이동
- 상세 페이지에서 본인 글이므로 수정·삭제 버튼 노출
- 페이지네이션 동일하게 적용

---

### 3-4. 북마크한 글 `/mypage/bookmarks`

북마크한 게시글 목록. 목록 레이아웃은 내가 쓴 글과 동일.

```
GET /api/bookmarks/me
→ [ PostListItem (+ deleted 여부), ... ]
```

- 원본 글이 소프트 딜리트된 경우 "삭제된 글입니다" 표시 (북마크 레코드는 보존)
- 카드 클릭 시 `/community/:id` 이동 (삭제된 글은 상세에서도 삭제 안내)

---

### 3-5. 좋아요한 글 `/mypage/likes`

좋아요를 누른 게시글 목록. 레이아웃은 북마크한 글과 동일.

```
GET /api/likes/me
→ [ PostListItem (+ deleted 여부), ... ]
```

좋아요 데이터는 기존 `post_like(member_id, post_id)` 테이블로 이미 관리되고 있어 조회 API만 추가하면 된다.

**북마크와의 차이**

|      | 북마크                       | 좋아요                    |
| ---- | ---------------------------- | ------------------------- |
| 의도 | 나중에 다시 보고 싶어서 저장 | 글이 마음에 들었다는 반응 |
| 행동 | 능동적 저장                  | 즉각적 감정 표현          |

---

## 4. 북마크 기능 기획

### UX

- **위치**: 게시글 상세 페이지에만 노출 (목록 카드 미제공)
- **동작**: 토글 방식 — 한 번 누르면 북마크, 한 번 더 누르면 취소
- **비로그인**: 버튼 항상 노출, 클릭 시 "로그인이 필요합니다" 토스트

### DB 설계

```sql
CREATE TABLE post_bookmark (
    member_id  BIGINT NOT NULL,
    post_id    BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT NOW(),
    PRIMARY KEY (member_id, post_id),
    FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    FOREIGN KEY (post_id)   REFERENCES post (id)   -- CASCADE 없음 (소프트 딜리트 연동)
);
```

복합 PK `(member_id, post_id)` — 중복 북마크 DB 레벨에서 방지.  
`post_id` FK에 CASCADE를 걸지 않아 원본 글이 삭제돼도 북마크 레코드를 보존한다.

### API

| 메서드   | 경로                       | 설명           |
| -------- | -------------------------- | -------------- |
| `POST`   | `/api/posts/{id}/bookmark` | 북마크 추가    |
| `DELETE` | `/api/posts/{id}/bookmark` | 북마크 취소    |
| `GET`    | `/api/bookmarks/me`        | 내 북마크 목록 |

게시글 상세 응답(`PostDetail`)에 `bookmarked: boolean` 필드 추가해 버튼 초기 상태를 결정한다.

---

## 5. 소프트 딜리트 (post 테이블)

### 배경

북마크·좋아요 목록에서 원본 글이 삭제됐을 때 항목이 조용히 사라지는 것보다 "삭제된 글입니다"로 표기하는 것이 더 나은 UX다.

### DB 변경

```sql
ALTER TABLE post
    ADD COLUMN deleted_at DATETIME NULL COMMENT '삭제일시 (NULL=정상, non-NULL=삭제됨)';
```

### 동작 변경

| 항목               | 기존                            | 변경                                                       |
| ------------------ | ------------------------------- | ---------------------------------------------------------- |
| 삭제 처리          | `DELETE FROM post WHERE id = ?` | `UPDATE post SET deleted_at = NOW()`                       |
| 목록·상세 조회     | 조건 없음                       | `WHERE deleted_at IS NULL` 추가                            |
| 북마크·좋아요 목록 | —                               | `LEFT JOIN post` 후 `deleted_at IS NOT NULL`이면 삭제 표시 |

### 수정 파일

- `PostServiceImpl.deletePost()` — DELETE → UPDATE로 변경
- `PostMapper.xml` — 목록·상세 쿼리에 `deleted_at IS NULL` 조건 추가
- `PostMapper.xml` — 북마크·좋아요 목록 쿼리에 삭제 여부 컬럼 추가

---

## 6. attach 테이블

### 배경

프로필 이미지 저장을 위한 파일 메타데이터 관리 테이블이 필요하다.  
파일 자체는 서버 디스크에 저장하고, DB에는 경로 등 메타데이터만 저장한다.

### DB 설계

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

### target 값 정의

| target    | target_id   | 용도                                |
| --------- | ----------- | ----------------------------------- |
| `profile` | `member.id` | 프로필 이미지                       |
| `post`    | `post.id`   | 게시글 첨부 이미지 (향후 전환 검토) |

### 현재 게시글 이미지와의 관계

현재 게시글 이미지는 `ImageController`가 UUID 파일명으로 서버에 저장하고 URL을 Tiptap HTML에 삽입하는 방식으로 attach 테이블과 무관하게 동작하고 있다.

**attach 전환 시 장단점 검토**

| | 내용 |
|--|------|
| 단점 | 글 작성 중엔 post_id가 없어 target_id 처리가 애매함. ImageController + Tiptap 에디터 연동 로직 전면 수정 필요. 구현 비용이 큼. |
| 장점 | 고아 파일(업로드 후 미저장) 추적·정리 가능. 삭제된 글의 이미지 파일 추적·정리 가능. 방문 지도의 지역별 사진 기능 구현 가능. |

**결정**: 포트폴리오 수준의 완성도를 목표로 **게시글 이미지도 attach 테이블로 전환한다.**  
글 작성 중 post_id가 없는 문제는 임시 `target='post_draft'`로 저장 후 글 저장 완료 시 `target_id` 업데이트하는 방식으로 처리한다.

### attach target_id 구분 방식

글 작성 중에는 아직 post_id가 없으므로 `memberId`를 임시 식별자로 사용한다.

**흐름**
```
[글 작성 중 이미지 업로드]
  → 파일 디스크 저장
  → attach INSERT: target='post_draft', target_id=memberId

[글 저장 완료 (createPost)]
  → post INSERT → post.id 채번
  → attach UPDATE: target='post', target_id=post.id
    WHERE target='post_draft' AND target_id=memberId
```

**target 값 정의**

| target | target_id | 상태 |
|--------|-----------|------|
| `post_draft` | `member.id` | 글 작성 중 임시 저장 |
| `post` | `post.id` | 글 저장 완료 후 확정 |
| `profile` | `member.id` | 프로필 이미지 |

**알려진 한계**

| 상황 | 결과 |
|------|------|
| 이미지 올리고 글 저장 안 함 | attach 레코드가 `post_draft`로 남음 (고아 파일) |
| 같은 계정으로 두 탭에서 동시에 글 작성 | 먼저 저장된 글이 두 탭의 이미지를 모두 가져감 |

두 번째 케이스는 현실에서 발생 가능성이 매우 낮고, 캡스톤 수준에서 허용 가능한 한계로 인정한다.

### 파일 저장 경로 전략

로컬 개발 환경과 배포 환경의 저장 경로를 환경변수로 분리한다.

```yaml
# application.yml
upload:
  dir: ${UPLOAD_DIR:./uploads}
```

| 환경      | 저장 경로                           | 서빙 방식                          |
| --------- | ----------------------------------- | ---------------------------------- |
| 로컬 개발 | `./uploads/`                        | Vite 프록시 → Spring `/uploads/**` |
| 배포      | `/var/app/uploads/` (환경변수 주입) | nginx `/uploads/*`                 |

---

## 7. 구현 순서

```
0. [선행] attach 테이블 생성 + post 소프트 딜리트 마이그레이션
1. 게시글 이미지 → attach 전환
   - ImageController: 업로드 시 attach 레코드 생성 (post_draft 임시 저장)
   - PostServiceImpl: 글 저장 완료 시 target_id 업데이트, 글 삭제 시 파일 정리
2. 북마크 백엔드 — BookmarkController, BookmarkService, BookmarkMapper
3. 북마크 프론트 — 상세 페이지 버튼, /mypage/bookmarks 페이지
4. 좋아요한 글 — /api/likes/me + /mypage/likes 페이지
5. 내가 쓴 글 — /api/posts?authorId=me + /mypage/posts 페이지
6. 내 정보 수정 — /mypage/profile 페이지 (attach 테이블 활용)
7. 방문 지도 — SVG 지도 컴포넌트 + /mypage/map 페이지
8. GNB 드롭다운 — 닉네임 클릭 메뉴 (전체 묶어서 마지막에)
```

**브랜치**: `feature/mypage` 단일 브랜치에서 기능별로 커밋 분리해 진행.

---

## 8. 미결 사항

- [ ] 방문 지도 SVG 파일 출처 확정 (공공데이터 행정구역 경계 SVG)
- [x] 프로필 이미지 미설정 시 기본 이미지 처리 방식 → **현행 유지** (닉네임 첫 글자를 CSS로 표시하는 방식)
