import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { availabilityApi } from '@/api/availability'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Skeleton } from '@/components/Skeleton'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { Plan } from '@/types'
import { DateTime } from 'luxon'

interface Props {
  groupId: string
  callerPlan: Plan
}

function toIso(local: string): string {
  return DateTime.fromISO(local, { zone: 'local' }).toUTC().toISO()!
}

function fmtRange(startsAt: string, endsAt: string): string {
  const s = DateTime.fromISO(startsAt).toLocal()
  const e = DateTime.fromISO(endsAt).toLocal()
  if (s.hasSame(e, 'day')) {
    return `${s.toFormat('dd MMM yyyy, HH:mm')} – ${e.toFormat('HH:mm')}`
  }
  return `${s.toFormat('dd MMM HH:mm')} – ${e.toFormat('dd MMM HH:mm')}`
}

function defaultDatetime(offsetHours: number): string {
  return DateTime.now().plus({ hours: offsetHours }).toFormat("yyyy-MM-dd'T'HH:mm")
}

function SlotSkeletonList() {
  return (
    <div className="flex flex-col gap-2">
      {Array.from({ length: 3 }).map((_, i) => (
        <div key={i} className="flex items-start justify-between rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-3">
          <div className="flex-1">
            <Skeleton className="h-4 w-3/4 mb-1" />
            <Skeleton className="h-3 w-1/3" />
          </div>
          <Skeleton className="ml-2 h-8 w-8 shrink-0" />
        </div>
      ))}
    </div>
  )
}

export function AvailabilityTab({ groupId, callerPlan }: Props) {
  const qc = useQueryClient()
  const [startsAt, setStartsAt] = useState(defaultDatetime(1))
  const [endsAt, setEndsAt] = useState(defaultDatetime(2))
  const [note, setNote] = useState('')
  const [formError, setFormError] = useState('')

  const onStartsAtChange = (value: string) => {
    setStartsAt(value)
    const newStart = DateTime.fromISO(value)
    const currentEnd = DateTime.fromISO(endsAt)
    if (!currentEnd.isValid || currentEnd <= newStart) {
      setEndsAt(newStart.plus({ hours: 1 }).toFormat("yyyy-MM-dd'T'HH:mm"))
    }
  }

  const { data: slots, isLoading, error } = useQuery({
    queryKey: ['availability', groupId],
    queryFn: () => availabilityApi.mySlots(groupId),
  })

  const add = useMutation({
    mutationFn: () => {
      setFormError('')
      const s = DateTime.fromISO(toIso(startsAt))
      const e = DateTime.fromISO(toIso(endsAt))
      if (e.toMillis() <= s.toMillis()) { setFormError('End must be after start'); return Promise.reject() }
      return availabilityApi.addSlot(groupId, {
        startsAt: toIso(startsAt),
        endsAt: toIso(endsAt),
        note: note || undefined,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['availability', groupId] })
      setNote('')
    },
  })

  const del = useMutation({
    mutationFn: (slotId: string) => availabilityApi.deleteSlot(groupId, slotId),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['availability', groupId] }),
  })

  const planLimits: Record<Plan, number> = { FREE: 50, PRO: 200, TEAM: 500 }
  const limit = planLimits[callerPlan]
  const count = slots?.length ?? 0

  if (error) return <ErrorMessage error={error} />

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      {/* Add slot form */}
      <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-5">
        <h3 className="mb-4 font-medium text-gray-900 dark:text-gray-100">Add availability</h3>
        <div className="flex flex-col gap-3">
          <Input
            label="From"
            type="datetime-local"
            value={startsAt}
            onChange={(e) => onStartsAtChange(e.target.value)}
          />
          <Input
            label="To"
            type="datetime-local"
            value={endsAt}
            onChange={(e) => setEndsAt(e.target.value)}
          />
          <Input
            label="Note (optional)"
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="e.g. Preferred"
            maxLength={200}
          />
          {formError && <p className="text-sm text-red-600 dark:text-red-400">{formError}</p>}
          {add.error && <ErrorMessage error={add.error} />}
          <Button
            loading={add.isPending}
            onClick={() => add.mutate()}
            className="mt-1 justify-center w-full"
          >
            Add slot
          </Button>
          <p className="text-xs text-gray-400 dark:text-gray-500 text-center">
            {count} / {limit} slots used
          </p>
        </div>
      </div>

      {/* My slots list */}
      <div>
        <h3 className="mb-3 font-medium text-gray-900 dark:text-gray-100">My slots ({count})</h3>
        {isLoading ? (
          <SlotSkeletonList />
        ) : (
          <>
            {count === 0 && (
              <p className="text-sm text-gray-500 dark:text-gray-400">🕐 No slots yet. Add your available times.</p>
            )}
            <div className="flex flex-col gap-2">
              {slots?.map((s) => (
                <div
                  key={s.id}
                  className="flex items-start justify-between rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-3"
                >
                  <div>
                    <p className="text-sm font-medium text-gray-900 dark:text-gray-100">
                      {fmtRange(s.startsAt, s.endsAt)}
                    </p>
                    {s.note && <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{s.note}</p>}
                  </div>
                  <button
                    className="ml-2 shrink-0 flex items-center justify-center min-h-[44px] min-w-[44px] text-gray-300 dark:text-gray-600 hover:text-red-500 dark:hover:text-red-400 transition-colors"
                    onClick={() => del.mutate(s.id)}
                    title="Delete"
                  >
                    ✕
                  </button>
                </div>
              ))}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
