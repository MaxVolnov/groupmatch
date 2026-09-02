import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { availabilityApi } from '@/api/availability'
import { Button } from '@/components/Button'
import { Spinner } from '@/components/Spinner'
import { Skeleton } from '@/components/Skeleton'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { HeatmapSlot } from '@/types'
import { DateTime } from 'luxon'
import { buildOwnGrid, DAYS_PER_WEEK, isBlockedState, ROWS_PER_DAY } from '@/utils/ownGrid'
import { cellStart, type SelectionPlan } from '@/utils/selection'
import { applyPlan, useDragSelection, type DragHighlight } from '@/hooks/useDragSelection'
import { WeekGrid } from './WeekGrid'

interface Props {
  groupId: string
  isOwner: boolean
  onCreateMeeting: (slot: HeatmapSlot) => void
  /** Увести на вкладку доступности из пустого состояния. */
  onSetMyTime?: () => void
  /**
   * Смещение недели в неделях от текущей. Состояние поднято в GroupPage и
   * общее с вкладкой доступности.
   */
  weekOffset: number
  onWeekOffsetChange: (next: number) => void
}

/**
 * Что делает жест по сетке.
 *
 * Владелец умеет два разных действия над одной и той же клеткой: отметить
 * своё время и назначить встречу. Разводить их модификатором клавиатуры
 * нельзя — на телефоне модификаторов нет, а основная аудитория продукта
 * приходит с телефона. Поэтому режим переключается явно и переключатель
 * всегда виден: человек в любой момент знает, что сделает следующее касание.
 *
 * У участника без прав владельца выбора нет — сетка всегда правит своё время,
 * и переключатель ему не показывается.
 */
type GridMode = 'availability' | 'meeting'

/**
 * Агрегат группы, разложенный по ячейкам недели.
 *
 * Логика раскладки не менялась: бэкенд отдаёт готовые получасовые бакеты,
 * здесь они только расставляются по координатам сетки.
 */
function buildAggregate(slots: HeatmapSlot[], from: DateTime) {
  const counts: number[][] = Array.from({ length: ROWS_PER_DAY }, () =>
    Array<number>(DAYS_PER_WEEK).fill(0),
  )
  const names: (string[] | null)[][] = Array.from({ length: ROWS_PER_DAY }, () =>
    Array<string[] | null>(DAYS_PER_WEEK).fill(null),
  )
  let max = 0

  for (const slot of slots) {
    const s = DateTime.fromISO(slot.startsAt).toLocal()
    const dayIndex = (((s.weekday - 1) % 7) + 7) % 7
    const weekDayOfSlot = from.plus({ days: dayIndex })
    if (!s.hasSame(weekDayOfSlot, 'day')) continue

    const bucketIndex = Math.floor((s.hour * 60 + s.minute) / 30)
    if (bucketIndex >= 0 && bucketIndex < ROWS_PER_DAY) {
      counts[bucketIndex][dayIndex] = slot.count
      names[bucketIndex][dayIndex] = slot.displayNames
      if (slot.count > max) max = slot.count
    }
  }

  return { counts, names, max }
}

