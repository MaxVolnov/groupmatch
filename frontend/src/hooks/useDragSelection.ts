import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { AvailabilityResponse } from '@/types'
import {
  clampSelection,
  resolveSelection,
  selectionToSlots,
  type BlockedSlot,
  type Cell,
  type GridSpec,
  type SelectionPlan,
  type SelectionRange,
} from '@/utils/selection'

/**
 * Протяжка по сетке доступности: зажал ячейку, потянул, отпустил.
 *
 * Файл делится надвое сознательно.
 *
 * {@link createDragSelection} — состояние жеста без React и без DOM: что
 * считается началом, что отменой, когда пора применять. Это единственная
 * часть, где есть решения, и она проверяется тестами напрямую. В проекте нет
 * ни Testing Library, ни jsdom, а заводить их запрещено; но даже будь они,
 * тестировать стоило бы именно это, а не разметку вокруг.
 *
 * {@link useDragSelection} — переходник: подписывает Pointer Events, ловит
 * Escape и потерю фокуса, переносит состояние в React. Решений в нём нет,
 * зато есть работа с DOM, которую в node-окружении всё равно не проверить.
 */

/** Подсветка ячейки во время жеста. Живёт только пока тянут. */
export type DragHighlight = 'create' | 'unchanged' | 'blocked'

export interface DragSelectionConfig {
  /** Сетка на момент жеста. Функция, а не значение: неделю могут переключить. */
  getGrid: () => GridSpec
  getSlots: () => AvailabilityResponse[]
  /** Запрос в полёте — новый жест не начинается. */
  isBusy: () => boolean
  /** Есть что менять: непустые `toCreate` или `toDelete`. */
  onApply: (plan: SelectionPlan) => void
  /** Есть что объяснить: выделение задело серию или слот через полночь. */
  onBlocked: (blocked: BlockedSlot[]) => void
  /** Текущее выделение изменилось (в том числе на `null` при отмене). */
  onChange: (range: SelectionRange | null) => void
}

export interface DragSelection {
  /** @returns начат ли жест */
  down(cell: Cell, button: number): boolean
  move(cell: Cell): void
  up(): void
  cancel(): void
  isActive(): boolean
  range(): SelectionRange | null
}

/**
 * Состояние жеста. Ни React, ни DOM — только «где начали, где палец сейчас».
 *
 * Разделение `onApply` и `onBlocked` — не украшательство. Правило «нет
 * изменений — нет запроса» должно жить в одном месте: будь тут один колбэк с
 * планом, проверять его пустоту пришлось бы на каждой стороне вызова, и
 * первая же забытая проверка превратила бы повторную протяжку по своему
 * времени в сетевой запрос ни о чём.
 */
export function createDragSelection(config: DragSelectionConfig): DragSelection {
  let anchor: Cell | null = null
  let focus: Cell | null = null

  const range = (): SelectionRange | null => {
    if (!anchor || !focus) return null
    const grid = config.getGrid()
    return clampSelection(resolveSelection(anchor, focus, grid), grid)
  }

  const reset = () => {
    anchor = null
    focus = null
    config.onChange(null)
  }

  return {
    down(cell, button) {
      // Только основная кнопка. Правая открывает контекстное меню, средняя на
      // многих системах включает автопрокрутку — и то и другое поверх начатого
      // выделения выглядит как зависшая подсветка.
      if (button !== 0) return false
      if (config.isBusy()) return false
      anchor = cell
      focus = cell
      config.onChange(range())
      return true
    },

    move(cell) {
      if (!anchor) return
      if (focus && focus.row === cell.row && focus.col === cell.col) return
      focus = cell
      config.onChange(range())
    },

    up() {
      // Отпускание без начатого жеста — обычное дело: кнопку могли зажать вне
      // сетки, а отпустить над ней. Выходим молча, не трогая состояние: лишний
      // onChange(null) заставил бы подписчиков думать, что что-то отменилось.
      if (!anchor) return
      const finished = range()
      reset()
      if (!finished) return

      const plan = selectionToSlots(finished, config.getSlots(), config.getGrid())
      if (plan.blocked.length > 0) config.onBlocked(plan.blocked)
      if (plan.toCreate.length > 0 || plan.toDelete.length > 0) config.onApply(plan)
    },

    /** Отмена: жест не завершён, значит не применяется. Ни запроса, ни разбора. */
    cancel() {
      if (!anchor) return
      reset()
    },

    isActive: () => anchor !== null,
    range,
  }
}

