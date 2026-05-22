# attraction.md — 관광지 조회·수집

> **수정 규칙**: 수정 전 변경 내용을 사용자에게 설명하고 승인을 받을 것.

## 패키지 위치

```
com.tripcraft.attraction/
├── AttractionController.java
├── AttractionService.java / AttractionServiceImpl.java
├── AttractionMapper.java
└── dto/
```

## TourAPI 4.0 수집 전략

- API: 한국관광공사 국문 관광정보 서비스
- 수집 대상 콘텐츠 타입: 관광지(12) · 문화시설(14) · 레포츠(28) · 숙박(32) · 쇼핑(38) · 음식점(39)
- **초기 1회 배치 수집** 후 자체 DB 운용. 이후 API 직접 호출 최소화
- 결측 데이터(이미지 없음, 주소 없음) 허용, 별도 처리 불필요

## 조회 규칙

- 지역 필터: 시도 → 시군구 (다중 선택)
- 카테고리 필터: 콘텐츠 타입 코드 기준 (다중 선택)
- 검색: 장소명 키워드 부분 일치
- 목록 API 응답 목표: **500ms 이하** (자체 DB 조회 기준)
