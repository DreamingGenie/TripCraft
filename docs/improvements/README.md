# 개선 계획 (Improvements)

캡스톤 1.0 종료 후 **1인 추가 개발 단계**에서 서비스를 심화·확장하기 위한 개선안을
**구현 전에 설계·기록**하는 Living 문서 폴더다.

> 이 폴더의 문서는 `docs/*` Living 문서 정책을 따른다(코드 변경에 맞춰 유지보수).
> 캡스톤 동결 아카이브(`docs/capstone-1.0/`)와 달리 계속 갱신한다.

## 개선안 인덱스

| # | 개선안 | 상태 | 요약 |
|---|--------|------|------|
| 01 | [일정 블록 메모 실시간 공유](01-block-memo-realtime.md) | 📝 계획중 | 일정 블록에 최대 한글 100자 메모를 달고 협업자에게 실시간 반영. 기존 STOMP 실시간 협업 인프라 재사용. |
| 02 | [대량 장소 확보 + 대용량 처리](02-bulk-place-data.md) | 📝 계획중 | 카카오 로컬 API로 장소 대량 확보 + 대용량 데이터를 쿼리·아키텍처·적재 3레벨에서 최적화하고 before/after 성능 수치를 기록. |

**상태 범례**: 📝 계획중 · 🔨 구현중 · ✅ 완료 · ⏸ 보류

## 하위 산출물

- [`benchmarks/`](benchmarks/) — 개선 02의 성능 측정 결과(최적화 전/후 수치)를 누적하는 폴더.

## 관련 문서

- 실시간 협업 동시성/락 설계: [`../features/realtime-collab.md`](../features/realtime-collab.md)
- 외부 데이터 동기화: [`../features/external-data-sync.md`](../features/external-data-sync.md)
- DB 스키마: [`../sql/schema.sql`](../sql/schema.sql)
- 변경 이력: [`../../CHANGELOG.md`](../../CHANGELOG.md)
</content>
