import { useState, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { availabilityApi } from '@/api/availability'
import { Button } from '@/components/Button'
import { Spinner } from '@/components/Spinner'
import { Skeleton } from '@/components/Skeleton'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { HeatmapSlot } from '@/types'
import { DateTime } from 'luxon'

interface Props {
  groupId: string
  isOwner: boolean
  onCreateMeeting: (slot: HeatmapSlot) => void
  /** Увести на вкладку доступности из пустого состояния. */
  onSetMyTime?: () => void
}

const DAY_KEYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']

function intensityClass(count: number, max: number): string {
  if (count === 0 || max === 0) return 'bg-gray-100 dark:bg-gray-700'
  const ratio = count / max
  if (ratio >= 0.8) return 'bg-green-500 dark:bg-green-500'
  if (ratio >= 0.6) return 'bg-green-400 dark:bg-green-600'
  if (ratio >= 0.4) return 'bg-green-300 dark:bg-green-700'
  if (ratio >= 0.2) return 'bg-green-200 dark:bg-green-800'
  return 'bg-green-100 dark:bg-green-900'
}

function buildGrid(slots: HeatmapSlot[], from: DateTime): {
  grid: (HeatmapSlot | null)[][]
  timeLabels: string[]
  maxCount: number
} {
  const grid: (HeatmapSlot | null)[][] = Array.from({ length: 48 }, () =>
    Array(7).fill(null),
  )

  let maxCount = 0

  for (const slot of slots) {
    const s = DateTime.fromISO(slot.startsAt).toLocal()
    const dayIndex = ((s.weekday - 1) % 7 + 7) % 7
    const weekDayOfSlot = from.plus({ days: dayIndex })
    if (!s.hasSame(weekDayOfSlot, 'day')) continue

    const bucketIndex = Math.floor((s.hour * 60 + s.minute) / 30)
    if (bucketIndex >= 0 && bucketIndex < 48) {
      grid[bucketIndex][dayIndex] = slot
      if (slot.count > maxCount) maxCount = slot.count
    }
  }

  const timeLabels = Array.from({ length: 48 }, (_, i) => {
    const h = Math.floor(i / 2)
    const m = i % 2 === 0 ? '00' : '30'
    return i % 4 === 0 ? `${String(h).padStart(2, '0')}:${m}` : ''
  })

  return { grid, timeLabels, maxCount }
}

export function HeatmapTab({ groupId, isOwner, onCreateMeeting, onSetMyTime}: Props) {
  const { t } = useTranslation()
  const [weekOffset, setWeekOffset] = useState(0)
  const [initialLoaded, setInitialLoaded] = useState(false)

  const monday = DateTime.now().startOf('week').plus({ weeks: weekOffset })
  const sunday = monday.plus({ days: 7 })

  const { data, isLoading, error } = useQuery({
    queryKey: ['heatmap', groupId, weekOffset],
    queryFn: () =>
      availabilityApi.heatmap(
        groupId,
        monday.toUTC().toISO()!,
        sunday.toUTC().toISO()!,
        30,
      ),
  })

  useEffect(() => {
    if (data) setInitialLoaded(true)
  }, [data])

  const { grid, timeLabels, maxCount } = data
    ? buildGrid(data.slots, monday)
    : { grid: Array.from({ length: 48 }, () => Array(7).fill(null)), timeLabels: Array(48).fill(''), maxCount: 0 }

  return (
    <div>
      {/* Week navigation */}
      <div className="mb-4 flex flex-wrap items-center gap-2">
        <Button variant="secondary" size="sm" onClick={() => setWeekOffset((w) => w - 1)}>
          {t('group.heatmapTab.prev')}
        </Button>
        <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
          {monday.toFormat('dd MMM')} – {sunday.minus({ days: 1 }).toFormat('dd MMM yyyy')}
        </span>
        <Button variant="secondary" size="sm" onClick={() => setWeekOffset((w) => w + 1)}>
          {t('group.heatmapTab.next')}
        </Button>
        {weekOffset !== 0 && (
          <Button variant="ghost" size="sm" onClick={() => setWeekOffset(0)}>
            {t('group.heatmapTab.today')}
          </Button>
        )}
        {isLoading && initialLoaded && <Spinner size="sm" />}
      </div>

      {error && <ErrorMessage error={error} />}

      {isLoading && !initialLoaded ? (
        <Skeleton className="h-96 w-full" />
      ) : !error && (
        <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 -mx-4 sm:mx-0">
          <table className="min-w-full border-collapse text-xs">
            <thead>
              <tr>
                <th className="w-10 border-b border-r border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-700 py-2 px-1 text-right text-gray-400 dark:text-gray-500 font-normal sticky left-0 z-10" />
                {DAY_KEYS.map((dayKey, i) => (
                  <th
                    key={dayKey}
                    className="min-w-[40px] border-b border-r border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-700 py-2 px-1 font-medium text-gray-700 dark:text-gray-300 text-center"
                  >
                    <div>{t(`group.heatmapTab.day.${dayKey}`)}</div>
                    <div className="text-gray-400 dark:text-gray-500 font-normal">
                      {monday.plus({ days: i }).toFormat('dd/MM')}
                    </div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {grid.map((row, rowIdx) => (
                <tr key={rowIdx} className="h-4">
                  <td className="border-b border-r border-gray-100 dark:border-gray-700/50 px-1 text-right text-gray-400 dark:text-gray-500 align-top leading-4 sticky left-0 bg-white dark:bg-gray-800 z-10 min-w-[40px]">
                    {timeLabels[rowIdx]}
                  </td>
                  {row.map((slot, colIdx) => {
                    const isClickable = slot !== null && slot.count > 0 && isOwner
                    const baseClass = 'min-w-[40px] border-b border-r border-gray-100 dark:border-gray-700/30 transition-colors'
                    const colorClass = slot ? intensityClass(slot.count, maxCount) : 'bg-white dark:bg-gray-800'
                    const interactClass = isClickable
                      ? 'cursor-pointer hover:ring-2 hover:ring-gm-400 hover:ring-inset'
                      : 'cursor-default'
                    const titleText = slot
                      ? `${slot.count} ${t('group.heatmapTab.available')}${slot.displayNames ? ': ' + slot.displayNames.join(', ') : ''}${isClickable ? ` ${t('group.heatmapTab.clickToSchedule')}` : ''}`
                      : ''

                    return (
                      <td
                        key={colIdx}
                        title={titleText}
                        className={`${baseClass} ${colorClass} ${interactClass}`}
                        onClick={isClickable ? () => onCreateMeeting(slot) : undefined}
                      />
                    )
                  })}
                </tr>
              ))}
            </tbody>
          </table>
          {maxCount > 0 && (
            <div className="flex flex-wrap items-center gap-2 px-4 py-2 text-xs text-gray-500 dark:text-gray-400">
              <span>0</span>
              {[0.2, 0.4, 0.6, 0.8, 1].map((r) => (
                <span
                  key={r}
                  className={`inline-block h-3 w-6 rounded ${intensityClass(Math.ceil(r * maxCount), maxCount)}`}
                />
              ))}
              <span>{maxCount} {t('group.heatmapTab.members')}</span>
            </div>
          )}
          {/*
            Пустая сетка раньше сообщала «Нет доступности на эту неделю» —
            констатация, из которой не следует ни одного действия. Человек,
            впервые открывший группу, видит её чаще всех остальных: до него в
            группе обычно вообще никто ничего не отмечал.

            Подсказка разная по роли, потому что и следующий шаг разный:
            участнику нужно отметить своё время, владельцу — позвать людей,
            которым будет что отмечать. Кнопка при этом одна и та же: своё
            время указывает и владелец тоже.
          */}
          {maxCount === 0 && (
            <div className="flex flex-col items-start gap-2 px-4 py-6">
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
        </div>
      )}
    </div>
  )
}
