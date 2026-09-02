import { useEffect, useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { availabilityApi } from '@/api/availability'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Skeleton } from '@/components/Skeleton'
import { ErrorMessage } from '@/components/ErrorMessage'
import type { Plan } from '@/types'
import { DateTime } from 'luxon'
import { defaultDatetime, fmtRange, toIso } from '@/utils/datetime'
import { buildOwnGrid } from '@/utils/ownGrid'
import { MyAvailabilityGrid } from './MyAvailabilityGrid'

interface Props {
  groupId: string
  callerPlan: Plan
  /**
   * Счётчик-триггер: при каждом изменении форма добавления слота получает
   * фокус и подскроллена в вид.
   *
   * Именно счётчик, а не boolean: кнопка «Указать своё время» может быть нажата
   * повторно, уже находясь на этой вкладке, и флаг во второй раз не изменился
   * бы — эффект не сработал, и нажатие осталось бы без ответа.
   *
   * Сама форма всегда отрисована и никогда не скрыта, поэтому «открыть» её —
   * это довести до неё каретку, а не показать.
   */
  focusRequest?: number
  /**
   * Смещение недели в неделях от текущей. Общее с теплокартой, поднято в
   * GroupPage — см. комментарий там.
   */
  weekOffset: number
  onWeekOffsetChange: (next: number) => void
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

export function AvailabilityTab({ groupId, callerPlan, focusRequest, weekOffset, onWeekOffsetChange }: Props) {
  const addFormRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!focusRequest) return
    const form = addFormRef.current
    if (!form) return
    form.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
    // Первое поле формы — «с какого времени». Ref прокидывать некуда: Input
    // не форвардит его наружу, а трогать общий компонент ради одной вкладки
    // дороже, чем спросить у собственного контейнера.
    form.querySelector('input')?.focus()
  }, [focusRequest])

  const { t } = useTranslation()
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
      if (e.toMillis() <= s.toMillis()) { setFormError(t('group.availabilityTab.endAfterStart')); return Promise.reject() }
      return availabilityApi.addSlot(groupId, {
        startsAt: toIso(startsAt),
        endsAt: toIso(endsAt),
        note: note || undefined,
      })
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['availability', groupId] })
      qc.invalidateQueries({ queryKey: ['heatmap', groupId] })
      setNote('')
    },
  })

  const del = useMutation({
    mutationFn: (slotId: string) => availabilityApi.deleteSlot(groupId, slotId),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['availability', groupId] })
      qc.invalidateQueries({ queryKey: ['heatmap', groupId] })
    },
  })

  const planLimits: Record<Plan, number> = { FREE: 50, PRO: 200, TEAM: 500 }
  const limit = planLimits[callerPlan]
  const count = slots?.length ?? 0

  /**
   * Понедельник показываемой недели и раскладка слотов по ячейкам. Тот же
   * queryKey, что у списка ниже, — сетка данных не запрашивает, она их
   * переиспользует.
   */
  const weekStart = DateTime.now().startOf('week').plus({ weeks: weekOffset })
  const ownGrid = useMemo(() => buildOwnGrid(slots ?? [], weekStart), [slots, weekStart])

  if (error) return <ErrorMessage error={error} />

  return (
    <div className="flex flex-col gap-6">
      {/* Сетка своего времени. Только показывает: протяжка — следующий заход. */}
      <div>
        <h3 className="mb-2 font-medium text-gray-900 dark:text-gray-100">
          {t('group.availabilityTab.grid.title')}
        </h3>
        {/* Навигация своей строкой: на 375px заголовок рядом с тремя кнопками
            переносился так, что «След. →» оставалась одна на строке и читалась
            как сбой вёрстки. */}
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <Button variant="secondary" size="sm" onClick={() => onWeekOffsetChange(weekOffset - 1)}>
            {t('group.heatmapTab.prev')}
          </Button>
          <span className="text-sm font-medium text-gray-700 dark:text-gray-300">
            {weekStart.toFormat('dd MMM')} – {weekStart.plus({ days: 6 }).toFormat('dd MMM yyyy')}
          </span>
          <Button variant="secondary" size="sm" onClick={() => onWeekOffsetChange(weekOffset + 1)}>
            {t('group.heatmapTab.next')}
          </Button>
          {weekOffset !== 0 && (
            <Button variant="ghost" size="sm" onClick={() => onWeekOffsetChange(0)}>
              {t('group.heatmapTab.today')}
            </Button>
          )}
        </div>
        {isLoading ? <Skeleton className="h-96 w-full" /> : <MyAvailabilityGrid grid={ownGrid} />}
      </div>

    <div className="grid gap-6 lg:grid-cols-2">
      {/* Add slot form */}
      <div ref={addFormRef} className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-5">
        <h3 className="mb-4 font-medium text-gray-900 dark:text-gray-100">{t('group.availabilityTab.addAvailability')}</h3>
        <div className="flex flex-col gap-3">
          <Input
            label={t('group.availabilityTab.from')}
            type="datetime-local"
            value={startsAt}
            onChange={(e) => onStartsAtChange(e.target.value)}
          />
          <Input
            label={t('group.availabilityTab.to')}
            type="datetime-local"
            value={endsAt}
            onChange={(e) => setEndsAt(e.target.value)}
          />
          <Input
            label={t('group.availabilityTab.note')}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder={t('group.availabilityTab.notePlaceholder')}
            maxLength={200}
          />
          {formError && <p className="text-sm text-red-600 dark:text-red-400">{formError}</p>}
          {add.error && <ErrorMessage error={add.error} />}
          <Button
            loading={add.isPending}
            onClick={() => add.mutate()}
            className="mt-1 justify-center w-full"
          >
            {t('group.availabilityTab.addSlot')}
          </Button>
          <p className="text-xs text-gray-400 dark:text-gray-500 text-center">
            {count} / {limit} {t('group.availabilityTab.slotsUsed')}
          </p>
        </div>
      </div>

      {/* My slots list */}
      <div>
        <h3 className="mb-3 font-medium text-gray-900 dark:text-gray-100">{t('group.availabilityTab.mySlots')} ({count})</h3>
        {isLoading ? (
          <SlotSkeletonList />
        ) : (
          <>
            {count === 0 && (
              <p className="text-sm text-gray-500 dark:text-gray-400">{t('group.availabilityTab.noSlots')}</p>
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
                    title={t('group.availabilityTab.delete')}
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
    </div>
  )
}
