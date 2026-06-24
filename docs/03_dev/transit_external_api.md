# 기술 노트 — 이동시간 외부 API 통합 (파싱·경로 조합·캐싱)

작성일: 2026-06-24
작성자: 송정기
관련 파일: `plan/service/TransitServiceImpl.java`, `plan/client/OdsayClient.java`, `plan/client/TMapClient.java`,
`place/client/KakaoLocalClient.java`, `plan/controller/TransitController.java`,
`components/ScheduleBoard.vue`(지도·핀 모달), `mapper/plan/TransitCacheMapper.xml`

> 프로젝트에서 **기술적으로 가장 복잡했던 작업**. 외부 API 4종의 응답을 파싱하고, 한 API로는 부족한 경로를
> 여러 호출로 **조합·보강**하며, 호출 제한(rate limit)을 막기 위해 **다층 캐싱**을 설계했다.

---

## 1. 한눈에 — 왜 복잡한가

여행 일정의 두 장소 사이 "이동수단별 경로"를 보여주는 기능 하나에 **외부 API 4종**이 얽힌다.

| API | 역할 | 좌표계 / 키 |
|-----|------|------------|
| **ODsay** | 대중교통 경로(버스·지하철·KTX·고속버스), 노선 폴리라인 | WGS84, REST 키 |
| **TMap** | 자동차(택시) 4개 옵션, 도보 경로 | WGS84, AppKey |
| **Kakao Local** | 커스텀 장소 키워드 검색(좌표·분류 변환) | WGS84, KakaoAK |
| **Naver Maps** | 지도 렌더링 + 위치 직접 선택(클릭) | JS SDK |

어려움의 본질은 세 가지다.
1. **파싱** — ODsay 응답이 깊게 중첩된 JSON(구간별 subPath, 교통수단 코드)이라 의미 단위로 풀어야 함.
2. **조합** — 한 번의 호출로 완결되지 않음. 도시간(KTX) 경로는 "역→역"만 주므로 출발지·목적지 접근 구간을
   **추가 호출로 합성**해야 하고, 지도 선은 또 **별도 호출**(loadLane)로 그려야 함.
3. **호출 제한** — 무료 API라 짧은 시간에 같은 좌표를 반복 호출하면 **빈 응답**을 준다 → 캐싱이 필수.

---

## 2. 파싱 — ODsay 응답 구조 해석

ODsay `searchPubTransPathT` 응답의 핵심은 `path[].subPath[]` 와 두 분류 코드다.

**pathType** (경로 유형)
- `< 11` : **시내(intra-city)** — 버스/지하철 (지하철=1, 버스=2, 버스+지하철=3)
- `>= 11` : **도시간(intercity)** — 열차(11)·고속/시외버스(12)·항공(13)·복합(20)

**trafficType** (구간 교통수단)

| 코드 | 의미 | 표시 |
|------|------|------|
| 1 | 지하철 | 🚇 노선명/호선 |
| 2 | 버스 | 🚌 버스번호 |
| 3 | 도보 | 🚶 거리 |
| 4 | 열차(KTX 등) | 🚅 |
| 5 / 6 | 고속버스 / 시외버스 | 🚌 |

프론트 `parseTransitPaths()` / `parseStep()` 가 `subPath` 를 순회하며 위 코드로 단계(step) 카드를 만든다.
요금(`totalPayment`→없으면 `payment`), 환승수(`transitCount-1`), 총 도보거리(`totalWalk`) 등도 같은 응답에서 추출.

---

## 3. 조합·보강 — 도시간 경로의 "역까지/역에서" 합성

ODsay 도시간 경로(`pathType >= 11`)는 **출발역→도착역(KTX 구간)만** 반환한다.
"우리 집 → 서울역", "부산역 → 목적지" 같은 **접근 구간이 빠져서** 총 소요시간이 비현실적으로 짧게 나왔다.

→ 해결: 도시간 경로마다 **로컬 경로를 추가로 호출**해 앞뒤에 붙인다. (`TransitServiceImpl.enrichPublicTransitLive`)

```
[출발지] --findLocalPath--> [출발역] ==KTX(원래 응답)== [도착역] --findLocalPath--> [목적지]
   localFrom(예: 46분)            interTime(138분)            localTo(예: 20분)
                         총 = localFrom + interTime + localTo
```

- 비-도보 첫 구간의 시작좌표 = 출발역, 마지막 비-도보 구간의 끝좌표 = 도착역으로 잡고
  `OdsayClient.findLocalPath(출발지→출발역)`, `findLocalPath(도착역→목적지)` 호출.
