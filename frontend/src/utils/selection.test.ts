import { describe, expect, it } from 'vitest'
import { DateTime } from 'luxon'
import type { AvailabilityResponse } from '@/types'
import {
  cellStart,
  clampSelection,
  isOvernightSlot,
  resolveSelection,
  selectionToSlots,
  type GridSpec,
} from './selection'

/**
 * Математика протяжки. Проверяется числами, а не «работает ли»: этот модуль
 * специально отделён от жеста, чтобы у него был оракул, и оракул — вот он.
 *
 * Параметры сетки взяты из HeatmapTab: 48 строк по 30 минут, семь дней от
 * понедельника. Строка 20 — это 10:00, строка 47 — 23:30.
 */

/** Неделя с понедельника 19 октября 2026. */
const WEEK = [
  '2026-10-19', '2026-10-20', '2026-10-21', '2026-10-22',
  '2026-10-23', '2026-10-24', '2026-10-25',
]

const grid = (over: Partial<GridSpec> = {}): GridSpec => ({
  stepMinutes: 30,
  rowsPerDay: 48,
  days: WEEK,
  zone: 'UTC',
  ...over,
})

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

const hoursBetween = (a: string, b: string) =>
  DateTime.fromISO(b).diff(DateTime.fromISO(a), 'hours').hours

// ── нормализация и форма ────────────────────────────────────────────────────

describe('resolveSelection', () => {
  it('протяжка вниз с 3 до 7 даёт диапазон 3..7 длиной 5', () => {
    const range = resolveSelection({ row: 3, col: 0 }, { row: 7, col: 0 }, grid())
    expect(range.days).toHaveLength(1)
    expect(range.days[0]).toMatchObject({ col: 0, startRow: 3, endRow: 7, length: 5 })
    expect(range.cellCount).toBe(5)
  })

  /** Направление жеста на результат влиять не должно — только на то, где якорь. */
  it('протяжка вверх с 7 до 3 даёт тот же диапазон', () => {
    const down = resolveSelection({ row: 3, col: 0 }, { row: 7, col: 0 }, grid())
    const up = resolveSelection({ row: 7, col: 0 }, { row: 3, col: 0 }, grid())
    expect(up.days).toEqual(down.days)
    expect(up.cellCount).toBe(5)
  })

  it('клик без протяжки даёт диапазон из одной ячейки', () => {
    const range = resolveSelection({ row: 12, col: 2 }, { row: 12, col: 2 }, grid())
    expect(range.days).toHaveLength(1)
    expect(range.days[0].length).toBe(1)
    expect(range.cellCount).toBe(1)
  })

  /**
   * Сетка — таблица, а не текст. Протяжка по диагонали даёт прямоугольник:
   * три отдельных дня с одним и тем же временем, а не одна лента, тянущаяся
   * через две ночи.
   */
  it('протяжка через три столбца даёт три диапазона с одинаковыми границами', () => {
    const range = resolveSelection({ row: 10, col: 1 }, { row: 12, col: 3 }, grid())

    expect(range.days.map((d) => d.col)).toEqual([1, 2, 3])
    for (const day of range.days) {
      expect(day).toMatchObject({ startRow: 10, endRow: 12, length: 3 })
    }
    expect(range.cellCount).toBe(9)
  })
})

// ── склейка и дубли ─────────────────────────────────────────────────────────

