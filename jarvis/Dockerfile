# Build stage (Maven)
FROM maven:3.9.11-eclipse-temurin-21 AS builder
WORKDIR /app

# Copy build metadata first to leverage Docker layer cache.
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./
RUN chmod +x mvnw
RUN ./mvnw -q -DskipTests dependency:go-offline

COPY src ./src
RUN ./mvnw -q -DskipTests clean package spring-boot:repackage

# Runtime stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S jarvis && adduser -S jarvis -G jarvis
COPY --from=builder /app/target/*.jar app.jar
RUN chown jarvis:jarvis app.jar

USER jarvis
EXPOSE 8080

# -Xmx384m: cabe en instancias pequeñas dejando margen para SO y nginx.
ENTRYPOINT ["java", "-Xmx384m", "-Xms128m", "-XX:+UseContainerSupport", "-jar", "app.jar"]
