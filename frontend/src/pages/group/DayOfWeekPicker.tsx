import { useTranslation } from 'react-i18next'

/**
 * Выбор дней недели — общий для формы серии и формы очистки.
 *
 * Семь кнопок-переключателей, а не список с галочками: на 375px список из
 * семи строк занимает пол-экрана, а ряд кнопок помещается в одну строку и
 * читается как календарная неделя.
 */

const DAY_KEYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']

interface Props {
  /** Номера дней по Luxon: 1 — понедельник. */
  value: number[]
  onChange: (next: number[]) => void
  disabled?: boolean
}

export function DayOfWeekPicker({ value, onChange, disabled }: Props) {
  const { t } = useTranslation()
  const selected = new Set(value)

  const toggle = (weekday: number) => {
    const next = new Set(selected)
    if (next.has(weekday)) next.delete(weekday)
    else next.add(weekday)
    onChange([...next].sort((a, b) => a - b))
  }

  return (
    <div className="flex flex-wrap gap-1" role="group">
      {DAY_KEYS.map((key, i) => {
        const weekday = i + 1
        const on = selected.has(weekday)
        return (
          <button
            key={key}
            type="button"
            disabled={disabled}
            aria-pressed={on}
            onClick={() => toggle(weekday)}
            // 44 пикселя по обеим сторонам: это первое, во что человек тычет
            // пальцем, настраивая повтор.
            className={`min-h-[44px] min-w-[44px] rounded-lg border text-sm font-medium transition-colors disabled:opacity-50 ${
              on
                ? 'border-gm-600 bg-gm-600 text-white'
                : 'border-gray-300 text-gray-700 hover:border-gray-400 dark:border-gray-600 dark:text-gray-300'
            }`}
          >
            {t(`group.heatmapTab.day.${key}`)}
          </button>
        )
      })}
    </div>
  )
}
