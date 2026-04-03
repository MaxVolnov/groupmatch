import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { availabilityApi } from '@/api/availability'
import { Button } from '@/components/Button'
import { Spinner } from '@/components/Spinner'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { HeatmapSlot } from '@/types'
import { DateTime } from 'luxon'

interface Props {
  groupId: string
}

const DAYS = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

// Bucket intensity: 0 = no coverage, max = full green
function intensityClass(count: number, max: number): string {
  if (count === 0 || max === 0) return 'bg-gray-100'
  const ratio = count / max
  if (ratio >= 0.8) return 'bg-green-500'
  if (ratio >= 0.6) return 'bg-green-400'
  if (ratio >= 0.4) return 'bg-green-300'
  if (ratio >= 0.2) return 'bg-green-200'
  return 'bg-green-100'
}

function buildGrid(slots: HeatmapSlot[], from: DateTime): {
  grid: (HeatmapSlot | null)[][]
  timeLabels: string[]
  maxCount: number
} {
  // 7 days × 48 half-hour buckets
  const grid: (HeatmapSlot | null)[][] = Array.from({ length: 48 }, () =>
    Array(7).fill(null),
  )

  let maxCount = 0

  for (const slot of slots) {
    const s = DateTime.fromISO(slot.startsAt).toLocal()
    const dayIndex = ((s.weekday - 1) % 7 + 7) % 7 // Mon=0 … Sun=6
    // relative to monday of the week
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

export function HeatmapTab({ groupId }: Props) {
  const [weekOffset, setWeekOffset] = useState(0)

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

  const { grid, timeLabels, maxCount } = data
    ? buildGrid(data.slots, monday)
    : { grid: Array.from({ length: 48 }, () => Array(7).fill(null)), timeLabels: Array(48).fill(''), maxCount: 0 }

  return (
    <div>
      {/* Week navigation */}
      <div className="mb-4 flex items-center gap-3">
        <Button variant="secondary" size="sm" onClick={() => setWeekOffset((w) => w - 1)}>
          ← Prev
        </Button>
        <span className="text-sm font-medium text-gray-700">
          {monday.toFormat('dd MMM')} – {sunday.minus({ days: 1 }).toFormat('dd MMM yyyy')}
        </span>
        <Button variant="secondary" size="sm" onClick={() => setWeekOffset((w) => w + 1)}>
          Next →
        </Button>
        {weekOffset !== 0 && (
          <Button variant="ghost" size="sm" onClick={() => setWeekOffset(0)}>
            Today
          </Button>
        )}
      </div>

      {isLoading && <div className="flex justify-center py-8"><Spinner /></div>}
      {error && <ErrorMessage error={error} />}

      {!isLoading && !error && (
        <div className="overflow-x-auto rounded-xl border bg-white">
          <table className="min-w-full border-collapse text-xs">
            <thead>
              <tr>
                <th className="w-12 border-b border-r bg-gray-50 py-2 px-1 text-right text-gray-400 font-normal" />
                {DAYS.map((d, i) => (
                  <th
                    key={d}
                    className="border-b border-r bg-gray-50 py-2 px-2 font-medium text-gray-700"
                  >
                    <div>{d}</div>
                    <div className="text-gray-400 font-normal">
                      {monday.plus({ days: i }).toFormat('dd/MM')}
                    </div>
                  </th>
                ))}
              </tr>
            </thead>
            <tbody>
              {grid.map((row, rowIdx) => (
                <tr key={rowIdx} className="h-4">
                  <td className="border-b border-r px-1 text-right text-gray-400 align-top leading-4">
                    {timeLabels[rowIdx]}
                  </td>
                  {row.map((slot, colIdx) => (
                    <td
                      key={colIdx}
                      title={slot ? `${slot.count} available${slot.displayNames ? ': ' + slot.displayNames.join(', ') : ''}` : ''}
                      className={`border-b border-r cursor-default transition-colors ${
                        slot ? intensityClass(slot.count, maxCount) : 'bg-white'
                      }`}
                    />
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
          {maxCount > 0 && (
            <div className="flex items-center gap-2 px-4 py-2 text-xs text-gray-500">
              <span>0</span>
              {[0.2, 0.4, 0.6, 0.8, 1].map((r) => (
                <span
                  key={r}
                  className={`inline-block h-3 w-6 rounded ${intensityClass(Math.ceil(r * maxCount), maxCount)}`}
                />
              ))}
              <span>{maxCount} members</span>
            </div>
          )}
          {maxCount === 0 && (
            <p className="px-4 py-4 text-sm text-gray-500">No availability for this week.</p>
          )}
        </div>
      )}
    </div>
  )
}
