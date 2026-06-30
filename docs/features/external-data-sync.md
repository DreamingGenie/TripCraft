# 기술 노트 — 외부 데이터 동기화 (TourAPI 수집)

관광지 데이터는 조회 때마다 외부 API를 호출하지 않고, 한국관광공사 TourAPI로 **미리 수집해 DB에 적재**한다.
조회 응답 속도와 외부 API 호출 한도를 모두 해결하기 위한 설계.

관련 코드: `backend/.../attraction/{service,client,batch}/`.

---

## 1. 수집 범위

`areaBasedList2`를 **지역 × 콘텐츠 타입** 행렬로 순회한다(`TourApiSyncServiceImpl`).
- 콘텐츠 타입 6종: `12`(관광지)·`14`(문화시설)·`28`(레포츠)·`32`(숙박)·`38`(쇼핑)·`39`(음식점)
- 지역 17개 시도(`AREA_CODES`)
- 페이지 `PAGE_SIZE=1000`로 끝까지 순회, DB INSERT는 `BATCH_SIZE=500` 단위 분할(`insertAll`).

`TourApiItem` → `Attraction` 매핑 시 좌표 주의: TourAPI `mapx=경도`, `mapy=위도`. 날짜는 `yyyyMMdd[HHmmss]` 둘 다 허용하는 포맷터로 파싱하며, 변환 실패 행은 건너뛰고 로그만 남긴다(전체 중단 방지).

## 2. 두 가지 실행 경로

| 경로 | 트리거 | 구현 |
|------|--------|------|
| **수동(관리자)** | `POST /api/admin/attractions/sync`(전체)·`/sync/partial`(부분) | `TourApiSyncServiceImpl.syncAll/syncByArea` — 동기 호출, 소요 시간 반환 |
| **정기(배치)** | 매일 새벽 2시(Asia/Seoul) | `TourApiSyncScheduler` → Spring Batch `tourApiSyncJob`(reader/processor/writer) |

> 정기 배치는 `JobParameters`에 `runDate`(당일)를 넣어 매일 **새 Job 인스턴스**로 실행한다 — Spring Batch가
> 동일 파라미터의 성공 Job을 재실행하지 않기 때문. 참조 데이터 동기화가 실패해도 관광지 배치는 계속 진행한다(독립 try).

## 3. 증분 동기화

각 행에 `api_modified_at`(TourAPI `modifiedtime`)·`api_created_at`을 저장하고 `idx_api_modified` 인덱스로
변경분만 갱신할 수 있게 한다. 시도·시군구 참조(`sido`/`sigungu`)는 `areaCode2`로 동기화하되
사람이 지정한 표시용 `alias`는 보존한다(`ReferenceDataSyncServiceImpl`).

## 4. 호출 한도 — `TourApiCallLimiter`

무료 TourAPI는 일일 호출 한도가 있다. `system_config` 테이블에 `tour_api_call_date`·`tour_api_call_count`·
`tour_api_daily_limit`(기본 500)을 두고, `tryConsume()`이 **날짜가 바뀌면 카운트 리셋**, 한도 초과 시 `false`를
반환해 호출을 막는다(`synchronized`로 동시성 보호). `remainingToday()`로 잔여 호출량 확인.

## 5. 상세 데이터 지연 로딩

상세(`detailCommon/Intro/Image/Info`)는 별도 테이블로 분리한다. 콘텐츠 타입별로 필드가 다른 `intro_data`·
`room_data`는 JSON 컬럼으로 저장하고, 검색이 필요해지면 컬럼으로 승격한다.

## 6. 관련 코드
- 서비스: `attraction/service/{TourApiSyncServiceImpl,ReferenceDataSyncServiceImpl}.java`
- 배치: `attraction/batch/{TourApiSyncJobConfig,TourApiItemReader,TourApiItemProcessor,TourApiItemWriter,TourApiSyncScheduler}.java`
- 클라이언트: `attraction/client/{TourApiClient,TourApiCallLimiter,TourApiConfig}.java`
- 엔드포인트: [api.md](../api.md) §2-13, 스키마: [database.md](../database.md)
