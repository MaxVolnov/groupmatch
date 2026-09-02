import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * Перевод SPA на фирменную гамму легко откатить одним неаккуратным копипастом
 * из старого компонента: `bg-indigo-600` соберётся молча и будет выглядеть
 * почти правдоподобно. Эти проверки не дают дефолтной палитре вернуться.
 */

const root = resolve(__dirname, '../..')
const read = (p: string) => readFileSync(p, 'utf-8')

/**
 * То же содержимое без комментариев: проверки ниже ищут классы, а не прозу.
 * Иначе комментарий вида «раньше здесь был indigo-500» считается нарушением,
 * хотя описывает как раз его отсутствие.
 *
 * Двоеточие перед `//` не трогаем, чтобы не покалечить `https://`.
 */
const readCode = (p: string) =>
  read(p)
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/(^|[^:])\/\/.*$/gm, '$1')

function sourceFiles(dir = resolve(root, 'src')): string[] {
  return readdirSync(dir).flatMap((entry) => {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) return sourceFiles(path)
    return /\.tsx?$/.test(path) && !/\.test\.tsx?$/.test(path) ? [path] : []
  })
}

const FILES = sourceFiles()
const rel = (p: string) => p.slice(root.length + 1)

describe('фиолетовая гамма вычищена', () => {
  it('в src/ нет классов indigo / violet / purple / fuchsia / pink', () => {
    const offenders = FILES.flatMap((file) => {
      const hits = readCode(file).match(/\b(indigo|violet|purple|fuchsia|pink)-\d+/g) ?? []
      return hits.map((hit) => `${rel(file)}: ${hit}`)
    })
    expect(offenders).toEqual([])
  })

  /** Тот же цвет, вписанный руками мимо токенов, обошёл бы проверку выше. */
  it('в src/ нет хардкоженных hex фиолетовой гаммы', () => {
    const PURPLE_HEX = [
      '6366f1', '4f46e5', '4338ca', '3730a3', '818cf8', 'a5b4fc', 'c7d2fe', 'e0e7ff',
      '8b5cf6', '7c3aed', '6d28d9', 'a78bfa', 'c4b5fd', 'ddd6fe',
      'd946ef', 'c026d3', 'ec4899', 'db2777', 'be185d',
    ]
    const offenders = FILES.flatMap((file) => {
      const text = readCode(file).toLowerCase()
      return PURPLE_HEX.filter((hex) => text.includes(`#${hex}`)).map((hex) => `${rel(file)}: #${hex}`)
    })
    expect(offenders).toEqual([])
  })

  /** Синий вне бренда — тоже расхождение с палитрой, хоть и не фиолетовый. */
  it('в src/ не осталось не-фирменного синего', () => {
    const offenders = FILES.flatMap((file) => {
      const hits = readCode(file).match(/\b(bg|text|border|ring)-(blue|sky|cyan)-\d+/g) ?? []
      return hits.map((hit) => `${rel(file)}: ${hit}`)
    })
    expect(offenders).toEqual([])
  })
})

describe('токены gm.* заведены и применяются', () => {
  const config = read(resolve(root, 'tailwind.config.cjs'))

  it('шкала опирается на фирменные цвета', () => {
    expect(config).toContain("light: '#5DB7F4'")   // 400 — ссылки на тёмном
    expect(config).toContain("accent: '#055EC2'")  // 600 — кнопки
    expect(config).toContain("deep: '#143291'")    // 700 — hover, бейдж админа
    expect(config).toContain("bg: '#040929'")      // 950 — фон
    for (const step of [50, 100, 200, 300, 400, 500, 600, 700, 800, 900, 950]) {
      expect(config, `нет ступени ${step}`).toMatch(new RegExp(`\\b${step}:`))
    }
  })

  /** Токены, заведённые и не использованные, — мёртвый конфиг. */
  it('шкала действительно используется в компонентах', () => {
    const used = new Set<string>()
    for (const file of FILES) {
      for (const hit of readCode(file).match(/\bgm-\d+\b/g) ?? []) used.add(hit)
    }
    for (const step of ['gm-400', 'gm-600', 'gm-700']) {
      expect(used, `${step} заведён, но нигде не применяется`).toContain(step)
    }
    expect(used.size).toBeGreaterThanOrEqual(6)
  })

  it('значения совпадают с CSS-переменными brand.css', () => {
    const brand = read(resolve(root, 'src/shared/brand.css'))
    expect(brand).toContain('--gm-accent: #055EC2;')
    expect(brand).toContain('--gm-accent-light: #5DB7F4;')
    expect(brand).toContain('--gm-accent-deep: #143291;')
    expect(brand).toContain('--gm-bg: #040929;')
  })
})

