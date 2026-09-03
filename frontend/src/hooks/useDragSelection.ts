import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import type { AvailabilityResponse } from '@/types'
import {
  cellOwnership,
  clampSelection,
  resolveSelection,
  selectionToErase,
  selectionToSlots,
  type BlockedSlot,
  type Cell,
  type GridSpec,
  type SelectionPlan,
  type SelectionRange,
} from '@/utils/selection'

/**
 * Выделение по сетке доступности — двумя разными жестами на одном состоянии.
 *
 * Мышь: зажал ячейку, потянул, отпустил — применилось.
 *
 * Тач: тап по ячейке, ручки по углам, подтверждение кнопкой. Жест другой не
 * из вкусовых соображений: проведение пальцем по ячейкам неотличимо от
 * скролла, а скролл — единственный способ листать сутки на телефоне. Отнимать
 * его ради выделения нельзя, поэтому `touch-action: none` висит только на
 * ручках, а не на контейнере сетки.
 *
 * Состояние при этом одно на оба жеста: `anchor`, `focus` и нормализация те
 * же самые. Отличается только источник событий и то, применяется ли выделение
 * по отпусканию.
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
export type DragHighlight = 'create' | 'unchanged' | 'blocked' | 'erase'

/**
 * Что жест делает. Определяется первой ячейкой и до конца жеста не меняется.
 *
 * Начали на свободной — рисуем. Начали на своём отмеченном — стираем. Раньше
 * второго не было вовсе: протяжка внутри своего слота подсвечивалась и не
 * делала ничего, то есть интерфейс обещал действие и не выполнял его.
 */
export type DragMode = 'create' | 'erase'

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
  /**
   * Тап по своей занятой ячейке, когда выделения нет: открыть слот.
   *
   * Раньше такой тап начинал стирающее выделение. Тап по своему слоту на
   * телефоне гораздо чаще значит «посмотреть и поправить», чем «стереть», а
   * удаление в модалке никуда не делось — оно там отдельной кнопкой.
   */
  onActivateSlot?: (cell: Cell) => void
}

/** За какой угол выделения держатся: верхний-левый или нижний-правый. */
export type SelectionHandle = 'start' | 'end'

export interface DragSelection {
  /** Мышь: зажали ячейку. Отпускание применит выделение. @returns начат ли жест */
  down(cell: Cell, button: number): boolean
  move(cell: Cell): void
  up(): void
  cancel(): void
  /** Ведём ли выделение прямо сейчас (кнопка или палец в движении). */
  isActive(): boolean
  range(): SelectionRange | null

  /**
   * Тач: тап по ячейке. Выделение остаётся жить и ждёт подтверждения.
   * @returns создано ли выделение (нет — если ячейка заблокирована или
   *          выделение уже было и тап его отменил)
   */
  tap(cell: Cell): boolean
  /** Тач: взялись за ручку. Дальше `move` тянет именно её. */
  grab(handle: SelectionHandle): boolean
  /** Тач: применить то, что выделено. */
  commit(): void
  /** Есть живое выделение (мышиное в процессе или тач-выделение в ожидании). */
  hasSelection(): boolean
  /** Выделение ждёт подтверждения — значит, надо показать ручки и кнопки. */
  isPending(): boolean
  /** Что сделает текущий жест. `null`, если жеста нет. */
  mode(): DragMode | null
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
  /** Ведём ли выделение прямо сейчас: кнопка зажата или палец на ручке. */
  let dragging = false
  /**
   * Применить ли выделение по отпусканию.
   *
   * Мышь — да: отпустил кнопку, получил слот. Тач — нет: палец поднимается и
   * с ручки, и просто так, а угадывать по этому намерение нельзя. Там
   * применение отдельным нажатием.
   */
  let commitOnRelease = true
  /** Режим текущего жеста. Ставится по первой ячейке и дальше не меняется. */
  let mode: DragMode | null = null
  /**
   * Двигали ли выделение с момента, как за него взялись.
   *
   * Нужно на тач-экране: ручки одноклеточного выделения перекрывают его
   * целиком своими зонами захвата, и подтверждающий тап приходит не в ячейку,
   * а в ручку. Взялись и отпустили, не сдвинув, — это тот же тап по
   * выделению, и значит он то же самое.
   */
  let movedSinceGrab = false

  const range = (): SelectionRange | null => {
    if (!anchor || !focus) return null
    const grid = config.getGrid()
    return clampSelection(resolveSelection(anchor, focus, grid), grid)
  }

  const reset = () => {
    anchor = null
    focus = null
    dragging = false
    mode = null
    config.onChange(null)
  }

