# ODsay 대중교통 길찾기 v1.8 API 레퍼런스

> 출처: https://lab.odsay.com/guide/releaseReference#searchPubTransPathT
> 2020.09.24 업데이트 — 도시간 길찾기 결과 중 열차 환승 지원

## 엔드포인트

```
GET https://api.odsay.com/v1/api/searchPubTransPathT
```

> ⚠️ `searchPubTransPath` (구 URL)는 레거시. 신규 개발 시 `searchPubTransPathT` 사용.

---

## 요청 파라미터

| # | 파라미터 | 필수 | 설명 |
|---|---------|------|------|
| - | apiKey | Y | 발급된 API 키 |
| - | lang | N | 0=국문(default), 1=영문, 2=일문, 3=중문간체, 4=중문번체, 5=베트남어 |
| 1 | SX | Y | 출발지 X좌표 (경도) |
| 2 | SY | Y | 출발지 Y좌표 (위도) |
| 3 | EX | Y | 도착지 X좌표 (경도) |
| 4 | EY | Y | 도착지 Y좌표 (위도) |
| 5 | OPT | N | 0=추천경로(default), 1=타입별정렬 |
| 6 | SearchType | N | **0 입력 시 도시내검색** (도시내검색에서 도시간검색결과 있을 경우 활용) |
| 7 | SearchPathType | N | 0=모두(default), 1=지하철, 2=버스 |

---

## 도시간 경로 반환 시 추가 호출 필요

경로 검색 결과에 도시간 경로가 반환될 경우 아래 API를 추가 호출해야 함:
- **출발지 → 출발 터미널**(시외, 고속, 열차, 항공 등) 이동을 위한 도시내 경로 API 호출
- **도착지 → 도착 터미널** 이동을 위한 도시내 경로 API 호출

(동일 URL에 `SearchType=0` 파라미터 사용)

---

## 출력 데이터 — 도시내 길찾기

### result 최상위 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| searchType | int | **0=도시내, 1=도시간 직통, 2=도시간 환승** |
| outTrafficCheck | int | 도시간 직통 탐색 결과 유무 (0=False, 1=True) |
| busCount | int | 버스 결과 개수 |
| subwayCount | int | 지하철 결과 개수 |
| subwayBusCount | int | 버스+지하철 결과 개수 |
| pointDistance | double | 출발~도착 직선거리 (미터) |

### path 배열

| 필드 | 타입 | 설명 |
|------|------|------|
| pathType | int | **1=지하철, 2=버스, 3=버스+지하철** |

### info (도시내 요약)

| 필드 | 타입 | 설명 |
|------|------|------|
| trafficDistance | double | 도보 제외 총 이동거리 |
| totalWalk | int | 총 도보 이동거리 |
| **totalTime** | int | **총 소요시간 (분)** |
| **payment** | int | **총 요금** |
| busTransitCount | int | 버스 환승 수 |
| subwayTransitCount | int | 지하철 환승 수 |
| firstStartStation | string | 최초 출발역/정류장 |
| lastEndStation | string | 최종 도착역/정류장 |
| totalStationCount | int | 총 정류장 수 |

### subPath (도시내 이동 수단)

| 필드 | 타입 | 설명 |
|------|------|------|
| **trafficType** | int | **1=지하철, 2=버스, 3=도보** |
| distance | double | 이동 거리 |
| sectionTime | int | 이동 소요 시간 (분) |
| startName / endName | string | 승하차 정류장/역명 |
| startX / startY | double | 승차 정류장 좌표 (X=경도, Y=위도) |
| endX / endY | double | 하차 정류장 좌표 |
| startID / endID | int | 정류장/역 코드 |

---

## 출력 데이터 — 도시간 길찾기

### result 최상위 필드

| 필드 | 타입 | 설명 |
|------|------|------|
| searchType | int | 0=도시내, 1=도시간 |
| busCount | int | 고속/시외버스 결과 개수 |
| trainCount | int | 열차 결과 개수 |
| airCount | int | 항공 결과 개수 |
| mixedCount | int | 시외교통 복합 이용 결과 개수 |

### path 배열

| 필드 | 타입 | 설명 |
|------|------|------|
| pathType | int | **11=열차, 12=고속/시외버스, 13=항공, 20=시외교통 복합** |

### info (도시간 요약)

| 필드 | 타입 | 설명 |
|------|------|------|
| **totalTime** | int | **시외교통 총 소요시간 (분)** |
| **totalPayment** | int | **시외교통 총 요금** |
| **transitCount** | int | **이용하는 시외교통 수** |
| firstStartStation | string | 최초 출발역/터미널/공항 |
| lastEndStation | string | 최종 도착역/터미널/공항 |
| totalDistance | int | 시외교통 총 이동거리 |

### subPath (도시간 이동 수단)

| 필드 | 타입 | 설명 |
|------|------|------|
| **trafficType** | int | **4=열차, 5=고속버스, 6=시외버스, 7=항공** |
| **trainType** | int | 열차 종류: **1=KTX, 2=새마을, 3=무궁화, 4=누리로, 5=통근, 6=ITX, 7=ITX-청춘, 8=SRT** |
| distance | int | 이동거리 (미터) |
| sectionTime | int | 이동시간 (분) |
| **payment** | int | 요금 |
| trainSpSeatYn | string | 특실 존재 여부 Y/N |
| trainSpSeatPayment | int | 특실 요금 |
| startName / endName | string | 출발/도착 역·터미널·공항 명칭 |
| **startX / startY** | double | 출발 역/터미널 좌표 (X=경도, Y=위도) |
| **endX / endY** | double | 도착 역/터미널 좌표 |
| startID / endID | int | 역/터미널/공항 코드 |
| startCityCode / endCityCode | int | 도시코드 |
| intervalTime | int | 평균 배차간격 (분) |
| intervalCount | int | 운행 횟수 |

---

## 에러 코드

| 코드 | 메시지 |
|------|--------|
| 500 | 서버 내부 오류 |
| -8 | 필수 입력값 형식 및 범위 오류 |
| -9 | 필수 입력값 누락 |
| 3 | 출발지 정류장이 없습니다 |
| 4 | 도착지 정류장이 없습니다 |
| 5 | 출·도착지 정류장이 없습니다 |
| 6 | 서비스 지역이 아닙니다 |
| **-98** | **출·도착지가 700m 이내입니다** |
| **-99** | **검색결과가 없습니다** |

---

## 도시내/도시간 구분 요약

| 구분 | searchType(응답) | pathType | trafficType | info 요금 필드 | info 환승 필드 |
|------|----------------|----------|-------------|--------------|--------------|
| 도시내 | 0 | 1/2/3 | 1/2/3 | `payment` | `busTransitCount` + `subwayTransitCount` |
| 도시간 | 1/2 | 11/12/13/20 | 4/5/6/7 | `totalPayment` | `transitCount` |
