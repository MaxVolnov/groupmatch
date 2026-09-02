import { DateTime } from 'luxon'
import type { AvailabilityResponse } from '@/types'

/**
 * Математика протяжки по сетке доступности.
 *
 * Здесь нет ни React, ни DOM, ни сети — намеренно. Жест руками проверяется
 * глазами, а числа под ним проверяются тестами; смешав то и другое в одном
 * модуле, мы теряем оракул: непонятно, «съехало выделение» из-за арифметики
 * или из-за обработчика событий. Обработчики придут отдельно и будут звать
 * уже проверенные функции.
 *
 * Параметры сетки взяты из фактического кода `HeatmapTab.tsx`, а не выдуманы:
 * 48 строк по 30 минут от полуночи до полуночи и семь столбцов-дней от
 * понедельника (`DateTime.now().startOf('week')`).
 */

/** Потолок на один жест. Больше — не выделение, а промах или залипшая кнопка. */
export const MAX_SELECTION_CELLS = 50

/**
 * Параметры сетки.
 *
 * Зона живёт здесь же, а не отдельным аргументом: даты столбцов — это
 * календарные дни *в какой-то зоне*, и разнести их по разным параметрам
 * значит завести два источника правды об одном и том же. Разъезжаются такие
 * пары молча.
 */
export interface GridSpec {
  /** Минут в одной ячейке. В HeatmapTab — 30. */
  stepMinutes: number
  /** Строк в сутках. В HeatmapTab — 48. */
  rowsPerDay: number
  /** Даты столбцов слева направо, ISO без времени: `'2026-10-20'`. */
  days: string[]
  /** IANA-зона, в которой читаются даты столбцов и время ячеек. */
  zone: string
}

export interface Cell {
  /** Индекс строки, 0 — полночь. */
  row: number
  /** Индекс столбца-дня, 0 — крайний левый. */
  col: number
}

/** Диапазон строк внутри одного дня. Обе границы включительно. */
export interface DayRange {
  col: number
  startRow: number
  endRow: number
  /** `endRow - startRow + 1`. Считается здесь, чтобы не считать её в вызывающем коде. */
  length: number
}

export interface SelectionRange {
  /**
   * Ячейка, с которой жест начался. Нужна не для отрисовки, а для обрезки:
   * когда выделение упирается в потолок, отрезать надо со стороны пальца, а
   * не со стороны, где человек начал.
   */
  anchor: Cell
  /** По одному диапазону на каждый затронутый столбец. */
  days: DayRange[]
  cellCount: number
}

export interface SlotDraft {
  /** ISO в UTC — в том виде, в каком его ждёт API. */
  startsAt: string
  endsAt: string
}

/**
 * Почему слот не редактируется протяжкой.
 *
 * - `series` — у слота есть `seriesId`; резать серию жестом нельзя, у неё
 *   своя механика удаления.
 * - `overnight` — слот начинается в одном календарном дне сетки, а
 *   заканчивается в другом. Протяжка по вторнику 00:00–01:00, поглотившая
 *   слот 23:00–01:00, унесла бы вместе с ним кусок понедельника, которого в
 *   жесте не было. Тот же принцип, по которому массовая очистка на бэкенде не
 *   режет частично пересекающиеся слоты.
 * - `unaligned` — границы слота не лежат на границах ячеек (14:15–15:47 при
 *   шаге в полчаса). Сетка такой слот не выражает: протяжка рядом с ним
 *   сливалась бы в слот, начинающийся в 14:15, — время, которого в жесте не
 *   было и которое в сетке не видно. Причина та же, что у `overnight`:
 *   результат жеста должен быть виден в жесте.
 */
export type BlockReason = 'series' | 'overnight' | 'unaligned'

export interface BlockedSlot {
  slot: AvailabilityResponse
  reason: BlockReason
}

