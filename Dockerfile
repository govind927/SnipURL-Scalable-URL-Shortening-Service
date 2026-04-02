# ─────────────────────────────────────────────────────────────────
# Multi-Stage Dockerfile — URL Shortener
#
# Stage 1 (builder): Compiles the Java source → produces a JAR
# Stage 2 (runtime): Minimal JRE image — runs only the JAR
#
# Why multi-stage?
#  - Builder image includes Maven + full JDK (~500MB)
#  - Runtime image is just JRE + JAR (~180MB)
#  - Final image is lean, faster to deploy, smaller attack surface
#
# Build:  docker build -t url-shortener .
# Run:    docker run -p 8080:8080 --env-file .env url-shortener
# ─────────────────────────────────────────────────────────────────

# ── Stage 1: Build ───────────────────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy pom.xml first and download dependencies separately.
# This layer is cached — dependencies only re-download when pom.xml changes.
COPY pom.xml .
RUN mvn dependency:go-offline -q

# Copy source and build
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2: Runtime ─────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Non-root user — security best practice
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy only the fat JAR from the build stage
COPY --from=builder /app/target/url-shortener-1.0.0.jar app.jar

# Expose app port
EXPOSE 8080

# JVM tuning for containers:
#  -XX:+UseContainerSupport        → respect container memory limits
#  -XX:MaxRAMPercentage=75.0       → use 75% of container RAM for heap
#  -Djava.security.egd=...         → faster startup (no blocking entropy)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
