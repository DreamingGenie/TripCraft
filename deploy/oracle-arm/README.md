# 배포 계획 (초안) — Oracle Cloud ARM 단일 호스트 + 개인 도메인 서브도메인

> **상태**: 초안 (검토 대기). 확정 후 `deploy/README.md` 를 대체하거나 병행 유지 결정.
> **타겟 확정**: Oracle Cloud Always Free **ARM Ampere A1**(영구 무료) 단일 VM.
> **합류 방식**: 개인 포트폴리오 도메인 아래 **서브도메인**(`tripcraft.<내도메인>.com`)으로 노출.
> **핵심**: 기존 `deploy/README.md`(호스트 nginx 합류)의 구조를 **자기 소유 호스트에서 재현**. 컨테이너는 그대로, 바뀌는 건 호스트 프로비저닝·도메인·ARM 빌드·iptables·자체 certbot.

---

## 0. 아키텍처 (이 호스트 한 대가 전부)

```
                          [Oracle ARM VM — Ubuntu, 공인 IP 1개]
브라우저 ─https:443─> [호스트 nginx] ─┬─ <내도메인>.com          → 포트폴리오 정적 사이트(추후)
   (TLS 종단·자체 certbot)           └─ tripcraft.<내도메인>.com → proxy → 127.0.0.1:8095
                                                                              │
                                                    [app nginx 컨테이너:80] ──┼─ /            → dist(SPA)
                                                                              ├─ /api,/uploads → backend:8080
                                                                              └─ /ws          → backend:8080 (WebSocket)
                                                              [backend:8080] ── [mysql]  (둘 다 도커 내부망 전용)
```

- **호스트 nginx = 공용 현관.** 포트폴리오(정적)와 tripcraft(동적)를 server_name 으로 분기. tripcraft 만 도커로 프록시.
- 컨테이너는 **TLS를 잡지 않는다.** 외부로 여는 포트는 **없음** — `8095`는 **127.0.0.1 로만 바인딩**(호스트 nginx만 접근).
- 서브도메인이라 **한 오리진** → 프론트 상대경로(`/api`·`/ws`·`/uploads`) 그대로, CORS/SameSite 무이슈, Vue base path 수정 불필요.

### 재사용/변경 요약 (기존 deploy/README.md 대비)

| 항목 | 기존(호스트 nginx 합류) | 이번(Oracle ARM 단독) |
|------|------------------------|----------------------|
| 호스트 | 이미 운영 중인 홈서버 | **신규 Oracle ARM VM 프로비저닝** |
| 도메인 | `tripcraft.thdwjdrl.com` | **`tripcraft.<내도메인>.com` (신규 등록)** |
| TLS | 기존 certbot 자동갱신에 편입 | **자체 certbot 설치 + 갱신 timer** |
| 방화벽 | 홈서버 기존 룰 | **Security List + OS iptables 둘 다** |
| 빌드 | (동일) | **ARM 서버 위에서 `--build`** (코드 0 수정) |
| compose/컨테이너 nginx conf | **그대로 재사용** | **그대로 재사용** |
| 8095 노출 | 도커호스트:8095 | **127.0.0.1:8095 로만** (개선) |

---

## 1. 도메인 + DNS (사용자 작업)

1. 개인 도메인 신규 등록(.com 기준 ~₩15,000/년). 이 도메인이 포트폴리오 겸 tripcraft 상위 도메인.
2. DNS A레코드 2개 → Oracle VM 공인 IP:
   - `@` (또는 `www`) → 포트폴리오용 (추후)
   - **`tripcraft`** → **VM 공인 IP** (이게 이번 배포 대상)
3. `dig tripcraft.<내도메인>.com` 으로 전파 확인 후 TLS 단계 진행(전파 전 certbot 실패).

## 2. Oracle ARM 인스턴스 프로비저닝 (사용자 작업)

- **Shape**: `VM.Standard.A1.Flex` (Ampere ARM). 무료 한도 내 예: **4 OCPU / 24GB** 통째 할당(단일 VM이면 여유 최대).
- **이미지**: Canonical **Ubuntu 22.04 (aarch64)**.
- **SSH 키**: 로컬 키페어 등록(비번 로그인 금지).
- ⚠ **함정 ①**: 가입/생성 시 **"out of capacity"** 흔함 → 다른 가용 도메인(AD)·리전 재시도, 시간대 바꿔 재시도.
- **Security List / NSG**(Oracle 콘솔 방화벽): Ingress 에 **22, 80, 443**(0.0.0.0/0) 허용.