export interface SelectionPlan {
  /** Что создать. Соседние ячейки уже склеены, слияния со старыми учтены. */
  toCreate: SlotDraft[]
  /** Что удалить: старые слоты, поглощённые слиянием. */
  toDelete: AvailabilityResponse[]
  /** Чего касались, но менять не нужно. */
  unchanged: AvailabilityResponse[]
  /**
   * Слоты, которые выделение задело, но менять не будет. Не попадают ни в
   * `toCreate`, ни в `toDelete`; их интервалы вырезаны из выделения, остальная
   * его часть обрабатывается как обычно.
   *
   * Один список с причиной, а не поле на каждый вид: вызывающему нужен ответ
   * на вопрос «есть ли что объяснить человеку», и он должен быть одной
   * проверкой. Два параллельных поля — развилка, которую при появлении
   * третьего вида забудут дополнить.
   */
  blocked: BlockedSlot[]
}

/**
 * Нормализует пару «начали — тянем» в прямоугольное выделение.
 *
 * Протяжка вверх и вниз дают один результат, обе крайние ячейки входят
 * внутрь. Вбок выделение расходится **отдельным диапазоном на каждый день**, а
 * не одной лентой через ночь: сетка — таблица, и человек, тянущий по
 * диагонали, ожидает прямоугольник, а не выделение текста.
 *
 * Границы здесь не проверяются — этим занимается {@link clampSelection}.
 */
export function resolveSelection(anchor: Cell, focus: Cell, grid: GridSpec): SelectionRange {
  const startRow = Math.min(anchor.row, focus.row)
  const endRow = Math.max(anchor.row, focus.row)
  // Столбцы ограничиваются здесь, а не в clampSelection: несуществующий день
  // — это не диапазон, который надо подрезать, это отсутствующий столбец.
  const startCol = Math.max(0, Math.min(anchor.col, focus.col))
  const endCol = Math.min(grid.days.length - 1, Math.max(anchor.col, focus.col))

  const days: DayRange[] = []
  for (let col = startCol; col <= endCol; col++) {
    days.push({ col, startRow, endRow, length: endRow - startRow + 1 })
  }

  return { anchor, days, cellCount: days.length * (endRow - startRow + 1) }
}

/**
 * Загоняет выделение в границы сетки и в потолок по числу ячеек.
 *
 * Обрезка идёт со стороны пальца: то, что человек выделил первым, остаётся.
 * Прямоугольность при этом сохраняется — рваное выделение выглядит как сбой,
 * даже когда числа под ним верны, поэтому подрезается вся сторона целиком, а
 * не «лишние» ячейки по одной.
 */
export function clampSelection(
  range: SelectionRange,
  grid: GridSpec,
  limits: { maxCells?: number } = {},
): SelectionRange {
  const maxCells = limits.maxCells ?? MAX_SELECTION_CELLS
  const lastRow = grid.rowsPerDay - 1
  const lastCol = grid.days.length - 1

  // Границы суток. Протяжка ниже последней ячейки упирается в неё и в
  // следующий день не переезжает: столбец — это календарный день, и ночной
  // «хвост» в соседнем столбце был бы уже другим выделением.
  const withinDay = range.days
    .filter((d) => d.col >= 0 && d.col <= lastCol)
    .map((d) => ({
      col: d.col,
      startRow: Math.max(0, Math.min(d.startRow, lastRow)),
      endRow: Math.max(0, Math.min(d.endRow, lastRow)),
    }))

  if (withinDay.length === 0 || lastRow < 0) {
    return { anchor: range.anchor, days: [], cellCount: 0 }
  }

  const anchorCol = Math.max(0, Math.min(range.anchor.col, lastCol))
  const anchorRow = Math.max(0, Math.min(range.anchor.row, lastRow))

  let cols = withinDay
  let rows = cols[0].endRow - cols[0].startRow + 1

  if (rows * cols.length > maxCells) {
    // Ужимаем сторону, вдоль которой тянут чаще, — строки. Дней в сетке
    // семь, так что одной этой подрезки почти всегда достаточно.
    const maxRows = Math.floor(maxCells / cols.length)
    if (maxRows >= 1) {
      rows = maxRows
    } else {
      rows = 1
      cols = cols
        .slice()
        .sort((a, b) => Math.abs(a.col - anchorCol) - Math.abs(b.col - anchorCol))
        .slice(0, maxCells)
        .sort((a, b) => a.col - b.col)
    }
  }

  const days = cols.map((d) => {
    // После нормализации якорь стоит на одном из концов диапазона. Обрезаем
    // противоположный: то, что человек выделил первым, остаётся.
    const anchoredAtTop = anchorRow <= d.startRow
    const startRow = anchoredAtTop ? d.startRow : Math.max(d.startRow, d.endRow - rows + 1)
    return { col: d.col, startRow, endRow: startRow + rows - 1, length: rows }
  })

  return { anchor: range.anchor, days, cellCount: cols.length * rows }
}

