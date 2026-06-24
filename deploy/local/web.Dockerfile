# 로컬용 web — 프론트 dist + app-nginx.conf 를 이미지에 구워 자족화(볼륨 마운트 불필요).
# build context = 레포 루트. (frontend/.env.production 이 있으면 빌드 시 인라인; 없어도 빌드됨
#  — 이메일 로그인·협업은 상대경로라 동작. 네이버지도·카카오는 콘솔에 localhost 등록 필요.)
FROM node:20-alpine AS build
WORKDIR /src
COPY frontend/package*.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

FROM nginx:1.27-alpine
COPY --from=build /src/dist /usr/share/nginx/html
COPY deploy/app-nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