/**
 * Применяет план к серверу.
 *
 * Порядок обязателен: **сначала создать объединённый слот, потом удалить
 * поглощённые**. При обратном порядке падение создания уничтожает уже
 * отмеченное человеком время без следа — а это не неудачное действие, это
 * потеря данных. Выбранный порядок в худшем случае (упало удаление) оставляет
 * лишнюю строку в списке: её видно и её можно убрать руками.
 *
 * Последовательно, а не `Promise.all`: параллельное создание при частичном
 * отказе оставило бы половину слотов, и по результату нельзя было бы сказать,
 * какую именно.
 */
export async function applyPlan(
  plan: SelectionPlan,
  addSlot: (draft: { startsAt: string; endsAt: string }) => Promise<unknown>,
  deleteSlot: (slotId: string) => Promise<unknown>,
): Promise<void> {
  for (const draft of plan.toCreate) await addSlot(draft)
  for (const victim of plan.toDelete) await deleteSlot(victim.id)
}

/** Ячейка под точкой экрана. Координаты лежат на самой ячейке в `data-*`. */
function domCellFromPoint(x: number, y: number): Cell | null {
  const el = document.elementFromPoint(x, y)
  const cell = el instanceof Element ? el.closest('[data-row][data-col]') : null
  if (!cell) return null
  const row = Number(cell.getAttribute('data-row'))
  const col = Number(cell.getAttribute('data-col'))
  return Number.isInteger(row) && Number.isInteger(col) ? { row, col } : null
}

export interface UseDragSelectionOptions {
  grid: GridSpec
  slots: AvailabilityResponse[]
  busy: boolean
  onApply: (plan: SelectionPlan) => void
  onBlocked: (blocked: BlockedSlot[]) => void
}

export function useDragSelection(options: UseDragSelectionOptions) {
  const [range, setRange] = useState<SelectionRange | null>(null)

  // Свежие значения для контроллера: он создаётся один раз, а неделя, слоты и
  // состояние запроса меняются под ним.
  const latest = useRef(options)
  latest.current = options

  const controller = useMemo(
    () =>
      createDragSelection({
        getGrid: () => latest.current.grid,
        getSlots: () => latest.current.slots,
        isBusy: () => latest.current.busy,
        onApply: (plan) => latest.current.onApply(plan),
        onBlocked: (blocked) => latest.current.onBlocked(blocked),
        onChange: setRange,
      }),
    [],
  )

  const onPointerDown = useCallback(
    (e: React.PointerEvent<HTMLElement>) => {
      const cell = domCellFromPoint(e.clientX, e.clientY)
      if (!cell) return
      if (!controller.down(cell, e.button)) return
      // Захват указателя: курсор, ушедший за пределы таблицы с зажатой
      // кнопкой, продолжает слать события сюда. Без него протяжка обрывается
      // на краю, и человек, промахнувшийся мимо последней строки, теряет весь
      // жест. Побочный эффект — события перенаправляются на контейнер,
      // поэтому ячейка ниже ищется по координатам, а не по e.target.
      e.currentTarget.setPointerCapture(e.pointerId)
      e.preventDefault()
    },
    [controller],
  )

  const onPointerMove = useCallback(
    (e: React.PointerEvent<HTMLElement>) => {
      if (!controller.isActive()) return
      const cell = domCellFromPoint(e.clientX, e.clientY)
      if (cell) controller.move(cell)
    },
    [controller],
  )

  const release = (e: React.PointerEvent<HTMLElement>) => {
    if (e.currentTarget.hasPointerCapture?.(e.pointerId)) {
      e.currentTarget.releasePointerCapture(e.pointerId)
    }
  }

  const onPointerUp = useCallback(
    (e: React.PointerEvent<HTMLElement>) => {
      release(e)
      controller.up()
    },
    [controller],
  )

  const onPointerCancel = useCallback(
    (e: React.PointerEvent<HTMLElement>) => {
      release(e)
      controller.cancel()
    },
    [controller],
  )

  // Escape и потеря фокуса окна — отмена. Незавершённый жест ничего не
  // создаёт: человек, переключившийся на другое окно посреди протяжки, к
  // выделению уже не вернётся, а «применить на всякий случай» — худший из
  // возможных ответов.
  useEffect(() => {
    if (!range) return
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') controller.cancel()
    }
    const onBlur = () => controller.cancel()
    window.addEventListener('keydown', onKeyDown)
    window.addEventListener('blur', onBlur)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('blur', onBlur)
    }
  }, [range, controller])

  return {
    range,
    isDragging: range !== null,
    gridProps: { onPointerDown, onPointerMove, onPointerUp, onPointerCancel },
  }
}
