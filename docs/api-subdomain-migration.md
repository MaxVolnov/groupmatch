# Перенос API на `api.groupmatch.app` через Cloudflare

**Дата:** 3 августа 2026
**Проблема:** у части российских провайдеров (подтверждено на dom.ru) XHR-запросы
к `groupmatch-production.up.railway.app` висят ~1.3 минуты и не завершаются.
Фронтенд при этом грузится — он на Vercel и идёт через Cloudflare. Похоже на
троттлинг диапазонов Railway/AWS у провайдера.

**План:** поставить перед бэкендом Cloudflare — завести `api.groupmatch.app`
с проксированием (оранжевое облако). Тогда клиент из РФ ходит на IP Cloudflare,
у которого с российскими провайдерами проблем нет, а до Railway дотягивается
уже сам Cloudflare.

> **Порядок важен.** Прод-переменные меняются в самом конце, когда новый домен
> уже отвечает. Если поменять их раньше, сломается доступ у всех, а не только
> у проблемных провайдеров.

---

## 0. Что уже сделано в коде (в этой ветке, не в проде)

| Файл | Что |
|---|---|
| `.env.example` | справочный блок с прод-значениями и пометками, что меняется, а что нет |
| `scripts/smoke-test.sh` | дефолтный `BASE_URL` → `https://api.groupmatch.app`; прямой адрес Railway можно передать первым аргументом |
| `docs/api-subdomain-migration.md` | этот файл |

Ветку **не мержить и не деплоить**, пока не пройден шаг 5.

---

## 1. Cloudflare: CNAME

Dash → зона `groupmatch.app` → **DNS** → **Add record**

| Поле | Значение |
|---|---|
| Type | `CNAME` |
| Name | `api` |
| Target | `groupmatch-production.up.railway.app` |
| Proxy status | **сначала DNS only (серое облако)** — см. ниже |
| TTL | Auto |

**Почему сначала серое облако.** Railway выпускает Let's Encrypt-сертификат для
кастомного домена сам и для этого должен получить валидационный запрос напрямую.
Если сразу включить проксирование, Cloudflare перехватит проверку и выпуск
зависнет в статусе pending. Правильный порядок: серое облако → дождаться, пока
Railway выдаст сертификат (шаг 2) → переключить на оранжевое (шаг 3).

## 2. Railway: custom domain

Dash → проект → сервис бэкенда → **Settings → Networking → Custom Domain** →
**+ Custom Domain** → ввести `api.groupmatch.app`.

Railway покажет целевой CNAME — он должен совпасть с тем, что уже прописан
(`groupmatch-production.up.railway.app`). Если Railway выдаёт другой target
(например `*.railway.app` вида `xxx.up.railway.app` с иным префиксом) —
**использовать тот, что показал Railway**, и поправить запись в Cloudflare.

Дождаться, пока домен перейдёт в состояние с выданным сертификатом (обычно
пара минут, иногда до получаса). Проверить:

```bash
curl -sI https://api.groupmatch.app/actuator/health | head -1
# ожидаем: HTTP/2 200
```

На этом шаге домен работает **без** Cloudflare-проксирования — то есть проблему
провайдера ещё не решает. Это промежуточная точка.

## 3. Cloudflare: включить проксирование

Та же DNS-запись → Proxy status → **Proxied (оранжевое облако)**.

**Перед включением проверить SSL-режим зоны:** Dash → **SSL/TLS → Overview**.
Должно быть **Full (strict)**. Если стоит **Flexible**, Cloudflare пойдёт в
Railway по HTTP, Railway отдаст редирект на HTTPS, и получится бесконечный
цикл редиректов. Если **Full** без strict — работает, но сертификат origin не
проверяется; для нашего случая Railway отдаёт валидный LE-сертификат, так что
Full (strict) корректен и безопаснее.

Проверить, что трафик реально пошёл через Cloudflare:

```bash
curl -sI https://api.groupmatch.app/actuator/health | grep -iE "^HTTP|server|cf-ray"
# ожидаем: server: cloudflare  и заголовок cf-ray
```

## 4. TRUSTED_PROXIES — обязательно, иначе поедет rate-limit

После проксирования цепочка становится: **клиент → Cloudflare → Railway → приложение**.

`ClientIpResolver` доверяет `X-Forwarded-For` только от доверенного пира и берёт
из цепочки самый правый недоверенный хоп. Сейчас в `app.trusted-proxies` только
loopback и приватные диапазоны, поэтому самым правым недоверенным окажется
**IP Cloudflare**, а не пользователь. Последствия:

- rate-limit на `/auth/signin` и `/auth/signup` начнёт считать всех, кто пришёл
  через один PoP Cloudflare, **одним общим счётчиком** → массовые 429 на ровном
  месте;
- IP-allowlist вебхука ЮKassa (`YOOKASSA_WEBHOOK_IP_CHECK`) сравнивал бы адрес
  Cloudflare с диапазонами ЮKassa. Сейчас флаг выключен, но при включении
  сломается.

**Что сделать:** добавить в `TRUSTED_PROXIES` диапазоны Cloudflare — актуальный
список берётся отсюда (я не привожу его по памяти, чтобы не занести устаревший):

```bash
curl -s https://www.cloudflare.com/ips-v4
curl -s https://www.cloudflare.com/ips-v6
```

Итоговое значение — текущий список плюс диапазоны Cloudflare, через запятую:

```
TRUSTED_PROXIES=127.0.0.1/32,::1/128,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16,100.64.0.0/10,fd00::/8,<сюда диапазоны Cloudflare>
```

