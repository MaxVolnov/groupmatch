# Сборка контекстом от корня репозитория: пути в COPY относительны корня,
# а не backend/. Так собирал Railway, так же настраивается App Platform
# (Dockerfile path = backend/Dockerfile, build context = корень).

# ─── Stage 1: сборка ──────────────────────────────────────────────────────────
FROM gradle:jdk25-alpine AS build

WORKDIR /app

# Отдельным слоем — только описание зависимостей, чтобы правка исходников
# не инвалидировала выкачанный кэш Gradle.
COPY backend/build.gradle.kts backend/settings.gradle.kts ./
COPY backend/gradle gradle/
COPY backend/gradlew ./
RUN gradle dependencies --no-daemon --quiet || true

COPY backend/src/ src/
RUN gradle bootJar --no-daemon --quiet -x test

# ─── Stage 2: рантайм ─────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

# Корневой сертификат Timeweb: нужен, чтобы проверять подлинность сервера
# PostgreSQL. Тянем на этапе сборки, а не храним в репозитории, — но если
# билдер окажется без доступа наружу, сертификат можно положить в
# backend/certs/timeweb-ca.crt и заменить эту строку на COPY. Файл публичный,
# коммитить его не запрещено; вопрос только в воспроизводимости сборки.
#
# Проверять именно так, а не sslmode=require: require шифрует канал, но не
# проверяет, кто на том конце. Разница между «трафик не прочитают» и «мы
# точно говорим с нашей базой» — вторая половина как раз про подмену.
ADD https://st.timeweb.com/cloud-static/ca.crt /app/certs/timeweb-ca.crt

RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && chmod 0444 /app/certs/timeweb-ca.crt
USER appuser

# Имя задано в build.gradle.kts (bootJar.archiveFileName) — по маске *.jar
# копировать нельзя: если рядом окажется второй jar, COPY потребует, чтобы
# назначение было каталогом, и сборка упадёт.
COPY --from=build /app/build/libs/app.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
