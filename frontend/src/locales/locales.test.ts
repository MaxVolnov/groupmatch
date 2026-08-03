import { describe, expect, it } from 'vitest'
import ru from './ru.json'
import en from './en.json'

type Tree = { [key: string]: string | Tree }

function flatten(tree: Tree, prefix = ''): Record<string, string> {
  const out: Record<string, string> = {}
  for (const [key, value] of Object.entries(tree)) {
    const path = prefix ? `${prefix}.${key}` : key
    if (typeof value === 'string') out[path] = value
    else Object.assign(out, flatten(value, path))
  }
  return out
}

const flatRu = flatten(ru as Tree)
const flatEn = flatten(en as Tree)

describe('паритет локалей', () => {
  it('наборы ключей ru и en совпадают', () => {
    expect(Object.keys(flatRu).sort()).toEqual(Object.keys(flatEn).sort())
  })

  it('нет пустых строк', () => {
    for (const [key, value] of Object.entries({ ...flatRu, ...flatEn })) {
      expect(value.trim(), `пустое значение у ${key}`).not.toBe('')
    }
  })

  it('плейсхолдеры {{...}} совпадают между локалями', () => {
    const placeholders = (s: string) => (s.match(/\{\{[^}]+\}\}/g) ?? []).sort()
    for (const key of Object.keys(flatRu)) {
      expect(placeholders(flatEn[key]), `плейсхолдеры разошлись в ${key}`)
        .toEqual(placeholders(flatRu[key]))
    }
  })
})

/**
 * Тариф в системе называется Pro (Plan.PRO, /pricing, Profile). Слово
 * «Premium» ошибочно приехало вместе с триал-баннером и создавало два имени
 * для одной сущности — этот тест не даёт ему вернуться.
 */
describe('название платного тарифа', () => {
  it('нигде не осталось слова Premium', () => {
    const offenders = Object.entries({ ...flatRu, ...flatEn })
      .filter(([, value]) => /premium/i.test(value))
      .map(([key]) => key)
    expect(offenders).toEqual([])
  })

  it('бейдж триала называется Pro в обеих локалях', () => {
    expect(flatRu['trial.badge']).toBe('Pro')
    expect(flatEn['trial.badge']).toBe('Pro')
  })

  it('тексты триала упоминают Pro', () => {
    for (const key of ['trial.badgeHint', 'trial.bannerTitle', 'trial.profileNote']) {
      expect(flatRu[key], `${key} (ru)`).toContain('Pro')
      expect(flatEn[key], `${key} (en)`).toContain('Pro')
    }
  })

  /** Обещание «дальше будет платно» — не косметика, его нельзя потерять. */
  it('баннер и заметка в профиле честно говорят о платности дальше', () => {
    expect(flatRu['trial.bannerAfter']).toMatch(/199 ₽/)
    expect(flatEn['trial.bannerAfter']).toMatch(/199 ₽/)
    expect(flatRu['trial.profileNote']).toMatch(/платн/i)
    expect(flatEn['trial.profileNote']).toMatch(/paid/i)
  })
})
