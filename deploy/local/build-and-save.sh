#!/usr/bin/env bash
# 전달자용 — 로컬 이미지 3개를 빌드해 단일 tar.gz 로 묶는다.
# 받는 사람은 레포 없이 docker load 후 바로 실행 가능(README.md 의 "B. 이미지 번들" 참고).
set -euo pipefail
cd "$(dirname "$0")"

echo "▶ 이미지 빌드 (mysql=스키마+시드 / backend=jar / web=dist+nginx)…"
docker compose -f docker-compose.local.yml build

OUT="tripcraft-images.tar.gz"
echo "▶ docker save → $OUT …"
docker save tripcraft-mysql:local tripcraft-backend:local tripcraft-web:local | gzip > "$OUT"

echo "✓ 완료: $(pwd)/$OUT ($(du -h "$OUT" | cut -f1))"
echo
echo "전달할 파일:"
echo "  - $OUT"
echo "  - docker-compose.local.yml"
echo "  - backend-secrets.local.env.example  (받는 사람이 .local.env 로 복사)"
echo
echo "받는 사람 실행:"
echo "  gunzip -c $OUT | docker load"
echo "  cp backend-secrets.local.env.example backend-secrets.local.env"
echo "  docker compose -f docker-compose.local.yml up -d --no-build"
echo "  → http://localhost:8095"
