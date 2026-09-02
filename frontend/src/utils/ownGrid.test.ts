import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import { DateTime } from 'luxon'
import type { AvailabilityResponse } from '@/types'
import { buildOwnGrid, DAYS_PER_WEEK, ROWS_PER_DAY, STEP_MINUTES } from './ownGrid'
import { resolveSelection, selectionToSlots } from './selection'

/**
 * Раскладка своих слотов по ячейкам недели.
 *
 * Проверяется числами, а не «отрисовалось ли»: компонент рядом только красит
 * то, что посчитано здесь, и вся арифметика, которую можно испортить, живёт в
 * этом модуле.
 *
 * Понедельник 19 октября 2026, зона UTC — чтобы «10:00» в ISO и «10:00» в
 * сетке читались одинаково и в тесте не приходилось держать в голове
 * смещение. Отдельная проверка на зону — в конце.
 */

const MONDAY = DateTime.fromISO('2026-10-19', { zone: 'UTC' })

let seq = 0
function slot(startsAt: string, endsAt: string, seriesId: string | null = null): AvailabilityResponse {
  return {
    id: `slot-${++seq}`,
    groupId: 'g1',
    userId: 'u1',
    startsAt,
    endsAt,
    note: null,
    seriesId,
    createdAt: '2026-10-01T00:00:00Z',
  }
}

describe('форма сетки', () => {
  it('48 строк по 7 столбцов', () => {
    const grid = buildOwnGrid([], MONDAY)
    expect(grid.cells).toHaveLength(48)
    for (const row of grid.cells) expect(row).toHaveLength(7)
  })

  it('столбцы — семь календарных дней от понедельника', () => {
    const grid = buildOwnGrid([], MONDAY)
    expect(grid.spec.days).toEqual([
      '2026-10-19', '2026-10-20', '2026-10-21', '2026-10-22',
      '2026-10-23', '2026-10-24', '2026-10-25',
    ])
  })

  it('пустой список слотов даёт полностью свободную сетку', () => {
    const grid = buildOwnGrid([], MONDAY)
    const states = new Set(grid.cells.flat())
    expect([...states]).toEqual(['free'])
  })
})

describe('раскладка слотов', () => {
  /** 10:00–12:00 — это строки 20..23, ровно четыре получасовки. */
  it('слот 10:00–12:00 в понедельник занимает ровно четыре ячейки', () => {
    const grid = buildOwnGrid([slot('2026-10-19T10:00:00Z', '2026-10-19T12:00:00Z')], MONDAY)

    expect(grid.cells.map((row) => row[0]).slice(20, 24)).toEqual(['busy', 'busy', 'busy', 'busy'])
    // Соседние по времени — свободны.
    expect(grid.cells[19][0]).toBe('free')
    expect(grid.cells[24][0]).toBe('free')
    // Соседний день — тоже.
    expect(grid.cells[20][1]).toBe('free')
    expect(grid.cells.flat().filter((s) => s !== 'free')).toHaveLength(4)
  })

  it('слот серии красится состоянием серии, а не обычным', () => {
    const grid = buildOwnGrid(
      [slot('2026-10-19T10:00:00Z', '2026-10-19T12:00:00Z', 'series-1')],
      MONDAY,
    )
    expect(grid.cells.map((row) => row[0]).slice(20, 24)).toEqual(
      ['series', 'series', 'series', 'series'],
    )
  })

  /**
   * Форма позволяет завести 14:15–15:47, и в сетке по полчаса такой слот не
   * выражается. Ни одна ячейка не покрыта им целиком, значит все они
   * «частичные» — и человек видит, что время есть, но сеткой его не поправить.
   */
  it('слот 14:15–15:47 даёт частично занятые ячейки, а не обычные', () => {
    const grid = buildOwnGrid([slot('2026-10-19T14:15:00Z', '2026-10-19T15:47:00Z')], MONDAY)
    const column = grid.cells.map((row) => row[0])

    // 14:00 (28), 14:30 (29), 15:00 (30), 15:30 (31) — все задеты, ни одна не целиком.
    expect(column.slice(28, 32)).toEqual(['partial', 'partial', 'partial', 'partial'])
    expect(column).not.toContain('busy')
    expect(column[27]).toBe('free')
    expect(column[32]).toBe('free')
  })

  /**
   * Достаточно одной неровной границы. У 10:15–12:00 середина покрыта
   * целиком, но красить её как обычную нельзя: она стала бы неотличима от
   * честных 10:30–12:00, и человек не понял бы, откуда в списке 10:15.
   */
  it('одна неровная граница делает частичным весь слот, а не только край', () => {
    const grid = buildOwnGrid([slot('2026-10-19T10:15:00Z', '2026-10-19T12:00:00Z')], MONDAY)
    const column = grid.cells.map((row) => row[0])
    expect(column.slice(20, 24)).toEqual(['partial', 'partial', 'partial', 'partial'])
    expect(column).not.toContain('busy')
  })

  /**
   * Серия выигрывает у обычного слота даже там, где обычный покрывает ячейку
   * целиком: решает не «чего больше», а что человек здесь может сделать.
   * Протяжка серию не изменит, и ячейка цвета обычного слота пообещала бы
   * редактируемость, которой нет.
   */
  it('на пересечении обычного слота и серии побеждает серия', () => {
    const grid = buildOwnGrid(
      [
        slot('2026-10-19T10:00:00Z', '2026-10-19T12:00:00Z'),
        slot('2026-10-19T11:00:00Z', '2026-10-19T11:30:00Z', 'series-1'),
      ],
      MONDAY,
    )
    const column = grid.cells.map((row) => row[0])
    expect(column.slice(20, 24)).toEqual(['busy', 'busy', 'series', 'busy'])
  })
})

