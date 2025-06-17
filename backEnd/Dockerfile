FROM gradle:8.5-jdk21-alpine as builder
WORKDIR /build

# 그래들 파일이 변경되었을 때만 새롭게 의존패키지 다운로드 받게함.
COPY build.gradle settings.gradle /build/
RUN gradle build -x test --parallel --continue > /dev/null 2>&1 || true

# 빌더 이미지에서 애플리케이션 빌드
COPY . /build
RUN gradle build -x test --parallel

# APP
FROM openjdk:21-jre-slim
WORKDIR /app

# 필요한 패키지 설치
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 빌더 이미지에서 jar 파일만 복사
COPY --from=builder /build/build/libs/BidFlow-0.0.1-SNAPSHOT.jar app.jar

# 로그 디렉토리 생성
RUN mkdir -p /app/logs

EXPOSE 8080

# Health check 추가
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# Spring Boot 애플리케이션 실행
ENTRYPOINT ["java", \
   "-Djava.security.egd=file:/dev/./urandom", \
   "-Dspring.profiles.active=prod", \
   "-jar", \
   "app.jar"]