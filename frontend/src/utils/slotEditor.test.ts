import { describe, expect, it, vi } from 'vitest'
import type { AvailabilityResponse } from '@/types'
import type { GridSpec } from './selection'
import {
  applySlotEdit,
  copyRuleFrom,
  needsScopeChoice,
  openSlotEditor,
  slotAtCell,
  validateSlotEdit,
  type SlotEditApi,
} from './slotEditor'

/**
 * Логика модалки слота: что открылось, что спросить и куда отправить.
 *
 * Разметка здесь не проверяется — jsdom в проект не заводится, да и решения
 * не в ней. Решений три: одинаковое состояние при входе из списка и из сетки,
 * вопрос про область действия только когда он осмыслен, и выбор эндпоинта по
 * ответу на этот вопрос.
 */

const WEEK = [
  '2026-11-02', '2026-11-03', '2026-11-04', '2026-11-05',
  '2026-11-06', '2026-11-07', '2026-11-08',
]
const GRID: GridSpec = { stepMinutes: 30, rowsPerDay: 48, days: WEEK, zone: 'UTC' }

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

describe('открытие редактора', () => {
  /**
   * Оба входа обязаны давать одинаковое состояние: иначе «одна и та же
   * модалка» — это две модалки, которые однажды разойдутся.
   */
  it('вход из списка и вход из сетки дают одно и то же состояние', () => {
    const mine = slot('2026-11-03T10:00:00Z', '2026-11-03T12:00:00Z')
    const all = [mine]

    // Из списка — слот приходит как есть.
    const fromList = openSlotEditor(mine, all, 'UTC')
    // Из сетки — сначала находим слот по координате: вторник, строка 20 = 10:00.
    const found = slotAtCell({ row: 20, col: 1 }, all, GRID)
    expect(found).toBe(mine)
    const fromGrid = openSlotEditor(found!, all, 'UTC')

    expect(fromGrid).toEqual(fromList)
    expect(fromList).toMatchObject({
      startTime: '10:00',
      endTime: '12:00',
      date: '2026-11-03',
      isSeries: false,
      seriesSize: 1,
    })
  })

  it('в сетке под свободной ячейкой слота нет', () => {
    const mine = slot('2026-11-03T10:00:00Z', '2026-11-03T12:00:00Z')
    expect(slotAtCell({ row: 40, col: 1 }, [mine], GRID)).toBeNull()
  })

  it('у слота серии видно её размер', () => {
    const a = slot('2026-11-03T10:00:00Z', '2026-11-03T12:00:00Z', 'series-1')
    const b = slot('2026-11-10T10:00:00Z', '2026-11-10T12:00:00Z', 'series-1')
    const other = slot('2026-11-04T10:00:00Z', '2026-11-04T11:00:00Z')

    const state = openSlotEditor(a, [a, b, other], 'UTC')
    expect(state.isSeries).toBe(true)
    expect(state.seriesSize).toBe(2)
  })

  it('время читается в зоне пользователя, а не в UTC', () => {
    const mine = slot('2026-11-03T07:00:00Z', '2026-11-03T09:00:00Z')
    expect(openSlotEditor(mine, [mine], 'Europe/Moscow')).toMatchObject({
      startTime: '10:00',
      endTime: '12:00',
    })
  })
})

describe('выбор области действия', () => {
  const single = slot('2026-11-03T10:00:00Z', '2026-11-03T12:00:00Z')
  const inSeries = slot('2026-11-03T10:00:00Z', '2026-11-03T12:00:00Z', 'series-1')

  it('у одиночного слота выбор не показывается', () => {
    const state = openSlotEditor(single, [single], 'UTC')
    expect(needsScopeChoice(state, '11:00', '13:00')).toBe(false)
  })

  it('у слота серии при изменённом времени выбор показывается', () => {
    const state = openSlotEditor(inSeries, [inSeries], 'UTC')
    expect(needsScopeChoice(state, '11:00', '13:00')).toBe(true)
    expect(state.seriesSize).toBe(1)
  })

  /** Вопрос задаётся после правки: время не тронули — спрашивать нечего. */
  it('у слота серии без изменения времени выбор не показывается', () => {
    const state = openSlotEditor(inSeries, [inSeries], 'UTC')
    expect(needsScopeChoice(state, '10:00', '12:00')).toBe(false)
  })
})

describe('отправка правки', () => {
  const inSeries = slot('2026-11-03T10:00:00Z', '2026-11-03T12:00:00Z', 'series-1')
  const api = () => ({
    retimeSlot: vi.fn<Parameters<SlotEditApi['retimeSlot']>, Promise<unknown>>(async () => undefined),
    retimeSeries: vi.fn<Parameters<SlotEditApi['retimeSeries']>, Promise<unknown>>(async () => undefined),
  })

  it('«только этот» зовёт эндпоинт одного слота', async () => {
    const a = api()
    const state = openSlotEditor(inSeries, [inSeries], 'UTC')

    await applySlotEdit(state, 'single', { startTime: '11:00', endTime: '13:00' }, 'UTC', a)

    expect(a.retimeSlot).toHaveBeenCalledTimes(1)
    expect(a.retimeSlot).toHaveBeenCalledWith(inSeries.id, {
      startTime: '11:00', endTime: '13:00', timeZone: 'UTC',
    })
    expect(a.retimeSeries).not.toHaveBeenCalled()
  })

  it('«вся серия» зовёт эндпоинт серии', async () => {
    const a = api()
    const state = openSlotEditor(inSeries, [inSeries], 'UTC')

    await applySlotEdit(state, 'series', { startTime: '11:00', endTime: '13:00' }, 'UTC', a)

    expect(a.retimeSeries).toHaveBeenCalledTimes(1)
    expect(a.retimeSeries).toHaveBeenCalledWith(inSeries.id, {
      startTime: '11:00', endTime: '13:00', timeZone: 'UTC',
    })
    expect(a.retimeSlot).not.toHaveBeenCalled()
  })
})

describe('создать копию', () => {
  /** Форма серии предзаполняется временем исходного слота — остальное человек задаёт сам. */
  it('правило копии берёт время у исходного слота', () => {
    const mine = slot('2026-11-03T10:00:00Z', '2026-11-03T12:00:00Z')
    const state = openSlotEditor(mine, [mine], 'UTC')

    expect(copyRuleFrom(state)).toEqual({
      startDate: '2026-11-03',
      endDate: '2026-12-01',
      daysOfWeek: [2],           // вторник — день исходного слота
      startTime: '10:00',
      endTime: '12:00',
    })
  })
})

describe('валидация времени', () => {
  it('корректное время проходит', () => {
    expect(validateSlotEdit('10:00', '12:00')).toEqual([])
  })

  it('конец не позже начала — сохранять нельзя', () => {
    expect(validateSlotEdit('12:00', '12:00')).toEqual(['endNotAfterStart'])
    expect(validateSlotEdit('12:00', '11:00')).toEqual(['endNotAfterStart'])
  })

  /** Те же пределы, что в validateSlotTimes на бэкенде. */
  it('короче пяти минут и длиннее 48 часов — нельзя', () => {
    expect(validateSlotEdit('10:00', '10:03')).toEqual(['tooShort'])
  })

  it('мусор во времени — нельзя', () => {
    expect(validateSlotEdit('', '12:00')).toEqual(['invalid'])
  })
})
