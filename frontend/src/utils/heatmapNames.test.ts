import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { cellNames, MAX_VISIBLE_NAMES } from './heatmapNames'

/**
 * Кто свободен в ячейке теплокарты.
 *
 * Репортом на это вышла репетитор: «мне нужно видеть, кто какой слот отметил
 * непосредственно в таблице». Проверяется отбор имён к показу — то, что
 * решается без React и потому проверяемо отдельно от разметки.
 */

describe('① имена отметившихся попадают в подсказку', () => {
  it('трое отметившихся — все три имени', () => {
    const shown = cellNames(['Аня Петрова', 'Борис Сидоров', 'Вера Кузнецова'])
    expect(shown).not.toBeNull()
    expect(shown!.visible).toEqual(['Аня Петрова', 'Борис Сидоров', 'Вера Кузнецова'])
    expect(shown!.hiddenCount).toBe(0)
    expect(shown!.total).toBe(3)
  })

  it('порядок сохраняется — он приходит с сервера', () => {
    expect(cellNames(['Яна', 'Аня'])!.visible).toEqual(['Яна', 'Аня'])
  })
})

describe('② пустая ячейка ничего не показывает', () => {
  it('никто не отметился — панели нет', () => {
    expect(cellNames([])).toBeNull()
  })

  /** null приходит, когда у группы выключена настройка «показывать участников». */
  it('имена скрыты настройкой группы — панели нет', () => {
    expect(cellNames(null)).toBeNull()
    expect(cellNames(undefined)).toBeNull()
  })

  it('строки из пробелов не создают пустых пунктов', () => {
    expect(cellNames(['   ', ''])).toBeNull()
    expect(cellNames(['Аня', '  ', 'Борис'])!.visible).toEqual(['Аня', 'Борис'])
  })
})

describe('③ длинный список обрезается с остатком', () => {
  const many = (n: number) => Array.from({ length: n }, (_, i) => `Участник ${i + 1}`)

  it('ровно на пороге — не обрезается', () => {
    const shown = cellNames(many(MAX_VISIBLE_NAMES))!
    expect(shown.visible).toHaveLength(MAX_VISIBLE_NAMES)
    expect(shown.hiddenCount).toBe(0)
  })

  it('на единицу больше порога — один в остатке', () => {
    const shown = cellNames(many(MAX_VISIBLE_NAMES + 1))!
    expect(shown.visible).toHaveLength(MAX_VISIBLE_NAMES)
    expect(shown.hiddenCount).toBe(1)
    expect(shown.total).toBe(MAX_VISIBLE_NAMES + 1)
  })

  it('счётчик остатка верный на большой группе', () => {
    const shown = cellNames(many(30))!
    expect(shown.visible).toHaveLength(MAX_VISIBLE_NAMES)
    expect(shown.hiddenCount).toBe(30 - MAX_VISIBLE_NAMES)
    expect(shown.visible.length + shown.hiddenCount).toBe(shown.total)
  })

  it('показанные — это начало списка, а не случайные', () => {
    expect(cellNames(many(20))!.visible[0]).toBe('Участник 1')
    expect(cellNames(many(20))!.visible.at(-1)).toBe(`Участник ${MAX_VISIBLE_NAMES}`)
  })

  it('порог можно задать явно — для проверки границ', () => {
    const shown = cellNames(['а', 'б', 'в'], 2)!
    expect(shown.visible).toEqual(['а', 'б'])
    expect(shown.hiddenCount).toBe(1)
  })

  /** Вырожденный порог не должен ронять панель. */
  it('нулевой порог — всё уходит в остаток, но не в исключение', () => {
    const shown = cellNames(['а', 'б'], 0)!
    expect(shown.visible).toEqual([])
    expect(shown.hiddenCount).toBe(2)
  })

  it('пробелы не учитываются в остатке', () => {
    const shown = cellNames([...many(MAX_VISIBLE_NAMES), '  '], MAX_VISIBLE_NAMES)!
    expect(shown.hiddenCount).toBe(0)
  })
})

