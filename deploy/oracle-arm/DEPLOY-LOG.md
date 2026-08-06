# TripCraft 배포 로그 — Oracle ARM

> 실제 배포 진행 기록. **Notion에 그대로 붙여넣을 수 있는 형태**로 유지한다
> (제목/표/체크박스/코드블록/콜아웃 모두 Notion 호환). 각 단계는 **목표 → 실행 → 결과 → 발생 문제·해결** 순.
> 정본 절차 문서: [`README.md`](README.md).

## 진행 현황 요약

| 단계 | 항목 | 상태 | 날짜 |
|------|------|:----:|------|
| 0 | 배포 계획 확정(Oracle ARM · 서브도메인) | ✅ 완료 | 2026-08-06 |
| 0 | deploy 폴더 재구성 + 계획 문서 push | 🔄 진행 | 2026-08-06 |
| 1 | 도메인 등록 + DNS A레코드 | ⬜ 예정 | |
| 2 | Oracle ARM 인스턴스 프로비저닝 | ⬜ 예정 | |
| 3 | 서버 기본 세팅(docker·iptables·swap) | ⬜ 예정 | |
| 4 | 코드 + 환경파일 3종 | ⬜ 예정 | |
| 5 | 컨테이너 ARM 빌드·기동 | ⬜ 예정 | |
| 6 | 호스트 nginx 서브도메인 블록 | ⬜ 예정 | |
| 7 | certbot TLS 발급·자동갱신 | ⬜ 예정 | |
| 8 | 네이버·카카오 콘솔 URL 교체 | ⬜ 예정 | |
| 9 | 멀티디바이스 협업 검증 | ⬜ 예정 | |

> 상태 범례: ⬜ 예정 · 🔄 진행 · ✅ 완료 · ⚠️ 문제발생 · ⏸ 보류

---

## 로그

### 2026-08-06 — 계획 확정 & 문서 재구성

**목표**: 배포 타겟 확정하고 deploy 문서 체계를 새 구성으로 정리.

**실행 / 결과**
- 타겟 확정: **Oracle Cloud Always Free ARM(Ampere A1)** 단일 VM, 개인 도메인 **서브도메인**(`tripcraft.<내도메인>.com`)으로 노출. AWS는 학습용 부록으로 보류.
- deploy 폴더 재구성:
  - 홈서버 전용 절차 → `deploy/legacy-homeserver/README.md` 로 이동(레거시 배너 추가).
  - `deploy/README.md` 새 진입점 인덱스로 재작성.
  - `deploy/oracle-arm/README.md` 정본 배포 절차 초안.
  - `docker-compose.yml`: nginx 포트를 `127.0.0.1:8095` 로 제한(공개 노출 차단).
- 브랜치 정리: `feature/deploy-oracle-arm` 을 카카오 브랜치 위에서 팠던 것을 발견 → **최신 origin/master 기준으로 재생성**하고 deploy 커밋만 이관.

**발생 문제 · 해결**
> ⚠️ **문제**: deploy 브랜치가 `master`가 아니라 미병합 `feature/kakao-local-bulk-place` 위에 생성되어, 무관한 카카오 기능 7커밋이 섞이고 origin/master 최신 3커밋이 누락됨.
> ✅ **해결**: `master`(=origin/master)로 이동 후 브랜치 삭제·재생성, 기존 deploy 커밋만 cherry-pick. 카카오 브랜치는 손대지 않음.

> ⚠️ **문제**: `git mv deploy/README.md deploy/legacy-homeserver/README.md` 가 대상 폴더 부재로 실패(`No such file or directory`).
> ✅ **해결**: `mkdir -p deploy/legacy-homeserver` 후 재실행.

---

## (이후 단계 기록 템플릿 — 복사해서 채우기)

### YYYY-MM-DD — <단계 제목>

**목표**: 

**실행**
```bash
# 실행한 명령
```

**결과**: 

**발생 문제 · 해결**
> ⚠️ **문제**: 
> ✅ **해결**: 
