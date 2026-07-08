# 실행 · 배포 가이드 — TripCraft

로컬 개발 실행과 운영 배포의 진입점. 상세 배포 절차는 [`deploy/README.md`](../deploy/README.md), 로컬 도커 묶음은 [`deploy/local/README.md`](../deploy/local/README.md) 참조.

---

## 1. 사전 준비

| 항목 | 버전/비고 |
|------|-----------|
| JDK | 21 (Temurin 권장). 터미널 `gradlew` 실행엔 `JAVA_HOME`이 21이어야 함 |
| Node.js | 20+ (Vite 6). `frontend/.nvmrc` = 20 |
| MySQL | 8.0 (로컬 설치 또는 도커) |
| 외부 API 키 | TourAPI · ODsay · T Map · Naver Maps · gms(OpenAI 호환) · Kakao(로그인·로컬 API) — [§5 발급 가이드](#5-외부-api-키-발급-가이드) |

> IDE: 백엔드는 **IntelliJ로 `backend/` 폴더를 프로젝트로 열어야** Gradle 임포트가 정상 동작한다(루트 아님). 공유 실행구성 `.run/Backend bootRun.run.xml`(Gradle `bootRun`)이 커밋되어 있다. 프론트는 **VSCode로 `frontend/`**(또는 루트 `project.code-workspace`)를 열면 `.vscode/`의 권장 확장·디버그 구성이 적용된다.

---

## 2. 환경 변수

시크릿은 커밋하지 않는다. `*.example`를 복사해 실제 값을 채운다.

| 복사 원본 | 대상 | 용도 |
|-----------|------|------|
| `backend/.env.example` | `backend/.env` | DB 접속·JWT·외부 API 키 |
| `frontend/.env.example` | `frontend/.env` | Naver Maps·Kakao 프론트 키 |
| `deploy/.env.example` | `deploy/.env` | compose 인터폴레이션(도메인·포트·DB) |
| `deploy/backend-secrets.env.example` | `deploy/backend-secrets.env` | 운영 백엔드 시크릿 |

주입되는 키 목록은 [`backend/src/main/resources/application.yml`](../backend/src/main/resources/application.yml)의 `${...}` 플레이스홀더 참조.

**env 파일을 왜 4개로 나누는가 (통합하지 않는 이유)** — 프론트 `VITE_*`는 **브라우저 번들에 인라인되는 공개값**이고 백엔드 `.env`는 **서버 시크릿**이다. 한 파일로 합치면 시크릿을 프론트 번들에 실수로 노출할 위험이 커진다. 로딩 시점도 다르다(Vite 빌드타임 vs `spring-dotenv` 런타임 vs compose 인터폴레이션). 따라서 **역할별 분리가 정석**이다.

---

## 3. 로컬 실행

### 3-1. DB 스키마
```bash
mysql -u<user> -p tripcraft < docs/sql/schema.sql
# 필요 시 시드: docs/sql/*_seed*.sql, *_test_data.sql
```
스키마·마이그레이션 정본은 [`docs/sql/`](sql/), 모델 개요는 [`docs/database.md`](database.md).

### 3-2. 백엔드 (Spring Boot)
```bash
cd backend
./gradlew bootRun          # 기본 dev 프로필 (DEBUG 로깅), :8080
```

### 3-3. 프론트엔드 (Vue 3 + Vite)
```bash
cd frontend
npm install
npm run dev                # http://localhost:5173
```

---

## 4. 운영 배포 (Docker)

호스트 nginx에 서브도메인으로 합류하는 단일 인스턴스 구성. 인증 쿠키가 `Secure`라 **HTTPS 필수**, STOMP 브로커가 in-memory라 **백엔드 단일 인스턴스**(스케일아웃 금지).

```bash
cd deploy
docker compose up -d       # app nginx(:8095) + backend(:8080) + mysql
```
전체 절차(콘솔 키 등록·호스트 nginx 블록·TLS)는 [`deploy/README.md`](../deploy/README.md).

---

## 5. 외부 API 키 발급 가이드

| 키 | 발급처 | 비고 |
|----|--------|------|
| `DB_*` | 로컬 MySQL 8.0 | DB명 `trip_craft`, `docs/sql/schema.sql` 적용 |
| `JWT_SECRET` | 자체 생성 | 256bit Base64 (예: `openssl rand -base64 32`) |
| `KAKAO_LOCAL_API_KEY` | [Kakao Developers](https://developers.kakao.com) 앱 → REST API 키 | 로컬(장소) API는 REST 키로 바로 호출(별도 사용설정 불필요) |
| `VITE_NAVER_MAP_CLIENT_ID` | Naver Cloud Platform 콘솔 → Maps → Application 등록 → Client ID(ncpKeyId) | 프론트 지도 렌더 |
| `TOUR_API_KEY` | [공공데이터포털](https://data.go.kr) → 한국관광공사 KorService2 활용신청 | 관광지 수집 |
| `ODSAY_API_KEY` | [ODsay LAB](https://lab.odsay.com) 개발자센터 | 대중교통 이동시간 |
| `TMAP_API_KEY` | [SK open API](https://openapi.sk.com) 앱 등록 → appKey | 도보·자동차 경로 |
| `KAKAO_CLIENT_ID` / `_SECRET` / `_REDIRECT_URI` / `_ADMIN_KEY` | Kakao Developers 앱 → REST 키 / 보안 / Redirect URI / Admin 키 | 소셜 로그인 |
| `GMS_KEY` | SSAFY gms(OpenAI 호환 프록시, 교육용) | AI 챗봇 |

**개선 작업별 최소 키 세트**
- **개선 01(블록 메모 실시간)**: 신규 키 불필요. 앱 구동 최소 세트 = `DB_*` + `JWT_SECRET`. (프론트 지도까지 보려면 `VITE_NAVER_MAP_CLIENT_ID`.) WebSocket은 same-origin `/ws`라 별도 키 없음.
- **개선 02(카카오 로컬 API 수집)**: `KAKAO_LOCAL_API_KEY` + `DB_*`.
