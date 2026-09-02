import { DateTime } from 'luxon'
import type { AvailabilityResponse } from '@/types'
import { cellStart, isOvernightSlot, isUnalignedSlot, type GridSpec } from './selection'

/**
 * Сетка своего времени: раскладка собственных слотов участника по ячейкам
 * недели.
 *
 * Модуль чистый — ни React, ни DOM. Причина та же, по которой отдельно живёт
 * `selection.ts`: картинка проверяется глазами, а раскладка под ней —
 * числами, и у чисел должен быть свой оракул. Компонент рядом только
 * раскрашивает то, что посчитано здесь.
 *
 * Время ячеек считается функцией `cellStart` из `selection.ts`, а не
 * собственной копией: сетка и протяжка обязаны одинаково отвечать на вопрос
 * «где начинается строка 20», иначе подсветка разъедется с тем, что реально
 * создастся.
 */

/**
 * Параметры сетки. Совпадают с `buildGrid` в `HeatmapTab.tsx`: 48 строк по
 * 30 минут, семь дней от понедельника.
 *
 * Значения продублированы, а не вынесены в общий модуль, сознательно — но
 * дублирование это подстраховано: `ownGrid.test.ts` читает исходник
 * `HeatmapTab.tsx` и падает, если там появятся другие числа. Тихо разъехаться
 * копии не смогут.
 */
export const STEP_MINUTES = 30
export const ROWS_PER_DAY = 48
export const DAYS_PER_WEEK = 7

/**
 * Состояние одной ячейки.
 *
 * - `free` — время не отмечено;
 * - `busy` — обычный слот, редактируется протяжкой;
 * - `series` — слот повторяющейся серии: меняется только в списке;
 * - `partial` — слот занят, но сеткой не выражается — границы не по получасам
 *   (14:15–15:47) или он переходит через полночь. Тоже только в списке.
 *
 * Три занятых состояния делятся ровно по тому признаку, который человеку и
 * нужен: что из этого можно поправить пальцем прямо здесь. `series` и
 * `partial` — нельзя, и набор ячеек в этих двух состояниях совпадает с тем,
 * что `selectionToSlots` возвращает в `blocked`. Совпадает не случайно: обе
 * стороны спрашивают одни и те же предикаты.
 */
export type CellState = 'free' | 'busy' | 'series' | 'partial'

/** Ячейку нельзя редактировать жестом — только через список слотов. */
export const isBlockedState = (state: CellState) => state === 'series' || state === 'partial'

export interface OwnGrid {
  /** Ровно тот `GridSpec`, который принимает `selection.ts`. Без переходников. */
  spec: GridSpec
  /** `cells[row][col]`. Координата та же, что у `buildGrid` в HeatmapTab. */
  cells: CellState[][]
  /** Подписи слева: время каждые два часа, между ними пусто. */
  timeLabels: string[]
  /** Понедельник недели — для заголовков столбцов. */
  weekStart: DateTime
}

/**
 * Приоритет состояний, когда ячейку задевает несколько слотов.
 *
 * Серия выигрывает у всего остального, даже если покрывает ячейку лишь
 * частично, а обычный слот — целиком. Это не про «чего больше», а про то,
 * что человек здесь может сделать: протяжка серию не изменит, и ячейка,
 * покрашенная как обычная, пообещала бы редактируемость, которой нет.
 *
 * Отдельного состояния «частично занято серией» нет намеренно: пятое
 * состояние не меняет ни одного решения — протяжка заблокирована в обоих
 * случаях, — а различать пять оттенков в клетке 16 пикселей высотой уже
 * невозможно.
 */
const PRIORITY: Record<CellState, number> = { free: 0, partial: 1, busy: 2, series: 3 }

/**
 * Раскладывает слоты по ячейкам недели.
 *
 * @param slots свои слоты в группе, как их отдаёт `availabilityApi.mySlots`
 * @param weekStart понедельник недели в зоне пользователя
 */
export function buildOwnGrid(slots: AvailabilityResponse[], weekStart: DateTime): OwnGrid {
  const monday = weekStart.startOf('day')
  const spec: GridSpec = {
    stepMinutes: STEP_MINUTES,
    rowsPerDay: ROWS_PER_DAY,
    // Календарный шаг по дням, а не «плюс 24 часа»: в неделю перевода часов
    // сутки длятся 23 или 25 часов, и арифметика в часах сдвинула бы столбцы.
    days: Array.from({ length: DAYS_PER_WEEK }, (_, i) => monday.plus({ days: i }).toISODate()!),
    // `zone.name`, а не `zoneName`: второе типизировано как `string | null`
    // (null у невалидной даты), и `!` здесь скрыл бы настоящую проблему —
    // сетку, построенную на неразобранной дате.
    zone: monday.zone.name,
  }

  const cells: CellState[][] = Array.from({ length: ROWS_PER_DAY }, () =>
    Array<CellState>(DAYS_PER_WEEK).fill('free'),
  )

  // Границы всех ячеек считаются один раз: 7 × 49 моментов вместо того же
  // числа на каждый слот. При двух сотнях слотов разница — десятки тысяч
  // разобранных дат.
  const bounds = Array.from({ length: DAYS_PER_WEEK }, (_, col) =>
    Array.from({ length: ROWS_PER_DAY + 1 }, (_, row) => cellStart(col, row, spec)),
  )

  for (const slot of slots) {
    const start = DateTime.fromISO(slot.startsAt, { zone: spec.zone })
    const end = DateTime.fromISO(slot.endsAt, { zone: spec.zone })

    /**
     * Слот красится целиком одним состоянием, а не поячеечно.
     *
     * У слота 14:15–15:47 середина ячеек покрыта полностью, и поячеечный
     * расчёт покрасил бы её как обычную — то есть неотличимо от честных
     * 14:30–15:00. Пометка теряет смысл ровно там, где нужна: человек видит
     * в списке 14:15, в сетке — ровный блок, и не понимает, откуда взялось
     * расхождение. Достаточно одной неровной границы, чтобы весь слот был
     * «только в списке».
     *
     * Предикаты берутся из `selection.ts`, а не считаются здесь заново: набор
     * ячеек, которые сетка красит нередактируемыми, обязан совпадать с тем,
     * что протяжка возвращает в `blocked`. Второй реализации того же признака
     * достаточно разойтись на один случай, чтобы сетка начала обещать
     * редактируемость, которой нет.
     */
    const state: CellState = slot.seriesId
      ? 'series'
      : isOvernightSlot(slot, spec.zone) || isUnalignedSlot(slot, spec)
        ? 'partial'
        : 'busy'

    const touched: [number, number][] = []
    for (let col = 0; col < DAYS_PER_WEEK; col++) {
      for (let row = 0; row < ROWS_PER_DAY; row++) {
        const from = bounds[col][row]
        const to = bounds[col][row + 1]
        if (start < to && end > from) touched.push([row, col])
      }
    }

    for (const [row, col] of touched) {
      if (PRIORITY[state] > PRIORITY[cells[row][col]]) cells[row][col] = state
    }
  }

  return { spec, cells, timeLabels: buildTimeLabels(), weekStart: monday }
}

/** Подпись раз в два часа: на 48 строках подписывать каждую нечитаемо. */
export function buildTimeLabels(): string[] {
  return Array.from({ length: ROWS_PER_DAY }, (_, i) => {
    const h = Math.floor((i * STEP_MINUTES) / 60)
    const m = (i * STEP_MINUTES) % 60
    return i % 4 === 0 ? `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}` : ''
  })
}