  /**
   * Что жест сделает, начавшись на этой ячейке. `null` — не начнётся вовсе.
   *
   * Заблокированная ячейка не молчит: тап без единой реакции читается как
   * неработающий интерфейс, поэтому отказ обязан быть громким.
   */
  const modeFor = (cell: Cell): DragMode | null => {
    const grid = config.getGrid()
    const slots = config.getSlots()
    const owner = cellOwnership(cell, slots, grid)
    if (owner === 'blocked') {
      // Заблокированная ячейка — это всегда чей-то слот, и почти всегда свой:
      // серия, ночной или неровный. Жестом его не поправить, но открыть можно
      // и нужно — иначе единственный ответ на нажатие по собственному слоту
      // серии оставался бы «сюда нельзя», что и есть тупик.
      if (config.onActivateSlot) {
        config.onActivateSlot(cell)
        return null
      }
      const probe = clampSelection(resolveSelection(cell, cell, grid), grid)
      config.onBlocked(selectionToSlots(probe, slots, grid).blocked)
      return null
    }
    return owner === 'mine' ? 'erase' : 'create'
  }

  /** Применить текущее выделение и сбросить его. */
  const applyCurrent = () => {
    const finished = range()
    const erasing = mode === 'erase'
    reset()
    if (!finished) return

    const plan = (erasing ? selectionToErase : selectionToSlots)(
      finished,
      config.getSlots(),
      config.getGrid(),
    )
    if (plan.blocked.length > 0) config.onBlocked(plan.blocked)
    if (plan.toCreate.length > 0 || plan.toDelete.length > 0) config.onApply(plan)
  }

  /** Крайние ячейки выделения: верхняя-левая и нижняя-правая. */
  const corners = (): { start: Cell; end: Cell } | null => {
    const r = range()
    if (!r || r.days.length === 0) return null
    const first = r.days[0]
    const last = r.days[r.days.length - 1]
    return {
      start: { row: first.startRow, col: first.col },
      end: { row: last.endRow, col: last.col },
    }
  }

