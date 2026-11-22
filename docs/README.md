# TripCraft 문서 인덱스

프로젝트 문서의 진입점. 문서는 **성격별로 분류**한다 — 현행 시스템(상태) · 기능(설계) ·
기술 심화(학습) · 운영(실행) · 개선(계획) · 이력(시간순) · 아카이브(동결).

> 문서 유지보수 정책은 루트 [`CLAUDE.md`](../CLAUDE.md)의 "docs 정책" 참조.
> Living 문서는 코드 변경에 맞춰 갱신하고, `capstone-1.0/`는 동결(수정 금지)한다.

## 1. 시스템 기술 문서 (현행 아키텍처 — 상태)

| 문서 | 내용 |
|---|---|
| [architecture.md](architecture.md) | 전체 아키텍처 |
| [database.md](database.md) | ER 다이어그램 · 스키마 개요 |
| [api.md](api.md) | API 명세 |
| [frontend.md](frontend.md) | 프론트엔드(Vue 3) 아키텍처 |
| [design-system.md](design-system.md) | 디자인 시스템 |

## 2. 기능별 설계 문서 (`features/` — 설계)

| 문서 | 내용 |
|---|---|
| [auth-security.md](features/auth-security.md) | 인증·보안 |
| [external-data-sync.md](features/external-data-sync.md) | 외부 데이터 동기화 |
| [realtime-collab.md](features/realtime-collab.md) | 실시간 협업 동시성 |
| [transit-routing.md](features/transit-routing.md) · [transit-odsay-api.md](features/transit-odsay-api.md) | 이동 경로·ODsay |
| [image-lifecycle.md](features/image-lifecycle.md) · [perf-profile-image.md](features/perf-profile-image.md) | 이미지 라이프사이클·성능 |

## 3. 기술 심화·학습 노트 ([`tech-notes/`](tech-notes/) — 학습)

구현에서 익힌 기술을 개념·주의점 중심으로 정리한 **커밋 대상** 학습 문서.
(커밋하지 않는 개인 메모는 `.gitignore`로 제외되는 루트 `notes/`에 둔다 — 혼동 주의.)

- [카카오 로컬 수집기](tech-notes/kakao-local-collector.md) — 외부 API 대량 수집·quadtree·UPSERT 멱등성.

## 4. 운영 문서 (실행)

| 문서 | 내용 |
|---|---|
| [setup.md](setup.md) | 실행·배포 가이드 |
| [conventions.md](conventions.md) | 코딩·Git·PR 컨벤션 |
| [sql/](sql/) | 스키마 DDL(`schema.sql`)·마이그레이션 |

## 5. 개선 계획 ([`improvements/`](improvements/) — 계획)

1인 개발 단계의 심화·확장 개선안을 구현 전 설계·기록. [개선안 인덱스](improvements/README.md).

## 6. 이력 (시간순)

- [logs/](logs/) — 개발 일지(세션별 서술형 기록)
- 루트 [CHANGELOG.md](../CHANGELOG.md) — 변경 이력(Keep a Changelog)

## 7. 동결 아카이브 ([`capstone-1.0/`](capstone-1.0/) — 수정 금지)

캡스톤 제출 상태(as-submitted) 보존. 갱신하지 않는다.
