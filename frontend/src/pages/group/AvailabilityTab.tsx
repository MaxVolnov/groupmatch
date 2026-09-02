import { useEffect, useRef, useState } from 'react'
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
import { slotsInClearWindow, toSeriesRequest, type ClearWindow, type SeriesRule } from '@/utils/series'
import { SeriesForm } from './SeriesForm'
import { BulkClearForm, type ClearPreview } from './BulkClearForm'
import type { AvailabilityResponse } from '@/types'
import { AxiosError } from 'axios'
import { DAY_OF_WEEK_NAMES as DAY_NAMES } from '@/utils/series'

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

export function AvailabilityTab({ groupId, callerPlan, focusRequest }: Props) {
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
  /** Что открыто рядом с формой одиночного слота: серия или очистка. */
  const [panel, setPanel] = useState<'none' | 'series' | 'clear'>('none')
  const [clearPreview, setClearPreview] = useState<ClearPreview | null>(null)
  /** Слот, для которого спрашиваем «этот или всю серию». */
  const [confirmSeries, setConfirmSeries] = useState<AvailabilityResponse | null>(null)

  /**
   * Зона, в которой считаются серии и очистка. Та же, что у сетки и у формы
   * одиночного слота, — зона машины пользователя.
   */
  const timeZone = DateTime.local().zoneName

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

  const invalidate = () => {
    qc.invalidateQueries({ queryKey: ['availability', groupId] })
    qc.invalidateQueries({ queryKey: ['heatmap', groupId] })
  }

  const addSeries = useMutation({
    mutationFn: (rule: SeriesRule) =>
      availabilityApi.addSeries(groupId, toSeriesRequest(rule, timeZone)),
    onSuccess: () => {
      invalidate()
      setPanel('none')
    },
  })

  /** Удаление с областью действия: только этот слот или вся его серия. */
  const delScoped = useMutation({
    mutationFn: ({ slotId, scope }: { slotId: string; scope: 'single' | 'series' }) =>
      availabilityApi.deleteSlotScoped(slotId, scope),
    onSuccess: () => {
      invalidate()
      setConfirmSeries(null)
    },
  })

  const clear = useMutation({
    mutationFn: ({ window, dryRun }: { window: ClearWindow; dryRun: boolean }) =>
      availabilityApi.bulkClear(groupId, {
        daysOfWeek: window.daysOfWeek.map((d) => DAY_NAMES[d - 1]),
        startTime: window.startTime,
        endTime: window.endTime,
        fromDate: window.fromDate,
        toDate: window.toDate,
        timeZone,
        dryRun,
      }),
  })

  /**
   * Предпросмотр очистки. Число показывается **серверное** — оно и есть
   * правда; локальный расчёт нужен только чтобы сказать, попали ли под окно
   * слоты серий: сервер отвечает одним числом и о принадлежности молчит.
   */
  const previewClear = (window: ClearWindow) => {
    clear.mutate(
      { window, dryRun: true },
      {
        onSuccess: (res) =>
          setClearPreview({
            count: res.deletedCount,
            includesSeries: slotsInClearWindow(slots ?? [], window, timeZone).some((s) => !!s.seriesId),
          }),
      },
    )
  }

  const confirmClear = (window: ClearWindow) => {
    clear.mutate(
      { window, dryRun: false },
      {
        onSuccess: () => {
          invalidate()
          setClearPreview(null)
          setPanel('none')
        },
      },
    )
  }

  /** Сколько слотов в серии этого слота — для подтверждения «удалить всю». */
  const seriesSize = (slot: AvailabilityResponse) =>
    (slots ?? []).filter((s) => s.seriesId && s.seriesId === slot.seriesId).length

  const planLimits: Record<Plan, number> = { FREE: 50, PRO: 200, TEAM: 500 }
  const limit = planLimits[callerPlan]
  const count = slots?.length ?? 0

  if (error) return <ErrorMessage error={error} />

  return (
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

        {/*
          Серия и очистка — рядом с формой одиночного слота, а не отдельной
          вкладкой: это три способа сделать одно и то же, и разносить их по
          экранам значит повторять ошибку, из-за которой сеток стало две.
        */}
        <div className="mt-4 flex flex-wrap gap-2 border-t border-gray-200 pt-4 dark:border-gray-700">
          <Button
            variant={panel === 'series' ? 'primary' : 'secondary'}
            size="sm"
            onClick={() => setPanel(panel === 'series' ? 'none' : 'series')}
          >
            {t('group.availabilityTab.series.open')}
          </Button>
          <Button
            variant={panel === 'clear' ? 'primary' : 'secondary'}
            size="sm"
            onClick={() => {
              setPanel(panel === 'clear' ? 'none' : 'clear')
              setClearPreview(null)
            }}
          >
            {t('group.availabilityTab.clear.open')}
          </Button>
        </div>

        {panel === 'series' && (
          <div className="mt-4">
            <SeriesForm
              submitting={addSeries.isPending}
              onSubmit={(rule) => addSeries.mutate(rule)}
              onCancel={() => setPanel('none')}
              error={
                addSeries.error ? (
                  // 402 — это тариф, а не сбой. Бэкенд отвечает техническим
                  // английским текстом («Plan limit reached: max 50 slots…»),
                  // и показывать его человеку значит объяснять ему устройство
                  // сервера вместо его собственной ситуации.
                  (addSeries.error as AxiosError).response?.status === 402 ? (
                    <p className="text-sm text-red-600 dark:text-red-400">
                      {t('group.availabilityTab.series.planLimit', { limit })}
                    </p>
                  ) : (
                    <ErrorMessage error={addSeries.error} />
                  )
                ) : null
              }
            />
          </div>
        )}

        {panel === 'clear' && (
          <div className="mt-4">
            <BulkClearForm
              preview={clearPreview}
              busy={clear.isPending}
              error={clear.error ? <ErrorMessage error={clear.error} /> : null}
              onPreview={previewClear}
              onConfirm={confirmClear}
              onReset={() => setClearPreview(null)}
            />
          </div>
        )}
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
                    {s.seriesId && (
                      <span className="mt-0.5 inline-block rounded bg-gm-100 px-1.5 py-0.5 text-[11px] font-medium text-gm-700 dark:bg-gm-900 dark:text-gm-300">
                        {t('group.availabilityTab.series.badge')}
                      </span>
                    )}
                    {s.note && <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">{s.note}</p>}
                  </div>
                  {/*
                    Крестик у слота серии удаляет только его. Всю серию — через
                    отдельное подтверждение с числом: случайно снести двадцать
                    слотов одним нажатием нельзя, случайно снести один — терпимо,
                    он и раньше так удалялся.
                  */}
                  <div className="ml-2 flex shrink-0 items-center">
                    {s.seriesId && (
                      <button
                        className="flex min-h-[44px] items-center justify-center px-2 text-xs font-medium text-gm-600 hover:underline dark:text-gm-400"
                        onClick={() => setConfirmSeries(s)}
                      >
                        {t('group.availabilityTab.series.deleteAll')}
                      </button>
                    )}
                    <button
                      className="flex min-h-[44px] min-w-[44px] items-center justify-center text-gray-300 transition-colors hover:text-red-500 dark:text-gray-600 dark:hover:text-red-400"
                      onClick={() => del.mutate(s.id)}
                      title={t('group.availabilityTab.delete')}
                    >
                      ✕
                    </button>
                  </div>
                </div>
              ))}
            </div>
          </>
        )}

        {confirmSeries && (
          <div className="mt-3 flex flex-col gap-2 rounded-lg border border-red-200 bg-red-50 p-3 dark:border-red-800 dark:bg-red-900/20">
            <p className="text-sm font-medium text-red-700 dark:text-red-400">
              {t('group.availabilityTab.series.confirmAll', { count: seriesSize(confirmSeries) })}
            </p>
            {delScoped.error && <ErrorMessage error={delScoped.error} />}
            <div className="flex flex-wrap gap-2">
              <Button
                variant="danger"
                size="sm"
                loading={delScoped.isPending}
                className="min-h-[44px] justify-center"
                onClick={() => delScoped.mutate({ slotId: confirmSeries.id, scope: 'series' })}
              >
                {t('group.availabilityTab.series.confirmAllYes')}
              </Button>
              <Button
                variant="secondary"
                size="sm"
                className="min-h-[44px] justify-center"
                onClick={() => setConfirmSeries(null)}
              >
                {t('group.availabilityTab.grid.cancel')}
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