/**
 * Единственная проверка, доказывающая, что переходник между сеткой и
 * математикой протяжки не понадобится. Остальные тесты про раскладку —
 * этот про стык: `spec` уходит в `resolveSelection` как есть, без приведения
 * типов, и посчитанные из него моменты совпадают с ячейками, которые сетка
 * покрасила.
 */
describe('стык с selection.ts', () => {
  it('spec принимается resolveSelection без приведения типов', () => {
    const grid = buildOwnGrid([], MONDAY)
    const range = resolveSelection({ row: 20, col: 0 }, { row: 23, col: 1 }, grid.spec)

    expect(range.days.map((d) => d.col)).toEqual([0, 1])
    for (const day of range.days) {
      expect(day).toMatchObject({ startRow: 20, endRow: 23, length: 4 })
    }
    expect(range.cellCount).toBe(8)
  })

  it('ячейки, которые покрасила сетка, — это те же моменты, что создаст протяжка', () => {
    const existing = slot('2026-10-19T10:00:00Z', '2026-10-19T12:00:00Z')
    const grid = buildOwnGrid([existing], MONDAY)

    // Сетка считает строки 20..23 занятыми…
    expect(grid.cells.map((r) => r[0]).slice(20, 24)).toEqual(['busy', 'busy', 'busy', 'busy'])

    // …и протяжка ровно по ним не создаёт ничего нового.
    const range = resolveSelection({ row: 20, col: 0 }, { row: 23, col: 0 }, grid.spec)
    const plan = selectionToSlots(range, [existing], grid.spec)
    expect(plan.toCreate).toHaveLength(0)
    expect(plan.unchanged.map((s) => s.id)).toEqual([existing.id])

    // А протяжка по соседним свободным строкам создаёт слот, примыкающий к
    // существующему, — то есть границы ячеек у обоих модулей совпадают.
    const below = resolveSelection({ row: 24, col: 0 }, { row: 25, col: 0 }, grid.spec)
    expect(selectionToSlots(below, [existing], grid.spec).toCreate[0]).toEqual({
      startsAt: '2026-10-19T10:00:00.000Z',
      endsAt: '2026-10-19T13:00:00.000Z',
    })
  })
})

describe('зона и календарный шаг', () => {
  it('столбцы и ячейки считаются в зоне переданной даты', () => {
    const moscow = DateTime.fromISO('2026-11-02', { zone: 'Europe/Moscow' })
    // 10:00–11:00 по Москве — 07:00–08:00 UTC.
    const grid = buildOwnGrid([slot('2026-11-02T07:00:00Z', '2026-11-02T08:00:00Z')], moscow)

    expect(grid.spec.zone).toBe('Europe/Moscow')
    expect(grid.cells.map((r) => r[0]).slice(20, 22)).toEqual(['busy', 'busy'])
    expect(grid.cells[19][0]).toBe('free')
  })

  /**
   * Неделя перевода часов. Шаг по дням календарный, поэтому столбцы остаются
   * последовательными датами, а слот 10:00 по местному попадает в ту же
   * строку 20 и до перевода, и после — хотя в UTC это разные часы.
   */
  it('перевод часов не сдвигает слот по строкам', () => {
    const berlin = DateTime.fromISO('2026-10-19', { zone: 'Europe/Berlin' })
    const grid = buildOwnGrid(
      [
        slot('2026-10-20T08:00:00Z', '2026-10-20T09:00:00Z'), // вт, 10:00 CEST
        slot('2026-10-25T09:00:00Z', '2026-10-25T10:00:00Z'), // вс, 10:00 CET
      ],
      berlin,
    )

    expect(grid.spec.days[6]).toBe('2026-10-25')
    expect(grid.cells[20][1]).toBe('busy')
    expect(grid.cells[20][6]).toBe('busy')
  })
})

/**
 * Параметры сетки продублированы с `buildGrid` в HeatmapTab: рефакторить его
 * этот заход не разрешал. Дублирование само по себе не страшно — страшно,
 * когда копии расходятся молча. Здесь они разойтись не смогут.
 */
describe('параметры совпадают с теплокартой', () => {
  const heatmap = readFileSync(
    resolve(__dirname, '../pages/group/HeatmapTab.tsx'),
    'utf-8',
  )

  it('шаг, число строк и число дней те же', () => {
    expect(STEP_MINUTES).toBe(30)
    expect(ROWS_PER_DAY).toBe(48)
    expect(DAYS_PER_WEEK).toBe(7)

    expect(heatmap, 'HeatmapTab считает бакеты не по 30 минут')
      .toContain('/ 30)')
    expect(heatmap, 'у HeatmapTab другое число строк')
      .toContain('{ length: 48 }')
    expect(heatmap.match(/'mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun'/), 'дни недели разошлись')
      .not.toBeNull()
  })
})
