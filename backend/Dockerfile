# ── build: 프로젝트 gradlew(래퍼 버전 고정)로 bootJar ──
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY . .
RUN chmod +x gradlew && ./gradlew bootJar --no-daemon -x test

# ── runtime ──
FROM eclipse-temurin:21-jre
WORKDIR /app
# Spring Boot 실행 가능 jar(*-plain.jar 제외)
COPY --from=build /src/build/libs/*-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
# DB·키 등 환경변수는 docker-compose 에서 주입(application.yml 의 ${...} 해석)
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
