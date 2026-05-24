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

- API: 한국관광공사 국문 관광정보 서비스 **KorService2** (`https://apis.data.go.kr/B551011/KorService2`)
- 수집 엔드포인트: `/areaBasedList2` (KorService1의 `areaBasedList1` 아님)
- 수집 대상 콘텐츠 타입: 관광지(12) · 문화시설(14) · 레포츠(28) · 숙박(32) · 쇼핑(38) · 음식점(39)
- 수집 대상 지역: 17개 시도 (areaCode 1~8, 31~39)
- **초기 1회 배치 수집** 후 자체 DB 운용. 이후 API 직접 호출 최소화
- 결측 데이터(이미지 없음, 주소 없음) 허용, 별도 처리 불필요
- **수집 완료** (2026-05-24 기준 전국 관광지 DB 적재 완료)

### 구현 주의사항

- `serviceKey`는 URL-인코딩 키 사용. `.env`의 `TOUR_API_KEY` 참조
- RestClient에 URL 전달 시 반드시 `URI.create(url)` 사용 — `uri(String)` 직접 전달 시 `%2B`→`%252B` 이중 인코딩 발생해 401 오류
- `areaBasedList2` 응답: `overview` 미포함(별도 `/detailCommon2` 호출 필요), `tel` 빈 값 장소 다수

### 수집 트리거 (관리자 전용)

- `POST /api/admin/attractions/sync` — 전체 수집 (3~10분)
- `POST /api/admin/attractions/sync/partial?areaCode=1&contentTypeId=12` — 부분 수집
- 프론트엔드: `/admin` 관리자 페이지에서 버튼으로 실행 가능 (ADMIN role 필요)
- 컨트롤러: `com.tripcraft.attraction.controller.AttractionSyncController`
- HTTP 클라이언트: `com.tripcraft.attraction.client.TourApiClient` (Spring RestClient)

## 조회 규칙

- 지역 필터: 시도 → 시군구 (다중 선택)
- 카테고리 필터: 콘텐츠 타입 코드 기준 (다중 선택)
- 검색: 장소명 키워드 부분 일치
- 목록 API 응답 목표: **500ms 이하** (자체 DB 조회 기준)
