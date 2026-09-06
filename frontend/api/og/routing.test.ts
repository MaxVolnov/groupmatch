import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * `vercel.json` — единственное место, где описаны роутинг и заголовки
 * продакшена, и ошибка в нём не ловится ни типами, ни сборкой: она
 * обнаруживается тем, что `/promo` вдруг отдаёт SPA. Здесь проверяется не
 * Vercel, а наши правила: порядок, адресаты, то, что правило для `/join`
 * никого не съело, и состав заголовков безопасности.
 *
 * Файл формата JSON комментариев не допускает, поэтому обоснование решений,
 * записанных в нём, живёт здесь.
 */

interface Rewrite {
  source: string
  destination: string
  has?: { type: string; key: string; value: string }[]
}

interface HeaderRule {
  source: string
  headers: { key: string; value: string }[]
}

const config: {
  rewrites: Rewrite[]
  redirects: { source: string }[]
  headers: HeaderRule[]
} = JSON.parse(readFileSync(resolve(__dirname, '../../vercel.json'), 'utf-8'))

const { rewrites } = config
const joinRules = rewrites.filter((r) => r.source.startsWith('/join/'))
const spaFallbackIndex = rewrites.findIndex((r) => r.source === '/(.*)')

/**
 * Приближение к тому, как правило читает Vercel: движок там RE2 со встроенным
 * флагом `(?i)`, у JS этот флаг задаётся снаружи. Остальной синтаксис
 * (группы, классы символов) в этом выражении совпадает.
 */
const uaMatcher = new RegExp(joinRules[0].has![0].value.replace('(?i)', ''), 'i')

describe('порядок правил', () => {
  it('SPA-фолбэк последний — иначе он перехватывает всё', () => {
    expect(spaFallbackIndex).toBe(rewrites.length - 1)
  })

  it('правила /join стоят до фолбэка', () => {
    expect(joinRules).toHaveLength(2)
    for (const rule of joinRules) {
      expect(rewrites.indexOf(rule)).toBeLessThan(spaFallbackIndex)
    }
  })

  /** Статические страницы обслуживались этим файлом до нас и должны пережить правку. */
  it('/promo, /legal, /about по-прежнему ведут в свои html', () => {
    for (const page of ['promo', 'legal', 'about']) {
      for (const source of [`/${page}`, `/${page}/`]) {
        const rule = rewrites.find((r) => r.source === source)
        expect(rule, `нет правила для ${source}`).toBeDefined()
        expect(rule!.destination).toBe(`/${page}.html`)
        expect(rewrites.indexOf(rule!)).toBeLessThan(spaFallbackIndex)
      }
    }
  })

  it('/terms остаётся постоянным редиректом', () => {
    expect(config.redirects.some((r) => r.source === '/terms')).toBe(true)
  })
})

describe('правило /join', () => {
  it('оба варианта ведут в edge-функцию и передают токен', () => {
    for (const rule of joinRules) {
      expect(rule.destination).toBe('/api/og/join?token=:token')
      expect(rule.has?.[0]).toMatchObject({ type: 'header', key: 'user-agent' })
    }
  })

  /** Два правила отличаются только слешом; разъехавшиеся регекспы — тихий баг. */
  it('регексп user-agent в обоих правилах одинаковый', () => {
    expect(joinRules[0].has![0].value).toBe(joinRules[1].has![0].value)
  })
})