## 3. 서버 기본 세팅 (SSH 접속 후)

```bash
sudo apt update && sudo apt -y upgrade
# Docker + compose 플러그인
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker $USER && newgrp docker
docker compose version    # v2 플러그인 확인
```

⚠ **함정 ②(가장 흔한 실수) — OS iptables.** Oracle Ubuntu 이미지는 콘솔 Security List 와 **별개로** OS 내부 iptables INPUT 기본 룰이 있어, 콘솔만 열면 80/443 이 여전히 막힘:

```bash
sudo iptables -I INPUT 6 -p tcp --dport 80  -j ACCEPT
sudo iptables -I INPUT 6 -p tcp --dport 443 -j ACCEPT
sudo netfilter-persistent save      # 재부팅 후에도 유지 (iptables-persistent)
```

**Swap**(권장): 24GB RAM이면 압박은 없지만 안전망으로 4GB 정도.
```bash
sudo fallocate -l 4G /swapfile && sudo chmod 600 /swapfile
sudo mkswap /swapfile && sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
```

## 4. 코드 + 환경 파일 3종

```bash
git clone https://github.com/DreamingGenie/TripCraft.git && cd TripCraft/deploy
cp .env.example .env                                 # DOMAIN, APP_PORT=8095, DB_*
cp backend-secrets.env.example backend-secrets.env    # JWT·외부 API키·KAKAO_*
cp ../frontend/.env.production.example ../frontend/.env.production   # VITE_* (도메인 반영)
```

채울 값 (모두 **`tripcraft.<내도메인>.com`** 로 통일):
- `deploy/.env` — `DOMAIN`, `DB_*`/`MYSQL_ROOT_PASSWORD`(강한 값), `APP_PORT=8095`.
- `deploy/backend-secrets.env` — `JWT_SECRET`(`openssl rand -base64 48`), `TOUR/ODSAY/TMAP/GMS` 키, `KAKAO_*`, `KAKAO_REDIRECT_URI=https://tripcraft.<내도메인>.com/auth/kakao/callback`.
- `frontend/.env.production` — `VITE_NAVER_MAP_CLIENT_ID`, `VITE_KAKAO_REST_KEY`, `VITE_KAKAO_REDIRECT_URI`, `VITE_KAKAO_LOGOUT_REDIRECT_URI` (도메인 반영, **빌드 시점 인라인**이라 값 바꾸면 재빌드 필요).
- ⚠ 세 파일 전부 **git 커밋 금지**(gitignore 확인).

**개선 권장 — 8095 를 로컬에만 바인딩.** 호스트 nginx가 같은 머신이므로 8095를 공개할 이유 없음. `deploy/docker-compose.yml` 의 nginx 포트를:
```yaml
    ports:
      - "127.0.0.1:${APP_PORT}:80"   # (기존 "${APP_PORT}:80" → 공개 노출 차단)
```
로 바꾸면 외부에서 8095 직접 접근 불가(호스트 nginx만 프록시). *이 변경은 승인 후 반영.*

## 5. 컨테이너 빌드·기동 (ARM 서버 위에서)

```bash
docker compose up -d --build     # ⚠ ARM64 네이티브 빌드 — 베이스 이미지 전부 multi-arch, 코드 수정 0
docker compose ps                # mysql(healthy)·backend·nginx 확인
curl -I http://127.0.0.1:8095/   # 컨테이너 직접 200 (아직 도메인/TLS 전)
docker compose logs -f backend   # 기동/시드/협업/인증 INFO 로깅(prod 프로파일)
```
- mysql 최초 기동 시 `01 스키마 → 02 관광지 시드(gz)` 자동 적용 → 검색·후보담기 즉시 동작.
- ARM 빌드는 첫 회 gradle/npm 때문에 수 분 소요(24GB라 메모리 문제 없음).

## 6. 호스트 nginx 설치 + 서브도메인 블록

```bash
sudo apt -y install nginx
```

`/etc/nginx/sites-available/tripcraft.conf` (기존 deploy/README §4 블록을 self-host 로 조정 — upstream 이 `127.0.0.1:8095`):
```nginx
upstream tripcraft_proxy { server 127.0.0.1:8095; }

# 80 → 443 리다이렉트 (certbot 이 자동 삽입하거나 아래 수동)
server {
    listen 80;
    server_name tripcraft.<내도메인>.com;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    listen [::]:443 ssl;
    server_name tripcraft.<내도메인>.com;

    client_max_body_size 11m;          # 이미지 업로드 10MB + 여유 (호스트 hop 에도 필수)

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
        proxy_read_timeout 3600s;      # 협업 연결 장기 유지
    }

    # ssl_certificate 는 7단계 certbot 이 자동 채움
}
```
```bash
sudo ln -s /etc/nginx/sites-available/tripcraft.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```
> 포트폴리오(정적)는 추후 `<내도메인>.com` server 블록을 추가(별도 `root /var/www/portfolio;`)해 같은 nginx에서 병행 서빙. 이번 배포 범위 밖.

