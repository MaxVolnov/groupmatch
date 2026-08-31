/**
 * Персональное OG-превью для ссылки-приглашения.
 *
 * Зачем. Фронт — SPA: краулер мессенджера получает пустой `index.html` с общей
 * подписью продукта и не исполняет JS, поэтому `react-helmet` и любые
 * рантайм-подмены `<meta>` до него не доходят. Человек, которому прислали
 * ссылку, видит рекламу инструмента вместо «кто и куда его зовёт».
 *
 * Как. `vercel.json` переписывает `/join/:token` сюда, но только если
 * `user-agent` похож на краулера. Люди по тому же адресу получают обычное SPA —
 * эта функция их не видит вовсе.
 *
 * Граница ответственности: функция обязана вернуть валидный HTML **всегда**.
 * Мёртвый токен, недоступный бэкенд, таймаут, мусор в адресе — всё это даёт
 * дефолтное превью и код 200. Сломанная карточка в мессенджере выглядит как
 * сломанный продукт, а не как сломанное приглашение.
 */

export const config = { runtime: 'edge' }

/** Столько ждём бэкенд. Краулер Telegram столько не ждёт — см. комментарий ниже. */
const BACKEND_TIMEOUT_MS = 2000

/**
 * Потолок на название группы и на заголовок целиком.
 *
 * Требование было «обрезать название до 60», но причина у него другая: режут
 * мессенджеры не название, а `og:title`, и режут посреди слова. Название в 60
 * символов плюс обрамление «X приглашает вас в группу «…»» даёт заголовок под
 * 92 — то есть буква требования выполнена, а смысл нет.
 *
 * Поэтому оба потолка сразу: название не длиннее 60 в любом случае, но если
 * из-за длинного имени пригласившего заголовок всё равно вылезает за 90 —
 * название ужимается ещё.
 */
const MAX_GROUP_NAME = 60
const MAX_TITLE = 90

/**
 * Дефолтные теги — копия того, что лежит в `index.html`.
 *
 * Копия, а не чтение файла: функция живёт на краю сети, лишний сетевой хоп за
 * собственной статикой стоит дороже, чем дублирование пяти строк. Расхождение
 * копии с оригиналом ловит `join.test.ts` — он читает `index.html` и сверяет.
 */
const DEFAULTS = {
  title: 'GroupMatch — общее время для встречи без переписок',
  description: 'Общая сетка свободного времени группы. Pro на 3 месяца бесплатно',
  image: 'https://groupmatch.app/og-image.jpg',
  imageAlt: 'GroupMatch — планирование встреч',
  siteName: 'GroupMatch',
} as const

const COPY = {
  ru: {
    invitedBy: (who: string, group: string) => `${who} приглашает вас в группу «${group}»`,
    invitedTo: (group: string) => `Вас приглашают в группу «${group}»`,
    description: 'Отметьте, когда вам удобно, — и увидите, когда свободны все.',
    body: 'Откройте ссылку в браузере, чтобы присоединиться к группе.',
  },
  en: {
    invitedBy: (who: string, group: string) => `${who} invites you to «${group}»`,
    invitedTo: (group: string) => `You are invited to «${group}»`,
    description: "Mark when you're free — and see when everyone is.",
    body: 'Open the link in a browser to join the group.',
  },
} as const

type Locale = keyof typeof COPY

/**
 * Экранирование под вставку в HTML — и в текст узла, и в значение атрибута.
 *
 * ⚠️ Это не косметика. `groupName` и `inviterName` вводят пользователи, а мы
 * подставляем их в `content="…"` на нашем домене. Без экранирования группа с
 * названием `"><script>…` закрывает атрибут и выполняет чужой код на
 * groupmatch.app.
 *
 * Амперсанд обязан идти первым: иначе он second-hand поломает уже вставленные
 * сущности, превратив `&lt;` в `&amp;lt;`.
 */
export function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

/**
 * Обрезка до {@link MAX_GROUP_NAME} символов — многоточие **входит** в лимит.
 *
 * Иначе `truncate(x, 60)` возвращал бы 61 символ, и всякий расчёт бюджета
 * заголовка вокруг него ошибался бы ровно на единицу. Именно так и вышло с
 * первой версией: заголовок получался 91 при потолке 90.
 *
 * Делается **до** экранирования: иначе в лимит попадали бы `&amp;` и `&#39;`, и
 * название с апострофами обрезалось бы втрое короче обычного.
 */
export function truncate(value: string, max = MAX_GROUP_NAME): string {
  const trimmed = value.trim()
  const chars = [...trimmed]
  if (chars.length <= max) return trimmed
  return chars.slice(0, Math.max(max - 1, 1)).join('').trimEnd() + '…'
}

/**
 * Язык превью. Русский по умолчанию; английский — только если в
 * `Accept-Language` явно есть `en` и при этом нет `ru`.
 *
 * Асимметрия намеренная: продукт русскоязычный, и человек с
 * `Accept-Language: en-US,ru;q=0.9` почти наверняка поймёт русский, а вот
 * обратное неверно.
 */
