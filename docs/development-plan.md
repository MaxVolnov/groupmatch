# GroupMatch — Development Plan

> Last updated: 2026-07-31
> Stack: React 18 + TypeScript + Vite + Tailwind → Vercel (`groupmatch.app`)
> Backend: Spring Boot 4 + Java 25 + PostgreSQL 16 + Redis 7 → Railway
> Auth: Custom JWT (access 15 min / refresh 14 days regular / 90 days guest) + Argon2id

---

## ✅ Phase 1 — Foundation

- Project scaffolding: Vite + React 18 + TypeScript, Spring Boot 4 + Gradle
- CI pipeline (GitHub Actions): lint, type-check, test, build
- Database schema: `app_user`, JWT auth tables (Flyway V1–V2)
- Sign-up / sign-in pages with JWT access + refresh token flow
- Protected routes, Zustand auth store with `persist`
- Axios instance with automatic token refresh interceptor

---

## ✅ Phase 2 — Groups

- `groups` table and CRUD API (V3 migration)
- Dashboard: list user's groups, create group form
- Group page with tab navigation (Availability / Heatmap / Meetings / Members)
- Invite link system: `group_invites` table (V4), token-based join flow
- Member management: role/status (OWNER / MEMBER, ACTIVE / LEFT / BANNED)
- Group settings: lock, show/hide participant names

---

## ✅ Phase 3 — Availability & Heatmap

- `availability` table (V5 migration)
- Availability tab: add/delete time windows, 44 px touch targets
- Heatmap API: server-side aggregation into 30-minute slots, member name overlay
- Heatmap tab: week navigator, colour gradient (0 → max count), tooltip with names

---

## ✅ Phase 4 — Meetings & Polish (v0.4)

### 4.1 Meetings
- `meetings` table (V6 migration)
- Meetings tab: create / delete meetings, export single meeting to `.ics`
- Heatmap slot click → pre-fill meeting form

### 4.2 Dark mode
- Tailwind `darkMode: 'class'`, `useThemeStore` (Zustand + persist)
- `ThemeToggle` button in nav (☀️ / 🌙 / 💻 cycle)

### 4.3 Skeletons, empty states, error messages
- `Skeleton` component, empty states with emoji, `ErrorMessage` component

### 4.4 Edit group & user profile
- `EditGroupModal`, `Profile` page (`/profile`)

### 4.5 Feedback form
- `feedback` table (V7), `FeedbackModal` with category select

---

## ✅ Phase 4.5 — Stability & Guest Mode (v0.4.5)

- Hotfix 0.4.1: group page crash fix
- `RateLimitFilter`: `Retry-After` header on 429
- Top-level `ErrorBoundary`
- Guest mode (V8): `POST /api/v1/auth/guest`, guest badge, guest join flow
- `eslint-plugin-react-hooks` enforced

---

## ✅ Phase 5 — Admin Panel & Stability (v0.5.0)

- ADMIN role + `AdminPromotionRunner` (V9)
- Admin: Users — ban/unban, role/plan change
- Admin: Feedback inbox — resolve/unresolve
- Admin: Groups — search, force-delete
- `GuestCleanupJob` — daily cleanup of stale guest accounts (V12)
- Hotfix 0.5.1: proper 401/403 from `SecurityConfig`

---

## ✅ Phase 6 — Notifications & Email (v0.6.0)

- `EmailService` via Resend HTTP API (Railway blocks SMTP port 587)
- Email verification (V13), password reset
- In-app notification bell with 30s polling (V14)
- `MeetingReminderJob` — email reminder 1h before meeting (V15)
- Notification preferences — 4 toggles on Profile (V16)
- Guest account upgrade flow
- Test refactor: 8 modular test classes, 52 integration tests

---

## ✅ Phase 7 — Monetization (v0.7.0)