  return {
    down(cell, button) {
      // Только основная кнопка. Правая открывает контекстное меню, средняя на
      // многих системах включает автопрокрутку — и то и другое поверх начатого
      // выделения выглядит как зависшая подсветка.
      if (button !== 0) return false
      if (config.isBusy()) return false

      const next = modeFor(cell)
      if (!next) return false

      mode = next
      anchor = cell
      focus = cell
      dragging = true
      movedSinceGrab = false
      commitOnRelease = true
      config.onChange(range())
      return true
    },

    move(cell) {
      if (!dragging) return
      if (focus && focus.row === cell.row && focus.col === cell.col) return
      movedSinceGrab = true
      focus = cell
      config.onChange(range())
    },

    up() {
      // Отпускание без начатого жеста — обычное дело: кнопку могли зажать вне
      // сетки, а отпустить над ней. Выходим молча, не трогая состояние: лишний
      // onChange(null) заставил бы подписчиков думать, что что-то отменилось.
      if (!dragging) return
      if (!commitOnRelease) {
        dragging = false
        // Взялись за ручку и отпустили, не сдвинув, — это тап по выделению, а
        // не растягивание. Отличить их иначе нельзя: зона захвата ручки
        // накрывает одноклеточное выделение целиком.
        if (!movedSinceGrab) applyCurrent()
        return
      }
      // Мышь: щелчок без движения по своей занятой ячейке открывает слот, а
      // не стирает его. Одно нажатие, стирающее время без единого вопроса, —
      // не то, чего человек ждёт от клика; протяжка для стирания осталась.
      if (mode === 'erase' && !movedSinceGrab && config.onActivateSlot && anchor) {
        const cell = anchor
        reset()
        config.onActivateSlot(cell)
        return
      }
      applyCurrent()
    },

    /** Отмена: жест не завершён, значит не применяется. Ни запроса, ни разбора. */
    cancel() {
      if (!anchor) return
      reset()
    },

    tap(cell) {
      if (config.isBusy()) return false

      /*
       * Тап при живом выделении.
       *
       * По выделению — подтверждение: это самый естественный жест «да, вот
       * это», и он же убирает пару одинаковых кнопок, которые раньше стояли
       * рядом и читались одинаково.
       *
       * Мимо выделения — ничего. Не отмена: промах пальцем мимо узкой ячейки
       * — обычное дело, и терять из-за него настроенное выделение человек не
       * подписывался. Отмена теперь одна и отдельной кнопкой, вдали от самого
       * выделения.
       */
      if (anchor) {
        const current = range()
        const inside = current?.days.some(
          (d) => d.col === cell.col && cell.row >= d.startRow && cell.row <= d.endRow,
        )
        if (inside) applyCurrent()
        return false
      }

      const grid = config.getGrid()
      const slots = config.getSlots()
      const owner = cellOwnership(cell, slots, grid)

      // Занятая ячейка — своя обычная или заблокированная — открывает слот.
      // На тач-экране посмотреть и поправить нужно чаще, чем стереть, а
      // удаление никуда не делось: оно кнопкой в самой модалке.
      if (owner !== 'free') {
        if (config.onActivateSlot) {
          config.onActivateSlot(cell)
          return false
        }
        if (owner === 'blocked') {
          const probe = clampSelection(resolveSelection(cell, cell, grid), grid)
          config.onBlocked(selectionToSlots(probe, slots, grid).blocked)
          return false
        }
      }

      mode = owner === 'mine' ? 'erase' : 'create'
      anchor = cell
      focus = cell
      dragging = false
      movedSinceGrab = false
      commitOnRelease = false
      config.onChange(range())
      return true
    },

    grab(handle) {
      if (config.isBusy()) return false
      const ends = corners()
      if (!ends) return false

      // Тянут ту ручку, за которую взялись; противоположная становится
      // якорем. Нормализацию дальше делает resolveSelection — ручки можно
      // протащить одну сквозь другую, выделение просто перевернётся.
      anchor = handle === 'end' ? ends.start : ends.end
      focus = handle === 'end' ? ends.end : ends.start
      dragging = true
      movedSinceGrab = false
      commitOnRelease = false
      return true
    },

    commit() {
      if (config.isBusy()) return
      applyCurrent()
    },

    isActive: () => dragging,
    hasSelection: () => anchor !== null,
    isPending: () => anchor !== null && !commitOnRelease,
    mode: () => mode,
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
  /** Тап по своей занятой ячейке без активного выделения: открыть слот. */
  onActivateSlot?: (cell: Cell) => void
}

export function useDragSelection(options: UseDragSelectionOptions) {
  const [range, setRange] = useState<SelectionRange | null>(null)
  /** Отдельный флаг: выделение живо, но подтверждения ещё не было. */
  const [pending, setPending] = useState(false)

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
        onActivateSlot: (cell) => latest.current.onActivateSlot?.(cell),
        onChange: setRange,
      }),
    [],
  )

  /**
   * Тип указателя последнего нажатия. По нему решается, чей это жест: у мыши
   * выделение ведётся зажатой кнопкой, у пальца — только за ручку, а
   * проведение по ячейкам обязано остаться скроллом.
   */
  const lastPointerType = useRef<string>('mouse')

  const onPointerDown = useCallback(
    (e: React.PointerEvent<HTMLElement>) => {
      lastPointerType.current = e.pointerType
      // Палец по ячейкам не выделяет — он скроллит. Тач-выделение начинается
      // тапом (событие click, которое браузер не пришлёт, если был скролл) и
      // растягивается ручками.
      if (e.pointerType === 'touch') return
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
      setPending(controller.isPending())
    },
    [controller],
  )

  const onPointerCancel = useCallback(
    (e: React.PointerEvent<HTMLElement>) => {
      release(e)
      controller.cancel()
      setPending(controller.isPending())
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
      if (e.key !== 'Escape') return
      controller.cancel()
      setPending(false)
    }
    // Потеря фокуса окна отменяет только незавершённый жест. Тач-выделение,
    // уже ждущее подтверждения, переживает переключение вкладки: человек
    // вернётся и нажмёт кнопку.
    const onBlur = () => {
      if (!controller.isActive()) return
      controller.cancel()
      setPending(false)
    }
    window.addEventListener('keydown', onKeyDown)
    window.addEventListener('blur', onBlur)
    return () => {
      window.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('blur', onBlur)
    }
  }, [range, controller])

  /**
   * Тач-тап. Именно `click`, а не `pointerup`: браузер сам не присылает click
   * после того, как касание превратилось в скролл, — то есть он уже отличил
   * тап от прокрутки, и повторять эту логику руками не нужно.
   */
  const onClick = useCallback(
    (e: React.MouseEvent<HTMLElement>) => {
      if (lastPointerType.current !== 'touch') return
      const target = e.target instanceof Element ? e.target : null
      if (target?.closest('[data-handle]')) return
      const cell = target?.closest('[data-row][data-col]')
      if (!cell) return
      controller.tap({
        row: Number(cell.getAttribute('data-row')),
        col: Number(cell.getAttribute('data-col')),
      })
      setPending(controller.isPending())
    },
    [controller],
  )

  const handleProps = useCallback(
    (handle: SelectionHandle) => ({
      'data-handle': handle,
      onPointerDown: (e: React.PointerEvent<HTMLElement>) => {
        // Не даём событию дойти до контейнера: там оно завело бы мышиный жест
        // от ячейки под ручкой.
        e.stopPropagation()
        if (!controller.grab(handle)) return
        e.currentTarget.setPointerCapture(e.pointerId)
        // Единственное место, где нативный жест перебивается. touch-action на
        // контейнере убил бы скролл сетки целиком.
        e.preventDefault()
      },
    }),
    [controller],
  )

  const commit = useCallback(() => {
    controller.commit()
    setPending(controller.isPending())
  }, [controller])

  const cancel = useCallback(() => {
    controller.cancel()
    setPending(controller.isPending())
  }, [controller])

  return {
    range,
    /** Что сделает текущий жест: нарисует или сотрёт. */
    mode: controller.mode(),
    isDragging: range !== null,
    /** Выделение ждёт подтверждения: показываем ручки и кнопки. */
    pending,
    gridProps: { onPointerDown, onPointerMove, onPointerUp, onPointerCancel, onClick },
    handleProps,
    commit,
    cancel,
  }
}