## 7. TLS 발급 + 자동 갱신

```bash
sudo apt -y install certbot python3-certbot-nginx
sudo certbot --nginx -d tripcraft.<내도메인>.com    # 인증서 발급 + 443 블록에 자동 삽입
sudo systemctl status certbot.timer                 # 자동 갱신 timer 활성 확인 (기존 홈서버 갱신 편입 불가 → 자체 관리)
```
- DNS A레코드 전파(1단계) 이후 실행해야 검증 통과.

## 8. 외부 콘솔 URL·Redirect URI 교체 체크리스트 (사용자 작업, 도메인 일치 필수)

- [ ] **네이버 클라우드 Maps** — Web 서비스 URL 에 `https://tripcraft.<내도메인>.com` 등록 (미등록 시 지도 안 뜸).
- [ ] **카카오 개발자 콘솔** — Web 플랫폼 도메인 `https://tripcraft.<내도메인>.com` 추가.
- [ ] 카카오 **Redirect URI** `https://tripcraft.<내도메인>.com/auth/kakao/callback` 등록.
- [ ] 위 값이 `KAKAO_REDIRECT_URI`(백) ↔ `VITE_KAKAO_REDIRECT_URI`(프) 와 **3자 일치**.
- [ ] 로그아웃 Redirect `https://tripcraft.<내도메인>.com/` (`VITE_KAKAO_LOGOUT_REDIRECT_URI`).

## 9. 검증 (멀티디바이스 협업)

- [ ] `https://tripcraft.<내도메인>.com` → 정식 인증서, 경고 없음.
- [ ] 이메일/카카오 로그인 OK (쿠키 Secure 전송).
- [ ] **2대 이상 기기**에서 같은 일정(`/plan/:id`) → 커서·블록 드래그/리사이즈 **실시간 동기화**, WS 200(401 없음).
- [ ] 지도(Naver) 로드, 관광지 검색·후보담기, 이미지 업로드(413 없음).
- [ ] 동시 편집 시 시간 겹침 금지(409)·낙관적 락.
- [ ] `docker compose logs -f backend` 에 `협업 WS 연결`·`로그인 성공` INFO.

## 10. 운영 / 갱신

```bash
git pull && docker compose up -d --build     # 코드 갱신 후 재빌드·재기동 (ARM 서버)
docker compose logs -f backend               # 로그(INFO)
docker compose down                          # 중지 (volume 유지)
```
- DB/업로드는 named volume(`mysql-data`·`uploads`) 유지. 시드는 최초 1회만(볼륨 삭제 시 재적용).

## 11. (선택) AWS EC2 학습용 1회 배포 — 부록

메인은 이 Oracle 구성. AWS는 "EC2 배포 경험" 이력용으로 별도 1회:
- EC2 프리티어(t2/t3.micro, x86, 1GB) → **swap 필수**(6단계 swap 그대로), 나머지 절차 동일.
- 같은 compose·같은 host nginx 블록 재사용 → 두 번째 부담 작음. 12개월 후 과금 유의(학습 끝나면 종료).

## 트러블슈팅

- **80/443 접속 자체 안 됨** → OS **iptables**(3단계) 또는 Security List 누락. `sudo iptables -L INPUT -n --line-numbers` 확인.
- **로그인 풀림 / WS 401** → HTTPS 아님·도메인 불일치(쿠키 Secure). 인증서·`server_name`·`Host` 확인.
- **지도 안 뜸** → 네이버 콘솔 Web URL 미등록 또는 `VITE_NAVER_MAP_CLIENT_ID` 누락(빌드 시점 값 → 재빌드).
- **카카오 로그인 실패** → 콘솔 Redirect URI ↔ 백 ↔ 프 3자 불일치.
- **413 업로드 실패** → 호스트 nginx 블록 `client_max_body_size 11m` 누락.
- **certbot 실패** → DNS A레코드 미전파 또는 80 포트 미개방.
- **ARM 빌드 느림/실패** → 정상적으로 수 분 소요. OOM 시(작은 shape면) swap 확인.