/**
 * Момент начала ячейки — по настенным часам своего дня.
 *
 * Именно `.set({ hour, minute })` на дате столбца, а не «полночь плюс N
 * минут»: в сутки перевода часов между полуночью и десятью утра лежит 23 или
 * 25 часов, и арифметика в минутах выдала бы 09:00 или 11:00 там, где в
 * заголовке сетки написано 10:00. Ячейка подписана настенным временем — она
 * обязана ему и соответствовать.
 *
 * Конец суток (строка `rowsPerDay`) — это полночь **следующего календарного
 * дня**, а не «плюс 24 часа»: шаг по дням календарный, иначе в ту же неделю
 * перевода конец слота уедет на час.
 */
export function cellStart(col: number, row: number, grid: GridSpec): DateTime {
  const dayStart = DateTime.fromISO(grid.days[col], { zone: grid.zone }).startOf('day')
  const minutes = row * grid.stepMinutes
  if (minutes >= grid.rowsPerDay * grid.stepMinutes) {
    return dayStart.plus({ days: 1 }).startOf('day')
  }
  return dayStart.set({
    hour: Math.floor(minutes / 60),
    minute: minutes % 60,
    second: 0,
    millisecond: 0,
  })
}

interface Interval {
  start: DateTime
  end: DateTime
}

const overlaps = (a: Interval, b: Interval) => a.start < b.end && a.end > b.start
/** Касание считается пересечением: конец одного равен началу другого — это один слот. */
const touches = (a: Interval, b: Interval) => a.start <= b.end && a.end >= b.start

function toInterval(slot: AvailabilityResponse, zone: string): Interval {
  return {
    start: DateTime.fromISO(slot.startsAt, { zone }),
    end: DateTime.fromISO(slot.endsAt, { zone }),
  }
}

/**
 * Слот пересекает границу суток: заканчивается позже ближайшей полуночи после
 * своего начала.
 *
 * Слот, заканчивающийся ровно в полночь (23:00–00:00), сюда не попадает — он
 * целиком лежит в своём дне и редактируется протяжкой как любой другой.
 *
 * Полночь считается календарным шагом, а не «плюс 24 часа»: в сутки перевода
 * часов их 23 или 25, и слот 23:00–00:30 в неделю перевода иначе то попадал
 * бы в ночные, то нет.
 */
export function isOvernightSlot(slot: AvailabilityResponse, zone: string): boolean {
  const { start, end } = toInterval(slot, zone)
  return end > start.startOf('day').plus({ days: 1 })
}

/**
 * Границы слота не совпадают с границами ячеек: 14:15–15:47 при шаге в
 * полчаса.
 *
 * Считается минутами от начала своих суток, а не по `minute % 30`: в зонах со
 * сдвигом в полчаса (Индия, Непал) полдень приходится не на круглый час, и
 * проверка по минутам часа объявила бы неровным каждый слот подряд.
 */
