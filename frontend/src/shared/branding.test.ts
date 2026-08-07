import { existsSync, readFileSync, statSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * Брендинг живёт в статических файлах и HTML-шапках, которые ничем больше не
 * покрыты: сборка остаётся зелёной, даже если ассет не доехал или тег указывает
 * в пустоту. Именно так до этой задачи index.html месяцами ссылался на
 * /vite.svg, которого в public/ нет.
 */

const root = resolve(__dirname, '../..')
const read = (p: string) => readFileSync(resolve(root, p), 'utf-8')

const ENTRY_POINTS = ['index.html', 'promo.html', 'legal.html', 'about.html'] as const
/** Точки входа с OG-разметкой; у index.html её нет (SPA не расшаривают). */
const OG_ENTRY_POINTS = ['promo.html', 'legal.html', 'about.html'] as const

const ASSETS = [
  'favicon.ico',
  'favicon-96x96.png',
  'apple-touch-icon.png',
  'web-app-manifest-192x192.png',
  'web-app-manifest-512x512.png',
  'og-image.jpg',
  'site.webmanifest',
  'logo-on-dark.svg',
  'logo-on-light.svg',
] as const

const CANONICAL = 'https://groupmatch.app'

describe('ассеты бренда', () => {
  it.each(ASSETS)('%s лежит в public/ и не пустой', (asset) => {
    const path = resolve(root, 'public', asset)
    expect(existsSync(path), `нет public/${asset}`).toBe(true)
    expect(statSync(path).size).toBeGreaterThan(200)
  })

  /** Мессенджеры тянут превью синхронно — тяжёлая картинка задерживает карточку. */
  it('OG-превью укладывается в 300 КБ', () => {
    const kb = statSync(resolve(root, 'public/og-image.jpg')).size / 1024
    expect(kb).toBeLessThan(300)
  })

  /**
   * В архиве дизайнера размеры записаны кириллической «х» (U+0445). Такое имя
   * ломается на CI и в путях сборки, поэтому в репозитории — только ASCII.
   */
  it.each(ASSETS)('%s назван в чистом ASCII', (asset) => {
    expect(asset).toMatch(/^[\x20-\x7E]+$/)
  })

  it('заглушки прошлой итерации удалены', () => {
    for (const stale of ['favicon.svg', 'favicon-16.png', 'favicon-32.png',
                         'favicon-180.png', 'og-image.png', 'logo.svg', 'vite.svg']) {
      expect(existsSync(resolve(root, 'public', stale)), `${stale} ещё на месте`).toBe(false)
    }
  })
})

describe('фавиконы и манифест в точках входа', () => {
  it.each(ENTRY_POINTS)('%s содержит полный набор тегов', (page) => {
    const html = read(page)
    expect(html).toContain('<link rel="icon" href="/favicon.ico" sizes="32x32">')
    expect(html).toContain('href="/favicon-96x96.png"')
    expect(html).toContain('<link rel="apple-touch-icon" href="/apple-touch-icon.png">')
    expect(html).toContain('<link rel="manifest" href="/site.webmanifest">')
    expect(html).toContain('<meta name="theme-color" content="#040929">')
  })

  it.each(ENTRY_POINTS)('%s не ссылается на удалённые файлы', (page) => {
    const html = read(page)
    for (const stale of ['/vite.svg', '/favicon.svg', '/favicon-16.png',
                         '/favicon-32.png', '/favicon-180.png', '/og-image.png']) {
      expect(html, `${page} ссылается на ${stale}`).not.toContain(stale)
    }
  })

  it('site.webmanifest — валидный JSON, иконки указывают на существующие файлы', () => {
    const manifest = JSON.parse(read('public/site.webmanifest'))
    expect(manifest.name).toBe('GroupMatch')
    expect(manifest.theme_color).toBe('#040929')
    expect(manifest.icons).toHaveLength(2)
    for (const icon of manifest.icons) {
      expect(existsSync(resolve(root, 'public', icon.src.replace(/^\//, ''))),
        `манифест ссылается на ${icon.src}, файла нет`).toBe(true)
      expect(icon.type).toBe('image/png')
    }
  })
})

describe('OG-превью', () => {
  it.each(OG_ENTRY_POINTS)('%s: og:image — абсолютный URL', (page) => {
    const url = read(page).match(/property="og:image" content="([^"]+)"/)?.[1]
    expect(url, `${page}: нет og:image`).toBeDefined()
    // Относительный путь мессенджеры не резолвят — превью просто не подтянется.
    expect(url).toBe(`${CANONICAL}/og-image.jpg`)
  })

  it.each(OG_ENTRY_POINTS)('%s: указаны размеры 1200×630', (page) => {
    const html = read(page)
    expect(html).toContain('<meta property="og:image:width" content="1200">')
    expect(html).toContain('<meta property="og:image:height" content="630">')
  })
})

describe('палитра', () => {
  const FINAL = {
    '--gm-bg': '#040929',
    '--gm-accent': '#055EC2',
    '--gm-accent-light': '#5DB7F4',
    '--gm-accent-deep': '#143291',
    '--gm-text': '#FFFFFF',
  }

  it('brand.css — единственный источник значений', () => {
    const brand = read('src/shared/brand.css')
    for (const [token, value] of Object.entries(FINAL)) {
      expect(brand).toContain(`${token}: ${value};`)
    }
    // Оба входа импортируют его, а не переобъявляют палитру у себя.
    expect(read('src/shared/site.css')).toContain("@import './brand.css';")
    expect(read('src/promo/promo.css')).toContain("@import '../shared/brand.css';")
    expect(read('src/shared/site.css')).not.toContain('--gm-bg:')
    expect(read('src/promo/promo.css')).not.toContain('--gm-bg:')
  })

  it('токены Tailwind совпадают с CSS-переменными', () => {
    const tw = read('tailwind.config.cjs')
    expect(tw).toContain("'#5DB7F4'")
    expect(tw).toContain("'#143291'")
    expect(tw).toContain("'#040929'")
  })

  /** Старое значение светло-голубого не должно вернуться ни в каком регистре. */
  it('#6499D0 не встречается в исходниках', () => {
    for (const file of ['src/shared/brand.css', 'src/shared/site.css',
                        'src/promo/promo.css', 'tailwind.config.cjs',
                        ...ENTRY_POINTS]) {
      expect(read(file).toLowerCase(), `${file} содержит старый цвет`)
        .not.toContain('6499d0')
    }
  })
})

describe('знак в шапках', () => {
  /** На тёмной шапке нужен вариант с белой G, на светлой — с тёмной. */
  it('SPA подменяет вариант знака по теме', () => {
    const layout = read('src/components/Layout.tsx')
    expect(layout).toContain('/logo-on-light.svg')
    expect(layout).toContain('/logo-on-dark.svg')
    expect(layout).toMatch(/dark:hidden/)
    expect(layout).toMatch(/dark:block/)
  })

  it('статические страницы берут вариант для тёмного фона', () => {
    expect(read('src/shared/siteChrome.ts')).toContain('/logo-on-dark.svg')
    expect(read('src/shared/siteChrome.ts')).not.toContain('/logo-on-light.svg')
    expect(read('promo.html')).toContain('/logo-on-dark.svg')
  })

  /** Знак декоративный: рядом в той же ссылке всегда есть текст «GroupMatch». */
  it('знак не создаёт дублирующий alt для скринридера', () => {
    for (const file of ['src/components/Layout.tsx', 'src/shared/siteChrome.ts']) {
      const marks = read(file).match(/<img[^>]*logo-on-[^>]*>/g) ?? []
      expect(marks.length).toBeGreaterThan(0)
      for (const mark of marks) expect(mark).toMatch(/alt=("")|(\{?''\}?)/)
    }
  })
})
