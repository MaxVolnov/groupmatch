# Сборка бэкенда GroupMatch (Spring Boot 4, Java 25, Gradle).
#
# ВАЖНО ПРО РАСПОЛОЖЕНИЕ. Файл лежит в корне репозитория намеренно: Timeweb
# App Platform ищет Dockerfile в корне контекста сборки, а контекст у неё —
# корень репозитория (это видно по её же логу: путь в ошибке был
# `backend/pom.xml`, то есть с префиксом директории проекта). Копия этого
# файла лежит в backend/Dockerfile на случай, если платформа или CI смотрят
# туда; содержимое обязано совпадать байт в байт, за этим следит шаг
# «Dockerfile copies in sync» в .github/workflows/ci.yml.
#
# Все пути в COPY — от корня репозитория. Если запускать сборку с контекстом
# backend/, они не разрешатся; см. комментарий в docs/timeweb-migration.md.

# ─── Stage 1: сборка ──────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jdk-alpine AS build

WORKDIR /app

# Сначала — только описание сборки и wrapper. Отдельным слоем, чтобы правка
# исходников не выбрасывала выкачанные зависимости из кэша.
COPY backend/gradlew ./
COPY backend/gradle gradle/
COPY backend/build.gradle.kts backend/settings.gradle.kts ./

# Прогрев кэша. `|| true` намеренно: задача dependencies падает, если какая-то
# конфигурация не резолвится в отрыве от исходников, но всё, что успело
# скачаться, уже осело в GRADLE_USER_HOME и переживёт этот слой. Настоящая
# сборка ниже упадёт по-честному, если чего-то не хватает.
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet || true

COPY backend/src/ src/

# Ровно та команда, что указана в дашборде Timeweb. Тесты исключены осознанно:
# интеграционные поднимают Testcontainers, а Docker внутри сборочного
# контейнера недоступен. Их гоняет CI.
RUN ./gradlew clean build -x test --no-daemon

# ─── Stage 2: рантайм ─────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre-alpine AS runtime

WORKDIR /app

# Корневой сертификат Timeweb: нужен, чтобы проверять подлинность сервера
# PostgreSQL. Путь обязан совпадать с sslrootcert в SPRING_DATASOURCE_URL —
# сейчас там /app/certs/timeweb-ca.crt.
#
# Проверять именно так, а не sslmode=require: require шифрует канал, но не
# проверяет, кто на том конце. Разница между «трафик не прочитают» и «мы
# точно говорим с нашей базой» — вторая половина как раз про подмену.
#
# Если билдер платформы окажется без доступа наружу — положить файл в
# backend/certs/timeweb-ca.crt и заменить ADD на COPY. Сертификат публичный,
# коммитить его не запрещено; вопрос только в воспроизводимости сборки.
ADD https://st.timeweb.com/cloud-static/ca.crt /app/certs/timeweb-ca.crt

RUN addgroup -S appgroup && adduser -S appuser -G appgroup \
    && chmod 0444 /app/certs/timeweb-ca.crt

# Имя артефакта фиксировано в build.gradle.kts (bootJar.archiveFileName).
# Копировать по маске *.jar нельзя: при двух файлах Docker требует, чтобы
# назначение было каталогом, и сборка падает.
COPY --from=build /app/build/libs/app.jar app.jar

USER appuser

# Spring и так слушает все интерфейсы, пока не задан server.address, — но в
# контейнере цена ошибки высокая, поэтому фиксируем явно.
ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=8080

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
