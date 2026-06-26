# -*- coding: utf-8 -*-
"""TripCraft 발표 50장 콘텐츠 (build.py 가 소비)."""

SLIDES = [
    # 1
    {"type": "cover", "sub": "여행을 설계하는 가장 스마트한 방법 — 탐색·일정·이동시간·협업·공유까지",
     "team": "전진 · 송정기", "date": "2026.05 ~ 2026.06 (약 6주)"},
    # 2
    {"type": "toc", "items": [
        "**1.** 기획 배경 · 목표", "**2.** 추진 계획", "**3.** 시장 분석",
        "**4.** 개발 결과 — 핵심 기술 · 구현", "**5.** 개발 환경 & 시스템 구조도",
        "**6.** 화면 흐름도 및 시연", "**7.** 적용 패턴 및 핵심 알고리즘",
        "**8.** 기대 효과", "**9.** 개발 후기", "**부록** A. AI 사용 보고서 · B. 설계 산출물"]},
    # 3
    {"type": "divider", "num": "01", "title": "기획 배경 · 목표"},
    # 4
    {"type": "bullets", "title": "문제 인식",
     "lead": "국내 여행 계획 시 사용자는 **최소 4~5개 서비스를 동시에** 열어두고 작업한다.",
     "items": [
         "관광지는 **포털 지도**로 검색, 운영시간·예약은 **개별 사이트**에서 확인",
         "장소 간 이동수단·소요시간은 **대중교통 앱**으로 따로 계산",
         "숙소는 **예약 플랫폼**, 일행 조율은 **메신저·노션·공유 메모**를 오감",
         "→ 계획 세우기 자체가 피로, 현장에서 이동시간이 어긋나면 **동선 전체가 붕괴**"]},
    # 5
    {"type": "table", "title": "핵심 불편함",
     "headers": ["불편", "내용"], "widths": [1, 3], "rows": [
         ["정보의 분산", "관광지·이동·숙소가 서로 다른 서비스에 흩어져 통합 뷰 없음"],
         ["수동 이동시간 계산", "장소가 바뀔 때마다 직접 검색해 수기 반영"],
         ["일정의 경직성", "시간·장소를 옮겨도 자동 재배치·재계산해 주는 도구 부재"],
         ["협업의 불편함", "범용 도구는 여행 일정 특유의 시간·장소 구조·실시간 논의에 부적합"]]},
    # 6
    {"type": "table", "title": "목표 — \"한 화면에서 끝나는 여행 일정 도구\"",
     "lead": "탐색 → 후보군 → **드래그앤드롭 일정 확정** → 구간 이동시간 자동 계산 → 커뮤니티 공유를 한 흐름으로.",
     "headers": ["핵심 가치", "설명"], "widths": [1, 3], "rows": [
         ["통합", "탐색–후보군–타임라인–공유를 한 플로우로"],
         ["자동화", "장소 추가 시 도시 분류·이동시간·경로 연동 자동 처리"],
         ["유연성", "후보군을 먼저 모으고 날짜·시간은 나중에 정하는 2단계 계획"],
         ["협업", "여행 일정 특화 UI로 일행과 실시간 공동 편집"]]},
    # 7
    {"type": "cards", "title": "추가 · 차별화 기능", "cards": [
        ("실시간 공동 편집", "WebSocket(STOMP) 동시 편집 + 낙관적 락 + 상대 커서 표시"),
        ("멀티모달 이동시간", "ODsay(대중교통) + T Map(자동차·도보·택시요금) 구간별 모드, 경로 지도 시각화"),
        ("관광지 AI 챗봇", "Spring AI 컨텍스트 Q&A + 반경 3km 주변 추천"),
        ("공유 링크 / 커뮤니티", "VIEW·EDIT 공유 링크, 일정 게시판 공유, 방문 지도 기록")]},
    # 8
    {"type": "bullets", "title": "목표 사용자", "items": [
        "국내 여행지를 직접 조사·설계하는 **자유여행 선호자**",
        "2인 이상이 함께 계획하는 **소그룹 여행자**",
        "촘촘한 일정보다 유연한 플로우를 선호하되 **기본 동선은 미리 잡아두고 싶은** 여행자"]},
    # 9
    {"type": "divider", "num": "02", "title": "추진 계획"},
    # 10
    {"type": "table", "title": "전체 일정 요약", "headers": ["단계", "기간", "주요 내용"],
     "widths": [1.4, 1, 4], "rows": [
         ["1 기획", "Week 1", "요구사항 정의·WBS·협업 환경 세팅"],
         ["2 설계", "Week 2", "ERD·API 명세·UI 와이어프레임·코딩 컨벤션"],
         ["3 개발", "Week 3~5", "회원 → 관광지·즐겨찾기 → 일정·이동시간"],
         ["4 커뮤니티·고도화", "Week 6~", "공유게시판·공지, 협업·공유링크, AI 챗봇, 마이페이지"],
         ["5 QA·정리", "막바지", "통합 테스트·버그 수정·보안 점검·산출물 정리"]]},
    # 11
    {"type": "diagram", "title": "간트 차트", "img": "gantt"},
    # 12
    {"type": "table", "title": "마일스톤", "headers": ["마일스톤", "목표", "완료 기준", "상태"],
     "widths": [1.5, 0.8, 3, 0.6], "rows": [
         ["M1 설계 완료", "W2", "ERD·API·와이어프레임 확정", "✅"],
         ["M2 회원+관광지", "W4", "로그인·관광지 조회·즐겨찾기 동작", "✅"],
         ["M3 일정 기능", "W5", "드래그 타임라인·이동시간·저장 동작", "✅"],
         ["M4 MVP+고도화", "W6", "커뮤니티·협업·AI 챗봇·마이페이지 동작", "✅"],
         ["M5 제출", "막바지", "QA·산출물·발표 준비 완료", "✅"]]},
    # 13
    {"type": "table", "title": "개인별 분담", "headers": ["주차", "전진", "송정기"],
     "widths": [0.7, 2.4, 2.4], "rows": [
         ["W1~2", "요구사항·유스케이스·API 명세", "ERD·스키마·컨벤션·환경 세팅"],
         ["W3", "회원·JWT·Spring Security", "관광지 TourAPI 수집·조회"],
         ["W4", "커뮤니티 게시판", "후보군 자동연동·탐색 UI·네이버맵"],
         ["W5", "마이페이지·드래그앤드롭", "타임라인·이동시간(ODsay/T Map)·경로 시각화"],
         ["W6~", "실시간 협업", "공유링크·AI 챗봇"]],
     "note": "핵심 화면(일정 편집)·협업은 공동 작업, 세부 분담은 Git/MR 이력 기준."},
    # 14
    {"type": "table", "title": "리스크 & 대응", "headers": ["리스크", "대응"],
     "widths": [1.5, 3], "rows": [
         ["TourAPI 할당량 초과", "초기 일괄 적재 후 자체 DB 서비스, call limiter"],
         ["ODsay/T Map 지연·쿼터", "좌표 기반 모드별 캐시 + 노선 폴리라인 영구 캐시"],
         ["드래그앤드롭 복잡도", "vuedraggable 활용"],
         ["동시 편집 충돌", "version 낙관적 락으로 후행 저장 거부·재동기화"],
         ["2인 일정 지연", "주 1회 동기화·우선순위 재조정"]]},
    {"type": "divider", "num": "03", "title": "시장 분석"},
    # 15
    {"type": "table", "title": "경쟁 서비스 비교",
     "lead": "탐색·길찾기는 잘하지만 **여러 장소를 하루 동선으로 엮고 이동시간 자동 반영 + 일행과 함께 편집**하는 흐름은 비어 있다.",
     "headers": ["항목", "네이버 여행", "구글 지도", "트리플", "TripCraft"],
     "widths": [1.6, 0.9, 0.9, 0.8, 1.0], "rows": [
         ["드래그앤드롭 일정", "△", "✕", "△", "◎"],
         ["구간 이동시간 자동", "✕", "◯", "△", "◎"],
         ["이동수단 구간별 선택", "✕", "△", "✕", "◎"],
         ["실시간 공동 편집", "✕", "✕", "✕", "◎"],
         ["AI 관광 챗봇", "✕", "✕", "△", "◎"],
         ["일정 커뮤니티 공유", "△", "✕", "◯", "◯"]],
     "note": "◎ 강점 · ◯ 지원 · △ 부분 · ✕ 미지원   |   핵심 공백 = 드래그 일정 + 구간별 이동수단 + 실시간 편집"},
    # 16
    {"type": "bullets", "title": "차별화 전략", "items": [
        "**통합 동선 설계** — 탐색·후보군·타임라인을 한 작업실에서, 서비스 전환 비용 제거",
        "**현실적 이동시간** — ODsay + T Map 구간별 모드로 실제 동선과 맞물린 일정",
        "**협업 우선** — 공유 링크 + 실시간 동시 편집(낙관적 락·상대 커서), 경쟁사 미제공 영역",
        "**AI 도우미** — 관광지 컨텍스트 Q&A + 반경 3km 주변 추천",
        "**기록의 자산화** — 커뮤니티 공유 + 방문 지도로 재사용 가능한 기록"]},
    # 17
    {"type": "divider", "num": "04", "title": "개발 결과 — 핵심 기술 · 구현"},
    # 18
    {"type": "bullets", "title": "회원 · 인증", "items": [
        "Spring Security + **JWT**(Access 30분 / Refresh 7일), **HttpOnly 쿠키** 발급·검증",
        "토큰 자동 재발급, **BCrypt** 비밀번호 해시",
        "**Kakao OAuth** 소셜 로그인, 회원가입 / 정보 수정 / 탈퇴"]},
    # 19
    {"type": "bullets", "title": "관광지 — TourAPI · 지도", "items": [
        "한국관광공사 **TourAPI 4.0** 전국 데이터 일괄 수집 → 자체 DB 서비스",
        "`api_modified_at` 기준 증분 동기화, 지역·카테고리·키워드 조회, 상세(소개·이미지·이용안내)",
        "**Naver Maps** 마커·InfoWindow 연동, 즐겨찾기 토글"]},
    # 20
    {"type": "bullets", "title": "일정 · 드래그앤드롭", "items": [
        "**Trip → Candidate → Block** 모델, **vuedraggable** 드래그앤드롭 타임라인",
        "Day 탭, **30분 스냅**, 사이드바 삭제존, 후보군 도시(시군구) 자동 분류",
        "즐겨찾기 자동 연동, 중복 방문 허용, 날짜 범위 이탈 **DB TRIGGER** 방어"]},
    # 21
    {"type": "bullets", "title": "이동시간 · 경로 시각화", "items": [
        "ODsay(대중교통) · T Map(자동차·도보) 연동, **구간별 모드** + 택시 예상요금",
        "좌표 기반 **모드별 캐시**로 무료 API 쿼터 보호",
        "대중교통 노선 폴리라인 + 환승/도보 구간 + 역 마커 지도 시각화"]},
    # 22
    {"type": "bullets", "title": "협업 · 커뮤니티", "items": [
        "**WebSocket(STOMP)** 실시간 협업 + 공유 링크(VIEW/EDIT)",
        "게시판(작성/목록/상세/수정/삭제, 소프트 딜리트)·공지·댓글/대댓글",
        "좋아요·북마크, 방문 지도로 기록 시각화"]},
    # 23
    {"type": "bullets", "title": "AI 챗봇 · 마이페이지", "items": [
        "**Spring AI + gms(gpt-4.1)** 관광지 컨텍스트 Q&A(멀티턴) + 반경 3km 주변 추천",
        "응답 언급 장소만 버튼화 → 지도 핀·상세 이동, 뒤로가기 복원",
        "마이페이지 **7탭**(여행·내정보·내장소·방문지도·내가쓴글·북마크·좋아요)"]},
    # 24
    {"type": "divider", "num": "05", "title": "개발 환경 & 시스템 구조도"},
    # 25
    {"type": "table", "title": "기술 스택", "headers": ["Layer", "기술"], "widths": [1, 4], "rows": [
        ["Backend", "Java 21 · Spring Boot 3.5 · Security/Batch/AI · MyBatis · MySQL 8 · Gradle"],
        ["Frontend", "Vue 3 + Vite · Pinia · Vue Router · vuedraggable · Tiptap"],
        ["인증", "JWT(30분/7일) · BCrypt · Kakao OAuth"],
        ["실시간", "WebSocket + STOMP + SockJS"],
        ["외부 API", "TourAPI · ODsay · T Map · Naver Maps · Kakao · gms(gpt-4.1)"]]},
    # 26
    {"type": "diagram", "title": "전체 시스템 구조도", "img": "arch"},
    # 27
    {"type": "bullets", "title": "인증 흐름 · 공통 응답 · 보안", "items": [
        "로그인 → `access_token`·`refresh_token` **HttpOnly 쿠키** → JwtAuthenticationFilter 매 요청 검증 → 만료 시 `/api/auth/refresh`",
        "공통 응답 `ApiResponse { success, data, message, errorCode }`",
        "서버 측 소유권/역할 검증, MyBatis `#{}` 바인딩, API 키 환경변수 주입"]},
    # 28
    {"type": "divider", "num": "06", "title": "화면 흐름도 및 시연"},
    # 29
    {"type": "diagram", "title": "화면 흐름도 ① 진입 · 작업실", "img": "flow1"},
    # 30
    {"type": "diagram", "title": "화면 흐름도 ② GNB · 커뮤니티 · 마이페이지", "img": "flow2"},
    # 31
    {"type": "placeholder", "title": "핵심 5화면 와이어프레임",
     "boxes": ["M. 일정 생성 모달", "A. 관광지 탐색", "B. 일정 작업실", "C. 커뮤니티", "D. 로그인/회원가입"],
     "note": "설계문서/wireframes/screen-M·A·B·C·D.svg 삽입"},
    # 32
    {"type": "placeholder", "title": "🎬 시연 영상", "boxes": ["시연 영상 (약 3분)\n[영상 임베드 / 링크]"],
     "note": "흐름: 탐색 → 상세·AI 챗봇·담기 → 드래그 일정·지도·이동수단·내 장소 → 실시간 협업·읽기전용 공유 → 여행 이야기·가져오기 → 글쓰기·수정 → 방문 지도 → 마이페이지 → 클로징"},
    # 33
    {"type": "divider", "num": "07", "title": "적용 패턴 및 핵심 알고리즘"},
    # 34
    {"type": "table", "title": "[경로] 왜 무료 API 조합인가",
     "lead": "유료 길찾기 API 한 호출이면 끝나지만 **비용 회피**를 위해 무료 조합을 택했다.",
     "headers": ["접근", "API", "비용", "기술 부담"], "widths": [1, 1.8, 0.8, 2.2], "rows": [
         ["유료 단일", "Kakao Mobility 길찾기", "호출당 과금", "거의 없음(응답 그대로)"],
         ["무료 조합(채택)", "ODsay + T Map + Naver", "무료", "응답 직접 합성·파싱 + 캐시로 호출제한 방어"]],
     "note": "무료의 대가 = 합성(조립)과 다층 캐싱 — 다음 슬라이드의 두 축"},
    # 35
    {"type": "diagram", "title": "[경로] API 오케스트레이션", "img": "route_api",
     "note": "도시간 경로는 '역→역'만 줘서 접근 구간을 추가 호출로 합성, 지도 선은 loadLane 별도 호출."},
    # 36
    {"type": "diagram", "title": "[경로] 경로 합성 · 보강 (조립)", "img": "route_build",
     "note": "[출발지→역]+[KTX]+[역→목적지] 합성, 실패 시 하버사인 추정 폴백, 3단 폴리라인 폴백."},
    # 37
    {"type": "diagram", "title": "[경로] 다층 캐싱 (핵심)", "img": "route_cache",
     "note": "DB transit_cache·lane_polyline + in-memory 좌표·로컬경로. 무료 API 빈 응답(8연타 실험) → 성공만 캐시."},
    # 38
    {"type": "bullets", "title": "드래그 타임라인", "items": [
        "후보군 → Day 그리드 드롭으로 블록 생성, **30분 스냅**, 사이드바 삭제존",
        "`display_order`로 날짜 내 순서 관리, 중복 방문 허용",
        "날짜 범위 이탈은 DB **TRIGGER**(trg_trip_block_date_*)로 방어"]},
    # 39
    {"type": "ascii", "title": "[협업] 2채널 분리 설계",
     "items": [
         "**편집(영속)** = REST + DB 트랜잭션 + 낙관적 락 → STOMP '변경됨' broadcast",
         "**presence(커서·휘발)** = DB 없이 in-memory(ConcurrentHashMap), 고빈도라 가볍게"],
     "ascii": "[사용자 A]            [Spring 서버]                  [사용자 B]\n 드래그 ─REST─▶ TripServiceImpl(@Transactional)\n                ├ 낙관적 락(version)·grab·겹침 검사\n                ├ DB 반영\n                └ broadcast(TripEvent, seq) ─STOMP─▶ /topic/trip/{id} ─▶ loadTrip\n 마우스 ─STOMP─▶ /app/trip/{id}/pointer\n                TripPresenceController(in-memory)\n                └ broadcastPresence ─STOMP─▶ /topic/trip/{id}/presence ─▶ 커서 렌더"},
    # 40
    {"type": "bullets", "title": "[협업] WebSocket(STOMP) 실시간 적용", "items": [
        "폴링·SSE 대비 **양방향·저지연** WebSocket 채택, SockJS 폴백",
        "STOMP 토픽 pub/sub·채널 분리(`/topic/trip/{id}`, `/presence`)",
        "쿠키 핸드셰이크 인증 + 권한 캐시(`TripAccessVersion`), **seq**로 순서·중복 방어",
        "이동시간 재계산은 `afterCommit`으로 분리해 외부 지연 동안 DB 커넥션 미점유"]},
    # 41
    {"type": "table", "title": "[협업] 낙관적 락 충돌 매트릭스",
     "lead": "`trip_block.version` 조건부 UPDATE → 0행이면 **409** → 재조회·재시도.",
     "headers": ["시나리오", "메커니즘", "결과"], "widths": [2.2, 1.6, 1.4], "rows": [
         ["같은 블록 동시 이동", "version", "둘째 409"],
         ["드래그 중 타인 수정", "grab 게이트", "즉시 409"],
         ["서로 다른 블록", "독립 version", "충돌 없음"],
         ["transit 재계산 ↔ 편집", "version 불변 UPDATE", "충돌 없음"],
         ["같은 시간대 겹침", "trip 행 FOR UPDATE", "둘째 409"]],
     "note": "핵심 불변식: version은 '같은 row'에만 작용 → 무관 편집·재계산은 오탐 없음."},
    # 42
    {"type": "bullets", "title": "[협업] 상대 좌표 커서 동기화", "items": [
        "절대 픽셀은 창 크기·스크롤·패널 폭이 달라 **엉뚱한 곳에** 찍힘",
        "→ **zone + 의미 좌표**(timetable: dayIndex + colRatioX + contentY)로 송수신",
        "수신측이 자기 DOM 기준 역환산, 적응형 throttle(AIMD)·보간으로 백로그 제거"]},
    # 43
    {"type": "twocol", "title": "AI 주변 추천 · 데이터 보존 정책",
     "left": {"head": "AI 주변 추천", "items": [
         "`ST_Distance_Sphere`로 반경 3km 거리순 8곳 조회 → 프롬프트 주입",
         "응답 언급 장소만 버튼화 → 지도 핀·상세 이동",
         "뒤로가기 시 대화·스크롤 복원"]},
     "right": {"head": "데이터 보존", "items": [
         "작성자/일정 삭제 시 게시글·공지 **SET NULL** 보존",
         "후보군→블록 **RESTRICT**(모달 확인 UX)",
         "글 **소프트 딜리트** + 북마크 보존"]}},
    # 44
    {"type": "bullets", "sec": "8", "title": "기대 효과", "items": [
        "**여행 준비 시간 단축** — 탐색·동선·이동시간을 한 화면에서, 앱 전환 비용 제거",
        "**현실적인 일정** — 구간별 실제 이동시간·수단으로 무리 없는 동선",
        "**함께 만드는 여행** — 실시간 협업·공유로 일행과 합의된 일정",
        "**경험의 축적** — 여행 이야기·방문 지도로 기록 자산화",
        "**확장성** — 도메인 중심 설계 + 캐시 전략으로 기능·트래픽 확장 용이"]},
    # 45
    {"type": "placeholder", "sec": "9", "title": "개발 후기 — 팀", "boxes": ["팀 사진\n[사진 삽입]"],
     "note": "팀 한 줄 소감을 사진 아래에 추가하세요."},
    # 46
    {"type": "twocol", "title": "개발 후기 — 개인 회고",
     "left": {"head": "전진", "items": ["[회고 작성]", "배운 점 · 어려웠던 점 · 다음에 시도할 것"]},
     "right": {"head": "송정기", "items": [
         "**배운 점** — 외부 API를 '쓰는 것'과 그것으로 '제품을 만드는 것'은 다르다. 경로 합성·다층 캐시로 제약 안에서 안정화하는 법을 체감.",
         "**어려웠던 점** — 무캐시일 때만 간헐 발생하는 rate limit 버그. 재현이 까다로워 같은 좌표 반복 호출로 원인 규명.",
         "**다음 시도** — 외부 의존 로직에 관측 지표(로깅·캐시 적중률) 내장, in-memory 캐시를 DB/Redis로 승격."]}},
    {"type": "divider", "num": "A", "title": "부록 A. AI 사용 보고서"},
    # 부록 A ①
    {"type": "twocol", "title": "활용 도구 & 원칙",
     "left": {"head": "활용 도구", "items": [
         "**Claude Code**(CLI 에이전트) — 코드 생성·리팩터링·디버깅, 문서 작성, 코드베이스 탐색",
         "**AI 챗봇**(서비스 기능) — 제품 내 관광지 Q&A, Spring AI + gms(gpt-4.1)"]},
     "right": {"head": "원칙", "items": [
         "AI 산출물은 **반드시 사람이 리뷰·검증** 후 반영(빌드·테스트 통과)",
         "보안·권한은 서버 검증 직접 점검, API 키는 환경변수 분리",
         "설계 결정(스키마·삭제정책·캐시)은 팀 합의 후 구현 위임"]}},
    # 48 — 부록 A ②
    {"type": "table", "title": "기능별 활용",
     "headers": ["영역", "AI 활용 내용"], "widths": [1.2, 4], "rows": [
         ["DB 설계", "ERD/스키마 초안 검토, FK 삭제 정책 트레이드오프 정리, 마이그레이션 SQL"],
         ["백엔드", "MyBatis 매퍼·DTO·Service 보일러플레이트, 공통 응답 패턴, 외부 API 클라이언트 연동"],
         ["이동시간 캐시", "좌표 기반 캐시 키·모드별 캐시 설계 리뷰, 정밀도 단계 로직 정리"],
         ["프론트엔드", "Vue 3 컴포넌트(드래그·모달·에디터) 스캐폴딩, Pinia 스토어, 라우터 가드"],
         ["실시간 협업", "WebSocket(STOMP) 구독/발행 구조, 낙관적 락 충돌 처리 설계 논의"],
         ["AI 챗봇", "Spring AI ChatClient·ChatMemory 설정, 주변 추천 프롬프트·컨텍스트 주입"],
         ["문서화", "요구사항·WBS·Mermaid 다이어그램·발표자료 초안 및 코드 정합성 점검"]]},
    # 49 — 부록 A ③
    {"type": "bullets", "title": "대표 프롬프트 유형", "items": [
        "**설계 검토형** — \"trip_block↔trip_candidate FK를 RESTRICT vs CASCADE로 둘 때 UX·정합성 차이를 정리해줘.\"",
        "**구현 위임형** — \"ODsay 응답을 좌표 route_key로 캐싱하고 미스일 때만 호출하는 TransitService를 모드별 독립 캐시로 작성해줘.\"",
        "**디버깅형** — \"프로필 이미지 조회 N+1 원인과 수정안을 매퍼 쿼리 기준으로 제시해줘.\"",
        "**리팩터링형** — \"죽은 transit 엔드포인트/메서드를 찾아 정리하고 자동차 옵션을 단일화해줘.\"",
        "**문서화형** — \"schema.sql 기준 Mermaid ER을 만들고 모든 FK 삭제 정책을 라벨에 표기해줘.\""]},
    # 50 — 부록 A ④
    {"type": "twocol", "title": "효과 · 한계",
     "left": {"head": "효과", "items": [
         "**반복 작업 가속** — 매퍼·DTO·컴포넌트 보일러플레이트 시간 단축",
         "**설계 품질** — 삭제 정책·캐시 전략 트레이드오프 빠르게 비교·문서화",
         "**탐색 효율** — 대규모 코드베이스에서 관련 파일·엔드포인트 신속 파악",
         "**문서 정합성** — 코드 기준 다이어그램·명세 생성으로 문서-구현 괴리 최소화"]},
     "right": {"head": "한계 → 대응", "items": [
         "외부 API 응답 불확실 → 실제 호출 결과로 검증·매핑 보정",
         "컨텍스트 한계 일관성 누락 → 사람이 도메인·권한 최종 점검",
         "그럴듯하나 틀린 코드 → 빌드·테스트·코드리뷰 게이트 필수",
         "보안 민감 로직 → 그대로 신뢰 않고 직접 검증·일괄 보안 점검"]}},
    {"type": "divider", "num": "B", "title": "부록 B. 설계 산출물"},
    # 부록 B①
    {"type": "diagram", "title": "ER 다이어그램", "img": "er",
     "note": "핵심 관계만 표기. 도메인별 상세 ERD·컬럼·삭제정책은 설계문서/04 참조."},
    # 부록 B②. 클래스 다이어그램
    {"type": "diagram", "title": "클래스 다이어그램 (발췌)", "img": "cls",
     "note": "Controller → Service → Mapper/Client 계층(이동·일정 도메인 발췌). 전체 도메인 모델은 설계문서/03 참조."},
    # 부록 B③. 유스케이스 다이어그램
    {"type": "diagram", "title": "유스케이스 다이어그램 (발췌)", "img": "usecase",
     "note": "회원 중심 핵심 유스케이스. 비회원·관리자·외부 API 포함 전체는 설계문서/02 참조."},
    # 부록 B④. API 명세 — 공통 규약
    {"type": "bullets", "title": "API 명세 — 공통 규약", "items": [
        "**공통 응답** — `ApiResponse { success, data, message, errorCode }` (NON_NULL 직렬화)",
        "**인증** — JWT **HttpOnly 쿠키**, JwtAuthenticationFilter 검증, 만료 시 `POST /api/auth/refresh` 재발급",
        "**권한 레벨** — 공개 / 인증 / 인증*(내 데이터) / ADMIN (`SecurityConfig` 기준)",
        "**에러** — GlobalExceptionHandler → 401·403·400·409·500, `errorCode`로 구분"],
     "note": "예) GET /api/transit → { durationMinutes:38, transportMode:\"SUBWAY,BUS\", transferCount:1, fare:1500, label:\"추천\" }"},
    # 부록 B⑤. API 엔드포인트 카탈로그
    {"type": "table", "title": "API 엔드포인트 카탈로그",
     "lead": "REST 약 **72개** + WebSocket(STOMP) 실시간 협업 채널.",
     "headers": ["도메인", "수", "대표 엔드포인트"], "widths": [1.7, 0.5, 4.3], "rows": [
         ["인증·회원", "14", "auth/signup·login·kakao·refresh, members 닉네임/비번/프로필·탈퇴"],
         ["관광지·AI 챗봇", "4", "attractions/regions·검색·{id}, {id}/chat"],
         ["장소·내 장소", "4", "places/search, my-places CRUD"],
         ["여행 일정", "20", "trips CRUD·공유·복제, 협업자, 후보군, 타임라인 블록"],
         ["이동 시간", "11", "transit·by-coords·detail·route-segments·select (ODsay·T Map)"],
         ["커뮤니티", "13", "posts·comments·likes·bookmarks"],
         ["공지·이미지·관리자", "6", "notices, images/upload, admin/attractions/sync"],
         ["실시간(WebSocket)", "STOMP", "/ws, SEND /app/trip/{id}/pointer, SUB /topic/trip/{id}·/presence"]],
     "note": "권한: 대부분 인증, 공개(관광지·게시글 조회·공유 미리보기), ADMIN(/admin, 공지 CUD)."},
    # 50
    {"type": "closing", "sub": "TripCraft — 계획부터 공유까지 함께하는 여행 플래너 · 전진 · 송정기"},
]