- Heatmap cache invalidation bugfix
- PRO plan limits + `GET /api/v1/me/plan` (billing model: owners pay, members free)
- `/pricing` page + `UpgradeModal` + paywall on Dashboard
- Legal pages: `/about`, `/legal` (оферта + privacy, ИНН 771887947687), footer
- `/signup` terms checkbox
- ЮKassa backend: `subscription` table (V17), `YooKassaService` stub mode, webhook
- Subscription UI on Profile: Plan & Billing section
- `SubscriptionExpiryJob` — hourly downgrade on expiry
- EmailService: JavaMail SMTP → Resend HTTP API
- Guest refresh TTL: 14 days → 90 days
- Guest session banner on Dashboard
- `ErrorMessage.tsx` translated to English
- `InviteService` N+1 fixed
- CI fix: `BaseIntegrationTest` with GitHub Actions services fallback
- 68 integration tests, 65%+ coverage
- **`MONETIZATION_ENABLED=false` feature flag** — paywall and billing UI disabled by default; plan limit check gated behind flag; `createFourthGroupOnFreeplanReturns402` test gated with `@EnabledIfEnvironmentVariable`

---

## ✅ Phase 7.5 — Domain & Infrastructure (v0.7.5)

- `groupmatch.app` registered on Cloudflare
- Frontend migrated GitHub Pages → Vercel; `vercel.json` SPA rewrites
- `VITE_DEPLOY_TARGET=vercel` → Vite `base: '/'`
- DNS: Cloudflare CNAME → Vercel (DNS only, no proxy)
- `www.groupmatch.app` → 308 redirect to apex
- `groupmatch.app` verified in Resend; emails deliver to any recipient from `noreply@groupmatch.app`
- Railway env vars updated: `APP_BASE_URL`, `CORS_ALLOWED_ORIGINS`, `MAIL_FROM`
- Dynamic `<title>`: group pages show `{name} · GroupMatch`
- Authenticated users redirected away from `/signin` and `/signup`
- `404.html` GitHub Pages → `groupmatch.app` redirect

---

## ✅ Phase 8 — Русификация / i18n (v0.8.0)

**Цель:** русский язык по умолчанию, переключатель RU/EN в профиле. Достигнута.

### 8.1 Инфраструктура i18n
- `i18next` + `react-i18next`, `i18n.ts` конфиг (`lng: 'ru'`, fallback `en`)
- `useLanguageStore` (Zustand + persist), переключатель на странице Profile

### 8.2–8.4 Перевод UI
- Все 33 компонента/страницы переведены (nav, dashboard, формы, модалки, все табы группы, footer, admin panel, error/auth flow страницы)
- 322 ключа, паритет ru/en 1:1

### 8.5 Backend: локализация email
- `locale VARCHAR(5) DEFAULT 'ru'` на `app_user` (V18)
- Двуязычные email-шаблоны: верификация, сброс пароля, участник присоединился, приглашение (готов, не подключён), напоминание о встрече
- `PATCH /api/v1/users/me` принимает `locale`

### Bugfix: логаут через 15 минут (P0, закрыт в рамках Фазы 8)
Корневая причина — три независимых слоя:
- `JwtAuthenticationFilter` не имел `shouldNotFilter()` для публичных `/auth/**` путей — протухший `Authorization` заголовок валидировался и рвал запрос до контроллера
- Request interceptor слал `Authorization` на `/auth/*` эндпоинты, включая `/auth/refresh`
- Отсутствовал mutex на параллельные `refresh()` вызовы (interceptor + Zustand store)
- Подтверждено стабильной сессией 4+ часа после фикса

---

## ✅ Phase 9.0 — Промо-лендинг и гигиена публичных страниц (v0.8.1)

**Цель:** промо-лендинг для предпродажи + закрытие протечек на публичных маршрутах перед альфой. Достигнута.

### Промо-лендинг `/promo`
- Отдельная точка входа Vite (`frontend/promo.html` + `src/promo/`) — без React, без i18n-рантайма, ради корректного OG-превью для краулеров и веса основного бандла
- Брендовая палитра — Концепт 2 (синий), значения сняты с исходного макета дизайнера: `#040929` / `#055EC2` / `#6499D0`
- Копирайт: вечнозелёный триал «3 месяца Pro бесплатно с момента регистрации», без жёсткого дедлайна и без формулировок дефицита мест
- Никаких кнопок стороннего OAuth
- QR-код для печатных материалов — **вне кодовой базы**: генерируется вручную через QRCode Monkey из логотипа, в репозитории не хранится (ни библиотеки, ни изображения, ни скрипта). Ведёт на `https://groupmatch.app/promo` с UTM-меткой `utm_source=qr&utm_medium=print&utm_campaign=alpha`; коррекция ошибок H, логотип ~22% площади. Проверка сканером — ручная операция при каждой перегенерации. Со стороны кода поддерживается только то, от чего QR зависит: `/promo` отдаёт 200 и не ломает UTM-параметры (см. `src/promo/main.ts`)
- Временные ассеты: `logo.svg`/`favicon.svg` (векторные заглушки), `og-image.png`, favicon PNG-набор — заменятся после получения файлов от дизайнера

