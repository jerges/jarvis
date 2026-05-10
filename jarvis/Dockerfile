# Build stage
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app

COPY gradlew gradlew.bat ./
COPY gradle ./gradle
RUN chmod +x gradlew

# Cache dependencies
COPY build.gradle settings.gradle ./
RUN ./gradlew dependencies --no-daemon -q 2>/dev/null || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S jarvis && adduser -S jarvis -G jarvis
COPY --from=builder /app/build/libs/*.jar app.jar
RUN chown jarvis:jarvis app.jar

USER jarvis
EXPOSE 8080

# -Xmx384m: cabe en t3.micro (1 GB RAM) dejando margen para el SO y nginx
ENTRYPOINT ["java", "-Xmx384m", "-Xms128m", "-XX:+UseContainerSupport", "-jar", "app.jar"]
