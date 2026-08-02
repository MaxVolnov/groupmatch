import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

const root = resolve(__dirname, '../..')
const read = (p: string) => readFileSync(resolve(root, p), 'utf-8')

const promo = read('promo.html')
const robots = read('public/robots.txt')
const sitemap = read('public/sitemap.xml')

const CANONICAL = 'https://groupmatch.app'

describe('/promo — метатеги для краулеров и соцсетей', () => {
  it('есть title, description и canonical', () => {
    expect(promo).toMatch(/<title>[^<]{10,}<\/title>/)
    expect(promo).toMatch(/<meta name="description" content="[^"]{30,}"/)
    expect(promo).toContain(`<link rel="canonical" href="${CANONICAL}/promo">`)
  })

  it('есть полный набор OG-тегов, включая картинку с размерами', () => {
    for (const property of ['og:type', 'og:locale', 'og:site_name', 'og:url',
                            'og:title', 'og:description', 'og:image',
                            'og:image:width', 'og:image:height', 'og:image:alt']) {
      expect(promo, `нет og-тега ${property}`)
        .toMatch(new RegExp(`<meta property="${property}" content="[^"]+"`))
    }
    expect(promo).toContain('<meta name="twitter:card" content="summary_large_image">')
  })

  /** Поддомен www отдаёт 308 на апекс — ссылаться на него нельзя. */
  it('канонические адреса без www', () => {
    const links = [
      ...[...promo.matchAll(/(?:href|content)="(https?:\/\/[^"]+)"/g)].map((m) => m[1]),
      ...[...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1]),
      ...[...robots.matchAll(/^Sitemap: (\S+)$/gm)].map((m) => m[1]),
    ]
    expect(links.length).toBeGreaterThan(0)
    for (const link of links) {
      expect(link, `ссылка на www-поддомен: ${link}`).not.toMatch(/^https?:\/\/www\./)
    }
  })
})