- 로컬 경로 API도 실패하면 **하버사인 거리 기반 추정**(`haversineMinutes`, 평균 30km/h)으로 폴백.
- 결과를 `RouteEnrichment(원본, 총분, localFrom, localTo)` 레코드로 담고,
  `buildPathDetail()` 이 `intercityPaths[].localFrom/localTo` 노드로 직렬화 → 모달 스텝/지도에서 재사용.

이 보강이 이 기능에서 **가장 비싼 부분**이고, 뒤의 캐싱이 중요한 이유다.

> **함정 — 보강 내부의 rate limit:** ODsay가 도시간 경로를 10~20개 반환하면, **경로마다** localFrom/localTo를
> 2번씩 호출해 한 번의 보강에서 ODsay를 20~40번 두드린다. 그러면 그 안에서 다시 rate-limit에 걸려
> "한두 경로만 실제 대중교통, 나머지는 '출발지→역 예상'"으로 망가진다.
> → 해결: `findLocalPath`를 **좌표키로 캐시 + 중복 제거**. 도시간 경로 대부분이 **같은 출발역**(예: 서울역)을
> 공유하므로, 캐시로 20~40회 호출이 **고유 역 수(보통 1~3회)**로 줄어든다.

---

## 4. 지도 폴리라인 조립 — 3단 폴백

경로 "선"을 지도에 그리려면 좌표 배열이 필요한데, ODsay는 이를 **또 다른 호출**(loadLane)로 준다.
`extractPublicTransitCoords()` 는 다음 순서로 좌표를 확보한다.

```
1) info.mapObj → loadLane API (노선 실폴리라인)   ← 가장 정확
     └ lane_polyline 테이블에 캐시(있으면 호출 생략)
2) 없으면 passStopList(정류장 좌표) 연결
3) 그래도 없으면 구간 시작·끝 좌표만 연결        ← 최후의 직선
```

지도에서는 구간(lane)별로 색을 달리(지하철=보라, 버스=파랑) 그리고, 구간 hover 강조·환승 마커까지 표시한다
(`ScheduleBoard.vue` `drawPublicTransitPolylines`, `drawTransferMarkers`).

**커스텀 장소의 지도 선 — 접근 구간 조립:** 커스텀(좌표 기반)은 관광지용 lane 캐시를 못 쓰므로, 도시간 경로의
지도 선이 처음엔 **KTX 역–역 직선만** 그려졌다. → `buildEnrichedRouteCoords`로
**[출발지→역(localFrom subPath 좌표)] + [본 구간] + [역→목적지(localTo subPath 좌표)]** 를 이어붙여
접근 구간까지 한 줄로 연결되게 했다.

> **알려진 한계:** 도시간(KTX) **본 구간 자체**는 loadLane 대상이 아니라 여전히 **역–역 2점 직선**(3단 폴백의 3번).
> 접근 구간(버스·지하철)은 정류장 좌표로 선이 그려지고, 모달 스텝에는 전부 표시된다. 철도 실폴리라인은 별도 과제.

---

## 5. 자동차 — TMap 4개 옵션

자동차는 TMap `tmapv1/routes` 의 검색옵션으로 **4가지**를 각각 호출해 비교 제공한다.

| 인덱스 | searchOption | 라벨 |
|--------|--------------|------|
| 0 | `00` | 추천 |
| 1 | `02` | 최단시간 |
| 2 | `01` | 무료도로 |
| 3 | `10` | 최소거리 |

응답에서 주요 도로명(`roadType==1` 우선 거리순 4개)으로 요약(`buildRoadSummary`), 통행료·택시요금·구간 좌표를 추출.

---

## 6. 캐싱 — 3계층 설계

외부 호출은 느리고 **제한이 있으므로**, 무엇을 어디에 캐시할지가 핵심 설계였다.

| 계층 | 저장소 | 키 | 대상 | 비고 |
|------|--------|-----|------|------|
| ① 이동시간 캐시 | DB `transit_cache` | (from_attr, to_attr, hour, request_mode) | 관광지쌍 경로·요약·path_detail | 모드별 행(대중교통 1 + 자동차 4), "NONE"도 캐시해 재시도 방지 |
| ② 노선 폴리라인 | DB `lane_polyline` | mapObj 키 | loadLane 결과 | 결과 없음도 저장(불필요 재호출 차단) |
| ③ 좌표 결과 | **in-memory** (ConcurrentHashMap) | `"%.5f,%.5f>%.5f,%.5f"` (+mode) | **커스텀 장소(by-coords)** 경로 | 세션 캐시, **성공만 저장**(빈 응답 미저장→재시도) |
| ④ 도시내 로컬 경로 | **in-memory** (OdsayClient) | 좌표키 | 보강용 findLocalPath 결과 | 같은 역 공유 중복 제거, 성공·추정만 저장 |

