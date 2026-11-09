# syntax=docker/dockerfile:1

# ---------- Stage 1: build ----------
# The full JDK + Maven image is only needed to compile the application.
FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

# Copy the POM first and download dependencies separately. Docker caches this
# layer, so source-only changes do not re-download the whole dependency tree.
COPY pom.xml .
RUN mvn -B -q -DskipTests dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Stage 2: runtime ----------
# The runtime image contains only a JRE and the built jar: no Maven, no sources,
# no build cache. This is the main reason the final image is much smaller and
# has a smaller attack surface.
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S app && adduser -S app -G app

WORKDIR /app
COPY --from=build /workspace/target/*.jar /app/app.jar

USER app
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=5 \
  CMD wget -qO- http://127.0.0.1:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
