# 카카오 연동 (소셜 로그인 · 일정 공유) 구현 계획

> **작성일**: 2026-06-22  
> **작성자**: 전진 (문서 분리·재구성: 송정기, 2026-06-22)  
> **관련 기능**: 카카오 OAuth 로그인 + 카카오 일정 공유하기 — 원본 `collab_plan.md`에서 분리
>
> 카카오 공유는 [`share_plan.md`](share_plan.md)의 `share_token` 발급이 전제다.

---

## 목차

1. [카카오 로그인 (소셜 OAuth)](#1-카카오-로그인-소셜-oauth)
2. [카카오 일정 공유하기](#2-카카오-일정-공유하기)

---

## 1. 카카오 로그인 (소셜 OAuth)

### 1-1. 개념

카카오 OAuth 2.0 Authorization Code Flow를 사용해 이메일·비밀번호 없이 로그인할 수 있다.  
기존 이메일 회원과 **이메일 기반 연결** 정책을 사용한다.

```
카카오 로그인 흐름:

프론트: [카카오로 로그인] 클릭
        → 카카오 로그인 페이지 리다이렉트 (Kakao SDK 또는 직접 URL)
        → 카카오: 인가 코드(code) 발급 → 콜백 URL로 리다이렉트
백엔드: GET /api/auth/kakao/callback?code={인가코드}
        → 카카오 서버: code → access_token 교환
        → 카카오 서버: access_token → 사용자 정보(이메일, 닉네임, 프로필 이미지) 조회
        → 이메일로 기존 회원 검색
           ├── 기존 회원 있음 → social_id 업데이트 후 JWT 발급
           └── 신규 회원 → 자동 회원가입 후 JWT 발급
        → 프론트로 Access Token 전달 (기존 로그인과 동일 방식)
```

### 1-2. 백엔드 구현

**추가할 API**

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `GET` | `/api/auth/kakao/callback` | 카카오 인가 코드 수신 → JWT 발급 |

> **리다이렉트 처리**: 프론트엔드에서 카카오 OAuth를 직접 처리하면 CORS 문제가 없다. 프론트가 카카오로부터 `code`를 받아 백엔드로 전달하는 방식을 사용한다.

**실제 흐름 (프론트 주도)**
```
프론트: Kakao SDK → 인가 코드(code) 수신
프론트: POST /api/auth/kakao  { code: "..." }   ← 백엔드에 code 전달
백엔드: code → 카카오 서버에서 access_token 교환 (RestTemplate / WebClient)
백엔드: access_token → 카카오 사용자 정보 조회
백엔드: 이메일 매핑 → 회원 조회/생성 → JWT 발급 → 응답
```

**추가할 API (수정)**

| 메서드 | 경로 | 설명 |
|--------|------|------|
| `POST` | `/api/auth/kakao` | 프론트로부터 카카오 code 수신 → JWT 발급 |

**`member` 테이블 컬럼 추가**

```sql
ALTER TABLE member
    ADD COLUMN social_provider VARCHAR(20)  NULL COMMENT '소셜 로그인 제공자 (kakao 등)',
    ADD COLUMN social_id       VARCHAR(100) NULL COMMENT '소셜 제공자의 사용자 고유 ID';

-- 이메일이 없는 카카오 계정을 위해 password, email 컬럼 NULL 허용
ALTER TABLE member
    MODIFY COLUMN password VARCHAR(255) NULL COMMENT 'BCrypt 해시 (소셜 계정은 NULL)',
    MODIFY COLUMN email    VARCHAR(100) NULL;

CREATE UNIQUE INDEX uq_member_social ON member(social_provider, social_id);
```

> **이메일 NULL 허용 이유**: 카카오 사용자가 이메일 제공 동의를 거부할 수 있다. 이 경우 이메일 없이 `social_id`만으로 식별한다.

**카카오 서비스 클래스**

```java
// com.tripcraft.member.service.KakaoOAuthService
@Service
public class KakaoOAuthService {

    // 1. code → kakao access_token 교환
    public String getKakaoAccessToken(String code) { ... }

    // 2. kakao access_token → 사용자 정보 (이메일, 닉네임, socialId)
    public KakaoUserInfo getKakaoUserInfo(String kakaoAccessToken) { ... }

    // 3. 이메일 기반 기존 회원 연결 또는 신규 회원 생성
    public Member findOrCreateMember(KakaoUserInfo userInfo) {
        // 이메일로 기존 회원 조회
        // → 있으면: social_provider·social_id 업데이트 후 반환
        // → 없으면: 자동 회원가입 (비밀번호 NULL, social_provider='kakao')
    }
}
```

**패키지 위치**
```
com.tripcraft.member/
└── service/
    ├── KakaoOAuthService.java   ← 신규
    └── dto/KakaoUserInfo.java   ← 신규 (socialId, email, nickname)
```

**환경 변수 (application.yml)**
```yaml
kakao:
  client-id: ${KAKAO_CLIENT_ID}
  redirect-uri: ${KAKAO_REDIRECT_URI}   # 카카오 앱 설정과 일치해야 함
  token-url: https://kauth.kakao.com/oauth/token
  user-info-url: https://kapi.kakao.com/v2/user/me
```

**SecurityConfig 추가**
```java
.requestMatchers("/api/auth/kakao").permitAll()
```

### 1-3. 프론트엔드 구현

**Kakao SDK 초기화 (index.html 또는 main.js)**
```html
<script src="https://developers.kakao.com/sdk/js/kakao.js"></script>
```
```js
// main.js
window.Kakao.init(import.meta.env.VITE_KAKAO_APP_KEY)
```

**카카오 로그인 버튼 동작**
```js
// LoginView.vue
function kakaoLogin() {
  window.Kakao.Auth.authorize({
    redirectUri: import.meta.env.VITE_KAKAO_REDIRECT_URI,
  })
  // 카카오가 code와 함께 redirectUri로 리다이렉트
}
```

**콜백 처리 (KakaoCallbackView.vue)**
```js
// /auth/kakao/callback 라우트에 마운트
onMounted(async () => {
  const code = new URLSearchParams(window.location.search).get('code')
  const res = await api.post('/api/auth/kakao', { code })
  // 기존 로그인과 동일하게 Access Token 저장 → 메인 페이지 이동
  authStore.setToken(res.data.accessToken)
  router.push('/')
})
```

**추가 라우트**
```js
{ path: '/auth/kakao/callback', component: KakaoCallbackView, meta: { requiresAuth: false } }
```

### 1-4. 이메일 기반 연결 처리 상세

| 상황 | 처리 |
|------|------|
| 카카오 이메일 == 기존 회원 이메일 | 기존 계정에 `social_provider='kakao'`, `social_id` 업데이트 → JWT 발급 |
| 카카오 이메일 없음 (동의 거부) | `social_id`만으로 신규 회원 생성 (이메일 NULL, 닉네임은 카카오 닉네임 사용) |
| 카카오 이메일이 기존 회원이 아님 | 신규 회원 자동 가입 → JWT 발급 |
| 이미 카카오 연결된 계정 재로그인 | `social_id`로 조회 → JWT 발급 |

### 1-5. 조심할 점

- **비밀번호 NULL 계정의 일반 로그인 시도**: `password` NULL인 카카오 전용 계정이 이메일·비밀번호 로그인을 시도하면 `"소셜 계정으로만 로그인 가능합니다"` 오류 반환 (AuthServiceImpl에서 처리).
- **닉네임 중복**: 카카오 닉네임이 이미 사용 중이면 접미사 추가 (`닉네임_2` 등)로 자동 해결하거나 사용자에게 닉네임 입력을 요청한다.
- **카카오 앱 설정**: Kakao Developers 콘솔에서 플랫폼(도메인), Redirect URI, 동의항목(이메일, 닉네임) 설정 필수.
- **VITE 환경 변수**: `VITE_KAKAO_APP_KEY`, `VITE_KAKAO_REDIRECT_URI` — `.env.local`에 추가 (`.gitignore` 확인).

---

## 2. 카카오 일정 공유하기

### 2-1. 개념

[`share_plan.md`](share_plan.md)에서 생성된 `share_token` 기반 공유 링크를 **카카오톡 메시지**로 전송한다.  
백엔드 추가 작업 없음 — 카카오 JavaScript SDK의 `Kakao.Share.sendDefault()`를 프론트엔드에서 직접 호출한다.

```
소유자 클릭: [카카오로 공유]
    → Kakao.Share.sendDefault() 호출
    → 카카오톡 공유 다이얼로그 팝업 (수신자 선택)
    → 수신자 카카오톡에 카드 메시지 도착
    → 수신자가 메시지 클릭 → https://tripcraft.com/trip/shared/{shareToken} 접속
    → 읽기 전용 뷰 표시 (share_plan.md 참조)
```

### 2-2. 의존성

**공유 링크 발급 전제**: [`share_plan.md`](share_plan.md)의 `share_token`이 발급되어 있어야 카카오 공유가 가능하다.  
공유 링크가 비활성화(`share_enabled = 0`) 상태이면 카카오 공유 버튼을 비활성화(disabled)한다.

`share_plan.md`의 "공유 링크 UI 배치" 드롭다운에 `[카카오로 공유]` 버튼 포함.

### 2-3. 프론트엔드 구현

**Kakao SDK**: 카카오 로그인(섹션 1)과 동일한 SDK를 사용하므로 추가 설치 없음.

```js
// TripShareButton.vue (또는 TripDetailHeader.vue)
function shareToKakao(trip) {
  if (!window.Kakao.isInitialized()) {
    window.Kakao.init(import.meta.env.VITE_KAKAO_APP_KEY)
  }

  const shareUrl = `${window.location.origin}/trip/shared/${trip.shareToken}`

  window.Kakao.Share.sendDefault({
    objectType: 'feed',
    content: {
      title: trip.title,
      description: `${trip.startDate} ~ ${trip.endDate} | ${trip.memberNickname}님의 여행 일정`,
      imageUrl: 'https://tripcraft.com/og-image.png',   // 대표 이미지 (정적 자산)
      link: {
        mobileWebUrl: shareUrl,
        webUrl: shareUrl,
      },
    },
    buttons: [
      {
        title: '일정 보기',
        link: { mobileWebUrl: shareUrl, webUrl: shareUrl },
      },
    ],
  })
}
```

### 2-4. Kakao Developers 설정

카카오 로그인 앱(섹션 1)과 **동일한 앱**에서 "카카오 공유하기" 기능을 활성화한다.

| 설정 항목 | 값 |
|-----------|-----|
| 플랫폼 > Web | 서비스 도메인 등록 |
| 카카오 공유하기 | 별도 활성화 불필요 (JavaScript SDK 자체 지원) |
| JavaScript 앱 키 | `VITE_KAKAO_APP_KEY` 와 동일 |

### 2-5. 조심할 점

- **공유 링크 미생성 상태에서 버튼 노출 금지**: `shareToken`이 없거나 `share_enabled = false`이면 버튼을 disabled 처리하거나 "공유 링크를 먼저 활성화하세요" 안내.
- **카카오 로그인 없이도 공유 가능**: `Kakao.Share.sendDefault()`는 서비스 로그인 상태와 무관하게 동작한다. 단, 카카오 SDK 초기화(`Kakao.init`)는 필요하다.
- **OG 이미지**: `imageUrl`에 지정한 이미지가 없으면 카카오 카드에 이미지가 표시되지 않는다. 정적 OG 이미지(`/public/og-image.png`) 준비 필요.
