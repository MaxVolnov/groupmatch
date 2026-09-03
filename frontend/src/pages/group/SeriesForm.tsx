import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { DateTime } from 'luxon'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { MAX_SERIES_SLOTS, validateSeries, type SeriesRule } from '@/utils/series'
import { DayOfWeekPicker } from './DayOfWeekPicker'

/**
 * Форма повторяющейся серии.
 *
 * Компонент не знает, где он стоит — во вкладке или в модалке, — и не ходит в
 * сеть. Наружу он отдаёт готовое правило, состояние и проверки держит внутри.
 * Следующий заход поставит эту же форму в модалку редактирования слота, и
 * менять в ней придётся только начальные значения.
 *
 * Число слотов считается на каждый ввод: человек, выбравший полгода и пять
 * дней недели, обязан увидеть «получится 130» пока настраивает, а не после
 * отказа сервера.
 */

interface Props {
  /** Начальные значения. Всё, чего нет, берётся из разумных умолчаний. */
  initial?: Partial<SeriesRule>
  /** Идёт ли отправка. Форма при этом заблокирована, но видима. */
  submitting?: boolean
  /** Ошибка от вызывающего — например, 402 от сервера. */
  error?: React.ReactNode
  submitLabel?: string
  onSubmit: (rule: SeriesRule) => void
  onCancel?: () => void
}

function defaults(): SeriesRule {
  const today = DateTime.now()
  return {
    startDate: today.toISODate()!,
    endDate: today.plus({ weeks: 4 }).toISODate()!,
    daysOfWeek: [today.weekday],
    startTime: '10:00',
    endTime: '12:00',
  }
}

export function SeriesForm({ initial, submitting, error, submitLabel, onSubmit, onCancel }: Props) {
  const { t } = useTranslation()
  const [rule, setRule] = useState<SeriesRule>({ ...defaults(), ...initial })
  const patch = (part: Partial<SeriesRule>) => setRule((r) => ({ ...r, ...part }))

  const validation = useMemo(() => validateSeries(rule), [rule])

  return (
    <div className="flex flex-col gap-3">
      <div className="grid gap-3 sm:grid-cols-2">
        <Input
          label={t('group.availabilityTab.series.fromDate')}
          type="date"
          value={rule.startDate}
          onChange={(e) => patch({ startDate: e.target.value })}
        />
        <Input
          label={t('group.availabilityTab.series.toDate')}
          type="date"
          value={rule.endDate}
          onChange={(e) => patch({ endDate: e.target.value })}
        />
      </div>

      <div>
        <label className="mb-1 block text-sm font-medium text-gray-700 dark:text-gray-300">
          {t('group.availabilityTab.series.days')}
        </label>
        <DayOfWeekPicker
          value={rule.daysOfWeek}
          onChange={(daysOfWeek) => patch({ daysOfWeek })}
          disabled={submitting}
        />
      </div>

      <div className="grid gap-3 sm:grid-cols-2">
        <Input
          label={t('group.availabilityTab.from')}
          type="time"
          value={rule.startTime}
          onChange={(e) => patch({ startTime: e.target.value })}
        />
        <Input
          label={t('group.availabilityTab.to')}
          type="time"
          value={rule.endTime}
          onChange={(e) => patch({ endTime: e.target.value })}
        />
      </div>

      {/*
        Число до отправки — главное, ради чего форма считает то же, что и
        сервер. Показывается и когда всё в порядке, и когда упёрлось в потолок:
        во втором случае человеку нужно понять, насколько сузить диапазон.
      */}
      {validation.count > 0 && (
        <p
          className={`text-sm ${
            validation.issues.includes('tooManySlots')
              ? 'text-red-600 dark:text-red-400'
              : 'text-gray-600 dark:text-gray-400'
          }`}
        >
          {t('group.availabilityTab.series.willCreate', { count: validation.count })}
          {validation.issues.includes('tooManySlots') &&
            ` · ${t('group.availabilityTab.series.issue.tooManySlots', { max: MAX_SERIES_SLOTS })}`}
        </p>
      )}

      {validation.issues
        .filter((issue) => issue !== 'tooManySlots')
        .map((issue) => (
          <p key={issue} className="text-sm text-red-600 dark:text-red-400">
            {t(`group.availabilityTab.series.issue.${issue}`)}
          </p>
        ))}

      {error}

      <div className="flex flex-wrap gap-2">
        <Button
          loading={submitting}
          disabled={!validation.ok}
          onClick={() => onSubmit(rule)}
          className="min-h-[44px] justify-center"
        >
          {submitLabel ?? t('group.availabilityTab.series.submit')}
        </Button>
        {onCancel && (
          <Button variant="secondary" onClick={onCancel} className="min-h-[44px] justify-center">
            {t('group.availabilityTab.grid.cancel')}
          </Button>
        )}
      </div>
    </div>
  )
}
