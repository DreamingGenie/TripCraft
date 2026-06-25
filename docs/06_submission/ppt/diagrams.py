# TripCraft 발표 덱 — Mermaid 다이어그램 원본 (build.py가 PNG로 렌더 후 임베드)
DIAGRAMS = {
"gantt": """gantt
    title TripCraft 개발 일정
    dateFormat YYYY-MM-DD
    axisFormat %m/%d
    section 기획·설계
    요구사항 정의/WBS        :done, p1, 2026-05-12, 5d
    ERD/API/와이어프레임      :done, p2, 2026-05-19, 5d
    컨벤션·환경 세팅          :done, p3, 2026-05-19, 5d
    section 회원·인증
    회원/JWT/소셜로그인       :done, m1, 2026-05-26, 6d
    section 관광지
    TourAPI 수집·조회 DB      :done, a1, 2026-06-02, 5d
    탐색 UI·네이버맵·즐겨찾기  :done, a2, 2026-06-02, 6d
    section 일정·이동시간
    Trip/Block·드래그앤드롭   :done, t1, 2026-06-09, 7d
    ODsay/T Map 이동시간      :done, t2, 2026-06-11, 6d
    경로 지도 시각화          :done, t3, 2026-06-16, 4d
    section 커뮤니티·고도화
    공유게시판·공지·댓글       :done, c1, 2026-06-16, 5d
    실시간 협업·공유링크       :done, c2, 2026-06-18, 5d
    AI 챗봇·주변추천          :done, c3, 2026-06-18, 4d
    마이페이지·방문지도        :done, c4, 2026-06-20, 4d
    section QA·정리
    통합테스트·버그수정·보안   :active, q1, 2026-06-23, 4d
    산출물 정리·발표 준비      :active, q2, 2026-06-24, 3d
""",
"arch": """flowchart LR
    subgraph Client["Vue 3 SPA"]
        UI[Views/Components]
        Store[Pinia Stores]
        WS[STOMP Client]
    end
    subgraph Server["Spring Boot"]
        CT[Controllers]
        SV[Services]
        MP[MyBatis Mappers]
        SEC[Spring Security/JWT]
        WSS[WebSocket /ws]
        BATCH[Spring Batch]
    end
    DB[(MySQL 8.0)]
    subgraph Ext["외부 API"]
        TOUR[TourAPI 4.0]
        ODSAY[ODsay]
        TMAP[T Map]
        NAVER[Naver Maps]
        KAKAO[Kakao OAuth/Local]
        GMS[gms gpt-4.1]
    end
    UI --> Store --> CT
    WS <--> WSS
    CT --> SV --> MP --> DB
    CT --> SEC
    SV --> ODSAY
    SV --> TMAP
    SV --> GMS
    SV --> KAKAO
    BATCH --> TOUR
    BATCH --> DB
    UI -.지도.-> NAVER
""",
"flow1": """flowchart TD
    Landing["/ 랜딩"] -->|탐색| Discover["/discover 관광지 탐색"]
    Landing -->|로그인| Auth["/auth 로그인·가입"]
    Discover -->|로그인 필요 액션| Auth
    Auth -->|카카오| KakaoCb["/auth/kakao/callback"]
    Auth -->|성공| Plan
    KakaoCb -->|성공| Plan["/plan 여행 작업실"]
    Plan -->|탐색 모드| PlanExplore["관광지 검색·후보군 추가"]
    Plan -->|정리 모드| PlanBoard["ScheduleBoard 드래그 타임라인"]
    Plan -->|공유| Share["ShareModal 링크·협업 초대"]
    Plan -->|지도| MapPanel["경로 지도 패널"]
    PlanBoard -->|이동시간 자동| Transit["TransitPill (ODsay/T Map)"]
""",
"flow2": """flowchart TD
    GNB{{"GNB: 여행 작업실 · 여행 이야기 · 마이페이지"}}
    GNB --> Plan["/plan 여행 작업실"]
    GNB --> Community["/community 여행 이야기"]
    GNB --> MyPage["/mypage 마이페이지"]
    GNB -.관리자.-> Admin["/admin TourAPI 동기화"]
    Community --> PostDetail["/community/:id 상세"]
    PostDetail -->|좋아요·북마크·댓글| PostDetail
    Community -->|글쓰기| Write["/community/write"]
    PostDetail -->|수정-작성자만| Write
    MyPage --> Trips["여행 목록"]
    MyPage --> Profile["내 정보"]
    MyPage --> Places["내 장소"]
    MyPage --> VMap["방문 지도"]
    Trips -->|새 일정/선택| Plan
""",
"route_api": """flowchart TD
    FE["프론트<br/>ScheduleBoard.vue"] -->|"GET /api/transit"| CTL[TransitController]
    CTL --> SVC[TransitServiceImpl]
    SVC -->|"PUBLIC_TRANSIT"| ENR["enrichPublicTransitLive<br/>(경로 합성)"]
    SVC -->|"DRIVING / WALKING"| TMAP["TMapClient<br/>fetchTaxiRoute(4옵션)"]
    ENR -->|"searchPubTransPathT"| ODSAY1["findTransitPath"]
    ENR -->|"접근구간 보강"| ODSAY2["findLocalPath"]
    ENR -->|"노선 폴리라인"| ODSAY3["loadLane"]
    SVC --> RESP["TransitResponse<br/>(routeCoords, 요약)"]
    RESP --> DRAW["Naver Maps 렌더"]
""",
"route_build": """flowchart TD
    A["enrichPublicTransitLive"] --> B["findTransitPath"]
    B --> C{"pathType >= 11 ?"}
    C -->|"아니오 (시내)"| D["원본 경로 그대로"]
    C -->|"예 (도시간)"| E["출발역/도착역 좌표 추출"]
    E --> F["findLocalPath(출발지→출발역)"]
    E --> G["findLocalPath(도착역→목적지)"]
    F -->|"실패 -98"| F2["haversine 추정"]
    G -->|"실패 -98"| G2["haversine 추정"]
    F --> H["RouteEnrichment<br/>총분 = localFrom+inter+localTo"]
    G --> H
    D --> I["extractPublicTransitCoords (3단 폴백)"]
    H --> I
    I --> J["buildRouteSegments"]
""",
"route_cache": """flowchart TD
    REQ["경로 요청"] --> M3{"in-memory 좌표 캐시"}
    M3 -->|"hit"| OUT["TransitResponse 반환"]
    M3 -->|"miss"| DB1{"DB transit_cache"}
    DB1 -->|"hit"| OUT
    DB1 -->|"miss"| CALL["외부 호출"]
    CALL --> LANE{"loadLane"}
    LANE -->|"hit"| DB2[("DB lane_polyline")]
    LANE -->|"miss"| LANEAPI["ODsay loadLane"] --> DB2
    CALL --> LOCAL{"findLocalPath"}
    LOCAL --> M4[("localPathCache")]
    CALL --> STORE{"성공 결과?"}
    STORE -->|"성공"| WRITE["캐시 저장(mem+DB)"]
    STORE -->|"빈 응답"| SKIP["저장 안 함(재시도)"]
    WRITE --> OUT
    SKIP --> OUT
""",
"er": """erDiagram
    MEMBER ||--o{ MEMBER_TOKEN : "CASCADE"
    MEMBER ||--o{ TRIP : "CASCADE"
    MEMBER ||--o{ FAVORITE : "CASCADE"
    MEMBER ||--o{ POST : "SET NULL"
    SIDO ||--o{ SIGUNGU : "code"
    SIDO ||--o{ ATTRACTION : "code"
    ATTRACTION ||--o{ FAVORITE : "CASCADE"
    ATTRACTION ||--o{ TRIP_CANDIDATE : "nullable"
    TRIP ||--o{ TRIP_CANDIDATE : "CASCADE"
    TRIP ||--o{ TRIP_COLLABORATOR : "CASCADE"
    TRIP ||--o{ POST : "SET NULL"
    TRIP_CANDIDATE ||--o{ TRIP_BLOCK : "RESTRICT"
    POST ||--o{ POST_LIKE : "CASCADE"
    POST ||--o{ POST_COMMENT : "CASCADE"
    POST ||--o{ POST_BOOKMARK : "no-cascade"
    POST_COMMENT ||--o{ POST_COMMENT : "parent"
""",
"cls": """classDiagram
    direction LR
    class TripController
    class TransitController
    class TripService
    class TransitService
    class TripMapper
    class TransitCacheMapper
    class OdsayClient
    class TMapClient
    TripController --> TripService
    TransitController --> TransitService
    TripService --> TripMapper
    TransitService --> TransitCacheMapper
    TransitService --> OdsayClient
    TransitService --> TMapClient
""",
"usecase": """flowchart LR
    Member([회원])
    subgraph 일정["여행 일정"]
        UC9(일정 생성)
        UC11(드래그 편집)
        UC12(이동시간 자동계산)
        UC14(공유·협업)
    end
    subgraph 탐색["관광지"]
        UC5(검색·필터)
        UC8(AI 챗봇)
    end
    Member --> UC5
    Member --> UC8
    Member --> UC9
    Member --> UC11
    Member --> UC12
    Member --> UC14
""",
}