/**
 * ④⑤ Показ имён не должен ломать то, что уже работает.
 *
 * Жест выделения и создание встречи живут в других модулях и покрыты своими
 * тестами (`useDragSelection.test.ts`, `selection.test.ts`). Здесь проверяется
 * ровно то, что могла сломать эта правка, — проводка обработчиков на ячейке.
 *
 * Проверка по исходнику, а не рендером: jsdom и Testing Library в проекте нет,
 * а тот же приём уже используется в бэкендовых тестах конфигурации. Ловит она
 * то, что и должна: обработчик, забравший событие себе.
 */
const GRID_SOURCE = readFileSync(resolve(__dirname, '../pages/group/WeekGrid.tsx'), 'utf-8')
const TAB_SOURCE = readFileSync(resolve(__dirname, '../pages/group/HeatmapTab.tsx'), 'utf-8')

describe('④⑤ существующие действия не сломаны', () => {
  /** Кусок разметки ячейки — от data-row до её содержимого. */
  const cellMarkup = GRID_SOURCE.slice(
    GRID_SOURCE.indexOf('data-row={rowIdx}'),
    GRID_SOURCE.indexOf("{mine !== 'free' &&"),
  )

  it('обработчики показа имён навешаны на ячейку', () => {
    expect(cellMarkup).toContain('onMouseEnter')
    expect(cellMarkup).toContain('onPointerDown')
    expect(cellMarkup).toContain('onCellFocus')
  })

  /**
   * Главное. Жест выделения слушает контейнер, а не ячейку: событие обязано
   * всплыть. stopPropagation на ячейке отобрал бы у него и протяжку, и
   * тач-выделение с ручками — молча, без единой ошибки в консоли.
   */
  it('ячейка не перехватывает событие у жеста на контейнере', () => {
    // Ищем вызовы, а не упоминания: в самой разметке эти имена стоят в
    // комментарии, объясняющем, почему их там нет. Первая версия теста ловила
    // собственный комментарий и падала — на нём это и обнаружилось.
    expect(cellMarkup).not.toMatch(/\.stopPropagation\s*\(/)
    expect(cellMarkup).not.toMatch(/\.preventDefault\s*\(/)
  })

  /** Создание встречи по клику осталось на месте и не заменено показом имён. */
  it('клик по ячейке по-прежнему создаёт встречу', () => {
    expect(cellMarkup).toContain('onCellActivate(rowIdx, colIdx)')
    expect(cellMarkup).toContain('onClick={clickable')
  })

  /** Показ имён не должен зависеть от режима: он нужен в обоих. */
  it('показ имён не привязан к режиму встречи', () => {
    const focusLine = TAB_SOURCE.split('\n').find((l) => l.includes('onCellFocus='))
    expect(focusLine, 'onCellFocus должен передаваться в WeekGrid').toBeDefined()
    expect(focusLine).not.toContain('meetingMode')
  })

  /**
   * Панель подтягивается к экрану только под пальцем.
   *
   * На 375px сетка занимает экран целиком, и панель под таблицей оказывалась за
   * нижним краем: имена были в разметке, но человек их не видел — ручная
   * проверка на этом и споткнулась. Мышью подтягивать нельзя: прокрутка уводит
   * сетку из-под курсора, срабатывает наведение на соседнюю ячейку, и панель
   * начинает мигать. Ровно это наблюдалось в браузере до разделения.
   */
  it('панель подтягивается к экрану только при касании', () => {
    expect(TAB_SOURCE).toContain('scrollIntoView')
    const effect = TAB_SOURCE.slice(
      TAB_SOURCE.indexOf('if (!focusedCell?.byTouch)'),
      TAB_SOURCE.indexOf('scrollIntoView'),
    )
    expect(effect, 'подтягивание должно быть под условием byTouch').toContain('return')
    expect(TAB_SOURCE).toContain("block: 'nearest'")
    // Наведение мышью помечается как не-касание — иначе условие бессмысленно.
    expect(GRID_SOURCE).toMatch(/onMouseEnter=\{[^}]*onCellFocus\(rowIdx, colIdx, false\)/)
    expect(GRID_SOURCE).toContain("e.pointerType === 'touch'")
  })
})