describe('/promo — содержимое', () => {
  it('ровно один h1', () => {
    expect(promo.match(/<h1[\s>]/g) ?? []).toHaveLength(1)
  })

  it('у всех картинок есть атрибут alt', () => {
    const images = promo.match(/<img\b[^>]*>/g) ?? []
    expect(images.length).toBeGreaterThan(0)
    for (const img of images) expect(img, `нет alt: ${img}`).toMatch(/\salt="/)
  })

  it('все CTA ведут на /signup', () => {
    const ctas = promo.match(/<a\b[^>]*\bdata-cta\b[^>]*>/g) ?? []
    expect(ctas.length).toBeGreaterThanOrEqual(2)
    for (const cta of ctas) expect(cta).toContain('href="/signup"')
  })

  it('есть три шага «как это работает» и блок про Pro-триал', () => {
    expect(promo).toContain('id="how-it-works"')
    expect(promo).toContain('id="pro"')
    expect(promo.match(/<span class="text-sm font-semibold text-\[var\(--gm-accent-light\)\]">0\d<\/span>/g))
      .toHaveLength(3)
  })

  /**
   * Обещание «дальше платно» — то, ради чего блок и написан. Заодно
   * фиксируем, что мы не обещаем письмо-напоминание: рассылки перед
   * окончанием триала в продукте нет, PlanExpiryJob только меняет план.
   */
  it('честно предупреждает о платности и не обещает несуществующих писем', () => {
    expect(promo).toContain('станет платным')
    expect(promo).toMatch(/199 ₽/)
    expect(promo).not.toMatch(/напомним по email|напомним письмом/i)
  })

  /**
   * Юридическое ограничение РФ: сторонний OAuth на лендинге не показываем.
   * Ищем именно признаки входа через провайдера, а не любое упоминание —
   * иначе тест ловит контактный email на @yandex.ru и слот Яндекс.Метрики.
   */
  it('нет кнопок стороннего OAuth', () => {
    for (const marker of [
      /oauth/i,
      /accounts\.google\.com/i,
      /id\.vk\.com/i,
      /github\.com\/login/i,
      /appleid\.apple\.com/i,
      /войти через/i,
      /sign in with/i,
    ]) {
      expect(promo, `похоже на сторонний OAuth: ${marker}`).not.toMatch(marker)
    }
    // Единственные ссылки на аутентификацию — наши собственные.
    const authLinks = [...promo.matchAll(/href="(\/sign[^"]*)"/g)].map((m) => m[1])
    expect(new Set(authLinks)).toEqual(new Set(['/signin', '/signup']))
  })

  it('в футере юрлицо, ИНН и ссылки на /legal и /about', () => {
    expect(promo).toContain('771887947687')
    expect(promo).toContain('Волнов Максим Юрьевич')
    expect(promo).toContain('href="/legal"')
    expect(promo).toContain('href="/about"')
  })

  it('место под Telegram помечено греппаемым TODO', () => {
    expect(promo).toContain('TODO: telegram link')
  })

  /** Смысл отдельной MPA-страницы — не тянуть SPA-бандл ради OG-тегов. */
  it('подключает только собственную точку входа, без React-бандла', () => {
    const scripts = promo.match(/<script\b[^>]*src="[^"]+"/g) ?? []
    expect(scripts).toHaveLength(1)
    expect(scripts[0]).toContain('/src/promo/main.ts')
    expect(promo).not.toContain('/src/main.tsx')
  })
})

describe('robots.txt', () => {
  it('содержит группу User-agent и ссылку на карту сайта', () => {
    expect(robots).toMatch(/^User-agent: \*$/m)
    expect(robots).toMatch(new RegExp(`^Sitemap: ${CANONICAL}/sitemap\\.xml$`, 'm'))
  })

  it('закрывает приватные и транзакционные пути', () => {
    for (const path of ['/groups/', '/profile', '/admin', '/join/',
                        '/verify-email', '/reset-password', '/forgot-password',
                        '/signin', '/signup', '/api/']) {
      expect(robots, `не закрыт ${path}`).toMatch(new RegExp(`^Disallow: ${path}$`, 'm'))
    }
  })

  it('оставляет открытыми публичные точки входа', () => {
    for (const path of ['/promo', '/legal', '/about', '/pricing']) {
      expect(robots).toMatch(new RegExp(`^Allow: ${path}$`, 'm'))
    }
  })

  /** Disallow: / закрыл бы вообще всё, включая лендинг. */
  it('нет тотального запрета', () => {
    expect(robots).not.toMatch(/^Disallow: \/$/m)
  })
})

describe('sitemap.xml', () => {
  it('корректный XML-каркас', () => {
    expect(sitemap.startsWith('<?xml version="1.0" encoding="UTF-8"?>')).toBe(true)
    expect(sitemap).toContain('<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">')
    expect(sitemap.trimEnd().endsWith('</urlset>')).toBe(true)
    expect(sitemap.match(/<url>/g) ?? []).toHaveLength((sitemap.match(/<\/url>/g) ?? []).length)
  })

  it('перечисляет ровно три публичные точки входа', () => {
    const locs = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1])
    expect(locs).toEqual([
      `${CANONICAL}/promo`,
      `${CANONICAL}/about`,
      `${CANONICAL}/legal`,
    ])
  })

  it('у каждой записи валидный lastmod и приоритет', () => {
    const urls = sitemap.match(/<url>[\s\S]*?<\/url>/g) ?? []
    expect(urls).toHaveLength(3)
    for (const url of urls) {
      expect(url).toMatch(/<lastmod>\d{4}-\d{2}-\d{2}<\/lastmod>/)
      const priority = Number(url.match(/<priority>([\d.]+)<\/priority>/)?.[1])
      expect(priority).toBeGreaterThan(0)
      expect(priority).toBeLessThanOrEqual(1)
    }
  })

  /** Всё, что в карте, должно быть открыто в robots.txt. */
  it('не противоречит robots.txt', () => {
    const locs = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map((m) => m[1])
    for (const loc of locs) {
      const path = loc.replace(CANONICAL, '')
      expect(robots, `${path} есть в sitemap, но закрыт в robots`)
        .not.toMatch(new RegExp(`^Disallow: ${path}$`, 'm'))
    }
  })
})
