FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q verify

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S fire && adduser -S fire -G fire
WORKDIR /app
COPY --from=builder /workspace/target/fire-api-*.jar /app/fire-api.jar
USER fire
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/fire-api.jar"]