**Как убедиться, что IP резолвится верно** (после шага 5, на живом домене):

1. Railway → Variables → временно `LOGGING_LEVEL_COM_GROUPMATCH_FILTER=DEBUG`
   (или `SPRING_PROFILES_ACTIVE=dev`, но тогда включится весь DEBUG).
2. Сделать один запрос: `curl -s https://api.groupmatch.app/api/v1/groups -o /dev/null`
3. В логах найти строку `RateLimit check: ip=...` и сверить с собственным
   публичным адресом (`curl -s ifconfig.me`).
4. Совпало — вернуть уровень логов обратно. Не совпало (виден IP Cloudflare) —
   список диапазонов неполный или не применился.

Точное поведение зависит от того, дописывает ли Railway свой хоп в XFF или
заменяет заголовок — проверить из окружения разработки я не могу, поэтому шаг
именно проверочный, а не «настроил и забыл».

## 5. Проверка ДО смены прод-переменных

Новый домен уже работает, но приложение ещё ходит на старый адрес — можно
проверять без риска.

```bash
# 1. Здоровье через новый домен
curl -sI https://api.groupmatch.app/actuator/health | head -1

# 2. Полный смок против нового домена
scripts/smoke-test.sh https://api.groupmatch.app https://groupmatch.app

# 3. Сравнение со старым адресом — оба должны отвечать одинаково
scripts/smoke-test.sh https://groupmatch-production.up.railway.app https://groupmatch.app

# 4. CORS: preflight с боевого origin
curl -sI -X OPTIONS https://api.groupmatch.app/api/v1/auth/signin \
  -H 'Origin: https://groupmatch.app' \
  -H 'Access-Control-Request-Method: POST' | grep -i "access-control"
```

**Главная проверка — из проблемной сети.** Всё вышеперечисленное с машины
разработчика ничего не доказывает: проблема воспроизводится только у части
провайдеров. Нужен доступ к dom.ru-подключению (свой канал, знакомый, телефон
с этим провайдером через мобильный интернет не подойдёт — нужен именно
домашний). Там:

```bash
curl -w '\nвсего: %{time_total}s\n' -o /dev/null -s https://api.groupmatch.app/actuator/health
curl -w '\nвсего: %{time_total}s\n' -o /dev/null -s https://groupmatch-production.up.railway.app/actuator/health
```

Первый должен отвечать за доли секунды, второй — воспроизвести зависание.
**Пока это не подтверждено, дальше не идти:** если гипотеза про блокировку
диапазонов неверна, смена домена ничего не починит, а риск сломать работающее
останется.

## 6. Смена прод-переменных

Только после подтверждённого шага 5.

**Railway → Variables:**

| Переменная | Было | Стало |
|---|---|---|
| `API_BASE_URL` | `https://groupmatch-production.up.railway.app` | `https://api.groupmatch.app` |
| `TRUSTED_PROXIES` | приватные диапазоны | + диапазоны Cloudflare (шаг 4) |
| `CORS_ALLOWED_ORIGINS` | `https://groupmatch.app` | **не трогать** |
| `APP_BASE_URL` | `https://groupmatch.app` | **не трогать** |

`API_BASE_URL` влияет на ссылку `.ics`-подписки: она отдаётся клиенту и
сохраняется у него в календаре. Старые уже выданные ссылки продолжат работать,
пока жив прямой домен Railway, — новые будут выдаваться уже на `api.`.

**Vercel → Settings → Environment Variables → Production:**

| Переменная | Было | Стало |
|---|---|---|
| `VITE_API_URL` | `https://groupmatch-production.up.railway.app` | `https://api.groupmatch.app` |

Значение вшивается в бандл на этапе сборки, поэтому после изменения нужен
**Redeploy** (Deployments → последний → ⋯ → Redeploy). Перезапуска без пересборки
недостаточно.

## 7. Деплой правок кода

Смержить эту ветку в `develop`, затем в `main`.

## 8. Постпроверка

```bash
scripts/smoke-test.sh                       # дефолт теперь api.groupmatch.app
curl -s https://groupmatch.app/promo -o /dev/null -w '%{http_code}\n'
```

Плюс вручную: зайти в приложение, создать группу, открыть вкладку встреч и
проверить, что ссылка на `.ics`-подписку начинается с `https://api.groupmatch.app`.

---

## Что стоит иметь в виду

- **Прямой адрес Railway остаётся рабочим.** Откат — вернуть две переменные и
  сделать redeploy Vercel; DNS-запись можно не трогать. Это дёшево, и это
  главная причина, почему порядок шагов именно такой.
- **Кэширование.** Cloudflare по умолчанию кэширует статику по расширениям, а
  `/api/v1/**` отдаёт `application/json` и под правила не попадает. Отдельно
  стоит посмотреть на `/api/v1/groups/{id}/calendar.ics`: расширение `.ics`,
  и контроллер сам ставит `Cache-Control: max-age=900`. Ключ кэша включает
  query-строку с токеном, так что перепутать фиды двух групп нельзя, но если
  поведение не устраивает — завести Cache Rule на `/api/*` с Bypass.
- **Таймаут Cloudflare** на бесплатном плане — 100 секунд до ошибки 524. Наши
  запросы укладываются с большим запасом; отдельного внимания требуют только
  долгие операции, если такие появятся.
- **Rate limit самого Cloudflare** на бесплатном плане не включён по умолчанию —
  наш bucket4j остаётся единственным ограничителем.
