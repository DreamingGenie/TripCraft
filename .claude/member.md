# member.md — 회원·인증·보안

> **수정 규칙**: 수정 전 변경 내용을 사용자에게 설명하고 승인을 받을 것.

## 패키지 위치

```
com.tripcraft.member/
├── controller/
│   └── AuthController.java
├── service/
│   ├── AuthService.java
│   └── AuthServiceImpl.java
├── mapper/
│   ├── MemberMapper.java
│   ├── MemberTokenMapper.java
│   └── FavoriteMapper.java
├── domain/
│   ├── Member.java          (Role enum: USER / ADMIN)
│   ├── MemberToken.java
│   └── Favorite.java
└── dto/
    ├── SignupRequest.java
    ├── LoginRequest.java
    └── TokenResponse.java
```

MyBatis XML: `src/main/resources/mapper/member/`

## Auth API

| 엔드포인트 | 메서드 | 인증 | 설명 |
|-----------|--------|------|------|
| `/api/auth/signup` | POST | 불필요 | 회원가입 (토큰 미발급, 201 반환) |
| `/api/auth/login` | POST | 불필요 | 로그인 → Access Token 반환 |
| `/api/auth/refresh` | POST | 불필요 | Refresh Token으로 Access Token 재발급 |
| `/api/auth/logout` | POST | 불필요 | Refresh Token 무효화 + 쿠키 clear |

> **회원가입은 토큰을 발급하지 않음.** 가입 완료 후 별도 로그인 필요.

## JWT 전략

| 토큰 | 만료 | 전달 방식 |
|------|------|---------|
| Access Token | 30분 | `Authorization: Bearer {token}` 헤더 (**미결**: HttpOnly 쿠키 전환 논의 중) |
| Refresh Token | 7일 | HttpOnly 쿠키 (`refresh_token`, path: `/api/auth/refresh`) |

> **미결 사항**: Access Token을 HttpOnly 쿠키로 전환할지 논의 중.  
> 현재 구현은 Bearer 헤더 방식. 결정 후 `JwtAuthenticationFilter` 및 프론트엔드 동시 수정 필요.

- 로그인 시 기존 Refresh Token 전체 삭제 후 재발급 (`deleteByMemberId` → `insert`)
- 로그아웃 시 해당 Refresh Token만 삭제

## Role

`member.role` 컬럼: `USER` / `ADMIN`

- 관리자 전용 API(`/api/admin/**`, `/api/notices/**`)는 서버에서 role 검증 후 미통과 시 **403** 반환
- 클라이언트 role 값만으로 접근 허용 금지

## 인증 오류 응답

공통 응답 형식 그대로 사용:
```json
{ "success": false, "data": null, "message": "인증이 필요합니다.", "errorCode": "UNAUTHORIZED" }
```

| 상황 | HTTP 상태 | errorCode |
|------|----------|-----------|
| 미로그인 접근 | 401 | `UNAUTHORIZED` |
| 권한 없음 (role 불일치) | 403 | `FORBIDDEN` |
| 이미 사용 중인 이메일 | 409 | — |
| 이메일/비밀번호 불일치 | 401 | — |

## 비밀번호

BCrypt 해시 저장. 평문 저장·로그 출력 절대 금지.

## 회원 탈퇴

탈퇴 시 해당 회원의 일정·즐겨찾기·게시글 연쇄 삭제 (DB CASCADE 설정).  
단, 게시글은 보존하고 `member_id = NULL` 처리 (탈퇴한 사용자로 표시).
