FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q verify

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache ffmpeg
RUN addgroup -S fire && adduser -S fire -G fire
WORKDIR /app
RUN mkdir -p /app/data/media && chown -R fire:fire /app/data
COPY --from=builder /workspace/target/fire-api-*.jar /app/fire-api.jar
USER fire
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/fire-api.jar"]