export function HeatmapTab({ groupId, isOwner, onCreateMeeting, onSetMyTime, weekOffset, onWeekOffsetChange }: Props) {
  const { t } = useTranslation()
  const qc = useQueryClient()
  const [initialLoaded, setInitialLoaded] = useState(false)
  const [mode, setMode] = useState<GridMode>('availability')
  const [blockedNotice, setBlockedNotice] = useState(false)

  const monday = DateTime.now().startOf('week').plus({ weeks: weekOffset })
  const sunday = monday.plus({ days: 7 })

  const { data, isLoading, error } = useQuery({
    queryKey: ['heatmap', groupId, weekOffset],
    queryFn: () => availabilityApi.heatmap(groupId, monday.toUTC().toISO()!, sunday.toUTC().toISO()!, 30),
  })

  // Тот же queryKey, что у списка на вкладке доступности: свои слоты берутся
  // из общего кэша, лишнего запроса не возникает.
  const { data: mySlots } = useQuery({
    queryKey: ['availability', groupId],
    queryFn: () => availabilityApi.mySlots(groupId),
  })

  useEffect(() => {
    if (data) setInitialLoaded(true)
  }, [data])

  const ownGrid = useMemo(() => buildOwnGrid(mySlots ?? [], monday), [mySlots, monday])
  const aggregate = useMemo(() => buildAggregate(data?.slots ?? [], monday), [data, monday])

  const applySelection = useMutation({
    mutationFn: (plan: SelectionPlan) =>
      applyPlan(
        plan,
        (draft) => availabilityApi.addSlot(groupId, draft),
        (slotId) => availabilityApi.deleteSlot(groupId, slotId),
      ),
    // Обновляем и в случае ошибки: при падении на удалении часть операций уже
    // прошла, и показывать устаревшую картину — значит заставить человека
    // гадать, что применилось.
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['availability', groupId] })
      qc.invalidateQueries({ queryKey: ['heatmap', groupId] })
    },
  })

  const drag = useDragSelection({
    grid: ownGrid.spec,
    slots: mySlots ?? [],
    busy: applySelection.isPending,
    onApply: (plan) => {
      setBlockedNotice(plan.blocked.length > 0)
      applySelection.mutate(plan)
    },
    onBlocked: () => setBlockedNotice(true),
  })

  /**
   * Подсветка на время жеста. Считается по готовой сетке, а не вызовом
   * `selectionToSlots` на каждое движение: тот разбирает даты всех слотов и на
   * протяжке в шестьдесят кадров в секунду обошёлся бы тысячами разборов.
   */
  const highlightAt = (row: number, col: number): DragHighlight | null => {
    if (!drag.range) return null
    const inSelection = drag.range.days.some((d) => d.col === col && row >= d.startRow && row <= d.endRow)
    if (!inSelection) return null
    const state = ownGrid.cells[row][col]
    if (isBlockedState(state)) return 'blocked'
    if (state !== 'free') return 'unchanged'
    return 'create'
  }

  const handles =
    drag.pending && drag.range && drag.range.days.length > 0
      ? {
          start: { row: drag.range.days[0].startRow, col: drag.range.days[0].col },
          end: {
            row: drag.range.days[drag.range.days.length - 1].endRow,
            col: drag.range.days[drag.range.days.length - 1].col,
          },
        }
      : null

  /** Клик по ячейке в режиме встречи: час начиная с этой получасовки. */
  const activateCell = (row: number, col: number) => {
    const start = cellStart(col, row, ownGrid.spec)
    onCreateMeeting({
      startsAt: start.toUTC().toISO()!,
      endsAt: start.plus({ hours: 1 }).toUTC().toISO()!,
      count: aggregate.counts[row][col],
      memberIds: null,
      displayNames: aggregate.names[row][col],
    })
  }

  const meetingMode = isOwner && mode === 'meeting'

  return (
    <div>
      {/* Week navigation */}
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Button variant="secondary" size="sm" onClick={() => onWeekOffsetChange(weekOffset - 1)}>
          {t('group.heatmapTab.prev')}
        </Button>
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          {monday.toFormat('dd MMM')} – {sunday.minus({ days: 1 }).toFormat('dd MMM yyyy')}
        </span>
        <Button variant="secondary" size="sm" onClick={() => onWeekOffsetChange(weekOffset + 1)}>
          {t('group.heatmapTab.next')}
        </Button>
        {weekOffset !== 0 && (
          <Button variant="ghost" size="sm" onClick={() => onWeekOffsetChange(0)}>
            {t('group.heatmapTab.today')}
          </Button>
        )}
        {isLoading && initialLoaded && <Spinner size="sm" />}
      </div>

      {/*
        Переключатель режима — только у владельца: только он умеет создавать
        встречи. Кнопки в 44 пикселя высотой, потому что это первое, чего
        касаются пальцем перед самим жестом.
      */}
      {isOwner && (
        <div className="mb-3 inline-flex rounded-lg border border-gray-200 p-0.5 dark:border-gray-700">
          {(['availability', 'meeting'] as const).map((m) => (
            <button
              key={m}
              onClick={() => setMode(m)}
              className={`min-h-[44px] rounded-md px-3 text-sm font-medium transition-colors ${
                mode === m
                  // gm-600 в обеих темах: на gm-500 белый текст даёт 3.81:1 —
                  // ниже AA. Это поймал palette.test.ts, а не глаз.
                  ? 'bg-gm-600 text-white'
                  : 'text-gray-600 hover:text-gray-900 dark:text-gray-400 dark:hover:text-gray-100'
              }`}
            >
              {t(m === 'availability' ? 'group.heatmapTab.modeAvailability' : 'group.heatmapTab.modeMeeting')}
            </button>
          ))}
        </div>
      )}

      {error && <ErrorMessage error={error} />}

      {isLoading && !initialLoaded ? (
        <Skeleton className="h-96 w-full" />
      ) : (
        !error && (
          <>
            <WeekGrid
              grid={ownGrid}
              aggregate={{
                counts: aggregate.counts,
                max: aggregate.max,
                namesAt: (row, col) => aggregate.names[row][col],
              }}
              highlightAt={meetingMode ? undefined : highlightAt}
              gridProps={meetingMode ? undefined : drag.gridProps}
              handles={meetingMode ? null : handles}
              handleProps={meetingMode ? undefined : drag.handleProps}
              onCellActivate={meetingMode ? activateCell : undefined}
            />

            {/* Панель подтверждения тач-выделения: прилипает к низу, чтобы
                доставаться большим пальцем, и появляется только когда есть что
                подтверждать. */}
            {drag.pending && (
              <div className="sticky bottom-0 z-30 -mx-4 mt-2 flex items-center gap-2 border-t border-gray-200 bg-white px-4 py-3 dark:border-gray-700 dark:bg-gray-800 sm:mx-0 sm:rounded-xl sm:border">
                <span className="mr-auto text-sm text-gray-700 dark:text-gray-300">
                  {t('group.availabilityTab.grid.selectedCells', { count: drag.range?.cellCount ?? 0 })}
                </span>
                <Button variant="secondary" size="sm" onClick={drag.cancel} className="min-h-[44px]">
                  {t('group.availabilityTab.grid.cancel')}
                </Button>
                <Button size="sm" onClick={drag.commit} className="min-h-[44px]">
                  {t('group.availabilityTab.grid.confirm')}
                </Button>
              </div>
            )}

            <p className="mt-2 text-xs text-gray-500 dark:text-gray-400">
              {t(meetingMode ? 'group.heatmapTab.meetingHint' : 'group.availabilityTab.grid.dragHint')}
            </p>
            {blockedNotice && (
              <p className="mt-1 text-xs text-gm-600 dark:text-gm-400">
                {t('group.availabilityTab.grid.blockedNotice')}
              </p>
            )}
            {applySelection.error && <ErrorMessage error={applySelection.error} />}

            {/*
              Пустая сетка раньше сообщала «Нет доступности на эту неделю» —
              констатация, из которой не следует ни одного действия. Человек,
              впервые открывший группу, видит её чаще всех остальных.
            */}
            {aggregate.max === 0 && (
              <div className="mt-4 flex flex-col items-start gap-2">
                <p className="text-base font-medium text-gray-900 dark:text-gray-100">
                  {t('group.heatmapTab.emptyTitle')}
                </p>
                <p className="text-sm text-gray-500 dark:text-gray-400">
                  {t(isOwner ? 'group.heatmapTab.emptyHintOwner' : 'group.heatmapTab.emptyHintMember')}
                </p>
                {onSetMyTime && (
                  <Button onClick={onSetMyTime} size="sm" className="mt-1">
                    {t('group.setMyTime')}
                  </Button>
                )}
              </div>
            )}
          </>
        )
      )}
    </div>
  )
}
