# GroupMatch

**GroupMatch** — веб-приложение для координации расписания группы и планирования встреч. Участники указывают свободное время, приложение строит тепловую карту пересечений, а владелец группы выбирает оптимальный слот.

## Возможности

- **Управление группами** — создание групп, приглашение участников по одноразовой ссылке, блокировка новых вступлений
- **Указание доступности** — каждый участник добавляет свои свободные окна; окна сохраняются между неделями
- **Тепловая карта пересечений** — 30-минутная сетка, раскрашенная по числу участников; клик по заполненному слоту предзаполняет форму создания встречи
- **Встречи** — владелец планирует встречи из тепловой карты или вручную; экспорт в `.ics`
- **Тёмная тема** — переключатель светлой / тёмной / системной темы, сохраняется в `localStorage`
- **Профиль пользователя** — изменение имени и домашнего часового пояса
- **Обратная связь** — встроенная форма обратной связи (баги, пожелания, прочее)
- **Панель администратора** (Phase 5, в планах) — модерация пользователей и групп

## Стек технологий

| Слой | Технология |
|---|---|
| Фронтенд | React 18, TypeScript 5, Vite 5, Tailwind CSS 3 |
| Состояние | Zustand (persist middleware) |
| Загрузка данных | TanStack Query v5 |
| Даты | Luxon |
| Бэкенд | Java 25, Spring Boot 4 |
| База данных | PostgreSQL 16 |
| Миграции | Flyway |
| Авторизация | JWT (access + refresh токены) |
| Тесты | JUnit 5, Testcontainers |

## Продуктовые URL

| Сервис | URL |
|---|---|
| Фронтенд | https://maxvolnov.github.io/groupmatch/ |

## Локальная разработка

### Необходимое ПО

- Node.js 20+
- Java 25 (или JDK, совместимый со Spring Boot 4)
- Docker (для PostgreSQL через Testcontainers или локальный инстанс)

### Фронтенд

```bash
cd frontend
npm install
npm run dev                  # http://localhost:5173
```

Установите `VITE_MOCK_API=true` в `.env.local` для работы только в браузере с mock-данными (бэкенд не нужен). Установите `VITE_API_URL` для подключения к локальному бэкенду.

### Бэкенд

```bash
cd backend
# Запустите локальный Postgres или используйте Testcontainers для тестов
./gradlew bootRun            # http://localhost:8080
```

Переменные окружения бэкенда:

| Переменная | Описание |
|---|---|
| `SPRING_DATASOURCE_URL` | JDBC URL, например `jdbc:postgresql://localhost:5432/groupmatch` |
| `SPRING_DATASOURCE_USERNAME` | Пользователь БД |
| `SPRING_DATASOURCE_PASSWORD` | Пароль БД |
| `JWT_SECRET` | 256-битный секрет для подписи JWT |
| `SPRING_REDIS_URL` | URL Redis, например `redis://localhost:6379` |
| `CORS_ALLOWED_ORIGINS` | Разрешённые origins через запятую, например `http://localhost:5173` |

### Запуск тестов

```bash
cd backend
./gradlew test   # требуется Docker для Testcontainers
```

## Структура проекта

```
groupmatch/
├── frontend/
│   ├── src/
│   │   ├── api/          # Обёртки Axios + mock-слой
│   │   ├── components/   # Переиспользуемые UI-компоненты
│   │   ├── pages/        # Страницы-маршруты
│   │   ├── store/        # Zustand-сторы (auth, theme)
│   │   └── types/        # Общие TypeScript-типы
│   └── ...
└── backend/
    └── src/main/java/com/groupmatch/
        ├── controller/   # REST-контроллеры
        ├── domain/       # JPA-сущности + enum'ы
        ├── dto/          # Записи запросов / ответов
        ├── repository/   # Spring Data JPA репозитории
        └── service/      # Бизнес-логика
```

## Участие в разработке

- Ветки `feature/*` создаются от `develop`; PR открывается обратно в `develop`
- Ветки `hotfix/*` создаются от `main`; PR в `main` и в `develop`
- `develop` → `main` мержится при релизе
- CI (lint, type-check, тесты, сборка) должен пройти перед любым слиянием