export function pickLocale(acceptLanguage: string | null): Locale {
  if (!acceptLanguage) return 'ru'
  const header = acceptLanguage.toLowerCase()
  const hasRu = /\bru\b/.test(header)
  const hasEn = /\ben\b/.test(header)
  return hasEn && !hasRu ? 'en' : 'ru'
}

/**
 * Токен — 48 hex-символов (`InviteService.TOKEN_BYTES = 24`). Всё, что не
 * похоже, до бэкенда не доводим: это либо опечатка, либо чужой сканер.
 */
function looksLikeToken(token: string | null): token is string {
  return !!token && /^[0-9a-f]{16,128}$/i.test(token)
}

interface InvitePreview {
  valid: boolean
  groupName?: string | null
  inviterName?: string | null
}

export interface PageInput {
  locale: Locale
  title: string
  description: string
  url: string
  body: string
}

export function renderHtml(input: PageInput): string {
  const { locale, title, description, url, body } = input
  return `<!doctype html>
<html lang="${locale}">
<head>
<meta charset="utf-8">
<title>${escapeHtml(title)}</title>
<meta name="description" content="${escapeHtml(description)}">
<meta property="og:title" content="${escapeHtml(title)}">
<meta property="og:description" content="${escapeHtml(description)}">
<meta property="og:image" content="${DEFAULTS.image}">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="${escapeHtml(DEFAULTS.imageAlt)}">
<meta property="og:url" content="${escapeHtml(url)}">
<meta property="og:type" content="website">
<meta property="og:site_name" content="${DEFAULTS.siteName}">
<meta name="twitter:card" content="summary_large_image">
</head>
<body><p>${escapeHtml(body)}</p></body>
</html>
`
}

function apiBaseUrl(): string {
  // Не хардкод: на превью-окружениях бэкенд другой. Значение задаётся
  // переменной окружения проекта Vercel.
  return process.env.API_BASE_URL ?? 'https://api.groupmatch.app'
}

/**
 * Запрос к бэкенду с жёстким потолком по времени.
 *
 * Две секунды — не «на всякий случай», а осознанный размен: краулер Telegram
 * ждёт карточку считанные секунды и при молчании показывает ссылку голым
 * текстом. Дефолтное превью через две секунды лучше персонального через
 * десять, которое никто не увидит.
 *
 * Любая ошибка — сеть, 5xx, 429 от rate-limit, битый JSON — это `null`, то есть
 * дефолтное превью. Отдельных веток нет намеренно: для карточки все эти случаи
 * неотличимы.
 */
async function fetchPreview(token: string): Promise<InvitePreview | null> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), BACKEND_TIMEOUT_MS)
  try {
    const response = await fetch(`${apiBaseUrl()}/api/v1/invites/${encodeURIComponent(token)}`, {
      signal: controller.signal,
      headers: { accept: 'application/json' },
    })
    if (!response.ok) return null
    return (await response.json()) as InvitePreview
  } catch {
    return null
  } finally {
    clearTimeout(timer)
  }
}

export default async function handler(request: Request): Promise<Response> {
  const requestUrl = new URL(request.url)
  const token = requestUrl.searchParams.get('token')
  const locale = pickLocale(request.headers.get('accept-language'))
  const copy = COPY[locale]

  // Публичный адрес приглашения, а не внутренний путь функции: именно он
  // окажется в карточке и по нему пойдут люди.
  const publicUrl = `https://groupmatch.app/join/${token ?? ''}`

  const preview = looksLikeToken(token) ? await fetchPreview(token) : null

  let title: string = DEFAULTS.title
  let description: string = DEFAULTS.description

  if (preview?.valid && preview.groupName) {
    const inviter = preview.inviterName?.trim()
    // Пригласивший мог удалить аккаунт — приглашение при этом живо, и группу
    // назвать всё ещё есть чем. Безымянный вариант лучше дефолтного.
    const compose = (group: string) =>
      inviter ? copy.invitedBy(inviter, group) : copy.invitedTo(group)

    // Сколько символов останется названию, если обрамление занять целиком.
    const overhead = [...compose('')].length
    const budget = Math.min(MAX_GROUP_NAME, Math.max(MAX_TITLE - overhead, 8))

    title = compose(truncate(preview.groupName, budget))
    description = copy.description
  }

  return new Response(
    renderHtml({ locale, title, description, url: publicUrl, body: copy.body }),
    {
      status: 200,
      headers: {
        'content-type': 'text/html; charset=utf-8',
        // Краулеры одной и той же ссылки ходят пачками: Telegram проверяет
        // превью и при отправке, и при пересылке. Названия групп меняются
        // несопоставимо реже.
        'cache-control': 'public, max-age=300',
      },
    },
  )
}
