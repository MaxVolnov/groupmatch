# ─── Stage 1: Build ───────────────────────────────────────────────────────────
# Используем официальный образ с Gradle — не нужен gradle-wrapper.jar в репо
FROM gradle:jdk25-alpine AS build

WORKDIR /app

# Копируем только конфиги зависимостей — Docker кэширует этот слой
COPY build.gradle.kts settings.gradle.kts ./
RUN gradle dependencies --no-daemon --quiet || true

# Копируем исходники и собираем fat JAR (без запуска тестов)
COPY src/ src/
RUN gradle bootJar --no-daemon --quiet -x test

# ─── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

# Не запускаем под root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
