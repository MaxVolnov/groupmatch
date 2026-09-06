import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * Номер версии записан в четырёх полях трёх файлов, и все они обязаны
 * совпадать — так требует `docs/release-process.md`.
 *
 * Сторожа не было, и версия стояла на 0.10.0 пять релизов подряд: 0.11.0,
 * 0.12.0 и 0.14.0 были затегированы, а в файлах не менялось ничего. Собранный
 * артефакт трёх разных релизов представлялся одним и тем же номером, и вопрос
 * «в какой версии это сломалось» оставался без ответа — ради него теги и
 * заводили.
 *
 * Проверка живёт во фронтенде, потому что читает оба файла одинаково просто.
 * Бэкендовый аналог был бы второй копией того же правила.
 */

const ROOT = resolve(__dirname, '../../..')

const read = (relative: string) => readFileSync(resolve(ROOT, relative), 'utf-8')

/** Версия из package.json или package-lock.json по указанному пути в объекте. */
function jsonVersion(relative: string, path: string[] = []): string {
  let node = JSON.parse(read(relative)) as Record<string, unknown>
  for (const key of path) node = node[key] as Record<string, unknown>
  const version = node.version
  expect(typeof version, `${relative}: поле version не найдено`).toBe('string')
  return version as string
}

function gradleVersion(): string {
  const match = read('backend/build.gradle.kts').match(/^version\s*=\s*"([^"]+)"/m)
  expect(match, 'backend/build.gradle.kts: строка version не найдена').not.toBeNull()
  return match![1]
}

describe('версия проекта записана одинаково везде', () => {
  const expected = jsonVersion('frontend/package.json')

  it('похожа на версию по SemVer', () => {
    expect(expected).toMatch(/^\d+\.\d+\.\d+$/)
  })

  it('backend/build.gradle.kts совпадает с frontend/package.json', () => {
    expect(gradleVersion(), 'версии бэкенда и фронтенда разошлись').toBe(expected)
  })

  /**
   * Второе поле в lock-файле забывают чаще всего: npm не синхронизирует его,
   * если править package.json руками, и расхождение всплывает при следующем
   * `npm ci`. Про это прямо написано в release-process.md.
   */
  it('оба поля в package-lock.json совпадают с package.json', () => {
    expect(jsonVersion('frontend/package-lock.json')).toBe(expected)
    expect(jsonVersion('frontend/package-lock.json', ['packages', ''])).toBe(expected)
  })

  /**
   * CHANGELOG должен содержать секцию текущей версии. Без этого номер
   * поднимают, а что в него вошло — не записывают, и релиз-ноутов снова нет.
   */
  it('в CHANGELOG есть секция этой версии', () => {
    expect(read('CHANGELOG.md')).toContain(`## [${expected}]`)
  })
})
