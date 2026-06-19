# chat.md — 관광지 AI 챗봇·주변 추천

> **수정 규칙**: 수정 전 변경 내용을 사용자에게 설명하고 승인을 받을 것.

관광지 상세에서 자연어로 묻고, 주변 장소 추천까지 받는 대화형 도우미.
Spring AI + gms(OpenAI 호환 프록시) 기반.

## 패키지 위치

```
com.tripcraft.chat/
├── config/ChatClientConfig.java          # ChatClient + MessageChatMemoryAdvisor
├── controller/AttractionChatController.java
├── service/AttractionChatService.java / AttractionChatServiceImpl.java
└── dto/AttractionChatRequest.java / AttractionChatResponse.java
```

주변 검색은 attraction 도메인에 위치:
`AttractionService.findNearby` · `AttractionMapper.findNearby` · `dto/NearbyAttraction`

## 엔드포인트

```
POST /api/attractions/{id}/chat   { message, conversationId? }
  → { reply, conversationId, nearby[] }
```

- **인증 필요** — AI 토큰 비용 때문에 회원만 호출. SecurityConfig는 `/api/attractions/**` GET만 permitAll이라 POST는 자동으로 인증 대상.
- 공개로 바꾸려면 `.requestMatchers(HttpMethod.POST, "/api/attractions/*/chat").permitAll()` 추가.

## Spring AI 설정

- 의존성: `spring-ai-starter-model-openai` + BOM `spring-ai-bom:1.0.6` (build.gradle.kts)
- `application.yml`: `spring.ai.openai.api-key=${GMS_KEY}`, `base-url=https://gms.ssafy.io/gmsapi/api.openai.com`, `chat.options.model=gpt-4.1`
- 프록시가 경로를 그대로 포워딩하므로 Spring AI 기본 `/v1/chat/completions` 사용 가능
- 키는 `.env`의 `GMS_KEY` (spring-dotenv 로드). 없으면 기동 실패.

## 멀티턴

- `MessageChatMemoryAdvisor` + 자동 구성 `ChatMemory`(InMemory, 기본 20메시지)
- 호출 시 `ChatMemory.CONVERSATION_ID` 파라미터로 대화 식별. 미지정 시 서버가 UUID 발급해 응답으로 반환
- **인메모리** — 서버 재시작 시 대화 소멸

## 컨텍스트 주입

`buildSystemPrompt(detail, nearby)` — `AttractionDetailDto`를 한국어 시스템 프롬프트로 직렬화:
- 이름·분류·지역·주소·전화·홈페이지·이용정보(intro)·추가정보(infoList)·소개(overview), HTML 태그 제거
- `[주변 장소]` 섹션: 좌표 기준 반경 **3km·최대 8곳**을 거리순으로
- 지침: 주변 질문은 목록만 활용, 목록에 없는 곳은 지어내지 말 것

## 주변 검색 (attraction 도메인)

- `AttractionMapper.findNearby` — bounding box 1차 필터(위도 1°≈111km, 경도는 cos 보정) + `ST_Distance_Sphere(POINT(lng,lat), ...)` 거리순 정렬
- `NearbyAttraction`: id·title·contentTypeId·category·addr1·latitude·longitude·distanceM
- 응답 `nearby[]`는 프롬프트에 넣은 목록 그대로. 프론트가 거리 버튼으로 활용.

## 프론트 (Vue)

- `components/AttractionChat.vue` — 상세 패널 내 접이식 챗 UI. ExploreView 상세 패널 하단에 임베드
- `stores/attractionChat.js` — **관광지별 대화(messages·conversationId) + 아코디언 open 상태 보존**. 패널 리마운트/네비게이션에도 유지
- 답변에 **실제 언급된 주변 장소만** 버튼으로 노출(`reply.includes(title)`), 메시지별로 표시
- 버튼 클릭 → ExploreView `pinNearby`(지도 핀 + "상세정보 보기" 인포윈도우). **현재 패널은 유지**(사이드바 초기화 방지)
- 인포윈도우 "상세정보 보기" → `goToNearbyDetail`: 현재 장소를 `detailStack`에 push 후 이동
- **뒤로가기**(`goBackDetail`): pop → 이전 장소·대화·스크롤 위치 복원. `detailCache`로 재조회 없이 즉시
- 전환 애니메이션: 앞으로는 패널 유지 + 새 내용만 좌측 슬라이드 인(`detail-push-anim`), 뒤로는 즉시(애니메이션 없음)

## 주의

- 프롬프트 비용/지연 — 주변 8곳 + 상세 정보가 매 호출마다 시스템 프롬프트로 들어감
- `gms` 프록시 base-url은 SSAFY 환경 전용. 다른 OpenAI 호환 서비스로 교체 시 base-url/모델만 변경