### Найденные и закрытые протечки
- **Кнопка отзыва в `Footer.tsx` была видна анонимам** на `/about`, `/legal`, `/pricing` без проверки авторизации; бэкенд отклонял запрос 401 (эндпоинт не в `permitAll()`). Гейт добавлен по `isAuthenticated` (гости сохранили доступ — подтверждено, что `FeedbackController`/`FeedbackService` не делают различий по роли)
- **Расхождение юрлица в футере** — `Footer.tsx` показывал `© 2026 Max Wave Studio`, независимая копия футера в `promo.html` — `© 2026 GroupMatch`. Синхронизировано дословно, включая формат ИНН и email
- **`/signin`, `/signup` были без шапки и футера вообще** — обёрнуты в `PublicLayout`, дублирующая ссылка (`Войти`/`Зарегистрироваться`) скрывается по текущему `pathname`
- **Баг в `src/promo/main.ts`**: UTM-прокидывание читало `cta.href` (свойство DOM, всегда абсолютный URL) вместо `getAttribute('href')` — при заходе с `www.groupmatch.app` навсегда записывало `www`-хост во все три CTA на регистрацию. Чинится чтением литерального атрибута
- **Инфраструктура (не код):** конфигурация доменов в Vercel имела `apex → 308 → www`, обратное задокументированному в Фазе 7.5 поведению. Исправлено в дашборде Vercel на `www → 308 → apex`, подтверждено `curl -I`

### В процессе / известные ограничения
- Вынос `/legal` и `/about` в отдельные статические точки входа (по образцу `/promo`) — см. ветку `refactor/decouple-legal-about`, только русский язык, английские версии отложены
- Скриншоты на `/promo` обрезаются под аспект `16:10`/`21:9` — не блокер
- `og-image.png`, `logo.svg`, favicon — временные, ждут финальных файлов от дизайнера

---

## Phase 9 — Стабилизация и технический долг (planned, pre-beta requirement)

**Цель:** закрыть находки полного аудита кодовой базы перед бета-тестом с учителями. Все пункты ниже — из аудита от 22.07.2026.

### 9.1 Критичные P0 (блокеры беты)

- **Гостевые аккаунты удаляются раньше обещанного срока** — `GuestCleanupJob` чистит по `created_at` (30 дней), UI обещает 90 дней (совпадает с refresh TTL). Активный гость теряет все данные на 31-й день. Исправить: считать от последней активности, согласовать с 90-дневным TTL (текст на `/legal` сегодня приведён в соответствие с ТЕКУЩИМ поведением джобы — «30 дней с момента создания» — это не решает сам P0, лишь убирает ложное обещание «90 дней» из юридического документа; после фикса этого пункта текст `/legal` нужно обновить снова)
- **Аудит разлогинов для обычных (не гостевых) пользователей** — 15-минутный P0 из Фазы 8 закрыт и подтверждён 4-часовой сессией, но отдельно поступила жалоба на разлогин «примерно раз в 2 недели» у обычных юзеров. Это не тот же баг — вероятно, TTL refresh-токена (14 дней) или его ротация. Нужно проверить отдельно, не считать закрытым автоматически вместе с гостевым багом
- **Webhook ЮKassa без проверки подписи** — `YooKassaService.handleWebhook()` публичный, принимает любой POST и активирует подписку/повышает план без валидации источника. Добавить проверку подписи + IP-диапазонов ЮKassa
- **Обход rate limit через X-Forwarded-For** — `RateLimitFilter.resolveClientIp()` доверяет заголовку без проверки доверенного прокси; позволяет обойти лимит на `/signin` (брутфорс паролей). Брать IP из `RemoteAddr` за настроенным trusted-proxy

### 9.2 Важные P1 (закрыть до или сразу после запуска беты)

