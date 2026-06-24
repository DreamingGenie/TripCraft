# 로컬용 mysql — 스키마 + 관광지 시드를 이미지에 구워 자족화(레포 없이도 docker load 로 시드됨).
# build context = 레포 루트.
FROM mysql:8.0
COPY docs/02_design/schema.sql                    /docker-entrypoint-initdb.d/01-schema.sql
COPY deploy/seed/02-attraction-seed.sql.gz        /docker-entrypoint-initdb.d/02-attraction-seed.sql.gz
