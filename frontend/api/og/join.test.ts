import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, describe, expect, it, vi } from 'vitest'
import handler, { escapeHtml, pickLocale, truncate } from './join'

/**
 * Функция отдаёт HTML, который целиком собирается из пользовательского ввода
 * (название группы, имя пригласившего) и уходит на наш домен. Поэтому здесь
 * проверяется не «красиво ли получилось», а две вещи: нельзя ли через неё
 * выполнить чужой код и остаётся ли карточка целой, когда всё вокруг сломалось.
 */

const TOKEN = 'a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718'

function request(url = `https://groupmatch.app/api/og/join?token=${TOKEN}`, headers: Record<string, string> = {}) {
  return new Request(url, { headers })
}

function mockBackend(body: unknown, init: ResponseInit = {}) {
  vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify(body), { status: 200, ...init })))
}

afterEach(() => { vi.unstubAllGlobals() })

describe('escapeHtml', () => {
  it('закрывает все пять опасных символов', () => {
    expect(escapeHtml(`& < > " '`)).toBe('&amp; &lt; &gt; &quot; &#39;')
  })

  /** Амперсанд обязан обрабатываться первым, иначе сущности ломают сами себя. */
  it('не портит уже вставленные сущности двойным экранированием', () => {
    expect(escapeHtml('<a>')).toBe('&lt;a&gt;')
    expect(escapeHtml('a & b < c')).toBe('a &amp; b &lt; c')
  })
})

describe('truncate', () => {
  it('короткое название не трогает', () => {
    expect(truncate('Выпить пиво')).toBe('Выпить пиво')
  })

  /** Многоточие входит в лимит: 60 на выходе — это ровно 60, а не 61. */
  it('длинное обрезает до 60 символов вместе с многоточием', () => {
    const result = truncate('я'.repeat(120))
    expect([...result]).toHaveLength(60)
    expect(result.endsWith('…')).toBe(true)
  })

  /** Кириллица — не ASCII: считаем кодовые точки, а не байты. */
  it('считает символы, а не длину в байтах', () => {
    expect(truncate('привет', 3)).toBe('пр…')
  })
})

describe('pickLocale', () => {
  it('по умолчанию русский', () => {
    expect(pickLocale(null)).toBe('ru')
    expect(pickLocale('')).toBe('ru')
  })

  it('английский — только если ru в заголовке нет', () => {
    expect(pickLocale('en-US,en;q=0.9')).toBe('en')
    expect(pickLocale('en-US,ru;q=0.9')).toBe('ru')
    expect(pickLocale('ru-RU,ru;q=0.9')).toBe('ru')
    expect(pickLocale('de-DE')).toBe('ru')
  })
})

describe('ответ функции', () => {
  it('валидный токен даёт персональный заголовок', async () => {
    mockBackend({ valid: true, groupName: 'Выпить пиво в пятницу', inviterName: 'Дима' })
    const html = await (await handler(request())).text()

    expect(html).toContain('<meta property="og:title" content="Дима приглашает вас в группу «Выпить пиво в пятницу»">')
    expect(html).toContain('Отметьте, когда вам удобно')
    expect(html).toContain(`<meta property="og:url" content="https://groupmatch.app/join/${TOKEN}">`)
  })

  /**
   * Главная проверка файла. Название группы вводит пользователь; если оно
   * попадёт в атрибут неэкранированным, у нас XSS на собственном домене.
   */
  it('название группы с разметкой не даёт исполняемого тега', async () => {
    mockBackend({
      valid: true,
      groupName: 'Пиво <script>alert(1)</script> & "друзья"',
      inviterName: '"><script>alert(2)</script>',
    })
    const html = await (await handler(request())).text()

    expect(html).not.toContain('<script>')
    expect(html).not.toContain('</script>')
    expect(html).toContain('&lt;script&gt;')
    expect(html).toContain('&amp;')
    expect(html).toContain('&quot;')

    // Атрибут не разорван: между content=" и закрывающей кавычкой нет сырых "
    const og = html.match(/<meta property="og:title" content="([^"]*)"/)
    expect(og, 'og:title должен остаться цельным атрибутом').not.toBeNull()
    expect(og![1]).not.toContain('<')
  })

  it('пригласивший без имени — название группы всё равно в заголовке', async () => {
    mockBackend({ valid: true, groupName: 'Планёрка', inviterName: null })
    const html = await (await handler(request())).text()
    expect(html).toContain('Вас приглашают в группу «Планёрка»')
  })

  it('длинное название обрезается, а не уходит целиком', async () => {
    mockBackend({ valid: true, groupName: 'я'.repeat(120), inviterName: 'Дима' })
    const html = await (await handler(request())).text()
    const og = html.match(/<meta property="og:title" content="([^"]*)"/)![1]
    expect(og).toContain('…')
    // Потолок 90 — включительно: режут то, что *длиннее* 90.
    expect([...og].length).toBeLessThanOrEqual(90)
  })

  it('английский заголовок при Accept-Language без ru', async () => {
    mockBackend({ valid: true, groupName: 'Beer night', inviterName: 'Dima' })
    const html = await (await handler(request(undefined, { 'accept-language': 'en-GB,en;q=0.9' }))).text()
    expect(html).toContain('<html lang="en">')
    expect(html).toContain('Dima invites you to «Beer night»')
  })
})

