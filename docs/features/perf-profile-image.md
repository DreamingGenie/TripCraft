# 기술 노트 — 프로필 이미지 N+1 쿼리 개선

작성일: 2026-06-18  
작성자: 전진  
관련 파일: `PostMapper.xml`, `PostLikeMapper.xml`, `PostBookmarkMapper.xml`

> **2026-09-20 정정**: 최초본은 상관 서브쿼리의 반복 실행을 **"DB 왕복 11회"** 라고 적었다. 틀린 표현이다.
> 상관 서브쿼리는 **한 문장의 SQL**이므로 클라이언트↔DB **왕복은 1회**이고, 11회인 것은 **엔진 내부 실행 횟수**다.
> 같은 맥락에서 아래 "총 DB 읽기" 표기도 "엔진 내부 실행"으로 고쳤다.
> 또한 채택안(방법 2)이 기존 서브쿼리의 `LIMIT 1`을 떼어냈다는 점을 §방법 2에 한계로 명시했다.
> **이 문서의 개선 전후는 측정한 수치가 아니라 실행 구조 비교다** — `EXPLAIN` 실측은 하지 않았다.

> **2026-11-21 실측** — 아래 본문의 개선 전후를 처음으로 실제 MySQL에서 쟀다. **결론이 본문과 다르다.** 자세한 내용은 문서 끝 "2026-11-21 실측".
> 1. 상관 서브쿼리와 파생 테이블 조인은 **실행 계획도 시간도 같았다.** 반복 조회는 없어지지 않았다.
> 2. 채택안(조인)은 프로필 이미지 행이 둘이면 글 행을 두 배로 늘렸다 — 중복 방지 조인으로 고쳤다.
> 3. 실제 비용은 글 목록 **정렬**이었다. 정렬 인덱스를 넣어 줄였다.

---

## 문제 정의

게시글 목록을 조회하는 5개 쿼리에서 작성자 프로필 이미지를 **상관 서브쿼리(Correlated Subquery)** 로 가져오고 있었다.

```sql
-- 기존 방식 (상관 서브쿼리)
SELECT p.id, p.title, ...,
       (SELECT CONCAT('/uploads/images/', a.name)
        FROM attach a
        WHERE a.target = 'profile' AND a.target_id = m.id
        LIMIT 1) AS authorProfileImageUrl
FROM post p
LEFT JOIN member m ON m.id = p.member_id
```

### 왜 N+1인가

상관 서브쿼리는 **외부 쿼리의 각 행에 대해 독립적으로 실행**된다. 즉:

```
외부 쿼리가 10개 행을 읽음
  → attach 서브쿼리 실행 (1번)
  → attach 서브쿼리 실행 (2번)
  → ...
  → attach 서브쿼리 실행 (10번)
총 실행 횟수: 1 (메인 쿼리) + 10 (서브쿼리) = 11
```

페이지 크기 `size=10` 기준으로 **엔진 내부 실행이 11회**가 된다.
⚠ **왕복(round trip)이 11회라는 뜻이 아니다.** SQL 문장은 하나이므로 클라이언트↔DB 왕복은 1회이고,
늘어나는 것은 서버 안에서 행마다 반복되는 서브쿼리 실행이다. N+1이라는 이름이 가리키는 비용도 그쪽이다.

### 현재 환경에서 체감되지 않는 이유

- 데이터 양이 적음 (현재 개발/테스트 환경)
- 인덱스 `idx_attach_target (target, target_id)` 가 존재해 서브쿼리 1회 실행이 빠름
- 같은 서버 내부 쿼리라 네트워크 지연 없음

그럼에도 **구조적 문제**이기 때문에 데이터 증가 시 선형적으로 악화된다.

---

## 해결 방법 비교

### 방법 1: 상관 서브쿼리 유지 (기존)

```sql
(SELECT CONCAT(...) FROM attach WHERE target='profile' AND target_id=m.id LIMIT 1)
```

- 장점: SQL 구조가 단순, JOIN 없이 SELECT 절에서 처리
- 단점: 행마다 서브쿼리 재실행 → **O(N) 추가 쿼리**

### 방법 2: LEFT JOIN + 파생 테이블 (채택)

```sql
LEFT JOIN (SELECT target_id, name FROM attach WHERE target = 'profile') pa
          ON pa.target_id = m.id
```

SELECT 에서는:
```sql
CONCAT('/uploads/images/', pa.name) AS authorProfileImageUrl
```