- **Backend-сообщения об ошибках не переведены** — `ErrorMessage.tsx` использует английский текст исключений как приоритетный источник поверх переведённых фолбэков; русскоязычный пользователь видит английские ошибки при неверном пароле, бане, лимите плана. Ветвиться по `code`, переводить на фронте
- **Невалидированные параметры хитмапа** — `granularityMinutes`, `from`/`to` не ограничены; `granularityMinutes=1` на большом окне создаёт ~500k+ бакетов за один запрос. Добавить `@Min/@Max`, ограничить окно (напр. 31 день)
- **N+1 при создании встречи** — `MeetingService.createMeeting()` вызывает `notificationPreferencesService.getOrCreate()` в цикле по каждому участнику. Пакетная загрузка одним запросом
- **Список встреч без пагинации** — `MeetingRepository.findByGroupIdOrderByStartsAtDesc` растёт неограниченно. Добавить `Pageable`
- **`Retry-After` не доезжает до браузера** — CORS не имеет `exposedHeaders`, фикс из Фазы 4.5 инертен в проде. Добавить `setExposedHeaders(List.of("Retry-After"))`
- **Rate limit не покрывает reset-password/verify-email** — оба `permitAll`, оба без лимита (перебор токенов)
- **Активный GitHub Pages workflow** — `.github/workflows/deploy-frontend.yml` всё ещё пушит устаревшую копию на GH Pages при каждом мерже в `main`. Удалить или задизейблить
- **`vite.config.ts` base зависит от недокументированной env переменной** — `VITE_DEPLOY_TARGET=vercel` задана только в дашборде Vercel, не зафиксирована в репо. Preview-деплой без этой переменной ломает все пути к ассетам
- **`GlobalExceptionHandler.handleGenericException()` не логирует** — 500-ки в проде невидимы. Добавить `log.error` со стектрейсом
- **DEBUG-логи в проде** — `application.yml` без prod-профиля, `com.groupmatch` и `org.springframework.security` на DEBUG в проде. Завести `application-prod.yml` с `INFO`
- **`<html lang="en">` не синхронизирован с i18n** — жёстко задан на английском при русском дефолте. Обновлять `document.documentElement.lang` в `setLanguage`
- **Модалки без focus trap / aria-атрибутов** — `Modal.tsx` без `role="dialog"`, `aria-modal`, без возврата фокуса
- **Форма апгрейда гостя без `<label>`** — только placeholder на трёх полях в `Profile.tsx`

### 9.3 Дешёвые фиксы (batch, low-risk)

- Удалить мёртвый код: `404.html`, SPA-shim в `index.html`, `*.yml.disabled` workflow-файлы, `Dockerfile`/`nginx.conf` фронта (или задокументировать как запасной путь), таблицу `report` (схема без сущности/репозитория/контроллера)
- Убрать отладочные `console.warn('[Interceptor]'...)` из `axios.ts`/`auth.ts` (остались после дебага логаут-бага) — спрятать за `import.meta.env.DEV` либо удалить
- `VITE_API_URL` без дефолта — падать на старте прод-сборки, если переменная не задана, а не молча идти на `localhost:8080`
- Дублирующиеся индексы: `idx_feedback_created_at` создаётся дважды (V7 + V11), явные индексы на `token` в V13 дублируют `UNIQUE`
- Отсутствующий индекс на `yookassa_payment_id` (`V17`) — основной путь вебхука
- `maskToken()` продублирован в `AuthService`/`RefreshTokenService` — вынести в общий `TokenUtils`
- `RefreshTokenService.issue()`/`issueForGuest()` — идентичные тела, отличается только TTL — объединить в один приватный метод
- Автовыставление конца слота в `AvailabilityTab.tsx` — при выборе начала, если конец не задан или равен началу, ставить конец = начало + 1 час (QoL, найдено при съёмке скриншотов для лендинга)

### 9.4 Рефакторинг (можно параллельно/после запуска)

