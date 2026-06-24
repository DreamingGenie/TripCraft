# 배포 — 기존 호스트 nginx 서버에 서브도메인으로 합류

실시간 협업(STOMP/SockJS)을 여러 디바이스로 테스트하기 위한 단일 호스트 배포.
**이미 여러 사이트를 443으로 서빙 중인 호스트 nginx**에 `tripcraft.thdwjdrl.com` 서브도메인 하나로 합류한다.

```
브라우저 ─https:443─> [호스트 nginx] ──proxy──> 도커호스트:8095 ─> [app nginx 컨테이너]
   (기존, 블록 1개만 추가)                                          ├ /        → dist(SPA)
                                                                    ├ /api,/uploads → backend:8080
                                                                    └ /ws      → backend:8080 (웹소켓)
                                                       [backend:8080] ── [mysql]  (둘 다 내부망 전용)
```

> 핵심 제약
> - 인증 쿠키가 **Secure** → **HTTPS 필수**(호스트 nginx가 TLS 종단).
> - 프론트는 상대경로(`/api`·`/ws`·`/uploads`)만 → 한 오리진이라 CORS/SameSite 무이슈.
> - STOMP 브로커가 **in-memory** → 백엔드 **단일 인스턴스만**(스케일아웃 금지).
> - 컨테이너는 **TLS를 잡지 않는다**. 외부로 여는 포트는 **8095 하나뿐**(`APP_PORT`).

로컬에서 간단히 돌려보거나 도커 이미지로 전달하려면 → [`local/README.md`](local/README.md).

---

## 0. 사전 준비 (호스트)
- Ubuntu에 `docker`·`docker compose` 설치.
- **DNS**: `tripcraft.thdwjdrl.com` A레코드 → 서버 공인 IP(다른 서브도메인과 동일).
- **외부 콘솔 등록**(도메인 일치 필수):
  - 네이버 클라우드 Maps 키 → **Web 서비스 URL `https://tripcraft.thdwjdrl.com`**.
  - 카카오 콘솔 → **Web 플랫폼 `https://tripcraft.thdwjdrl.com`** + **Redirect URI `https://tripcraft.thdwjdrl.com/auth/kakao/callback`**.

## 1. 코드 + 환경 파일
```bash
git clone <repo> && cd a11_final_jin_jeongki/deploy
cp .env.example .env                                  # DOMAIN, APP_PORT=8095, DB_*
cp backend-secrets.env.example backend-secrets.env     # JWT·외부 API키·KAKAO_*
cp ../frontend/.env.production.example ../frontend/.env.production   # VITE_*(도메인 반영)
# 세 파일 실제값으로 채우기. (.env / *secrets.env / .env.production 은 git 커밋 금지)
```

## 2. 컨테이너 기동 (8095, 호스트 nginx 연결 전 단독 확인)
```bash
docker compose up -d --build
docker compose ps                         # mysql(healthy)·backend·nginx 확인
curl -I http://127.0.0.1:8095/            # 컨테이너 직접 200 확인(아직 도메인 X)
docker compose logs -f backend            # 운영 로깅(INFO) — 기동/배치/협업/인증 이벤트
```
- mysql 최초 기동 시 initdb가 **01 통합 스키마 → 02 관광지 시드(gz)** 순서로 자동 적용 → 관광지 검색·후보담기 바로 동작.

## 3. 인증서 발급 (호스트 certbot — nginx 블록 넣기 *전*)
```bash
sudo certbot certonly --nginx -d tripcraft.thdwjdrl.com   # 기존에 쓰던 방식 그대로
# → /etc/letsencrypt/live/tripcraft.thdwjdrl.com/ 에 인증서 생성. 기존 자동갱신에 편입됨.
```

## 4. 호스트 nginx에 블록 추가 (기존 dashboard 블록 복제 패턴)
설정 파일에 아래를 추가하고 `sudo nginx -t && sudo systemctl reload nginx`:
```nginx
# (1) upstream 추가
upstream tripcraft_proxy { server 192.168.219.124:8095; }

# (2) 80→443 리다이렉트 server_name 목록에 tripcraft.thdwjdrl.com 추가

# (3) 443 블록 (dashboard 블록과 동일 구조 + 업로드 크기)
server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name tripcraft.thdwjdrl.com;

    client_max_body_size 11m;   # 이미지 업로드 10MB + 여유 (호스트 hop에도 필요)

    location / {
        proxy_pass http://tripcraft_proxy;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_set_header Host $http_host;

        # 협업 WebSocket(/ws) 업그레이드 통과
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "Upgrade";
        proxy_cache_bypass $http_upgrade;
        proxy_read_timeout 3600s;   # 협업 연결 장기 유지
    }

    ssl_certificate /etc/letsencrypt/live/tripcraft.thdwjdrl.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/tripcraft.thdwjdrl.com/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;
}
```
> 업그레이드 헤더를 `location /`에 두면 `/ws` 까지 통과(호스트→컨테이너→백엔드 hop 모두 전달 필요).

## 5. 검증 (멀티디바이스 협업)
- `https://tripcraft.thdwjdrl.com` → 정식 인증서(경고 없음).
- 이메일/카카오 로그인 OK(쿠키 Secure 전송).
- **2대 이상 기기**에서 같은 일정(`/plan/:id`) → 커서·블록 드래그/리사이즈 **실시간 동기화**, WS 200(401 없음).
- 지도(Naver) 로드, 관광지 검색·후보담기, 이미지 업로드.
- 동시 편집 시 시간 겹침 금지(409)·낙관적 락.
- `docker compose logs -f backend` 에 `협업 WS 연결`·`협업 세션 종료`·`로그인 성공` 등 INFO 확인.

## 갱신 / 운영
```bash
git pull && docker compose up -d --build     # 코드 갱신 후 재빌드·재기동
docker compose logs -f backend               # 로그(INFO)
docker compose down                          # 중지 (volume 유지)
```
- DB/업로드는 named volume(`mysql-data`·`uploads`)에 유지. 시드 재적용은 최초 1회뿐(볼륨 삭제 시 재적용).

## 트러블슈팅
- **로그인 풀림 / WS 401** → HTTPS 아님·도메인 불일치(쿠키 Secure). 인증서·`server_name`·`Host` 확인. backend 로그의 `협업 WS 발행 거부`·`구독 거부` WARN으로 원인 식별.
- **지도 안 뜸** → 네이버 콘솔 Web URL 미등록 또는 `VITE_NAVER_MAP_CLIENT_ID` 누락(빌드 시점 값).
- **카카오 로그인 실패** → 콘솔 Redirect URI ↔ `KAKAO_REDIRECT_URI`(백) ↔ `VITE_KAKAO_REDIRECT_URI`(프) 일치 확인.
- **413 업로드 실패** → 호스트 nginx 블록의 `client_max_body_size 11m` 누락.
- **호스트 nginx reload 실패** → 인증서 경로 없음(3단계 먼저) 또는 8095 포트 충돌.
