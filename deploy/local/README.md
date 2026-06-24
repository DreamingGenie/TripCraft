# 로컬 간단 실행 / 도커 이미지 전달

서버(호스트 nginx·TLS) 없이 **`http://localhost:8095`** 로 바로 띄워보는 방법.
DB 스키마·관광지 시드·nginx 설정을 **이미지에 구워** 자족화했다 → 레포 없이 이미지만 받아도 실행된다.

> **브라우저는 Chrome/Firefox 사용.** 인증 쿠키가 `Secure`라서, 두 브라우저는 `http://localhost`를
> 보안 컨텍스트로 취급해 쿠키를 저장한다(로그인·협업 정상). Safari는 localhost에서 Secure 쿠키를
> 막을 수 있으니 피한다.
>
> **로컬에서 되는 것**: 이메일 회원가입/로그인, 실시간 협업(여러 탭/기기), 커뮤니티, 관광지 검색(시드).
> **키/콘솔 필요**: 네이버 지도·카카오 로그인·이동시간(ODsay/TMap)·AI 챗봇 — 로컬은 보통 생략.

---

## A. 소스에서 실행 (레포 보유 시 — 가장 간단)
```bash
cd deploy/local
cp backend-secrets.local.env.example backend-secrets.local.env   # 그대로 둬도 기동됨(더미 키)
docker compose -f docker-compose.local.yml up -d --build
# → http://localhost:8095  (mysql 최초 기동 시 스키마+시드 자동 적용, 1~2분 소요)
docker compose -f docker-compose.local.yml logs -f backend       # INFO 로그
```
협업 테스트: 같은 브라우저 **여러 탭** 또는 **다른 기기(같은 LAN에서 `http://<이PC_IP>:8095`)**로
같은 일정을 열어 커서·블록 이동이 실시간 동기화되는지 확인.

중지/정리:
```bash
docker compose -f docker-compose.local.yml down        # 중지(volume 유지)
docker compose -f docker-compose.local.yml down -v     # DB·업로드까지 초기화
```

## B. 이미지 번들로 전달 (받는 사람은 레포 불필요)
**전달자:**
```bash
cd deploy/local
./build-and-save.sh
# → tripcraft-images.tar.gz 생성. 이 파일 + docker-compose.local.yml
#   + backend-secrets.local.env.example 를 함께 전달.
```
**받는 사람:** (Docker만 설치되어 있으면 됨)
```bash
gunzip -c tripcraft-images.tar.gz | docker load
cp backend-secrets.local.env.example backend-secrets.local.env
docker compose -f docker-compose.local.yml up -d --no-build
# → http://localhost:8095
```
`--no-build` 라서 `build:` 컨텍스트 없이 **load 된 이미지로만** 뜬다(레포·소스 불필요).

## 동작 안 할 때
- **포트 충돌(8095)** → `APP_PORT=9095 docker compose -f docker-compose.local.yml up -d` 처럼 변경.
- **로그인 직후 풀림** → Safari 사용 중일 가능성. Chrome/Firefox로.
- **지도 안 뜸 / 카카오 로그인 안 됨** → 정상(로컬은 콘솔 미등록). 이메일 계정으로 테스트.
- **mysql 첫 기동이 느림** → 시드(2.5만 행) 적용 중. `logs -f mysql` 로 `ready for connections` 대기.