- 장점: attach 테이블을 **한 번만** 읽고 해시 조인으로 매칭
- 단점: SQL 구조가 두 부분으로 분리됨 (SELECT 컬럼 + FROM/JOIN 절)
- `pa.name`이 NULL이면 `CONCAT` 결과도 NULL → 프로필 이미지 없음 처리와 동일
- ⚠ **한계 — 기존 서브쿼리에 있던 `LIMIT 1`이 이 방식에는 없다.** 한 회원에게 `target='profile'` 행이
  둘 이상 있으면 파생 테이블이 그 회원을 여러 행으로 내보내 **게시글 행이 증식한다**. 아래 방법 3에서
  비채택 사유로 적은 위험이 **방법 2에도 그대로 남아 있다**(앱 레이어가 중복 삽입 전에 삭제한다는 전제에 기대고 있을 뿐).
  DB만으로 막으려면 파생 테이블을 `GROUP BY target_id`로 접거나 `attach(target,target_id)`에 UNIQUE를 건다.

### 방법 3: LEFT JOIN 직접 (비채택)

```sql
LEFT JOIN attach a ON a.target = 'profile' AND a.target_id = m.id
```

- 문제: 한 회원이 프로필 이미지 레코드를 여러 개 가질 경우 행이 증가(Cartesian)
- 현재 앱 레이어에서 중복 삽입 전에 삭제하므로 이론적으로 안전하지만,
  데이터 불일치 시 버그 유발 가능성이 있어 파생 테이블 방식을 선택

---

## 개선 전후 실행 단계 비교

### 개선 전 — 게시글 목록 10개 조회

```
1. SELECT post p + LEFT JOIN member m  →  10행 반환
2.  → 프로필 이미지 서브쿼리 (member_id=1)
3.  → 프로필 이미지 서브쿼리 (member_id=2)
4.  → ...
11. → 프로필 이미지 서브쿼리 (member_id=10)

총 엔진 내부 실행: 11회 (클라이언트↔DB 왕복은 1회)
```

### 개선 후 — 동일 조회

```
1. SELECT post p
   LEFT JOIN member m
   LEFT JOIN (SELECT target_id, name FROM attach WHERE target='profile') pa
             ON pa.target_id = m.id
   →  10행 반환, 프로필 이미지 포함

총 엔진 내부 실행: 1회 (왕복도 1회 — 달라진 것은 왕복 수가 아니라 반복 실행이 사라진 것)
```

---

## 적용 범위

| 파일 | 쿼리 ID | 적용 |
|------|---------|------|
| `PostMapper.xml` | `findListItems` | ✅ |
| `PostMapper.xml` | `findDetailById` | ✅ |
| `PostMapper.xml` | `findByMemberId` | ✅ |
| `PostLikeMapper.xml` | `findByMemberId` | ✅ (cross-namespace include) |
| `PostBookmarkMapper.xml` | `findByMemberId` | ✅ (cross-namespace include) |

---

## MyBatis 구현 패턴

`profileImageSubquery` 단일 fragment → `profileImageCol` + `profileImageJoin` 두 fragment로 분리.

```xml
<!-- PostMapper.xml -->
<sql id="profileImageCol">
    CONCAT('/uploads/images/', pa.name) AS authorProfileImageUrl
</sql>

<sql id="profileImageJoin">
    LEFT JOIN (SELECT target_id, name FROM attach WHERE target = 'profile') pa
              ON pa.target_id = m.id
</sql>
```

사용 시 `profileImageJoin`은 **반드시 `LEFT JOIN member m` 다음**에 위치해야 한다 (`pa`가 `m.id`를 참조하기 때문).

다른 mapper에서 cross-namespace로 참조:
```xml
<include refid="com.tripcraft.community.mapper.PostMapper.profileImageCol"/>
<include refid="com.tripcraft.community.mapper.PostMapper.profileImageJoin"/>
```

---

## 남아있는 N+1 — 댓글 수

댓글 수는 여전히 상관 서브쿼리 방식을 유지하고 있다.

```sql
(SELECT COUNT(*) FROM post_comment c WHERE c.post_id = p.id) AS commentCount
```

이것도 동일한 N+1 구조다. 개선 방법:
```sql
LEFT JOIN (SELECT post_id, COUNT(*) AS commentCount FROM post_comment GROUP BY post_id) cc
          ON cc.post_id = p.id
```

단, 댓글 수 집계는 프로필 이미지와 달리 **전체 post_comment 테이블을 GROUP BY**해야 하기 때문에, 댓글이 많아질수록 파생 테이블 자체가 무거워질 수 있다. 게시글-댓글 비율을 보고 필요 시 적용한다.

---

## 참고