export function isUnalignedSlot(slot: AvailabilityResponse, grid: GridSpec): boolean {
  const { start, end } = toInterval(slot, grid.zone)
  const onBoundary = (t: DateTime) => {
    const minutes = t.diff(t.startOf('day'), 'minutes').minutes
    return Number.isInteger(minutes) && minutes % grid.stepMinutes === 0
  }
  return !onBoundary(start) || !onBoundary(end)
}

/**
 * Почему слот не редактируется жестом, или `null`, если редактируется.
 *
 * Серия проверяется первой: ночной слот серии остаётся серией. Причина важнее
 * для человека именно эта — у серии есть свой способ удаления, а «через
 * полночь» звучало бы как техническая случайность.
 */
export function blockReason(slot: AvailabilityResponse, grid: GridSpec): BlockReason | null {
  if (slot.seriesId) return 'series'
  if (isOvernightSlot(slot, grid.zone)) return 'overnight'
  if (isUnalignedSlot(slot, grid)) return 'unaligned'
  return null
}

/** Чем занята одна ячейка с точки зрения жеста. */
export type CellOwnership = 'free' | 'mine' | 'blocked'

/**
 * Кому принадлежит ячейка. По ней жест решает, что он делает: рисует, стирает
 * или не начинается вовсе.
 *
 * Заблокированное перевешивает своё — ровно как в раскраске сетки: решает не
 * «чего больше», а что человек здесь может сделать.
 */
export function cellOwnership(
  cell: Cell,
  existingSlots: AvailabilityResponse[],
  grid: GridSpec,
): CellOwnership {
  const window: Interval = {
    start: cellStart(cell.col, cell.row, grid),
    end: cellStart(cell.col, cell.row + 1, grid),
  }
  let mine = false
  for (const slot of existingSlots) {
    if (!overlaps(toInterval(slot, grid.zone), window)) continue
    if (blockReason(slot, grid)) return 'blocked'
    mine = true
  }
  return mine ? 'mine' : 'free'
}

/** Вычитает из интервала занятые куски; остаётся то, что действительно свободно. */
function subtract(base: Interval, holes: Interval[]): Interval[] {
  let pieces: Interval[] = [base]
  for (const hole of holes) {
    const next: Interval[] = []
    for (const piece of pieces) {
      if (!overlaps(piece, hole)) {
        next.push(piece)
        continue
      }
      if (piece.start < hole.start) next.push({ start: piece.start, end: hole.start })
      if (piece.end > hole.end) next.push({ start: hole.end, end: piece.end })
    }
    pieces = next
  }
  return pieces
}

/**
 * Превращает выделение в набор операций над слотами.
 *
 * Соседние выделенные ячейки склеиваются в **один** слот. Это не оптимизация
 * хранения: человек сделал один жест и ждёт одну запись, а не восемь
 * получасовых, которые ему потом удалять по одной. Слот, к которому выделение
 * примыкает вплотную, поглощается тем же слиянием — иначе в списке появлялись
 * бы две записи подряд без зазора между ними.
 *
 * Повторная протяжка по уже отмеченному ничего не создаёт: выделение внутри
 * существующего слота — это `unchanged`, а не дубль.
 */
