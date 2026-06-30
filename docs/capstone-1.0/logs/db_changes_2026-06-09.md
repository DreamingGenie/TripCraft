# DB 변경 내역 — 2026-06-09

> **대상 DB**: `trip_craft` (포트 3307)
> **작성자**: 전진
>
> 이 문서는 2026-06-09 작업에서 발생한 DB 변경을 정리한 것입니다.
> 팀원이 동일한 로컬 DB에 적용할 때 **실행 순서대로** 따라하면 됩니다.

---

## 변경 요약

| 순서 | 종류 | 대상 테이블 | 내용 | SQL 파일 |
|------|------|------------|------|---------|
| 1 | **DDL (ALTER)** | `post_comment` | `parent_id` 컬럼 추가 (대댓글) | `docs/02_design/migration_comment_parent.sql` |
| 2 | **DML (INSERT)** | `member`, `post`, `post_comment` | 커뮤니티 테스트 회원·게시글·댓글 5/10/30건 | `docs/05_sql/community_test_data.sql` |
| 3 | **DML (INSERT + UPDATE)** | `trip`, `trip_candidate`, `trip_block`, `post` | 테스트 일정 3개 + 게시글에 일정 연결 | `docs/05_sql/trip_test_data.sql` |

---

## 1단계 — 스키마 변경 (반드시 먼저 실행)

### 무엇이 바뀌었나?

`post_comment` 테이블에 **`parent_id` 컬럼**을 추가했습니다.

- `NULL` = 일반 댓글 (최상위)
- 숫자 값 = 대댓글 (해당 id의 댓글을 부모로 가짐)
- 부모 댓글 삭제 시 `ON DELETE CASCADE`로 대댓글도 함께 삭제됨

### 실행할 SQL

```sql
-- 파일 위치: docs/02_design/migration_comment_parent.sql

ALTER TABLE post_comment
    ADD COLUMN parent_id BIGINT NULL
        COMMENT '부모 댓글 FK (NULL=최상위, non-NULL=대댓글 — 1단계만 허용)'
        AFTER member_id,
    ADD CONSTRAINT fk_comment_parent
        FOREIGN KEY (parent_id) REFERENCES post_comment (id) ON DELETE CASCADE;
```

### 확인 쿼리

```sql
-- 컬럼이 추가됐는지 확인
DESCRIBE post_comment;
-- parent_id 컬럼이 member_id 바로 다음에 보여야 함
```

### 주의사항

- **이미 실행한 경우**: `ERROR 1060: Duplicate column name 'parent_id'` 에러가 납니다. 이미 적용된 것이므로 무시하고 넘어가면 됩니다.
- 기존 댓글 데이터는 `parent_id = NULL`로 유지되어 모두 정상 동작합니다.

---

## 2단계 — 커뮤니티 테스트 데이터 삽입

### 무엇이 추가되나?

| 테이블 | 건수 | 내용 |
|--------|------|------|
| `member` | 5건 | 테스트 계정 (비밀번호 공통: `Test1234!`) |
| `post` | 10건 | 한국 여행 후기 게시글 (제주도·부산·경주·강릉·전주·남해·설악산·속초·여수·통영) |
| `post_comment` | 30건 | 각 게시글에 달린 댓글 |

**추가되는 테스트 계정:**

| 이메일 | 닉네임 | 비밀번호 |
|--------|--------|---------|
| `jiyeon@test.com` | 김지연 | `Test1234!` |
| `minsoo@test.com` | 박민수 | `Test1234!` |
| `sohee@test.com` | 이소희 | `Test1234!` |
| `taehoon@test.com` | 최태훈 | `Test1234!` |
| `yurim@test.com` | 한유림 | `Test1234!` |

### 실행 방법

> **중요**: PowerShell 파이프로 실행하면 한글이 깨집니다.
> 반드시 MySQL Workbench에서 파일을 열어 실행하거나, `SOURCE` 명령을 사용하세요.

**방법 A — MySQL Workbench GUI**
1. MySQL Workbench 열기
2. `File → Open SQL Script` → `docs/05_sql/community_test_data.sql` 선택
3. 실행 (⚡ 버튼 또는 Ctrl+Shift+Enter)

**방법 B — CLI (SOURCE 명령)**
```bash
mysql -u root -prootpass -P 3307 trip_craft
```
```sql
SOURCE C:/Users/yusen/git/CapstonePJT/a11_final_jin_jeongki/docs/05_sql/community_test_data.sql;
```

### 확인 쿼리

```sql
-- 테스트 회원 확인
SELECT id, email, nickname FROM member WHERE email LIKE '%@test.com';

-- 게시글 확인 (10건)
SELECT id, title, member_id FROM post ORDER BY id DESC LIMIT 10;

-- 댓글 확인 (30건)
SELECT post_id, COUNT(*) AS cnt FROM post_comment GROUP BY post_id ORDER BY post_id;
```

### 주의사항

- **중복 실행 금지**: 이미 실행했다면 `member.email UNIQUE` 제약 조건 위반으로 에러가 납니다.
- 이미 실행된 경우 2단계를 건너뛰고 3단계로 넘어가세요.
- 중복 여부 사전 확인:
  ```sql
  SELECT COUNT(*) FROM member WHERE email = 'jiyeon@test.com';
  -- 1 이상이면 이미 실행된 것
  ```

---

## 3단계 — 일정(Trip) 테스트 데이터 삽입

### 무엇이 추가되나?

커뮤니티 게시글에 연결된 테스트 일정 3개를 삽입하고,
해당 게시글의 `trip_id`를 업데이트합니다.

