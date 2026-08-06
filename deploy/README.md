# 배포 (deploy)

TripCraft 배포 자산·절차의 진입점. 아키텍처 제약상 **상시 켜진 단일 VM + 직접 nginx/TLS** 구성이 정답이다
(STOMP in-memory → 백엔드 단일 인스턴스 고정, 협업 WebSocket 상시 연결, 인증 쿠키 Secure → HTTPS 필수,
이미지 업로드는 별도 볼륨). 오토스케일 PaaS는 궁합이 나빠 비권장.

## 정본 배포 절차

| 문서 | 용도 |
|------|------|
| **[`oracle-arm/README.md`](oracle-arm/README.md)** | **★ 현재 정본** — Oracle Cloud ARM 단일 호스트 + 개인 도메인 **서브도메인** 배포(영구 무료). 프로비저닝·iptables·ARM 빌드·자체 certbot·외부 콘솔 교체까지 단계별. |
| [`oracle-arm/DEPLOY-LOG.md`](oracle-arm/DEPLOY-LOG.md) | 실제 배포 진행 로그(단계별 결과·발생 문제·해결). Notion 붙여넣기 형태로 유지. |
| [`local/README.md`](local/README.md) | 로컬에서 도커로 띄워보거나 이미지로 전달. |
| [`legacy-homeserver/README.md`](legacy-homeserver/README.md) | (레거시) 홈서버 nginx 서브도메인 합류 초기 계획. 폐기됐으나 nginx/TLS 패턴 참고용 보존. |

## 공용 자산 (어느 호스트로 가든 그대로 사용)

| 파일 | 역할 |
|------|------|
| `docker-compose.yml` | `mysql` + `backend`(8080) + `nginx`(SPA+프록시). 뒤 둘은 내부망 전용. 단일 VM 배포에선 nginx를 `127.0.0.1:8095`로만 바인딩(호스트 nginx만 프록시). |
| `app-nginx.conf` | **컨테이너 내부** nginx — HTTP 전용(:80). SPA dist 서빙 + `/api`·`/uploads`·`/ws` 를 backend로 프록시. TLS/도메인은 **호스트 nginx** 담당. |
| `.env.example` | compose 인터폴레이션용 — `DOMAIN`·`APP_PORT`·`DB_*`. 실제값은 `.env`(커밋 금지). |
| `backend-secrets.env.example` | 백엔드 시크릿 — `JWT_SECRET`·외부 API 키·`KAKAO_*`. 실제값은 `backend-secrets.env`(커밋 금지). |
| `../frontend/.env.production.example` | 프론트 빌드용 `VITE_*`(도메인 반영, 빌드 시점 인라인). 실제값은 `.env.production`(커밋 금지). |
| `seed/02-attraction-seed.sql.gz` | 관광지 시드(약 27,800행). mysql 최초 기동 시 `01 스키마 → 02 시드` 자동 적용. |

## 아키텍처 (한 줄 요약)

```
브라우저 ─https:443─> [호스트 nginx: TLS 종단] ─proxy─> 127.0.0.1:8095 ─> [app nginx 컨테이너]
                                                                            ├ /            → dist(SPA)
                                                                            ├ /api,/uploads → backend:8080
                                                                            └ /ws          → backend:8080 (WebSocket)
                                                              [backend:8080] ── [mysql]  (내부망 전용)
```
