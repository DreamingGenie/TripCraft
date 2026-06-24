# ── build: Vite 프로덕션 빌드(.env.production 의 VITE_* 인라인) ──
FROM node:20-alpine AS build
WORKDIR /src
COPY package*.json ./
RUN npm ci
COPY . .
# vite build 는 production 모드 → .env.production 자동 로드(빌드 컨텍스트에 있어야 함)
RUN npm run build

# ── serve: nginx 가 SPA dist 서빙 + /api·/ws·/uploads 리버스 프록시(HTTP only) ──
# app-nginx.conf 를 docker-compose 가 conf.d/default.conf 로 마운트. TLS·도메인은 호스트 nginx 담당.
FROM nginx:1.27-alpine
COPY --from=build /src/dist /usr/share/nginx/html
EXPOSE 80