describe('selectionToSlots', () => {
  /** Один жест — одна запись. Четыре получасовки человек потом удалял бы по одной. */
  it('четыре соседние свободные ячейки склеиваются в один слот', () => {
    const range = resolveSelection({ row: 20, col: 0 }, { row: 23, col: 0 }, grid())
    const plan = selectionToSlots(range, [], grid())

    expect(plan.toCreate).toHaveLength(1)
    expect(plan.toCreate[0]).toEqual({
      startsAt: '2026-10-19T10:00:00.000Z',
      endsAt: '2026-10-19T12:00:00.000Z',
    })
    expect(plan.toDelete).toHaveLength(0)
  })

  it('выделение внутри существующего слота ничего не создаёт и не удаляет', () => {
    const existing = slot('2026-10-19T09:00:00Z', '2026-10-19T13:00:00Z')
    const range = resolveSelection({ row: 20, col: 0 }, { row: 21, col: 0 }, grid())
    const plan = selectionToSlots(range, [existing], grid())

    expect(plan.toCreate).toHaveLength(0)
    expect(plan.toDelete).toHaveLength(0)
    expect(plan.unchanged.map((s) => s.id)).toEqual([existing.id])
  })

  /**
   * Вплотную — значит один слот. Две записи подряд без зазора между ними
   * человек прочитает как одну и удивится, что удалять их надо дважды.
   */
  it('выделение вплотную к слоту снизу сливается с ним в один', () => {
    const existing = slot('2026-10-19T09:00:00Z', '2026-10-19T10:00:00Z')
    const range = resolveSelection({ row: 20, col: 0 }, { row: 23, col: 0 }, grid())
    const plan = selectionToSlots(range, [existing], grid())

    expect(plan.toDelete.map((s) => s.id)).toEqual([existing.id])
    expect(plan.toCreate).toHaveLength(1)
    expect(plan.toCreate[0]).toEqual({
      startsAt: '2026-10-19T09:00:00.000Z',
      endsAt: '2026-10-19T12:00:00.000Z',
    })
    // Длительность объединённого равна сумме: час старого плюс два выделенных.
    expect(hoursBetween(plan.toCreate[0].startsAt, plan.toCreate[0].endsAt)).toBe(3)
  })

  it('слот серии под выделением не режется, а возвращается отдельно', () => {
    const series = slot('2026-10-19T10:00:00Z', '2026-10-19T12:00:00Z', 'series-1')
    const range = resolveSelection({ row: 20, col: 0 }, { row: 23, col: 0 }, grid())
    const plan = selectionToSlots(range, [series], grid())

    expect(plan.toCreate).toHaveLength(0)
    expect(plan.toDelete).toHaveLength(0)
    expect(plan.blocked).toEqual([{ slot: series, reason: 'series' }])
    expect(plan.unchanged.map((s) => s.id)).toEqual([series.id])
  })

  /**
   * Смешанный случай, который и будет происходить на практике: серия
   * занимает часть выделения. Серию не трогаем, но и остальное выделение не
   * выбрасываем — иначе протяжка рядом с серией просто перестала бы работать.
   */
  it('свободная часть выделения рядом с серией создаётся, серия — нет', () => {
    const series = slot('2026-10-19T10:00:00Z', '2026-10-19T11:00:00Z', 'series-1')
    const range = resolveSelection({ row: 20, col: 0 }, { row: 23, col: 0 }, grid())
    const plan = selectionToSlots(range, [series], grid())

    expect(plan.toCreate).toEqual([
      { startsAt: '2026-10-19T11:00:00.000Z', endsAt: '2026-10-19T12:00:00.000Z' },
    ])
    expect(plan.blocked).toEqual([{ slot: series, reason: 'series' }])
    expect(plan.toDelete).toHaveLength(0)
  })

  /**
   * Слот 23:00 понедельника — 01:00 вторника. Протяжка по вторнику с 00:00 до
   * 01:00 примыкает к нему вплотную и по общему правилу слияния поглотила бы
   * его целиком — вместе с двумя часами понедельника, которых в жесте не было.
   * Молча уносить время за пределами выделения нельзя, поэтому такой слот
   * протяжкой не редактируется вовсе.
   */
  it('слот через полночь протяжкой не трогается', () => {
    const overnight = slot('2026-10-19T23:00:00Z', '2026-10-20T01:00:00Z')
    const range = resolveSelection({ row: 0, col: 1 }, { row: 1, col: 1 }, grid())
    const plan = selectionToSlots(range, [overnight], grid())

    expect(plan.blocked).toEqual([{ slot: overnight, reason: 'overnight' }])
    expect(plan.toDelete).toHaveLength(0)
    expect(plan.toCreate).toHaveLength(0)
  })

  it('свободная часть выделения рядом с ночным слотом всё равно создаётся', () => {
    const overnight = slot('2026-10-19T23:00:00Z', '2026-10-20T01:00:00Z')
    // Вторник 00:00–02:00: первые два получаса заняты ночным слотом, вторые два свободны.
    const range = resolveSelection({ row: 0, col: 1 }, { row: 3, col: 1 }, grid())
    const plan = selectionToSlots(range, [overnight], grid())

    expect(plan.blocked.map((b) => b.reason)).toEqual(['overnight'])
    expect(plan.toCreate).toEqual([
      { startsAt: '2026-10-20T01:00:00.000Z', endsAt: '2026-10-20T02:00:00.000Z' },
    ])
  })

  /** Ровно до полуночи — это ещё свой день, и редактируется как обычный слот. */
  it('слот, заканчивающийся ровно в полночь, ночным не считается', () => {
    const untilMidnight = slot('2026-10-19T23:00:00Z', '2026-10-20T00:00:00Z')
    expect(isOvernightSlot(untilMidnight, 'UTC')).toBe(false)
    expect(isOvernightSlot(slot('2026-10-19T23:00:00Z', '2026-10-20T00:30:00Z'), 'UTC')).toBe(true)

    // Протяжка по понедельнику 22:00–23:00 сливается с ним, как с любым другим.
    const range = resolveSelection({ row: 44, col: 0 }, { row: 45, col: 0 }, grid())
    const plan = selectionToSlots(range, [untilMidnight], grid())
    expect(plan.blocked).toHaveLength(0)
    expect(plan.toDelete.map((s) => s.id)).toEqual([untilMidnight.id])
    expect(plan.toCreate).toEqual([
      { startsAt: '2026-10-19T22:00:00.000Z', endsAt: '2026-10-20T00:00:00.000Z' },
    ])
  })

  /** У ночного слота серии причина — серия: у неё есть свой способ удаления. */
  it('ночной слот серии блокируется как серия', () => {
    const nightSeries = slot('2026-10-19T23:00:00Z', '2026-10-20T01:00:00Z', 'series-1')
    const range = resolveSelection({ row: 0, col: 1 }, { row: 1, col: 1 }, grid())
    expect(selectionToSlots(range, [nightSeries], grid()).blocked).toEqual([
      { slot: nightSeries, reason: 'series' },
    ])
  })

  /** «Через полночь» — про календарные дни зоны сетки, а не про UTC. */
  it('ночным слот считается по зоне сетки, а не по UTC', () => {
    const moscow = grid({ zone: 'Europe/Moscow', days: ['2026-11-02', '2026-11-03'] })
    // 21:00–23:00 UTC — это 00:00–02:00 вторника по Москве: в UTC переход
    // через полночь есть, по московскому календарю его нет.
    const s = slot('2026-11-02T21:00:00Z', '2026-11-02T23:00:00Z')
    expect(isOvernightSlot(s, 'UTC')).toBe(false)
    expect(isOvernightSlot(s, moscow.zone)).toBe(false)

    // А 22:00–01:00 по Москве — ночной именно в московском календаре.
    const night = slot('2026-11-02T19:00:00Z', '2026-11-02T22:00:00Z')
    expect(isOvernightSlot(night, 'Europe/Moscow')).toBe(true)
    expect(isOvernightSlot(night, 'UTC')).toBe(false)
  })

  it('пустая сетка и ноль слотов не роняют расчёт', () => {
    const emptyGrid = grid({ days: [] })
    const range = resolveSelection({ row: 0, col: 0 }, { row: 5, col: 3 }, emptyGrid)

    expect(range.days).toHaveLength(0)
    expect(range.cellCount).toBe(0)

    const plan = selectionToSlots(range, [], emptyGrid)
    expect(plan).toEqual({ toCreate: [], toDelete: [], unchanged: [], blocked: [] })
    expect(clampSelection(range, emptyGrid).days).toHaveLength(0)
  })
})

