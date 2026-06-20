# 게시글 이미지 업로드 생명주기 설계

## 1. 현재 구현 (MVP 단계)

`ImageController.java`는 `POST /api/images/upload`를 통해 파일을 받아 서버 디스크에 저장하고 URL을 반환한다.

```
클라이언트 → POST /api/images/upload (multipart)
  → 타입 검증 (JPEG·PNG·GIF·WebP)
  → 크기 검증 (5 MB 이하)
  → UUID 파일명으로 {upload.dir}/images/ 저장
  → 응답: /uploads/images/{uuid}.ext
```

동작은 하지만 두 가지 구조적 문제가 있다.

---

## 2. 문제: 컨트롤러에 파일 저장 로직이 직접 존재

`MemberController`에서 프로필 이미지를 저장할 때는 `FileStorageService`를 주입받아 사용한다. 반면 `ImageController`는 파일 저장 로직(`Files.createDirectories`, `file.transferTo`)을 컨트롤러 메서드 안에 직접 작성하고 있다.

**왜 컨트롤러에 파일 I/O가 있으면 안 되나:**

- 컨트롤러는 요청/응답 변환과 권한 진입점 역할만 해야 한다
- 파일 저장 위치나 파일명 규칙이 바뀌면 두 곳(MemberController·ImageController)을 각각 수정해야 한다
- 단위 테스트 작성 시 파일 시스템 의존성이 컨트롤러 레이어로 올라와 테스트가 복잡해진다

**개선 방향:**

```java
// ImageController (개선 후)
@PostMapping("/upload")
public ResponseEntity<ApiResponse<String>> upload(
        @RequestParam MultipartFile file,
        @AuthenticationPrincipal Long memberId) {

    validate(file);                                    // 타입·크기 검증만
    String url = fileStorageService.store(file);       // 저장 위임
    return ResponseEntity.ok(ApiResponse.ok(url));
}
```

---

## 3. 문제: 이미지 생명주기를 추적하지 않음

프로필 이미지는 `attach` 테이블에 `(target='profile', target_id=memberId, name=파일명)` 형태로 기록된다. 조회 시 이 레코드를 JOIN해서 URL을 구성한다.

게시글 본문 이미지는 디스크에만 저장되고 DB 레코드가 없다. 이 때문에:

| 시나리오 | 결과 |
|---------|------|
| 글 작성 중 이미지 업로드 후 브라우저 종료 | 파일이 디스크에 영원히 잔류 (고아 파일) |
| 게시글 삭제 | 본문에 포함된 이미지 파일 그대로 잔류 |
| 특정 게시글의 이미지 목록 조회 | 불가 (DB 레코드 없음) |
| 스토리지 사용량 계산 | 불가 |

---

## 4. 설계 개선안: attach 테이블로 생명주기 관리

### 4-1. 업로드 시 — draft 상태로 등록

```
POST /api/images/upload
  → 파일 저장 (UUID 파일명)
  → attach INSERT (target='post_draft', target_id=NULL)
  → 응답: { url, attachId }
```

`target_id=NULL`은 "아직 특정 게시글에 연결되지 않은 임시 파일"을 의미한다.

### 4-2. 게시글 등록 시 — attach 레코드 연결

```
POST /api/posts (content에 이미지 URL들 포함)
  → content에서 /uploads/images/{uuid} 패턴 파싱
  → 해당 uuid에 해당하는 attach 레코드의 target을 'post', target_id를 새 post.id로 UPDATE
```

### 4-3. 게시글 삭제 시 — 연결된 파일 삭제

```
DELETE /api/posts/{id}
  → attach에서 target='post', target_id={id} 레코드 조회
  → 디스크에서 파일 삭제
  → attach 레코드 삭제
```

### 4-4. 고아 파일 정리 — 스케줄러

```
@Scheduled(cron = "0 0 3 * * *")  // 매일 새벽 3시
  → attach WHERE target='post_draft' AND created_at < NOW() - INTERVAL 1 DAY
  → 해당 파일 삭제 + attach 레코드 삭제
```

24시간이 지난 draft 첨부파일은 사용되지 않은 것으로 간주해 정리한다.

---

## 5. Content-type 스푸핑 문제

현재 코드:
```java
String contentType = file.getContentType();
if (!ALLOWED_TYPES.contains(contentType)) { ... }
```

`getContentType()`은 HTTP 요청의 `Content-Type` 헤더 값을 반환한다. 클라이언트가 `.exe` 파일을 `image/jpeg`로 선언해 보내면 통과된다.

**magic bytes 검증 추가:**

```java
byte[] header = new byte[12];
file.getInputStream().read(header);

// JPEG: FF D8 FF
// PNG:  89 50 4E 47
// GIF:  47 49 46 38
// WebP: 52 49 46 46 ... 57 45 42 50
if (!isValidImageHeader(header)) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "올바른 이미지 파일이 아닙니다.");
}
```

실무에서는 Apache Tika 라이브러리가 MIME 타입 감지를 가장 신뢰성 있게 처리한다. 의존성 추가 비용이 있지만 보안이 중요한 파일 업로드 기능에서는 표준 선택지다.

---

## 6. 현재 구현 평가

| 항목 | 상태 | 비고 |
|------|------|------|
| 파일 타입 검증 | ⚠️ 부분 | Content-type 헤더 의존, magic bytes 미검증 |
| 파일 크기 제한 | ✅ | 5 MB |
| UUID 파일명 | ✅ | 원본 파일명 노출 없음 |
| 절대 경로 저장 | ✅ | transferTo() 버그 수정 완료 |
| 인증 확인 | ✅ | memberId null 체크 |
| 서비스 레이어 분리 | ❌ | 컨트롤러에 파일 I/O 직접 작성 |
| DB 추적 (attach) | ❌ | 디스크만 저장, 생명주기 관리 불가 |
| 고아 파일 정리 | ❌ | 스케줄러 미구현 |
| 게시글 삭제 연동 | ❌ | 이미지 파일 잔류 |

MVP로서 기능 동작은 확인됐고, 4번의 attach 테이블 연동 설계가 다음 구현 목표다.