- MySQL은 상관 서브쿼리를 자동으로 JOIN으로 최적화하지 않는다 (Dependent Subquery로 처리).
- `EXPLAIN` 실행 시 기존 방식은 `DEPENDENT SUBQUERY`, 개선 후는 `DERIVED` + `ref`로 **바뀔 것으로 본다**.
  ⚠ **실제로 `EXPLAIN`을 떠 보지 않았다.** 이 문서의 개선 전후는 측정치가 아니라 실행 구조 비교이며,
  수치로 말하려면 데이터를 채운 뒤 `EXPLAIN` + 응답시간을 먼저 재야 한다.
- 파생 테이블(`(SELECT ... FROM ...) alias`)은 MySQL 8.0부터 Derived Condition Pushdown 최적화가 적용된다.

---

## 2026-11-21 실측

**환경**: MySQL 8.0(컨테이너), 저장소 `docs/sql/schema.sql`, 합성 데이터 — 회원 5,000 · 게시글 50,000 · 프로필 이미지 4,000 · attach 전체 104,000행. 글 목록 쿼리(`LIMIT 10`)를 `EXPLAIN ANALYZE`로 두 번씩 돌린 둘째 값.

### 1. 서브쿼리 vs 조인 — 차이 없음

| 형태 | 시간 | attach 접근 |
|---|---|---|
| 상관 서브쿼리 + `LIMIT 1` (06-18 이전) | 12.4 ms | `idx_attach_target` 조회 `loops=10` |
| 파생 테이블 `LEFT JOIN` (06-18 채택안) | 12.4 ms | `idx_attach_target` 조회 `loops=10` |

MySQL 8은 단순 파생 테이블을 바깥 쿼리에 합친다(merge). 그래서 채택안도 결과 10행마다 attach 인덱스를 한 번씩 조회한다 — 서브쿼리와 같은 일이다. 본문이 예상한 "attach를 한 번만 읽고 조인"은 일어나지 않았다. 두 형태 모두 12ms의 거의 전부는 attach가 아니라 **게시글 50,000행 전체를 읽어 정렬**하는 데 썼다(`Table scan on p` → `Sort: p.created_at DESC`).

회원당 1행으로 묶으려고 `GROUP BY` 파생 테이블을 쓰면 오히려 크게 느려졌다(같은 조건에서 136초) — 합쳐지지 못하고 통째로 구체화되기 때문이다.

### 2. 채택안의 행 증식 — 재현하고 고침

채택안은 서브쿼리의 `LIMIT 1`을 떼면서 "회원당 프로필 1행"을 DB가 아니라 앱에 맡겼다. 한 회원에게 프로필 행을 하나 더 넣자 그 회원의 글이 목록에서 **10행 → 20행**이 됐다. 협업자 목록(`TripCollaboratorMapper`)도 같은 조인을 쓰고 있었다.

→ 조인 조건에서 회원당 최신 1장만 고르게 바꿨다.

```sql
LEFT JOIN attach pa
       ON pa.id = (SELECT MAX(pa2.id) FROM attach pa2
                   WHERE pa2.target = 'profile' AND pa2.target_id = m.id)
```

행마다 인덱스 조회 1회라는 비용은 그대로이고, 결과 행 수는 프로필 행 수와 무관해진다. 회귀 테스트 `PostListIntegrationTest`(수정 전 실패 → 수정 후 통과).

### 3. 실제 병목은 정렬 — 인덱스 추가

`post`에는 `created_at`·`like_count` 인덱스가 없어 목록을 볼 때마다 전체 행을 정렬했다. `idx_post_created_at (created_at)`, `idx_post_popular (like_count, created_at)`를 추가했다(`docs/sql/migration_post_list_index_v1.sql`).

| 목록 | 인덱스 전 | 인덱스 후 | 계획 변화 |
|---|---|---|---|
| 최신순 1쪽 | 13.1 ms | 0.06 ms | 전체 스캔 + 정렬 → 인덱스 역방향 스캔 |
| 인기순 1쪽 | 14.4 ms | 0.05 ms | 전체 스캔 + 정렬 → 인덱스 스캔 |
| 최신순 501쪽(`OFFSET 5000`) | 19 ms | 6.05 ms | 인덱스로 5,010행을 읽고 버림 — 깊은 쪽은 여전히 OFFSET 비용 |

중복 방지 조인을 넣은 최종 쿼리의 최신순 1쪽은 인덱스 후 0.16~0.22 ms.

### 한계

- 합성 데이터·단일 접속·컨테이너 한 대의 수치다. 운영 부하에서 잰 값이 아니다.
- 검색어(`LIKE '%…%'`)가 붙으면 인덱스로 건너뛸 수 없어 여전히 넓게 읽는다.
- 깊은 쪽은 OFFSET 방식의 한계가 남는다(키셋 페이지네이션은 하지 않았다).