// ── границы ─────────────────────────────────────────────────────────────────

describe('clampSelection', () => {
  it('протяжка за нижнюю границу суток обрезается последней ячейкой дня', () => {
    const range = resolveSelection({ row: 40, col: 0 }, { row: 60, col: 0 }, grid())
    const clamped = clampSelection(range, grid())

    expect(clamped.days).toHaveLength(1)
    expect(clamped.days[0]).toMatchObject({ col: 0, startRow: 40, endRow: 47, length: 8 })
    // В соседний день выделение не переезжает: столбец — это календарный день.
    expect(clamped.days.map((d) => d.col)).toEqual([0])
  })

  it('80 ячеек обрезаются ровно до 50', () => {
    // Восемьдесят ячеек в одном дне не помещаются — в сутках их 48, — поэтому
    // жест идёт через два столбца: 40 строк × 2 дня.
    const range = resolveSelection({ row: 0, col: 0 }, { row: 39, col: 1 }, grid())
    expect(range.cellCount).toBe(80)

    const clamped = clampSelection(range, grid())
    expect(clamped.cellCount).toBe(50)
    expect(clamped.days).toHaveLength(2)
    for (const day of clamped.days) {
      expect(day).toMatchObject({ startRow: 0, endRow: 24, length: 25 })
    }
  })

  /** Отрезаем со стороны пальца: начало жеста остаётся там, где человек его начал. */
  it('при протяжке вверх обрезается верхний край, а не нижний', () => {
    const range = resolveSelection({ row: 39, col: 1 }, { row: 0, col: 0 }, grid())
    const clamped = clampSelection(range, grid())

    expect(clamped.cellCount).toBe(50)
    for (const day of clamped.days) {
      expect(day).toMatchObject({ startRow: 15, endRow: 39 })
    }
  })

  it('свой потолок можно передать', () => {
    const range = resolveSelection({ row: 0, col: 0 }, { row: 20, col: 0 }, grid())
    expect(clampSelection(range, grid(), { maxCells: 4 }).cellCount).toBe(4)
  })
})