①②는 관광지(attraction id 기반) 경로용으로 처음부터 있었고, ③은 **커스텀 장소** 때문에 추가됐다.
커스텀 장소는 attraction id가 없어 ①을 못 쓰고 좌표로만 호출하는데, 처음엔 **무캐시**로 두었다가 아래 문제를 만났다.

---

## 7. 호출 제한(rate limit) — 발견과 대응

커스텀 장소 경로가 "가끔" 모달에 안 뜨고 지도엔 직선만 그려지는 현상이 있었다. 재현 실험:

```
by-coords/detail 같은 좌표쌍 8연타
 → try1~5: 정상(경로 있음)
 → try6~8: EMPTY (빈 응답)
```

원인: **무캐시라서** 지도 draw·핀 모달 open 마다 ODsay/TMap을 **생호출** → 같은 좌표를 짧게 5회 넘기면
ODsay가 빈 응답(rate limit). 섞인 날엔 관광지 loadLane 호출까지 같이 굶어 관광지 지도도 깨졌다.

대응(③ in-memory 캐시):
- `enrichPublicTransit`(대중교통), `getTransitByCoords`(자동차·도보), `buildDrivingResponse`(자동차 옵션)에
  좌표키 캐시를 씌워 **같은 좌표쌍은 한 번만** 외부 호출.
- **성공 결과만 저장** — 빈 응답을 캐시하면 영구히 빈 채로 굳으므로, 실패는 저장하지 않아 다음에 재시도.

```java
List<RouteEnrichment> cached = publicCoordCache.get(key);
if (cached != null) return cached;
List<RouteEnrichment> fresh = enrichPublicTransitLive(...);
if (!fresh.isEmpty()) publicCoordCache.put(key, fresh);   // 성공만 캐시
return fresh;
```

> 더 견고히 하려면 ①처럼 DB(`route_key` 컬럼은 이미 추가됨)로 승격 가능. 단 단일 인스턴스·세션 단위로는
> in-memory로 충분하고 회귀 위험이 작아 우선 채택.

---

## 8. 좌표 통일 — 관광지·커스텀 한 파이프라인

처음엔 커스텀 경로를 "요약만 뜨는" 간이 화면으로 만들었다가, **관광지와 동일한 리치 뷰**(경로 단계·자동차 4옵션)로
통일했다. 핵심은 **백엔드 보강 로직(`enrichPublicTransit`, `buildDrivingResponse`)을 좌표 기반으로 추출**해
관광지/커스텀이 같은 코드를 쓰게 한 것:
- `getTransitTime`(관광지, id) → 좌표 해석 후 동일 보강
- `getTransitDetailByCoords` / `getDrivingOptionsByCoords`(커스텀, 좌표) → 같은 보강 재사용
- 프론트는 기존 리치 UI를 그대로 두고 **데이터 패치 함수만** 커스텀일 때 by-coords로 분기.

---

## 9. 트러블슈팅 사례 (발표용 에피소드)

| 증상 | 원인 | 해결 |
|------|------|------|
| Kakao 검색 결과 0건 | `RestClient.uri(String)` 가 이미 인코딩된 URL을 **이중 인코딩**(`%EC`→`%25EC`) | `.uri(URI.create(uri))` 로 인코딩 보존 |
| 커스텀 블록 배치 시 NPE | `getAttractionId()` 언박싱(커스텀은 null) | 좌표 기반 `computeBlockTransit`로 분기 |
| 커스텀 경로 간헐적 빈 모달 + 직선 | 무캐시 by-coords의 **rate limit** | in-memory 좌표 캐시(§7) |
| KTX인데 역까지/역에서 미표시 | 모달 스텝이 도시간 구간만 그림 | `localAccessSteps`로 localFrom/localTo를 스텝에 합성(§3) |
| 같은 장소 중복 담김 | 커스텀은 attraction_id NULL이라 기존 UNIQUE 무력 | 좌표 기반 `UNIQUE(trip_id, place_lat, place_lng)` + 앱 사전체크 |

---

## 10. 발표 포인트 3줄 요약

1. **단일 API로 안 끝난다** — 도시간 경로는 "역→역"만 줘서, 접근 구간을 추가 호출로 **합성**하고
   지도 선은 또 별도(loadLane) 호출로 조립했다. (다중 호출 오케스트레이션)
2. **무료 API의 현실은 rate limit** — 무캐시로 반복 호출하면 빈 응답이 온다는 걸 8연타 실험으로 규명하고,
   **성공 결과만 저장하는** 다층 캐시(DB 2 + in-memory 1)로 해결했다.
3. **확장성** — 보강 로직을 좌표 기반으로 추출해 관광지와 커스텀 장소가 **동일 파이프라인**을 공유한다.