describe('превью не ломается никогда', () => {
  const DEFAULT_TITLE = 'GroupMatch — общее время для встречи без переписок'

  it('невалидный токен — дефолтные теги и 200', async () => {
    mockBackend({ valid: false, reason: 'not_found' })
    const response = await handler(request())
    expect(response.status).toBe(200)
    expect(await response.text()).toContain(DEFAULT_TITLE)
  })

  it('бэкенд ответил 500 — дефолтные теги и 200', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('boom', { status: 500 })))
    const response = await handler(request())
    expect(response.status).toBe(200)
    expect(await response.text()).toContain(DEFAULT_TITLE)
  })

  /** 429 от rate-limit — тот же случай: карточка важнее причины отказа. */
  it('бэкенд ответил 429 — дефолтные теги и 200', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 429 })))
    expect(await (await handler(request())).text()).toContain(DEFAULT_TITLE)
  })

  it('сеть упала — дефолтные теги и 200', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('ECONNREFUSED') }))
    const response = await handler(request())
    expect(response.status).toBe(200)
    expect(await response.text()).toContain(DEFAULT_TITLE)
  })

  it('мусор вместо токена до бэкенда не доходит', async () => {
    const spy = vi.fn()
    vi.stubGlobal('fetch', spy)
    const html = await (await handler(request('https://groupmatch.app/api/og/join?token=../../etc/passwd'))).text()
    expect(spy).not.toHaveBeenCalled()
    expect(html).toContain(DEFAULT_TITLE)
  })

  it('токена нет вовсе — дефолтные теги и 200', async () => {
    const response = await handler(request('https://groupmatch.app/api/og/join'))
    expect(response.status).toBe(200)
    expect(await response.text()).toContain(DEFAULT_TITLE)
  })
})

describe('служебные заголовки ответа', () => {
  it('HTML и кэш на пять минут', async () => {
    mockBackend({ valid: true, groupName: 'Пиво', inviterName: 'Дима' })
    const response = await handler(request())
    expect(response.headers.get('content-type')).toBe('text/html; charset=utf-8')
    expect(response.headers.get('cache-control')).toBe('public, max-age=300')
  })
})

/**
 * Дефолты в функции — копия того, что лежит в `index.html`. Копии расходятся
 * молча, а заметно это становится в мессенджере у пользователя.
 */
describe('дефолтные теги совпадают с index.html', () => {
  const indexHtml = readFileSync(resolve(__dirname, '../../index.html'), 'utf-8')
  const meta = (prop: string) =>
    indexHtml.match(new RegExp(`<meta property="${prop}" content="([^"]*)"`))?.[1]

  it('og:title, og:description, og:image и og:image:alt те же', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new Error('offline') }))
    const html = await (await handler(request())).text()

    for (const prop of ['og:title', 'og:description', 'og:image', 'og:image:alt']) {
      const expected = meta(prop)
      expect(expected, `${prop} не найден в index.html`).toBeTruthy()
      expect(html, `${prop} разошёлся с index.html`).toContain(`<meta property="${prop}" content="${expected}">`)
    }
  })
})
