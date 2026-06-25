#!/usr/bin/env bash
# TripCraft 최종 산출물 변환 스크립트 (v2)
# 변경점(v2):
#   - 05_reference/API명세.md 를 정식 변환 대상에 추가.
#   - 교정된 _v2 문서(요구사항정의서/WBS_간트/기타참고문서/화면설계서) 변환, 발표자료는 slides_v2.md.
#   - 심화 기술문서 2종(기술심화-*.md)은 초안이라 의도적으로 제외(작업자 완성 후 추가).
# 원본 build.sh 는 보존. Markdown/Mermaid 정본 → PDF · PPTX
# 요구: Node/npx + Chrome (npx가 최초 1회 패키지 다운로드 — 네트워크 필요)
set -euo pipefail

cd "$(dirname "$0")"
OUT="out"
mkdir -p "$OUT" assets

DOCS=(
  "01_requirements/요구사항정의서_v2.md"
  "02_diagrams/usecase.md"
  "02_diagrams/class-diagram.md"
  "02_diagrams/er-diagram.md"
  "03_wbs/WBS_간트_v2.md"
  "04_screen/화면설계서_v2.md"
  "05_reference/기타참고문서_v2.md"
  "05_reference/API명세.md"
  "ai-report/AI사용보고서.md"
)

echo "==> [1/2] 설계 문서 → PDF (md-to-pdf, Mermaid 자동 렌더)"
for d in "${DOCS[@]}"; do
  base="$(basename "${d%.md}")"
  echo "    - $d"
  npx -y md-to-pdf "$d" --launch-options '{"args":["--no-sandbox"]}' || {
    echo "      [경고] $d 변환 실패 — 네트워크/Chrome 확인"; }
  # md-to-pdf는 입력 옆에 .pdf 생성 → out/으로 이동
  [ -f "${d%.md}.pdf" ] && mv "${d%.md}.pdf" "$OUT/$base.pdf" || true
done

echo "==> [2/2] 발표자료 → PDF + PPTX (marp-cli)"
npx -y @marp-team/marp-cli ppt/slides_v2.md --pdf  --allow-local-files -o "$OUT/TripCraft_발표자료.pdf"  || echo "  [경고] PDF 변환 실패"
npx -y @marp-team/marp-cli ppt/slides_v2.md --pptx --allow-local-files -o "$OUT/TripCraft_발표자료.pptx" || echo "  [경고] PPTX 변환 실패"

echo "==> 완료. 생성물: $OUT/"
ls -la "$OUT" || true