describe('кого правило считает краулером', () => {
  const CRAWLERS = [
    'TelegramBot (like TwitterBot)',
    'Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)',
    'Mozilla/5.0 (compatible; YandexBot/3.0; +http://yandex.com/bots)',
    'facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)',
    'WhatsApp/2.23.20.0 A',
    'Mozilla/5.0 (compatible; vkShare; +http://vk.com/dev/Share)',
    'Slackbot-LinkExpanding 1.0 (+https://api.slack.com/robots)',
    'Mozilla/5.0 (compatible; Discordbot/2.0; +https://discordapp.com)',
    'SkypeUriPreview Preview/0.5',
    'LinkedInBot/1.0 (compatible; Mozilla/5.0; Jakarta Commons-HttpClient/3.1)',
    'http.rb/5.1.1 (Mastodon/4.2.1; +https://mastodon.social/)',
    'curl/8.5.0',
  ]

  /**
   * Обратная сторона важнее прямой: краулер, которого не поймали, увидит общее
   * превью — неприятно. Человек, которого приняли за краулера, увидит заглушку
   * вместо приложения — сломано. Поэтому здесь и настоящие браузеры, и марки
   * телефонов, чьё название содержит «bot» (Cubot — не бот).
   */
  const HUMANS = [
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_5 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1',
    'Mozilla/5.0 (Android 14; Mobile; rv:127.0) Gecko/127.0 Firefox/127.0',
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 YaBrowser/24.4.0.0 Safari/537.36',
    'Mozilla/5.0 (Linux; Android 12; CUBOT_NOTE_20) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/108.0.0.0 Mobile Safari/537.36',
    'Mozilla/5.0 (iPhone; CPU iPhone OS 17_4 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148 Twitter for iPhone',
    'Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36 Instagram 300.0.0.0',
  ]

  it.each(CRAWLERS)('краулер: %s', (ua) => {
    expect(uaMatcher.test(ua)).toBe(true)
  })

  it.each(HUMANS)('человек: %s', (ua) => {
    expect(uaMatcher.test(ua)).toBe(false)
  })
})

/**
 * Заголовки безопасности. До этой правки прод не отдавал ни одного своего:
 * единственный, `strict-transport-security`, добавляет сама платформа.
 *
 * CSP здесь намеренно нет. Для React-приложения со сторонними шрифтами она
 * требует итераций в report-only и легко выключает половину интерфейса — это
 * отдельная задача, а не строчка в этом списке.
 */
describe('заголовки безопасности', () => {
  const catchAll = config.headers.find((rule) => rule.source === '/(.*)')
  const value = (key: string) =>
    catchAll?.headers.find((h) => h.key.toLowerCase() === key.toLowerCase())?.value

  it('правило одно и покрывает все пути', () => {
    expect(config.headers).toHaveLength(1)
    expect(catchAll, 'нет правила на /(.*)').toBeDefined()
  })

  /**
   * Самый ценный из трёх. Без него приложение можно положить в iframe на чужом
   * сайте и собирать клики по «покинуть группу» и «удалить встречу». DENY, а не
   * SAMEORIGIN: своих iframe в приложении нет ни одного.
   */
  it('X-Frame-Options: DENY', () => {
    expect(value('X-Frame-Options')).toBe('DENY')
  })

  it('X-Content-Type-Options: nosniff', () => {
    expect(value('X-Content-Type-Options')).toBe('nosniff')
  })

  /**
   * `strict-origin-when-cross-origin`, а не `no-referrer`: свой Referer нужен,
   * а наружу уходит только origin. Для продукта это не абстракция — в адресе
   * `/join/{token}` лежит секрет приглашения, и при переходе по внешней ссылке
   * с этой страницы полный путь ушёл бы чужому сайту вместе с токеном.
   */
  it('Referrer-Policy не пускает путь на чужой домен', () => {
    expect(value('Referrer-Policy')).toBe('strict-origin-when-cross-origin')
  })

  /** CSP — отдельная задача; появиться она должна осознанно, а не мимоходом. */
  it('CSP тут пока нет', () => {
    expect(value('Content-Security-Policy')).toBeUndefined()
    expect(value('Content-Security-Policy-Report-Only')).toBeUndefined()
  })

  /**
   * Заголовки Vercel сопоставляет с входящим путём, до перезаписей, поэтому
   * `/(.*)` накрывает и четыре точки входа MPA, и `/join/:token`, уходящий в
   * edge-функцию. Проверяем, что общее правило не сузили: сузить его — значит
   * незаметно оставить часть страниц без заголовков.
   */
  it('под правило попадают все точки входа и /join', () => {
    const pattern = new RegExp(`^${catchAll!.source}$`)
    for (const path of ['/', '/promo', '/legal', '/about', '/signup', '/join/abc123']) {
      expect(pattern.test(path), `${path} не покрыт правилом`).toBe(true)
    }
  })
})