export function selectionToSlots(
  range: SelectionRange,
  existingSlots: AvailabilityResponse[],
  grid: GridSpec,
): SelectionPlan {
  const zone = grid.zone
  const toCreate: SlotDraft[] = []
  const toDelete = new Map<string, AvailabilityResponse>()
  const unchanged = new Map<string, AvailabilityResponse>()
  const blocked = new Map<string, BlockedSlot>()

  const untouchable = existingSlots.filter((s) => blockReason(s, grid) !== null)
  const plain = existingSlots.filter((s) => blockReason(s, grid) === null)

  for (const day of range.days) {
    if (day.col < 0 || day.col >= grid.days.length) continue

    const selection: Interval = {
      start: cellStart(day.col, day.startRow, grid),
      end: cellStart(day.col, day.endRow + 1, grid),
    }

    // Неприкосновенные слоты выделение не режут: их интервалы вырезаются из
    // выделения целиком, а сами слоты уходят наверх отдельным списком.
    const blocking = untouchable.filter((s) => overlaps(toInterval(s, zone), selection))
    for (const s of blocking) {
      blocked.set(s.id, { slot: s, reason: blockReason(s, grid)! })
      unchanged.set(s.id, s)
    }

    const pieces = subtract(selection, blocking.map((s) => toInterval(s, zone)))

    for (const piece of pieces) {
      const neighbours = plain.filter((s) => touches(toInterval(s, zone), piece))

      let start = piece.start
      let end = piece.end
      for (const s of neighbours) {
        const iv = toInterval(s, zone)
        if (iv.start < start) start = iv.start
        if (iv.end > end) end = iv.end
      }

      // Слияние ничего не изменило: выделение целиком лежало внутри одного
      // существующего слота. Ни создавать, ни удалять нечего.
      if (
        neighbours.length === 1 &&
        +start === +toInterval(neighbours[0], zone).start &&
        +end === +toInterval(neighbours[0], zone).end
      ) {
        unchanged.set(neighbours[0].id, neighbours[0])
        continue
      }

      for (const s of neighbours) toDelete.set(s.id, s)
      toCreate.push({
        startsAt: start.toUTC().toISO()!,
        endsAt: end.toUTC().toISO()!,
      })
    }
  }

  return {
    toCreate,
    toDelete: [...toDelete.values()],
    unchanged: [...unchanged.values()],
    blocked: [...blocked.values()],
  }
}

/**
 * Превращает выделение в стирание.
 *
 * Режим определяется первой ячейкой жеста, а не отдельной кнопкой: человек
 * начал на своём отмеченном времени — значит, он его убирает. До этого
 * протяжка внутри своего слота подсвечивалась и не делала ничего: интерфейс
 * обещал действие и не выполнял, что хуже обоих чистых вариантов.
 *
 * Стирание в середине слота **разрезает** его надвое: 10:00–14:00, стёрли
 * 11:00–12:00 → остаются 10:00–11:00 и 12:00–14:00. Деление сделано тем же
 * `subtract`, что вычитает неприкосновенные куски при создании: интервальная
 * арифметика здесь одна на оба направления.
 *
 * Серии, ночные и неровные слоты стиранием не трогаются — то же правило, что
 * при создании, и по той же причине: результат жеста должен быть виден в
 * жесте.
 */
export function selectionToErase(
  range: SelectionRange,
  existingSlots: AvailabilityResponse[],
  grid: GridSpec,
): SelectionPlan {
  const zone = grid.zone
  const toCreate: SlotDraft[] = []
  const toDelete = new Map<string, AvailabilityResponse>()
  const unchanged = new Map<string, AvailabilityResponse>()
  const blocked = new Map<string, BlockedSlot>()

  for (const day of range.days) {
    if (day.col < 0 || day.col >= grid.days.length) continue

    const selection: Interval = {
      start: cellStart(day.col, day.startRow, grid),
      end: cellStart(day.col, day.endRow + 1, grid),
    }

    for (const slot of existingSlots) {
      const iv = toInterval(slot, zone)
      if (!overlaps(iv, selection)) continue

      const reason = blockReason(slot, grid)
      if (reason) {
        blocked.set(slot.id, { slot, reason })
        unchanged.set(slot.id, slot)
        continue
      }

      // Слот уходит целиком, а остатки заводятся заново. Правки на месте нет
      // намеренно: PUT менял бы одну границу, а разрез в середине даёт два
      // слота, и «изменить» тут нечего.
      toDelete.set(slot.id, slot)
      for (const piece of subtract(iv, [selection])) {
        toCreate.push({
          startsAt: piece.start.toUTC().toISO()!,
          endsAt: piece.end.toUTC().toISO()!,
        })
      }
    }
  }

  return {
    toCreate,
    toDelete: [...toDelete.values()],
    unchanged: [...unchanged.values()],
    blocked: [...blocked.values()],
  }
}