- `GroupAccessGuard` вместо 6 копий `requireOwner`/`requireActiveMember` в 4 сервисах
- `utils/datetime.ts` вместо продублированных `toIso`/`defaultDatetime`/`fmtRange` в 3 компонентах
- Цены (`19900`/`149000` копеек) вынести из `YooKassaService` в конфиг, отдавать фронту эндпоинтом вместо дублирования в `ru.json`/`en.json`/`Pricing.tsx`
- `YooKassaService`: `HttpClient.newHttpClient()` создаётся на каждый вызов без таймаутов → вынести в `@Bean` с таймаутами; sandbox/prod переключатель в конфиг
- Арифметика подписки (`periodMonths * 30` дней) → `Instant.plus(Period.ofMonths(n))` через явную зону
- Унифицировать формат ошибок бэкенда — сейчас три разных JSON-формы и две конвенции `code` (`snake_case` vs `UPPERCASE`) между `GlobalExceptionHandler`, `SecurityConfig`, `JwtAuthenticationFilter`, `RateLimitFilter`. Вынести `ErrorResponse` в `dto/`, сериализовать через `ObjectMapper` везде
- `RateLimitFilter` бакеты в `ConcurrentHashMap` без вытеснения → Caffeine с `expireAfterAccess`, либо Redis-backed Bucket4j (нужно и для multi-instance масштабирования)
- Пагинация: `AdminService.toGroupDto()` N+1 (`// TODO: optimize with JOIN COUNT` уже в коде), список участников группы (`findByGroupAndStatus`), список групп пользователя, `size` без верхней границы в `AdminController` (3 эндпоинта)
- `refresh:user:{id}` Redis set — мёртвые токены копятся между ротациями у долгоживущих (90 дней) гостевых сессий

### 9.5 Производительность фронта

- **Bundle: 496KB / 153KB gzip, без code-splitting** — все 15 роутов импортированы статически. `React.lazy` + `Suspense` минимум для `AdminPage` (660 строк), `Legal`, `About`, `Pricing`
- `luxon` — 256KB (21% бандла) ради нескольких `toFormat`/`fromISO`/`plus`. Оценить замену на `Intl.DateTimeFormat` + нативный `Date`, либо `dayjs` (~2KB)
- `i18n.ts` — оба словаря (36KB) грузятся в основной чанк сразу. Динамический импорт по выбранному языку

### 9.6 Тесты

- Сброс пароля: happy path + повторное использование/протухший токен
- Подтверждение email: happy path + edge cases
- Rate limiting: включая `Retry-After` и обход через X-Forwarded-For (после фикса 9.1)
- ЮKassa webhook: активация подписки + отклонение неподписанного запроса (после фикса 9.1)
- `locale` (V18): дефолт `ru`, валидация `ru|en`, `PATCH /me`

### 9.7 UX-доработка: цветовая индикация хитмапа

- Текущая реализация: монохромный зелёный градиент (`bg-green-100` → `bg-green-500`) по 5 ступеням интенсивности
- **Запрошено:** red → yellow → green градиент (красный = минимум пересечений, зелёный = максимум) для более интуитивного восприятия нетехническими пользователями
- Затронутые файлы: `HeatmapTab.tsx` (`intensityClass()`), легенда под таблицей

---

## 🎯 Дорожная карта до публичной беты (15 августа 2026)

**Бюджет:** ~34 часа работы + 6 часов запас, из расчёта 40 часов до старта.

| Блок | Приоритет | Часы | Комментарий |
|---|---|---|---|
| 9.1 P0-техдолг (гостевой TTL, подпись вебхука ЮKassa, обход rate-limit) | 🔴 must | 6ч | Блокеры беты |
| Аудит разлогинов обычных пользователей | 🔴 must | 4ч | См. новый пункт в 9.1 — отдельно от гостевого TTL-бага |
| Лендинг `/promo` | 🔴 must | 10ч | Без него нечего раздавать на предпродаже — **выполнено** в рамках Phase 9.0 |
| Псевдо-премиум промо (баннер после регистрации + бейдж в UI + блок на лендинге + флаг на бэке) | 🟡 should | 6ч | Блок на лендинге и копирайт триала — готовы (Phase 9.0); баннер после регистрации, бейдж в UI и honoring-механика на бэке — остаются |
| Календарь — урезать до `.ics`-подписки на группу (без OAuth и синхронизации занятости) | 🟡 should | 4ч | Соответствует подпункту `10.3` существующего плана; полная `10.1`/`10.2` (OAuth + двусторонний sync) остаётся в Phase 10, после запуска, не в бюджете 40ч |
| Буфер / тестирование / деплой | — | 4ч | |

