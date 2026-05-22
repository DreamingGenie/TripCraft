# community.md — 커뮤니티·공지

> **수정 규칙**: 수정 전 변경 내용을 사용자에게 설명하고 승인을 받을 것.

## 패키지 위치

```
com.tripcraft.community/
├── PostController.java
├── PostService.java / PostServiceImpl.java
├── PostMapper.java
├── NoticeController.java
├── NoticeService.java / NoticeServiceImpl.java
├── NoticeMapper.java
└── dto/
```

## 권한 규칙

| 작업 | 허용 대상 |
|------|---------|
| 게시글 조회 | 비회원 포함 전체 |
| 게시글 작성 | 로그인 회원 |
| 게시글 수정·삭제 | 작성자 본인 또는 ADMIN |
| 공지 CRUD | ADMIN만 |

- 다른 사람 게시글 수정·삭제 API 호출 시 **403** 반환 (서버에서 소유권 검증)
- 공지사항: 커뮤니티 우측 사이드바에 최신 **5건** 표시
- 게시글 상세 접근 시 조회수 +1 (자동)
