import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { DateTime } from 'luxon'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import type { ClearWindow } from '@/utils/series'
import { DayOfWeekPicker } from './DayOfWeekPicker'

/**
 * Форма массовой очистки.
 *
 * Двухшаговая по построению: сначала посчитать, потом удалить. Бэкенд умеет
 * `dryRun`, и не воспользоваться им значило бы предложить человеку удалить
 * неизвестно сколько своего времени одним нажатием.
 *
 * Сеть снаружи, как и у формы серии: компонент зовёт `onPreview`, получает
 * число сверху через `preview` и показывает подтверждение.
 */

export interface ClearPreview {
  /** Число от сервера — оно и есть правда. */
  count: number
  /** Попали ли под окно слоты серий. Очистка их не щадит. */
  includesSeries: boolean
}

interface Props {
  initial?: Partial<ClearWindow>
  /** Результат `dryRun`. `null` — ещё не считали. */
  preview: ClearPreview | null
  busy?: boolean
  error?: React.ReactNode
  onPreview: (window: ClearWindow) => void
  onConfirm: (window: ClearWindow) => void
  /** Сбросить предпросмотр: правило изменилось, число устарело. */
  onReset: () => void
}

function defaults(): ClearWindow {
  const today = DateTime.now()
  return {
    daysOfWeek: [today.weekday],
    startTime: '10:00',
    endTime: '14:00',
    fromDate: today.toISODate()!,
    toDate: today.plus({ weeks: 4 }).toISODate()!,
  }
}

export function BulkClearForm({ initial, preview, busy, error, onPreview, onConfirm, onReset }: Props) {
  const { t } = useTranslation()
  const [win, setWin] = useState<ClearWindow>({ ...defaults(), ...initial })

  // Любая правка обесценивает посчитанное число: подтверждать по устаревшему
  // предпросмотру — это удалять не то, что показали.
  const patch = (part: Partial<ClearWindow>) => {
    setWin((w) => ({ ...w, ...part }))
    if (preview) onReset()
  }

  const valid =
    win.daysOfWeek.length > 0 &&
    win.startTime < win.endTime &&
    win.fromDate <= win.toDate

  return (
    <div className="flex flex-col gap-3">
      <div className="grid gap-3 sm:grid-cols-2">
        <Input
          label={t('group.availabilityTab.series.fromDate')}
          type="date"
          value={win.fromDate}
          onChange={(e) => patch({ fromDate: e.target.value })}
        />
        <Input
          label={t('group.availabilityTab.series.toDate')}
          type="date"
          value={win.toDate}
          onChange={(e) => patch({ toDate: e.target.value })}
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {t('group.availabilityTab.series.days')}
        </label>
        <DayOfWeekPicker value={win.daysOfWeek} onChange={(daysOfWeek) => patch({ daysOfWeek })} disabled={busy} />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Input
          label={t('group.availabilityTab.from')}
          type="time"
          value={win.startTime}
          onChange={(e) => patch({ startTime: e.target.value })}
        />
        <Input
          label={t('group.availabilityTab.to')}
          type="time"
          value={win.endTime}
          onChange={(e) => patch({ endTime: e.target.value })}
        />
      </div>

      {/*
        Правило частичного пересечения — строкой в интерфейсе, а не в тултипе.
        Человек, у которого слот 9:00–15:00 не удалился при очистке 10:00–14:00,
        должен понять почему сразу, а не через обращение в поддержку.
      */}
      <p className="text-xs text-gray-500 dark:text-gray-400">
        {t('group.availabilityTab.clear.partialRule')}
      </p>

      {error}

      {preview === null ? (
        <Button
          variant="secondary"
          loading={busy}
          disabled={!valid}
          onClick={() => onPreview(win)}
          className="min-h-[44px] justify-center"
        >
          {t('group.availabilityTab.clear.preview')}
        </Button>
      ) : preview.count === 0 ? (
        <p className="text-sm text-gray-600 dark:text-gray-400">
          {t('group.availabilityTab.clear.nothing')}
        </p>
      ) : (
        <div className="flex flex-col gap-2 rounded-lg border border-red-200 bg-red-50 p-3 dark:border-red-800 dark:bg-red-900/20">
          <p className="text-sm font-medium text-red-700 dark:text-red-400">
            {t('group.availabilityTab.clear.willDelete', { count: preview.count })}
          </p>
          {preview.includesSeries && (
            <p className="text-xs text-red-700 dark:text-red-400">
              {t('group.availabilityTab.clear.seriesIncluded')}
            </p>
          )}
          <div className="flex flex-wrap gap-2">
            <Button
              variant="danger"
              loading={busy}
              onClick={() => onConfirm(win)}
              className="min-h-[44px] justify-center"
            >
              {t('group.availabilityTab.clear.confirm')}
            </Button>
            <Button variant="secondary" onClick={onReset} className="min-h-[44px] justify-center">
              {t('group.availabilityTab.grid.cancel')}
            </Button>
          </div>
        </div>
      )}
    </div>
  )
}