// ── время ───────────────────────────────────────────────────────────────────

describe('ячейка → момент времени', () => {
  /**
   * Главная проверка модуля. Строка 20 подписана в сетке как 10:00, и она
   * обязана быть 10:00 по местным часам в обе стороны от перевода. Абсолютные
   * моменты при этом разные — это и есть доказательство, что считается
   * настенное время, а не «полночь плюс 600 минут».
   */
  it('10:00 в Europe/Berlin остаётся 10:00 по обе стороны перевода часов', () => {
    const berlin = grid({ zone: 'Europe/Berlin', days: ['2026-10-20', '2026-10-27'] })

    const before = cellStart(0, 20, berlin)
    const after = cellStart(1, 20, berlin)

    expect(before.hour).toBe(10)
    expect(after.hour).toBe(10)
    expect(before.toUTC().toISO()).toBe('2026-10-20T08:00:00.000Z')
    expect(after.toUTC().toISO()).toBe('2026-10-27T09:00:00.000Z')

    // Летнее +02:00 против зимнего +01:00.
    expect(before.offset - after.offset).toBe(60)
  })

  /**
   * Зеркало предыдущей проверки. Россия отменила сезонный перевод в 2011
   * году: в Москве смещение обязано остаться прежним, и «поправка на переход»,
   * применённая ко всем зонам подряд, споткнётся именно здесь.
   */
  it('в Europe/Moscow перевода нет и смещение не меняется', () => {
    const moscow = grid({ zone: 'Europe/Moscow', days: ['2026-10-20', '2026-10-27'] })

    const before = cellStart(0, 20, moscow)
    const after = cellStart(1, 20, moscow)

    expect(before.toUTC().toISO()).toBe('2026-10-20T07:00:00.000Z')
    expect(after.toUTC().toISO()).toBe('2026-10-27T07:00:00.000Z')
    expect(before.offset).toBe(after.offset)
  })

  /**
   * Конец суток — полночь следующего календарного дня, а не «плюс 24 часа».
   * 25 октября 2026 в Берлине длится 25 часов, и слот, дотянутый до конца
   * дня, обязан заканчиваться ровно на границе суток, а не за час до неё.
   */
  it('последняя ячейка упирается в полночь следующего календарного дня', () => {
    const berlin = grid({ zone: 'Europe/Berlin', days: ['2026-10-25'] })

    const dayStart = cellStart(0, 0, berlin)
    const dayEnd = cellStart(0, 48, berlin)

    expect(dayStart.toUTC().toISO()).toBe('2026-10-24T22:00:00.000Z')
    expect(dayEnd.toUTC().toISO()).toBe('2026-10-25T23:00:00.000Z')
    expect(dayEnd.diff(dayStart, 'hours').hours).toBe(25)
  })

  it('слот считается в зоне сетки, а не в зоне машины', () => {
    const moscow = grid({ zone: 'Europe/Moscow', days: ['2026-11-03'] })
    const range = resolveSelection({ row: 20, col: 0 }, { row: 23, col: 0 }, moscow)
    const plan = selectionToSlots(range, [], moscow)

    // 10:00–12:00 по Москве — это 07:00–09:00 UTC.
    expect(plan.toCreate).toEqual([
      { startsAt: '2026-11-03T07:00:00.000Z', endsAt: '2026-11-03T09:00:00.000Z' },
    ])
  })
})
