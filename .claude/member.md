# member.md — 회원·인증·보안

> **수정 규칙**: 수정 전 변경 내용을 사용자에게 설명하고 승인을 받을 것.

## 패키지 위치

```
com.tripcraft.member/
├── MemberController.java
├── MemberService.java / MemberServiceImpl.java
├── MemberMapper.java
└── dto/
```

## JWT 전략

| 토큰 | 만료 | 전달 방식 |
|------|------|---------|
| Access Token | 30분 | `Authorization: Bearer {token}` 헤더 |
| Refresh Token | 7일 | HttpOnly 쿠키 |

- 로그아웃 시 Refresh Token 무효화
- Access Token 만료 시 Refresh Token으로 자동 재발급

## Role

`member.role` 컬럼: `USER` / `ADMIN`

- 관리자 전용 API는 서버에서 role 검증 후 미통과 시 **403** 반환
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

## 비밀번호

BCrypt 해시 저장. 평문 저장·로그 출력 절대 금지.

## 회원 탈퇴

탈퇴 시 해당 회원의 일정·즐겨찾기·게시글 연쇄 삭제 (DB CASCADE 설정).
