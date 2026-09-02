import { describe, expect, it } from 'vitest'
import {
  countSeriesSlots,
  MAX_SERIES_SLOTS,
  toSeriesRequest,
  validateSeries,
  type SeriesRule,
} from './series'

/**
 * Подсчёт слотов серии до отправки.
 *
 * Проверяется одно: форма не врёт. Обещание «получится 4 слота» и фактические
 * 5 — это интерфейс, которому нельзя верить, и заметно это станет не сразу, а
 * когда человек упрётся в лимит на числе, которого он не ожидал.
 */

const rule = (over: Partial<SeriesRule> = {}): SeriesRule => ({
  startDate: '2026-11-02',   // понедельник
  endDate: '2026-11-29',
  daysOfWeek: [2],           // вторник
  startTime: '10:00',
  endTime: '12:00',
  ...over,
})

describe('countSeriesSlots', () => {
  it('четыре недели по одному дню недели дают 4 слота', () => {
    expect(countSeriesSlots(rule())).toBe(4)
  })

  it('два дня недели на две недели дают 4 слота', () => {
    // 2 ноября + 13 дней = 15 ноября; понедельники и среды: 2, 4, 9, 11.
    expect(countSeriesSlots(rule({ endDate: '2026-11-15', daysOfWeek: [1, 3] }))).toBe(4)
  })

  it('пустой набор дней недели даёт ноль', () => {
    expect(countSeriesSlots(rule({ daysOfWeek: [] }))).toBe(0)
  })

  it('конец раньше начала даёт ноль', () => {
    expect(countSeriesSlots(rule({ endDate: '2026-11-01' }))).toBe(0)
  })

  /** Границы диапазона входят в счёт: серия из одного дня — это один слот. */
  it('однодневный диапазон в свой день недели даёт один слот', () => {
    expect(countSeriesSlots(rule({ startDate: '2026-11-03', endDate: '2026-11-03' }))).toBe(1)
  })
})

describe('validateSeries', () => {
  it('корректное правило проходит', () => {
    const v = validateSeries(rule())
    expect(v.ok).toBe(true)
    expect(v.issues).toEqual([])
    expect(v.count).toBe(4)
  })

  /** 201 слот форма обязана отвергнуть до отправки, а не по ответу 400. */
  it('201 слот помечается как превышение', () => {
    // Все семь дней недели на 201 день подряд — ровно 201 слот.
    const v = validateSeries(rule({ endDate: '2027-05-21', daysOfWeek: [1, 2, 3, 4, 5, 6, 7] }))
    expect(v.count).toBe(201)
    expect(v.count).toBeGreaterThan(MAX_SERIES_SLOTS)
    expect(v.issues).toContain('tooManySlots')
    expect(v.ok).toBe(false)
  })

  it('конец не позже начала по времени — отправить нельзя', () => {
    expect(validateSeries(rule({ startTime: '12:00', endTime: '12:00' })).issues)
      .toContain('endTimeNotAfterStart')
    expect(validateSeries(rule({ startTime: '12:00', endTime: '11:00' })).issues)
      .toContain('endTimeNotAfterStart')
  })

  it('диапазон дат длиннее года — отправить нельзя', () => {
    const v = validateSeries(rule({ endDate: '2027-11-03' }))   // 366 дней
    expect(v.issues).toContain('horizon')
    expect(v.ok).toBe(false)
  })

  it('пустой набор дней недели — отправить нельзя', () => {
    expect(validateSeries(rule({ daysOfWeek: [] })).issues).toContain('noDays')
  })

  /**
   * «По воскресеньям с понедельника по пятницу» выглядит осмысленно ровно до
   * отправки: бэкенд отвечает 400 «No dates match». Форма обязана сказать это
   * раньше.
   */
  it('ни одна дата не подходит — форма сообщает, что слотов не будет', () => {
    const v = validateSeries(rule({ startDate: '2026-11-02', endDate: '2026-11-06', daysOfWeek: [7] }))
    expect(v.count).toBe(0)
    expect(v.issues).toContain('noMatchingDates')
    expect(v.ok).toBe(false)
  })

  it('конец раньше начала по датам — отправить нельзя', () => {
    expect(validateSeries(rule({ endDate: '2026-11-01' })).issues).toContain('endBeforeStart')
  })
})

/**
 * Главный тест файла.
 *
 * Те же входные данные, что в бэкендовом `AvailabilitySeriesTest`, и те же
 * ожидаемые числа. Расхождение здесь означает, что форма показывает одно, а
 * сервер создаёт другое, — и заметит это не тест, а человек, у которого
 * «получится 4» превратилось в шесть строк списка.
 */
describe('подсчёт совпадает с бэкендом', () => {
  const backendCases: { name: string; rule: SeriesRule; expected: number }[] = [
    {
      // weeklySeriesOverFourWeeksCreatesFourSlots
      name: 'понедельник +27 дней, вторники → 4',
      rule: rule({ startDate: '2026-11-02', endDate: '2026-11-29', daysOfWeek: [2] }),
      expected: 4,
    },
    {
      // twoDaysPerWeekOverTwoWeeksCreatesFourSlots
      name: 'понедельник +13 дней, понедельники и среды → 4',
      rule: rule({ startDate: '2026-11-02', endDate: '2026-11-15', daysOfWeek: [1, 3] }),
      expected: 4,
    },
    {
      // seriesKeepsLocalTimeAcrossDstEnd
      name: '13–27 октября, вторники → 3',
      rule: rule({ startDate: '2026-10-13', endDate: '2026-10-27', daysOfWeek: [2] }),
      expected: 3,
    },
    {
      // seriesExceedingSlotCapIsRejectedAndSavesNothing
      name: 'понедельник +200 дней, все семь дней → 201',
      rule: rule({
        startDate: '2026-11-02',
        endDate: '2027-05-21',
        daysOfWeek: [1, 2, 3, 4, 5, 6, 7],
      }),
      expected: 201,
    },
  ]

  it.each(backendCases)('$name', ({ rule: r, expected }) => {
    expect(countSeriesSlots(r)).toBe(expected)
  })
})

describe('toSeriesRequest', () => {
  it('дни недели уходят именами, как их ждёт Set<DayOfWeek>', () => {
    const req = toSeriesRequest(rule({ daysOfWeek: [4, 2] }), 'Europe/Moscow')
    expect(req.daysOfWeek).toEqual(['TUESDAY', 'THURSDAY'])
    expect(req).toMatchObject({
      startDate: '2026-11-02',
      endDate: '2026-11-29',
      startTime: '10:00',
      endTime: '12:00',
      timeZone: 'Europe/Moscow',
    })
  })
})

