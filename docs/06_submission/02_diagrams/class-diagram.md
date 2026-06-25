# 클래스 다이어그램 — TripCraft (백엔드)

> **기준**: `backend/src/main/java/com/tripcraft` · Spring Boot 3.5 · MyBatis
> 도메인 중심 패키지 구조(member · attraction · plan · community · place · chat · global).
> 계층: **Controller → Service(Impl) → Mapper(MyBatis) → DB**, 외부 연동은 **Client** 클래스로 분리.

---

## 1. 계층형 아키텍처 (대표 도메인)

```mermaid
classDiagram
    direction LR

    class AuthController
    class MemberController
    class AttractionController
    class AttractionChatController
    class TripController
    class TransitController
    class PostController
    class NoticeController

    class AuthService
    class MemberService
    class AttractionService
    class AttractionChatService
    class TripService
    class TransitService
    class PostService

    class MemberMapper
    class AttractionMapper
    class TripMapper
    class TripBlockMapper
    class TransitCacheMapper
    class PostMapper

    class TourApiClient
    class OdsayClient
    class TMapClient
    class KakaoLocalClient

    AuthController --> AuthService
    MemberController --> MemberService
    AttractionController --> AttractionService
    AttractionChatController --> AttractionChatService
    TripController --> TripService
    TransitController --> TransitService
    PostController --> PostService

    AuthService --> MemberMapper
    MemberService --> MemberMapper
    AttractionService --> AttractionMapper
    AttractionService --> TourApiClient
    TripService --> TripMapper
    TripService --> TripBlockMapper
    TransitService --> TransitCacheMapper
    TransitService --> OdsayClient
    TransitService --> TMapClient
    PostService --> PostMapper

    AttractionChatService --> "Spring AI ChatClient" ChatClient
```

---

## 2. 도메인 모델 (엔티티 관계)

```mermaid
classDiagram
    class Member {
        +Long id
        +String email
        +String password
        +String nickname
        +Role role
        +String socialProvider
        +String socialId
    }
    class Trip {
        +Long id
        +Long memberId
        +String title
        +LocalDate startDate
        +LocalDate endDate
        +Integer memberCount
        +String defaultTransitMode
        +Boolean isPublic
        +ShareAccess shareAccess
        +String shareToken
    }
    class TripCandidate {
        +Long id
        +Long tripId
        +Long attractionId
        +String source
        +String placeName
        +BigDecimal placeLat
        +BigDecimal placeLng
    }
    class TripBlock {
        +Long id
        +Long candidateId
        +LocalDate tripDate
        +Integer displayOrder
        +LocalTime startTime
        +Integer durationMinutes
        +Integer transitDurationMinutes
        +String transitMode
        +Integer transitOptionIndex
        +Integer version
    }
    class TripCollaborator {
        +Long id
        +Long tripId
        +Long memberId
        +TripRole role
    }
    class Attraction {
        +Long id
        +String contentId
        +Integer contentTypeId
        +String title
        +Integer sidoCode
        +Integer sigunguCode
        +BigDecimal latitude
        +BigDecimal longitude
    }
    class Favorite {
        +Long id
        +Long memberId
        +Long attractionId
    }
    class MemberPlace {
        +Long id
        +Long memberId
        +String name
        +String category
    }
    class TransitCache {
        +Long id
        +String routeKey
        +Integer departureHour
        +String requestMode
        +Integer durationMinutes
        +Integer fare
        +Integer taxiFare
    }
    class Post {
        +Long id
        +Long memberId
        +Long tripId
        +String title
        +String content
        +Integer viewCount
        +Integer likeCount
        +LocalDateTime deletedAt
    }
    class PostComment {
        +Long id
        +Long postId
        +Long memberId
        +Long parentId
        +String content
    }
    class PostLike {
        +Long id
        +Long postId
        +Long memberId
    }
    class Notice {
        +Long id
        +Long memberId
        +String title
        +String content
    }

    Member "1" --> "0..*" Trip : owns
    Member "1" --> "0..*" Favorite
    Member "1" --> "0..*" MemberPlace
    Member "1" --> "0..*" TripCollaborator
    Member "1" --> "0..*" Post : author
    Trip "1" --> "0..*" TripCandidate
    Trip "1" --> "0..*" TripCollaborator
    Trip "1" --> "0..1" Post : shared
    TripCandidate "1" --> "0..*" TripBlock
    Attraction "1" --> "0..*" Favorite
    Attraction "1" --> "0..*" TripCandidate
    Post "1" --> "0..*" PostComment
    Post "1" --> "0..*" PostLike
    PostComment "1" --> "0..*" PostComment : parent
    Member "1" --> "0..*" Notice : admin
```

---

## 3. 패키지·책임 요약

| 패키지 | Controller | Service | 주요 Client/Config |
|--------|-----------|---------|-------------------|
| `member` | AuthController, MemberController | AuthService, MemberService, MemberMapService | (Kakao OAuth) |
| `attraction` | AttractionController, AttractionSyncController | AttractionService | TourApiClient, TourApiSyncJobConfig, TourApiCallLimiter |
| `chat` | AttractionChatController | AttractionChatService | ChatClientConfig (Spring AI / gms) |
| `plan` | TripController, TransitController, TripPresenceController | TripService, TransitService | OdsayClient, TMapClient |
| `community` | PostController, CommentController, LikeController, BookmarkController, NoticeController | PostService, CommentService, LikeService, BookmarkService | PostImageCleanupListener |
| `place` | MyPlaceController, PlaceController | MemberPlaceService, PlaceService | KakaoLocalClient |
| `global` | ImageController | FileStorageService, OrphanImageCleanupScheduler | SecurityConfig, WebSocketConfig, JwtTokenProvider, GlobalExceptionHandler, ApiResponse |

> 공통 응답은 `ApiResponse<T>`(`{success, data, message, errorCode}`)로 래핑. 인증은 `JwtAuthenticationFilter` + `JwtTokenProvider`, 실시간 협업은 `WebSocketConfig` + STOMP(`TripPresenceController`) 기반.
