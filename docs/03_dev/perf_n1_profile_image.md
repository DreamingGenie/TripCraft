# 기술 노트 — 프로필 이미지 N+1 쿼리 개선

작성일: 2026-06-18  
작성자: 전진  
관련 파일: `PostMapper.xml`, `PostLikeMapper.xml`, `PostBookmarkMapper.xml`

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

페이지 크기 `size=10` 기준으로 항상 **11회** DB 왕복이 발생한다.

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

총 DB 읽기: 11회
```

### 개선 후 — 동일 조회

```
1. SELECT post p
   LEFT JOIN member m
   LEFT JOIN (SELECT target_id, name FROM attach WHERE target='profile') pa
             ON pa.target_id = m.id
   →  10행 반환, 프로필 이미지 포함

총 DB 읽기: 1회
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
- `EXPLAIN` 실행 시 기존 방식은 `DEPENDENT SUBQUERY`, 개선 후는 `DERIVED` + `ref`로 변경된다.
- 파생 테이블(`(SELECT ... FROM ...) alias`)은 MySQL 8.0부터 Derived Condition Pushdown 최적화가 적용된다.
