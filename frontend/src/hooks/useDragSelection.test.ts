import { describe, expect, it, vi } from 'vitest'
import type { AvailabilityResponse } from '@/types'
import type { BlockedSlot, GridSpec, SelectionPlan, SelectionRange } from '@/utils/selection'
import { applyPlan, createDragSelection } from './useDragSelection'

/**
 * Состояние жеста: что считается началом, что отменой, когда пора слать
 * запрос.
 *
 * Проверяется контроллер, а не компонент. Testing Library и jsdom в проект не
 * заводятся, но дело не только в этом: вся арифметика уже проверена в
 * `selection.test.ts`, а здесь остаются решения — «правая кнопка жест не
 * начинает», «отменённый жест ничего не создаёт», «нечего менять — не ходим в
 * сеть». Разметка вокруг решений не принимает.
 *
 * Чего этими тестами не проверить, сказано в отчёте: `setPointerCapture`,
 * поиск ячейки по координатам и подсветку — они проверялись руками на стенде.
 */

const WEEK = [
  '2026-10-19', '2026-10-20', '2026-10-21', '2026-10-22',
  '2026-10-23', '2026-10-24', '2026-10-25',
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

/** Стенд вокруг контроллера: те же колбэки, что подставляет хук, но со шпионами. */
function harness(slots: AvailabilityResponse[] = [], busy = false) {
  const onApply = vi.fn<[SelectionPlan], void>()
  const onBlocked = vi.fn<[BlockedSlot[]], void>()
  const changes: (SelectionRange | null)[] = []
  // Изменяемое поле, а не константа: запрос может улететь уже после того, как
  // выделение создано, — ровно этот случай проверяет тест про летящий запрос.
  const state = { busy }

  const drag = createDragSelection({
    getGrid: () => GRID,
    getSlots: () => slots,
    isBusy: () => state.busy,
    onApply,
    onBlocked,
    onChange: (r) => changes.push(r),
  })

  return {
    drag,
    onApply,
    onBlocked,
    changes,
    last: () => changes[changes.length - 1],
    set busy(value: boolean) { state.busy = value },
  }
}

describe('жест целиком', () => {
  it('клик без движения — выделение из одной ячейки, один вызов применения', () => {
    const h = harness()

    expect(h.drag.down({ row: 20, col: 0 }, 0)).toBe(true)
    h.drag.up()

    expect(h.onApply).toHaveBeenCalledTimes(1)
    const plan = h.onApply.mock.calls[0][0]
    expect(plan.toCreate).toEqual([
      { startsAt: '2026-10-19T10:00:00.000Z', endsAt: '2026-10-19T10:30:00.000Z' },
    ])
  })

  /** Один жест — одна запись. Четыре получасовки человек удалял бы по одной. */
  it('протяжка через четыре ячейки даёт один слот, а не четыре', () => {
    const h = harness()

    h.drag.down({ row: 20, col: 0 }, 0)
    h.drag.move({ row: 21, col: 0 })
    h.drag.move({ row: 22, col: 0 })
    h.drag.move({ row: 23, col: 0 })
    h.drag.up()

    const plan = h.onApply.mock.calls[0][0]
    expect(plan.toCreate).toHaveLength(1)
    expect(plan.toCreate[0]).toEqual({
      startsAt: '2026-10-19T10:00:00.000Z',
      endsAt: '2026-10-19T12:00:00.000Z',
    })
  })
})

describe('отмена', () => {
  it('pointercancel посреди жеста ничего не применяет', () => {
    const h = harness()

    h.drag.down({ row: 20, col: 0 }, 0)
    h.drag.move({ row: 23, col: 0 })
    h.drag.cancel()

    expect(h.onApply).not.toHaveBeenCalled()
    expect(h.onBlocked).not.toHaveBeenCalled()
    expect(h.drag.isActive()).toBe(false)
    expect(h.last()).toBeNull()
  })

  /** Escape в хуке зовёт тот же cancel — здесь проверяется его следствие. */
  it('отмена сбрасывает выделение, и последующий pointerup ничего не делает', () => {
    const h = harness()

    h.drag.down({ row: 20, col: 0 }, 0)
    h.drag.move({ row: 23, col: 0 })
    h.drag.cancel()
    h.drag.up()

    expect(h.onApply).not.toHaveBeenCalled()
    expect(h.drag.range()).toBeNull()
  })
})

describe('когда в сеть не ходим', () => {
  /**
   * Раньше здесь стояло «протяжка внутри своего слота не даёт ни одного
   * вызова»: жест подсвечивался и не делал ничего. Это и была та самая
   * претензия — интерфейс обещал действие и не выполнял его. Теперь такая
   * протяжка стирает, и проверка перевёрнута сознательно, а не подогнана.
   */
  it('протяжка внутри своего слота теперь стирает, а не молчит', () => {
    const mine = slot('2026-10-19T09:00:00Z', '2026-10-19T13:00:00Z')
    const h = harness([mine])

    h.drag.down({ row: 20, col: 0 }, 0)
    h.drag.move({ row: 21, col: 0 })
    h.drag.up()

    expect(h.drag.mode()).toBeNull()   // жест завершён
    const plan = h.onApply.mock.calls[0][0]
    expect(plan.toDelete.map((s) => s.id)).toEqual([mine.id])
    expect(plan.toCreate).toEqual([
      { startsAt: '2026-10-19T09:00:00.000Z', endsAt: '2026-10-19T10:00:00.000Z' },
      { startsAt: '2026-10-19T11:00:00.000Z', endsAt: '2026-10-19T13:00:00.000Z' },
    ])
    expect(h.onBlocked).not.toHaveBeenCalled()
  })

  it('протяжка поверх серии: слот не тронут, сообщение показано', () => {
    const series = slot('2026-10-19T10:00:00Z', '2026-10-19T12:00:00Z', 'series-1')
    const h = harness([series])

    h.drag.down({ row: 20, col: 0 }, 0)
    h.drag.move({ row: 23, col: 0 })
    h.drag.up()

    expect(h.onApply).not.toHaveBeenCalled()
    expect(h.onBlocked).toHaveBeenCalledTimes(1)
    expect(h.onBlocked.mock.calls[0][0]).toEqual([{ slot: series, reason: 'series' }])
  })

  /**
   * Слот 23:00 понедельника — 01:00 вторника. Протяжка по вторнику 00:00–01:00
   * примыкает к нему вплотную и по общему правилу слияния унесла бы вместе с
   * ним два часа понедельника, которых в жесте не было.
   */
  it('протяжка поверх слота через полночь: слот не тронут, сообщение показано', () => {
    const overnight = slot('2026-10-19T23:00:00Z', '2026-10-20T01:00:00Z')
    const h = harness([overnight])

    h.drag.down({ row: 0, col: 1 }, 0)
    h.drag.move({ row: 1, col: 1 })
    h.drag.up()

    expect(h.onApply).not.toHaveBeenCalled()
    expect(h.onBlocked.mock.calls[0][0]).toEqual([{ slot: overnight, reason: 'overnight' }])
  })
})

describe('чем жест не начинается', () => {
  it('правая кнопка', () => {
    const h = harness()
    expect(h.drag.down({ row: 20, col: 0 }, 2)).toBe(false)
    expect(h.drag.isActive()).toBe(false)

    // Последующее движение и отпускание тоже ничего не делают.
    h.drag.move({ row: 23, col: 0 })
    h.drag.up()
    expect(h.onApply).not.toHaveBeenCalled()
  })

  it('средняя кнопка', () => {
    const h = harness()
    expect(h.drag.down({ row: 20, col: 0 }, 1)).toBe(false)
    expect(h.drag.isActive()).toBe(false)
  })

  it('запрос в полёте', () => {
    const h = harness([], true)
    expect(h.drag.down({ row: 20, col: 0 }, 0)).toBe(false)

    h.drag.move({ row: 23, col: 0 })
    h.drag.up()
    expect(h.onApply).not.toHaveBeenCalled()
    expect(h.changes).toHaveLength(0)
  })
})

/**
 * Тач-жест: тап — ручки — подтверждение.
 *
 * Он другой не из вкусовых соображений: проведение пальцем по сетке
 * неотличимо от скролла, а скролл — единственный способ листать сутки на
 * телефоне. Поэтому выделение здесь начинается тапом, растягивается ручками и
 * применяется отдельным нажатием.
 *
 * Контроллер тот же самый, что у мыши; отличается только то, откуда приходят
 * события и применяется ли выделение по отпусканию.
 */
describe('тач: тап и подтверждение', () => {
  it('тап по свободной ячейке даёт выделение из одной ячейки и не шлёт запрос', () => {
    const h = harness()

    expect(h.drag.tap({ row: 20, col: 0 })).toBe(true)
    expect(h.drag.isPending()).toBe(true)
    expect(h.last()?.cellCount).toBe(1)
    expect(h.onApply).not.toHaveBeenCalled()
  })

  /** Тап без реакции читается как неработающий интерфейс — отказ обязан быть громким. */
  it('тап по заблокированной ячейке ничего не выделяет, но объясняет почему', () => {
    const series = slot('2026-10-19T10:00:00Z', '2026-10-19T12:00:00Z', 'series-1')
    const h = harness([series])

    expect(h.drag.tap({ row: 20, col: 0 })).toBe(false)
    expect(h.drag.hasSelection()).toBe(false)
    expect(h.onApply).not.toHaveBeenCalled()
    expect(h.onBlocked.mock.calls[0][0]).toEqual([{ slot: series, reason: 'series' }])
  })

  it('перетаскивание нижней ручки растягивает выделение, запрос всё ещё не уходит', () => {
    const h = harness()

    h.drag.tap({ row: 20, col: 0 })
    expect(h.drag.grab('end')).toBe(true)
    h.drag.move({ row: 24, col: 0 })
    h.drag.up()

    expect(h.last()?.cellCount).toBe(5)
    expect(h.drag.isPending()).toBe(true)
    expect(h.onApply).not.toHaveBeenCalled()
  })

  /**
   * Ручки можно протащить одну сквозь другую. Выделение при этом
   * переворачивается, а не схлопывается: нормализацию делает та же
   * `resolveSelection`, что и у мыши.
   */
  it('верхняя ручка, утащенная ниже нижней, переворачивает выделение', () => {
    const h = harness()

    h.drag.tap({ row: 20, col: 0 })
    h.drag.grab('end')
    h.drag.move({ row: 23, col: 0 })   // выделение 20..23
    h.drag.up()

    h.drag.grab('start')
    h.drag.move({ row: 27, col: 0 })   // верхнюю утащили ниже нижней
    h.drag.up()

    const r = h.last()!
    expect(r.days[0]).toMatchObject({ startRow: 23, endRow: 27, length: 5 })
    expect(r.cellCount).toBe(5)
  })

  it('подтверждение шлёт один слот и один вызов', () => {
    const h = harness()

    h.drag.tap({ row: 20, col: 0 })
    h.drag.grab('end')
    h.drag.move({ row: 23, col: 0 })
    h.drag.up()
    h.drag.commit()

    expect(h.onApply).toHaveBeenCalledTimes(1)
    expect(h.onApply.mock.calls[0][0].toCreate).toEqual([
      { startsAt: '2026-10-19T10:00:00.000Z', endsAt: '2026-10-19T12:00:00.000Z' },
    ])
    expect(h.drag.hasSelection()).toBe(false)
  })

  it('отмена сбрасывает выделение и ничего не шлёт', () => {
    const h = harness()

    h.drag.tap({ row: 20, col: 0 })
    h.drag.grab('end')
    h.drag.move({ row: 23, col: 0 })
    h.drag.up()
    h.drag.cancel()

    expect(h.onApply).not.toHaveBeenCalled()
    expect(h.drag.hasSelection()).toBe(false)
    expect(h.last()).toBeNull()
  })

  /**
   * Тап мимо выделения — отмена, а не новое выделение. Иначе промах мимо
   * ручки молча переносил бы выделение, и человек подтверждал бы не то, что
   * видел секунду назад.
   */
  it('тап при живом выделении отменяет его, а не создаёт новое', () => {
    const h = harness()

    h.drag.tap({ row: 20, col: 0 })
    expect(h.drag.tap({ row: 40, col: 3 })).toBe(false)

    expect(h.drag.hasSelection()).toBe(false)
    expect(h.onApply).not.toHaveBeenCalled()
  })

  it('выделение, задевшее серию, её не трогает и объясняет почему', () => {
    const series = slot('2026-10-19T11:00:00Z', '2026-10-19T12:00:00Z', 'series-1')
    const h = harness([series])

    h.drag.tap({ row: 20, col: 0 })   // 10:00, свободно
    h.drag.grab('end')
    h.drag.move({ row: 23, col: 0 })  // дотянули до 12:00, накрыв серию
    h.drag.up()
    h.drag.commit()

    expect(h.onBlocked.mock.calls[0][0]).toEqual([{ slot: series, reason: 'series' }])
    const plan = h.onApply.mock.calls[0][0]
    expect(plan.toDelete).toHaveLength(0)
    expect(plan.toCreate).toEqual([
      { startsAt: '2026-10-19T10:00:00.000Z', endsAt: '2026-10-19T11:00:00.000Z' },
    ])
  })

  it('выделение, задевшее ночной слот, его не трогает', () => {
    const overnight = slot('2026-10-19T23:00:00Z', '2026-10-20T01:00:00Z')
    const h = harness([overnight])

    h.drag.tap({ row: 2, col: 1 })    // вторник 01:00, свободно
    h.drag.grab('start')
    h.drag.move({ row: 0, col: 1 })   // дотянули вверх до полуночи
    h.drag.up()
    h.drag.commit()

    expect(h.onBlocked.mock.calls[0][0]).toEqual([{ slot: overnight, reason: 'overnight' }])
    expect(h.onApply.mock.calls[0][0].toDelete).toHaveLength(0)
  })

  it('подтверждение при летящем запросе не отправляет второй', () => {
    const h = harness()
    h.drag.tap({ row: 20, col: 0 })

    h.busy = true
    h.drag.commit()

    expect(h.onApply).not.toHaveBeenCalled()
    expect(h.drag.hasSelection(), 'выделение остаётся — подтвердить можно будет позже').toBe(true)
  })
})

/**
 * Порядок операций при слиянии. Создаём объединённый слот, потом удаляем
 * поглощённые — не наоборот. Обратный порядок при падении создания уничтожает
 * уже отмеченное время без следа; выбранный в худшем случае оставляет лишнюю
 * строку в списке, которую видно и можно убрать руками.
 */
describe('слияние со старым слотом', () => {
  it('план содержит и объединённый слот, и поглощённый старый', () => {
    const existing = slot('2026-10-19T09:00:00Z', '2026-10-19T10:00:00Z')
    const h = harness([existing])

    h.drag.down({ row: 20, col: 0 }, 0)
    h.drag.move({ row: 23, col: 0 })
    h.drag.up()

    const plan = h.onApply.mock.calls[0][0]
    expect(plan.toCreate).toEqual([
      { startsAt: '2026-10-19T09:00:00.000Z', endsAt: '2026-10-19T12:00:00.000Z' },
    ])
    expect(plan.toDelete.map((s) => s.id)).toEqual([existing.id])
  })

  /**
   * Падение создания не должно доводить дело до удаления. Здесь это проверено
   * на той же последовательности, что выполняет мутация: `await` на создании
   * бросает — до удалений управление не доходит.
   */
  it('создание упало — удаление не выполняется', async () => {
    const existing = slot('2026-10-19T09:00:00Z', '2026-10-19T10:00:00Z')
    const h = harness([existing])

    h.drag.down({ row: 20, col: 0 }, 0)
    h.drag.move({ row: 23, col: 0 })
    h.drag.up()
    const plan = h.onApply.mock.calls[0][0]

    const addSlot = vi.fn(async () => { throw new Error('503') })
    const deleteSlot = vi.fn(async () => undefined)

    await expect(applyPlan(plan, addSlot, deleteSlot)).rejects.toThrow('503')
    expect(addSlot).toHaveBeenCalledTimes(1)
    expect(deleteSlot, 'старые слоты обязаны остаться на месте').not.toHaveBeenCalled()
  })
})

/**
 * Режим жеста определяется первой ячейкой.
 *
 * До этого протяжка внутри своего слота подсвечивалась и не делала ничего:
 * интерфейс обещал действие и не выполнял, что хуже обоих чистых вариантов.
 */
describe('режим определяется первой ячейкой', () => {
  const mine = () => slot('2026-10-19T10:00:00Z', '2026-10-19T14:00:00Z')

  it('старт на свободной ячейке — рисуем', () => {
    const h = harness()
    h.drag.down({ row: 30, col: 0 }, 0)
    expect(h.drag.mode()).toBe('create')
  })

  it('старт на своей занятой — стираем', () => {
    const h = harness([mine()])
    h.drag.down({ row: 20, col: 0 }, 0)
    expect(h.drag.mode()).toBe('erase')
  })

  it('старт на заблокированной — жест не начинается, показано объяснение', () => {
    const series = slot('2026-10-19T10:00:00Z', '2026-10-19T12:00:00Z', 'series-1')
    const h = harness([series])

    expect(h.drag.down({ row: 20, col: 0 }, 0)).toBe(false)
    expect(h.drag.mode()).toBeNull()
    expect(h.drag.isActive()).toBe(false)
    expect(h.onBlocked.mock.calls[0][0]).toEqual([{ slot: series, reason: 'series' }])
  })

  /** Тач-путь принимает то же решение — режим один на оба жеста. */
  it('тап по своей занятой тоже даёт режим стирания', () => {
    const h = harness([mine()])
    expect(h.drag.tap({ row: 20, col: 0 })).toBe(true)
    expect(h.drag.mode()).toBe('erase')
  })

  /**
   * Режим ставится один раз. Протяжка, начатая на своём и уехавшая на
   * свободное, остаётся стиранием — иначе один жест делал бы два разных дела.
   */
  it('режим не меняется, когда выделение выходит на свободные ячейки', () => {
    const h = harness([mine()])
    h.drag.down({ row: 20, col: 0 }, 0)
    h.drag.move({ row: 40, col: 0 })
    expect(h.drag.mode()).toBe('erase')
  })
})

describe('стирание протяжкой', () => {
  it('стирание середины слота присылает удаление и два остатка', () => {
    const mine = slot('2026-10-19T10:00:00Z', '2026-10-19T14:00:00Z')
    const h = harness([mine])

    h.drag.down({ row: 22, col: 0 }, 0)   // 11:00
    h.drag.move({ row: 23, col: 0 })      // 12:00
    h.drag.up()

    const plan = h.onApply.mock.calls[0][0]
    expect(plan.toDelete.map((s) => s.id)).toEqual([mine.id])
    expect(plan.toCreate).toEqual([
      { startsAt: '2026-10-19T10:00:00.000Z', endsAt: '2026-10-19T11:00:00.000Z' },
      { startsAt: '2026-10-19T12:00:00.000Z', endsAt: '2026-10-19T14:00:00.000Z' },
    ])
  })

  it('старт на свободной ячейке по-прежнему создаёт, а не стирает', () => {
    const mine = slot('2026-10-19T10:00:00Z', '2026-10-19T11:00:00Z')
    const h = harness([mine])

    h.drag.down({ row: 24, col: 0 }, 0)   // 12:00, свободно
    h.drag.move({ row: 25, col: 0 })
    h.drag.up()

    const plan = h.onApply.mock.calls[0][0]
    expect(plan.toCreate).toHaveLength(1)
    expect(plan.toCreate[0].endsAt).toBe('2026-10-19T13:00:00.000Z')
  })

  /**
   * Стирание, начатое на своём и протянутое через серию: своё исчезает, серия
   * остаётся и объясняется. То же правило, что при создании.
   */
  it('серия под стиранием остаётся, своё стирается', () => {
    const mine = slot('2026-10-19T10:00:00Z', '2026-10-19T11:00:00Z')
    const series = slot('2026-10-19T11:00:00Z', '2026-10-19T12:00:00Z', 'series-1')
    const h = harness([mine, series])

    h.drag.down({ row: 20, col: 0 }, 0)
    h.drag.move({ row: 23, col: 0 })
    h.drag.up()

    expect(h.onBlocked.mock.calls[0][0]).toEqual([{ slot: series, reason: 'series' }])
    const plan = h.onApply.mock.calls[0][0]
    expect(plan.toDelete.map((s) => s.id)).toEqual([mine.id])
    expect(plan.toCreate).toHaveLength(0)
  })
})
