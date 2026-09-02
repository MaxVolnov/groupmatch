import { DateTime } from 'luxon'

/**
 * Правило повторения на стороне формы: подсчёт и проверки до отправки.
 *
 * Всё это уже проверяет бэкенд и отвечает 400. Дублирование здесь не про
 * недоверие к нему, а про момент: человек, выбравший полгода и пять дней
 * недели, должен увидеть «получится 130 слотов» пока настраивает, а не после
 * нажатия на кнопку. Ответ 400 при этом всё равно обрабатывается — форма не
 * единственный путь к эндпоинту.
 *
 * Числа обязаны совпадать с бэкендом до единицы: обещание «будет 4 слота» и
 * фактические 5 — это интерфейс, который тихо врёт. Совпадение закреплено
 * тестом на тех же входных данных, что в `AvailabilitySeriesTest`.
 */

/** Потолки из `AvailabilityService`: MAX_SERIES_SLOTS и MAX_SERIES_DAYS. */
export const MAX_SERIES_SLOTS = 200
export const MAX_SERIES_DAYS = 365

/** Имена дней в том виде, в каком их ждёт `Set<DayOfWeek>` на бэкенде. */
export const DAY_OF_WEEK_NAMES = [
  'MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY',
] as const

export type DayOfWeekName = (typeof DAY_OF_WEEK_NAMES)[number]

/** Номер дня недели по Luxon: 1 — понедельник, 7 — воскресенье. */
export const dayName = (weekday: number): DayOfWeekName => DAY_OF_WEEK_NAMES[weekday - 1]

export interface SeriesRule {
  /** ISO-дата без времени: `'2026-11-02'`. */
  startDate: string
  endDate: string
  /** Номера дней недели по Luxon, 1..7. */
  daysOfWeek: number[]
  /** Настенное время `'HH:mm'`. */
  startTime: string
  endTime: string
}

/**
 * Что не так с правилом. Порядок в списке — порядок показа: первым идёт то,
 * что человек правит раньше.
 */
export type SeriesIssue =
  | 'noDays'
  | 'invalidDates'
  | 'endBeforeStart'
  | 'horizon'
  | 'endTimeNotAfterStart'
  | 'noMatchingDates'
  | 'tooManySlots'

const parseDate = (iso: string) => DateTime.fromISO(iso)
const parseTime = (hhmm: string) => DateTime.fromFormat(hhmm, 'HH:mm')

/**
 * Сколько слотов даст правило.
 *
 * Шаг по календарным дням, как в `AvailabilityService.matchingDates`: считаем
 * даты, чей день недели входит в набор, а не делим длину на семь. Деление
 * ошибается на краях диапазона — а края и есть то место, где человек
 * настраивает границы.
 *
 * Диапазон длиннее горизонта не считается вовсе: результат всё равно
 * отвергнет и форма, и бэкенд, а перебирать десятилетие незачем.
 */
export function countSeriesSlots(rule: SeriesRule): number {
  const start = parseDate(rule.startDate)
  const end = parseDate(rule.endDate)
  if (!start.isValid || !end.isValid || end < start) return 0
  if (end.diff(start, 'days').days > MAX_SERIES_DAYS) return 0
  if (rule.daysOfWeek.length === 0) return 0

  const wanted = new Set(rule.daysOfWeek)
  let count = 0
  for (let d = start; d <= end; d = d.plus({ days: 1 })) {
    if (wanted.has(d.weekday)) count++
  }
  return count
}

export interface SeriesValidation {
  issues: SeriesIssue[]
  /** Сколько слотов получится. Ноль, если правило не считается. */
  count: number
  ok: boolean
}

/**
 * Проверки, повторяющие ограничения бэкенда.
 *
 * `noMatchingDates` бэкенд тоже отвергает (400 «No dates match…»), и здесь он
 * нужен не меньше: правило «по воскресеньям с понедельника по пятницу»
 * выглядит осмысленно ровно до момента отправки.
 */
export function validateSeries(rule: SeriesRule): SeriesValidation {
  const issues: SeriesIssue[] = []
  const start = parseDate(rule.startDate)
  const end = parseDate(rule.endDate)
  const from = parseTime(rule.startTime)
  const to = parseTime(rule.endTime)

  if (rule.daysOfWeek.length === 0) issues.push('noDays')
  if (!start.isValid || !end.isValid || !from.isValid || !to.isValid) {
    issues.push('invalidDates')
  } else {
    if (end < start) issues.push('endBeforeStart')
    else if (end.diff(start, 'days').days > MAX_SERIES_DAYS) issues.push('horizon')
    if (to <= from) issues.push('endTimeNotAfterStart')
  }

  const count = countSeriesSlots(rule)
  if (count > MAX_SERIES_SLOTS) issues.push('tooManySlots')
  if (count === 0 && issues.length === 0) issues.push('noMatchingDates')

  return { issues, count, ok: issues.length === 0 }
}

/** Правило в том виде, в каком его ждёт `POST /availability/series`. */
export function toSeriesRequest(rule: SeriesRule, timeZone: string) {
  return {
    startDate: rule.startDate,
    endDate: rule.endDate,
    daysOfWeek: [...rule.daysOfWeek].sort((a, b) => a - b).map(dayName),
    startTime: rule.startTime,
    endTime: rule.endTime,
    timeZone,
  }
}

// ── массовая очистка ────────────────────────────────────────────────────────

export interface ClearWindow {
  daysOfWeek: number[]
  startTime: string
  endTime: string
  fromDate: string
  toDate: string
}

/**
 * Слоты, которые попадут под очистку.
 *
 * Зеркало `AvailabilityService.fitsEntirelyInWindow`: слот удаляется, только
 * если лежит в окне своего дня **целиком**. Задевающий окно краем не
 * трогается — молча откусить у человека половину его 9:00–15:00, потому что он
 * чистил 10:00–14:00, хуже, чем не удалить ничего.
 *
 * Зачем вторая реализация того же правила, если бэкенд и так умеет `dryRun`:
 * тот отвечает одним числом, а подтверждение обязано сказать, попали ли под
 * окно слоты серий. Число в интерфейсе показывается **серверное** — эта
 * функция отвечает только на вопрос «какие именно», и её согласие с бэкендом
 * закреплено тестом.
 */
export function slotsInClearWindow<T extends { startsAt: string; endsAt: string }>(
  slots: T[],
  window: ClearWindow,
  zone: string,
): T[] {
  const wanted = new Set(window.daysOfWeek)

  return slots.filter((slot) => {
    const start = DateTime.fromISO(slot.startsAt, { zone })
    const end = DateTime.fromISO(slot.endsAt, { zone })
    const date = start.startOf('day')

    // Сравнение строками ISO-дат, а не объектами: `DateTime.fromISO` без зоны
    // разбирает границы диапазона в системной зоне, и сравнение с датой в
    // зоне сетки уезжает на смещение. Ночной слот по Москве из-за этого
    // выпадал из своего же диапазона.
    const day = start.toISODate()!
    if (day < window.fromDate || day > window.toDate) return false
    if (!wanted.has(start.weekday)) return false

    const [fh, fm] = window.startTime.split(':').map(Number)
    const [th, tm] = window.endTime.split(':').map(Number)
    const windowStart = date.set({ hour: fh, minute: fm, second: 0, millisecond: 0 })
    const windowEnd = date.set({ hour: th, minute: tm, second: 0, millisecond: 0 })

    return start >= windowStart && end <= windowEnd
  })
}