**Что сознательно режем/переносим на после запуска:**
- OAuth целиком — юридический риск (см. задачу 4) + не влезает по времени
- Полная интеграция календарей (двусторонний sync Google/Apple/Yandex, `Phase 10.1`/`10.2`) — остаётся отдельной фазой после запуска
- P1/P2 техдолг (`9.2`–`9.4`) — не блокирует предпродажу
- ЮKassa live-credentials — не нужны, доступ раздаём бесплатно + промо-триал

**ТГ-канал** — контентная задача, не входит в девчасы, делается параллельно.

---

## Phase 10 — Calendar Integrations (planned, MVP requirement)

**Цель:** импорт занятости из Google Calendar как слоты доступности.

**Задачи:**

**10.1 Google OAuth 2.0**
- Регистрация OAuth app в Google Cloud Console
- `google-auth-library` на бэкенде
- `POST /api/v1/integrations/google/connect` — OAuth flow
- Хранение `access_token` + `refresh_token` в зашифрованном виде (V19)

**10.2 Импорт занятости**
- `GET /api/v1/integrations/google/sync` — читает busy slots из Google Calendar API
- Автоматически создаёт слоты недоступности в GroupMatch (инвертирует: занято → недоступен)
- Кнопка "Sync with Google Calendar" в `AvailabilityTab`

**10.3 Экспорт встреч**
- `.ics` feed URL для группы — `/api/v1/groups/{id}/calendar.ics`
- Одиночный экспорт встречи уже есть; добавить подписку на всю группу

---

## Post-MVP (backlog)

### Монетизация (после набора пользовательской базы)
- **Пре-реквизит: honoring триала беты.** `/promo` обещает Pro на 3 месяца с момента регистрации всем, кто зарегистрировался в бете. Механики нет: `AuthService.signup()` ставит `Plan.FREE` безусловно, поля даты истечения триала на `app_user` не существует. Сейчас не баг — лимиты не проверяются (`MONETIZATION_ENABLED=false`). Нужно до флипа флага: `planExpiresAt = created_at + 3 месяца` для аккаунтов, созданных до флипа; email-напоминание за 14 дней (по образцу `SubscriptionExpiryJob`); тест на неприкосновенность триала беты
- Активировать `MONETIZATION_ENABLED=true` в Railway
- Активировать ЮKassa credentials
- A/B тест ценообразования (199/мес vs 299/мес)
- Годовой план со скидкой

### Мобильное приложение
- React Native (Expo) — после стабилизации веб-версии
- Push-уведомления через Expo / FCM

### Прочее
- Telegram / WhatsApp уведомления
- Recurring availability patterns
- AI-подбор времени встречи
- ~~SSO (Google, GitHub)~~ — **исключено из планов.** По не финализированной юристом сверке (сверка от 31 июля 2026, требует подтверждения): с 7 июля 2026 действуют штрафы по ст. 13.55 КоАП за использование иностранных систем авторизации («Войти через Google/Apple»); легальные варианты — СМС, ЕСИА, российские ID-провайдеры (Яндекс ID, VK ID, Sber ID). Текущий кастомный JWT email/password под это регулирование не подпадает. Если OAuth понадобится — только через Яндекс ID/VK ID, это отдельная интеграция с нуля, не быстрый OAuth-коннектор
- WebSocket / SSE вместо polling для уведомлений
- "For Teams" лендинг + корпоративная форма
- Admin: audit log
- Smoke test изоляция (отдельный Railway environment)
- HttpOnly cookie для refresh token (сейчас localStorage) — отложено: фронт и бэк на разных доменах, нужен `SameSite=None`; переоценить после переноса бэка на `api.groupmatch.app`

---

## 🐛 Known bugs

- **Monthly/Yearly billing buttons on Profile non-functional** — stub mode; will be hidden when `MONETIZATION_ENABLED=true`
- **ЮKassa not yet active** — stub mode until credentials added to Railway

---

## 📋 Pre-release checklist

- [ ] `RESEND_API_KEY` rotated
- [ ] Favicon + OG image deployed
- [ ] Beta test with 2-3 real teachers completed
- [ ] `YOOKASSA_*` credentials added (or confirmed deferred)
- [ ] Phase 9.1 (P0 tech debt) closed
