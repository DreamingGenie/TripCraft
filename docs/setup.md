# 실행 · 배포 가이드 — TripCraft

로컬 개발 실행과 운영 배포의 진입점. 상세 배포 절차는 [`deploy/README.md`](../deploy/README.md), 로컬 도커 묶음은 [`deploy/local/README.md`](../deploy/local/README.md) 참조.

---

## 1. 사전 준비

| 항목 | 버전/비고 |
|------|-----------|
| JDK | 21 |
| Node.js | 20+ (Vite 7) |
| MySQL | 8.0 (로컬 또는 도커) |
| 외부 API 키 | TourAPI · ODsay · T Map · Naver Maps · gms(OpenAI 호환) · Kakao(선택) |

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
