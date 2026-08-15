import { readFileSync, readdirSync, statSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * Юридический текст — единственное место в приложении, где утверждение может
 * стать ложным без единой правки кода. Так и вышло: после переезда с Railway
 * на Timeweb строка «Данные хранятся на серверах Railway (США)» осталась в
 * политике конфиденциальности и месяц вводила пользователей в заблуждение,
 * пока переезд не дошёл до ревизии текста.
 *
 * Здесь закрепляем те факты, которые проверяются машиной: название площадки и
 * сроки, у которых есть источник в коде бэкенда. Всё остальное в этом тексте
 * (тарифы, SLA, права) машине не по зубам и требует живого читателя.
 */

const root = resolve(__dirname, '../..')
const read = (p: string) => readFileSync(resolve(root, p), 'utf-8')

/**
 * Все текстовые исходники фронтенда — HTML, TS/TSX, локали. Сами тесты
 * пропускаем: этот файл обязан называть Railway, иначе не объяснить, от чего
 * он защищает.
 */
function frontendSources(dir = root): string[] {
  return readdirSync(dir).flatMap((entry) => {
    if (['node_modules', 'dist', '.git', 'coverage'].includes(entry)) return []
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) return frontendSources(path)
    if (/\.test\.tsx?$/.test(path)) return []
    return /\.(html|tsx?|json)$/.test(path) ? [path] : []
  })
}

describe('утверждения о хостинге', () => {
  /**
   * Проверяем весь фронтенд, а не только legal.html: упоминание старой
   * площадки может всплыть в промо-тексте или в локали ровно так же.
   */
  it('Railway нигде не упоминается', () => {
    const offenders = frontendSources()
      .filter((file) => /railway/i.test(readFileSync(file, 'utf-8')))
      .map((file) => file.slice(root.length + 1))
    expect(offenders).toEqual([])
  })

  it('политика называет фактическую площадку и страну', () => {
    const legal = read('legal.html')
    expect(legal).toContain('Timeweb Cloud')
    expect(legal).toContain('Россия')
  })
})

describe('сроки в тексте совпадают с кодом', () => {
  /**
   * `GUEST_RETENTION_DAYS` по умолчанию 90 (backend/application.yml). Число
   * названо в трёх местах, и разъезжаются они молча.
   */
  it('90 дней для гостевых аккаунтов — в политике и в баннере гостя', () => {
    expect(read('legal.html')).toMatch(/90 дней неактивности/)
    expect(read('src/locales/ru.json')).toMatch(/90 дней/)
  })
})
