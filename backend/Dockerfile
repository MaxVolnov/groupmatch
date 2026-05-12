# ─── Stage 1: Build ───────────────────────────────────────────────────────────
# Railway builds from repo root, so all COPY paths are relative to root
FROM gradle:jdk25-alpine AS build

WORKDIR /app

# Copy gradle configs — cached layer for dependencies
COPY backend/build.gradle.kts backend/settings.gradle.kts ./
COPY backend/gradle gradle/
COPY backend/gradlew ./
RUN gradle dependencies --no-daemon --quiet || true

# Copy sources and build fat JAR (skip tests)
COPY backend/src/ src/
RUN gradle bootJar --no-daemon --quiet -x test

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
