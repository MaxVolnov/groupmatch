import { DateTime } from 'luxon'
import type { AvailabilityResponse } from '@/types'
import { cellStart, type Cell, type GridSpec } from './selection'

/**
 * Состояние редактора слота — без React и без сети.
 *
 * Модалка открывается двумя путями: тапом по строке списка и тапом по своей
 * занятой ячейке сетки. Состояние у обоих обязано быть одинаковым, иначе
 * «одна и та же модалка» — это две разные модалки, которые однажды разойдутся.
 * Поэтому оба входа сводятся сюда.
 */

export interface SlotEditorState {
  slot: AvailabilityResponse
  /** Настенное время слота в зоне пользователя, `'HH:mm'`. */
  startTime: string
  endTime: string
  /** Дата слота — она не редактируется, но показывается. */
  date: string
  isSeries: boolean
  /** Сколько слотов в серии. Для одиночного — 1. */
  seriesSize: number
}

/** Открыть редактор для слота. Оба входа зовут именно это. */
export function openSlotEditor(
  slot: AvailabilityResponse,
  allSlots: AvailabilityResponse[],
  zone: string,
): SlotEditorState {
  const start = DateTime.fromISO(slot.startsAt, { zone })
  const end = DateTime.fromISO(slot.endsAt, { zone })
  return {
    slot,
    startTime: start.toFormat('HH:mm'),
    endTime: end.toFormat('HH:mm'),
    date: start.toISODate()!,
    isSeries: !!slot.seriesId,
    seriesSize: slot.seriesId
      ? allSlots.filter((s) => s.seriesId === slot.seriesId).length
      : 1,
  }
}

/**
 * Мой слот под этой ячейкой сетки, если он там есть.
 *
 * Вход из сетки идёт через координаты, вход из списка — через сам слот;
 * дальше оба пути одинаковы.
 */
export function slotAtCell(
  cell: Cell,
  slots: AvailabilityResponse[],
  grid: GridSpec,
): AvailabilityResponse | null {
  const from = cellStart(cell.col, cell.row, grid)
  const to = cellStart(cell.col, cell.row + 1, grid)
  return (
    slots.find((s) => {
      const start = DateTime.fromISO(s.startsAt, { zone: grid.zone })
      const end = DateTime.fromISO(s.endsAt, { zone: grid.zone })
      return start < to && end > from
    }) ?? null
  )
}

export type SlotEditIssue = 'endNotAfterStart' | 'tooShort' | 'tooLong' | 'invalid'

/**
 * Проверки, повторяющие `validateSlotTimes` на бэкенде: не меньше пяти минут,
 * не больше сорока восьми часов, конец позже начала.
 *
 * Дублирование здесь ради того же, что и в форме серии: кнопка «Сохранить»
 * обязана быть заблокирована до отправки, а не отвечать 400 после.
 */
export function validateSlotEdit(startTime: string, endTime: string): SlotEditIssue[] {
  const from = DateTime.fromFormat(startTime, 'HH:mm')
  const to = DateTime.fromFormat(endTime, 'HH:mm')
  if (!from.isValid || !to.isValid) return ['invalid']
  if (to <= from) return ['endNotAfterStart']

  const minutes = to.diff(from, 'minutes').minutes
  if (minutes < 5) return ['tooShort']
  if (minutes > 48 * 60) return ['tooLong']
  return []
}

/** Изменилось ли время относительно исходного. */
export function isTimeChanged(state: SlotEditorState, startTime: string, endTime: string): boolean {
  return startTime !== state.startTime || endTime !== state.endTime
}

/**
 * Нужен ли выбор «только этот слот или вся серия».
 *
 * Спрашивается **после** правки и только когда есть о чём спрашивать: у
 * одиночного слота выбора нет, у нетронутого времени — тоже. Задавать этот
 * вопрос заранее значит требовать решения раньше, чем человек понял, что он
 * вообще меняет.
 */
export function needsScopeChoice(
  state: SlotEditorState,
  startTime: string,
  endTime: string,
): boolean {
  return state.isSeries && isTimeChanged(state, startTime, endTime)
}

export type EditScope = 'single' | 'series'

/** Порт к серверу. Инъекция нужна, чтобы выбор области проверялся тестом. */
export interface SlotEditApi {
  retimeSlot(slotId: string, body: { startTime: string; endTime: string; timeZone: string }): Promise<unknown>
  retimeSeries(slotId: string, body: { startTime: string; endTime: string; timeZone: string }): Promise<unknown>
}

/**
 * Отправляет правку. Область действия решает, какой эндпоинт зовётся, — и это
 * единственное место, где она на что-то влияет.
 */
export function applySlotEdit(
  state: SlotEditorState,
  scope: EditScope,
  times: { startTime: string; endTime: string },
  timeZone: string,
  api: SlotEditApi,
): Promise<unknown> {
  const body = { ...times, timeZone }
  return scope === 'series'
    ? api.retimeSeries(state.slot.id, body)
    : api.retimeSlot(state.slot.id, body)
}

/**
 * Начальные значения формы серии для «создать копию»: время берётся у
 * исходного слота, дни недели и диапазон человек задаёт сам.
 */
export function copyRuleFrom(state: SlotEditorState) {
  const date = DateTime.fromISO(state.date)
  return {
    startDate: state.date,
    endDate: date.plus({ weeks: 4 }).toISODate()!,
    daysOfWeek: [date.weekday],
    startTime: state.startTime,
    endTime: state.endTime,
  }
}