| 테이블 | 건수 | 내용 |
|--------|------|------|
| `trip` | 3건 | 공개 일정 3개 |
| `trip_candidate` | 17건 | 일정별 후보 관광지 |
| `trip_block` | 17건 | 확정된 타임라인 블록 |
| `post` | 3건 UPDATE | post.trip_id 연결 |

**삽입되는 일정:**

| trip | 제목 | 기간 | 이동수단 | 연결 게시글 |
|------|------|------|---------|------------|
| trip 1 | 부산 2박 3일 핵심 코스 | 2026-07-10 ~ 2026-07-12 | 대중교통 | post 16 (혼자 떠난 부산 1박 2일) |
| trip 2 | 강릉 1박 2일 커피 & 바다 | 2026-08-15 ~ 2026-08-16 | 자동차 | post 18 (강릉 커피 거리 투어) |
| trip 3 | 제주도 3박 4일 오름 자연 탐방 | 2026-09-20 ~ 2026-09-23 | 자동차 | post 15 (제주도 3박 4일 완전 정복) |

**각 일정의 타임라인:**

*부산 2박 3일 — 관광지 5곳 (attraction id 기준)*
```
Day 1 (7/10): 광안리해수욕장(4641) 10:00 · 광안리해변테마거리(4640) 13:30
Day 2 (7/11): 국제시장먹자골목(4644) 11:00 · 깡깡이예술마을(4658) 15:00
Day 3 (7/12): 광복로패션거리(4639) 10:30
```

*강릉 1박 2일 — 관광지 5곳*
```
Day 1 (8/15): 강릉굴산사지(10815) 10:00 · 강릉아트센터(10822) 14:00 · 간현관광지(10810) 17:00
Day 2 (8/16): 가리왕산케이블카(10807) 09:30 · 강릉복사꽃마을(10816) 14:00
```

*제주도 3박 4일 — 관광지 7곳*
```
Day 1 (9/20): 가마오름(24449) 10:00 · 가문이오름(24450) 14:00
Day 2 (9/21): 가새기오름(24451) 09:30 · 가세오름(24452) 14:30
Day 3 (9/22): 가시오름(24454) 10:00 · 각시바위오름(24455) 15:00
Day 4 (9/23): 갈마못(24456) 11:00
```

### 실행 방법

**방법 A — MySQL Workbench GUI**
1. MySQL Workbench 열기
2. `File → Open SQL Script` → `docs/05_sql/trip_test_data.sql` 선택
3. 실행

**방법 B — CLI (SOURCE 명령)**
```bash
mysql -u root -prootpass -P 3307 trip_craft
```
```sql
SOURCE C:/Users/yusen/git/CapstonePJT/a11_final_jin_jeongki/docs/05_sql/trip_test_data.sql;
```

> **경고**: 이 파일은 `member_id = 3, 4`를 하드코딩하고 있습니다.
> 2단계(community_test_data.sql)를 먼저 실행해 김지연(id=3), 박민수(id=4) 계정이
> 존재하는 상태에서 실행해야 합니다.
> 만약 member id가 다르다면 파일 내 `VALUES (3, ...)`와 `VALUES (4, ...)` 부분을
> 실제 id로 수정 후 실행하세요.

### 확인 쿼리

```sql
-- 일정이 게시글에 연결됐는지 확인
SELECT t.id, t.title, t.start_date, t.end_date, t.is_public,
       p.id AS post_id, p.title AS post_title
FROM trip t
JOIN post p ON p.trip_id = t.id
WHERE t.is_public = 1
ORDER BY t.id;

-- 후보군·블록 수 확인
SELECT t.id, t.title,
       COUNT(DISTINCT tc.id) AS candidates,
       COUNT(DISTINCT tb.id) AS blocks
FROM trip t
LEFT JOIN trip_candidate tc ON tc.trip_id = t.id
LEFT JOIN trip_block tb ON tb.candidate_id = tc.id
WHERE t.is_public = 1
GROUP BY t.id;
```

### 주의사항

- **중복 실행 시**: trip·candidate·block은 UNIQUE 제약이 없어서 중복 삽입됩니다.
  실행 전 이미 데이터가 있는지 확인하세요:
  ```sql
  SELECT COUNT(*) FROM trip WHERE title LIKE '%부산 2박 3일%';
  -- 1 이상이면 이미 실행된 것
  ```
- `post.trip_id` UPDATE도 중복 실행해도 같은 값으로 덮어쓰므로 문제없습니다.
- `trip_block` 삽입 시 **날짜 범위 TRIGGER**가 동작합니다.
  `trip.start_date ~ end_date` 범위를 벗어난 날짜의 블록은 자동으로 거부됩니다.

---

## 전체 실행 순서 정리

```
1. migration_comment_parent.sql  ← 스키마 변경 (한 번만)
2. community_test_data.sql       ← 회원·게시글·댓글 테스트 데이터 (한 번만)
3. trip_test_data.sql            ← 일정 테스트 데이터 (한 번만, 2 실행 후)
```

모두 실행 후 아래 쿼리로 최종 상태를 한 번에 확인할 수 있습니다:

```sql
-- 전체 상태 확인
SELECT '회원 수'     AS 항목, COUNT(*) AS 건수 FROM member
UNION ALL
SELECT '게시글 수',   COUNT(*) FROM post
UNION ALL
SELECT '댓글 수',    COUNT(*) FROM post_comment
UNION ALL
SELECT '일정 수',    COUNT(*) FROM trip
UNION ALL
SELECT '후보군 수',  COUNT(*) FROM trip_candidate
UNION ALL
SELECT '블록 수',    COUNT(*) FROM trip_block;
```

---

## MySQL Workbench 접속 정보 (로컬 개발 환경)

| 항목 | 값 |
|------|-----|
| Host | `localhost` |
| Port | `3307` |
| Database | `trip_craft` |
| Username | `root` |
| Password | `rootpass` |