describe('роли цветов из брендбука', () => {
  it('основная кнопка — gm-600 с потемнением на hover', () => {
    const button = read(resolve(root, 'src/components/Button.tsx'))
    expect(button).toMatch(/bg-gm-600[^']*hover:bg-gm-700/)
    // gm-500 под белым текстом даёт 3.81:1 — ниже AA, возврату не подлежит.
    expect(button).not.toContain('dark:bg-gm-500')
  })

  it('бейдж админа — gm-700', () => {
    const layout = read(resolve(root, 'src/components/Layout.tsx'))
    expect(layout).toContain('bg-gm-700')
  })

  it('бейдж Pro — gm-600', () => {
    expect(read(resolve(root, 'src/components/ProBadge.tsx'))).toContain('text-gm-600')
  })

  /**
   * Заливка светлее gm-600 не выдерживает белый текст: на gm-500 это 3.81:1.
   * Светлые ступени сами по себе законны — на них кладут тёмный текст
   * (`bg-gm-100 text-gm-700`), поэтому проверяем именно пару в одной строке
   * классов, а не фон отдельно.
   */
  it('белый текст нигде не лежит на светлых ступенях', () => {
    const offenders: string[] = []
    for (const file of FILES) {
      for (const attr of readCode(file).match(/className=(?:"[^"]*"|\{`[^`]*`\}|\{[^}]*\})/g) ?? []) {
        const fill = attr.match(/(?<!disabled:)\bbg-gm-([1-5]00)\b/)
        if (fill && /\btext-white\b/.test(attr)) {
          offenders.push(`${rel(file)}: bg-gm-${fill[1]} + text-white`)
        }
      }
    }
    expect(offenders).toEqual([])
  })
})

describe('семантические цвета не тронуты', () => {
  it('ошибки, успех и предупреждения остались своими цветами', () => {
    const all = FILES.map(readCode).join('\n')
    expect(all).toMatch(/\btext-red-\d+/)
    expect(all).toMatch(/\bbg-red-\d+/)
    expect(all).toMatch(/\b(text|bg)-green-\d+/)
    expect(all).toMatch(/\b(text|bg)-(yellow|amber)-\d+/)
  })

  /**
   * Плотность группы — отдельная цветовая схема, зелёная, и бренду она не
   * подчиняется: синим по синему два слоя одной ячейки не разделить.
   *
   * Проверяется WeekGrid, а не HeatmapTab: сама таблица переехала туда, когда
   * две сетки схлопнули в одну. В HeatmapTab остались только кнопки, и
   * фирменный цвет на них законен.
   */
  it('шкала плотности не переведена на бренд', () => {
    const grid = read(resolve(root, 'src/pages/group/WeekGrid.tsx'))
    const intensity = grid.slice(grid.indexOf('function intensityClass'), grid.indexOf('const MINE_CLASS'))
    expect(intensity).not.toMatch(/\bbg-gm-\d+\b/)
    expect(intensity).toMatch(/bg-green-\d+/)
  })
})

describe('экран входа', () => {
  const publicLayout = read(resolve(root, 'src/components/PublicLayout.tsx'))

  it('градиент описан один раз в конфиге, а не в разметке', () => {
    const config = read(resolve(root, 'tailwind.config.cjs'))
    expect(config).toContain("'gm-auth'")
    expect(config).toContain('radial-gradient')
    // В компонентах — только имя класса, никаких inline-градиентов.
    for (const file of FILES) {
      expect(readCode(file), `${rel(file)}: градиент прописан в разметке`)
        .not.toContain('radial-gradient')
    }
  })

  it('фон — CSS, а не картинка', () => {
    const config = read(resolve(root, 'tailwind.config.cjs'))
    const gradient = config.slice(config.indexOf("'gm-auth'"), config.indexOf("'gm-auth'") + 600)
    expect(gradient).not.toMatch(/url\(|\.png|\.jpg|\.svg/)
  })

  it('вход и регистрация включают фирменный фон, /pricing — нет', () => {
    for (const page of ['SignIn.tsx', 'SignUp.tsx']) {
      expect(read(resolve(root, 'src/pages', page)), page)
        .toContain('<PublicLayout brandBackground>')
    }
    expect(read(resolve(root, 'src/pages/Pricing.tsx')))
      .not.toContain('brandBackground')
  })

  /**
   * Фон тёмный по природе, поэтому поверхность принудительно тёмная —
   * иначе у пользователя со светлой темой белая карточка на тёмном градиенте.
   */
  it('градиентная поверхность включает тёмные варианты', () => {
    expect(publicLayout).toMatch(/'dark min-h-screen[^']*bg-gm-auth'/)
  })

  it('карточка формы тонирована под фон, а не оставлена белой', () => {
    for (const page of ['SignIn.tsx', 'SignUp.tsx']) {
      const html = read(resolve(root, 'src/pages', page))
      expect(html, page).toContain('bg-gm-900/70')
      expect(html, page).not.toContain('bg-white dark:bg-gray-800 p-8 shadow-md')
    }
  })
})
