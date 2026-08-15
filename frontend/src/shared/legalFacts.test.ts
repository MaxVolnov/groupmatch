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

  /**
   * Страна названа, провайдер — намеренно нет: при смене площадки внутри
   * России текст обязан остаться верным без правки. Именно привязка к
   * конкретному провайдеру («серверах Railway») и протухла в прошлый раз.
   */
  it('политика называет страну размещения, но не провайдера', () => {
    const legal = read('legal.html')
    expect(legal).toContain('на серверах в России')
    for (const vendor of ['Timeweb', 'Railway', 'AWS', 'Hetzner']) {
      expect(legal, `провайдер ${vendor} назван в тексте`).not.toContain(vendor)
    }
  })

  /**
   * «Данные в России» без оговорки читается как «страну ничто не покидает», а
   * письма идут через Resend, платежи — через ЮKassa. Ссылка на раздел 2, где
   * оба перечислены, снимает противоречие; без неё разделы спорят друг с другом.
   */
  it('раздел о хранении ссылается на список сторонних сервисов', () => {
    const legal = read('legal.html')
    expect(legal).toContain('перечисленными в разделе 2')
    expect(legal).toContain('Resend')
    expect(legal).toContain('ЮKassa')
  })
})

/**
 * Числа в оферте живут отдельно от констант, которые их порождают: цена — в
 * трёх местах фронтенда, лимит групп и срок триала — в Java. Расходятся они
 * молча, а замечает это пользователь, которому пообещали одно, а выдали другое.
 *
 * Читать чужой язык регулярками — приём хрупкий, поэтому якоря выбраны самые
 * устойчивые (вызов конструктора, значение по умолчанию в @Value) и
 * отсутствие совпадения считается падением, а не «нечего проверять». Тест,
 * который молча проходит, не найдя якоря, хуже отсутствующего.
 */
const backend = (p: string) => readFileSync(resolve(root, '../backend', p), 'utf-8')

function requireMatch(text: string, re: RegExp, what: string): string {
  const found = text.match(re)
  expect(found, `не найден якорь: ${what}`).not.toBeNull()
  return found![1]
}

describe('числа в оферте совпадают с кодом', () => {
  const legal = read('legal.html')

  it('лимит групп на Free совпадает с Plan.FREE', () => {
    const maxGroups = requireMatch(
      backend('src/main/java/com/groupmatch/domain/Plan.java'),
      /case FREE\s*->\s*new PlanLimits\((\d+),/,
      'Plan.FREE -> new PlanLimits(N,',
    )
    expect(legal).toContain(`до ${maxGroups} групп`)
  })

  it('срок бесплатного Pro совпадает с TrialService', () => {
    const months = requireMatch(
      backend('src/main/java/com/groupmatch/service/TrialService.java'),
      /app\.trial\.duration-months:(\d+)/,
      '@Value("${app.trial.duration-months:N}")',
    )
    expect(legal).toContain(`бесплатно на ${months} месяца`)
  })

  /** Цена названа в трёх местах фронтенда — расходятся они между собой. */
  it('цены в оферте, на странице тарифов и в локали совпадают', () => {
    for (const price of ['199 ₽', '1 490 ₽']) {
      expect(legal, `оферта: ${price}`).toContain(price)
      expect(read('src/locales/ru.json'), `локаль: ${price}`).toContain(price)
    }
    expect(read('src/pages/Pricing.tsx')).toContain('199 ₽')
    expect(read('src/pages/Pricing.tsx')).toContain('1 490 ₽')
  })
})

describe('оферта не выдаёт будущее за настоящее', () => {
  const legal = read('legal.html')

  /**
   * MONETIZATION_ENABLED выключен: оплаты нет, лимиты не применяются. Пока это
   * так, оферта обязана говорить о платных тарифах в будущем времени — иначе
   * она обещает процесс, которого не существует.
   */
  it('платный тариф и оплата описаны как будущие', () => {
    expect(legal).toContain('С момента запуска платных подписок')
    expect(legal).toContain('Оплата будет производиться')
    expect(legal).toContain('платные тарифы\n          не действуют')
  })

  it('бесплатный Pro раннего доступа описан', () => {
    expect(legal).toContain('раннего доступа')
    expect(legal).toContain('переключается на базовый (Free)')
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
