import { useTranslation } from 'react-i18next'
import type { CellState, OwnGrid } from '@/utils/ownGrid'

/**
 * Сетка своего времени — только отображение.
 *
 * Ни одного обработчика событий здесь нет и в этом заходе не появится:
 * протяжка идёт следующим шагом и будет вешаться снаружи, на контейнер, через
 * `useDragSelection`. Разделено так намеренно — картинка проверяется глазами,
 * жест проверяется тестами, и в одном компоненте они лишают друг друга
 * оракула.
 *
 * Раскладка приходит готовой из `buildOwnGrid`: компонент её не считает, а
 * красит.
 */

interface Props {
  grid: OwnGrid
}

const DAY_KEYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']

/**
 * Четыре состояния — четыре заливки из шкалы `gm`, различимые в обеих темах.
 *
 * Разведены по светлоте через ступень, а не по соседним оттенкам: в каждой
 * теме получается три уровня — светлый, средний, тёмный. Первая версия давала
 * `series` ту же заливку, что и `busy`, и различала их одной внутренней
 * рамкой; на снимке стенда блоки вторника и четверга оказались почти
 * неотличимы — рамка на ячейке высотой 16 пикселей читается как обычная
 * межклеточная линия. Рамка осталась, но теперь она подсказка, а не
 * единственный признак.
 */
const CELL_CLASS: Record<CellState, string> = {
  free: 'bg-white dark:bg-gray-800',
  partial: 'bg-gm-300 dark:bg-gm-700',
  busy: 'bg-gm-600 dark:bg-gm-500',
  series: 'bg-gm-800 dark:bg-gm-300 ring-1 ring-inset ring-gm-400 dark:ring-gm-700',
}

/** Ключи перечислены явно, а не собираются из имени состояния: собранный
 *  на лету ключ не находится грепом, и при переименовании локали он молча
 *  превращается в подпись вида `group.availabilityTab.grid.legendBusy`. */
const LEGEND = [
  { state: 'busy', labelKey: 'group.availabilityTab.grid.legendBusy' },
  { state: 'series', labelKey: 'group.availabilityTab.grid.legendSeries' },
  { state: 'partial', labelKey: 'group.availabilityTab.grid.legendPartial' },
] as const satisfies readonly { state: CellState; labelKey: string }[]

export function MyAvailabilityGrid({ grid }: Props) {
  const { t } = useTranslation()
  const { cells, timeLabels, weekStart } = grid

  const cellTitle = (state: CellState): string | undefined => {
    if (state === 'busy') return t('group.availabilityTab.grid.legendBusy')
    if (state === 'series') return t('group.availabilityTab.grid.legendSeries')
    if (state === 'partial') return t('group.availabilityTab.grid.partialHint')
    return undefined
  }

  return (
    <div>
      <div className="overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800">
        <table className="w-full border-collapse text-xs">
          <thead>
            <tr>
              <th className="w-10 border-b border-r border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-700 py-2 px-1" />
              {DAY_KEYS.map((dayKey, i) => (
                <th
                  key={dayKey}
                  className="border-b border-r border-gray-200 dark:border-gray-700 bg-gray-50 dark:bg-gray-700 py-2 px-0.5 font-medium text-gray-700 dark:text-gray-300 text-center"
                >
                  <div>{t(`group.heatmapTab.day.${dayKey}`)}</div>
                  <div className="text-[10px] font-normal text-gray-400 dark:text-gray-500">
                    {weekStart.plus({ days: i }).toFormat('dd/MM')}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {cells.map((row, rowIdx) => (
              <tr key={rowIdx} className="h-4">
                <td className="border-b border-r border-gray-100 dark:border-gray-700/50 px-1 text-right text-[10px] text-gray-400 dark:text-gray-500 align-top leading-4">
                  {timeLabels[rowIdx]}
                </td>
                {row.map((state, colIdx) => (
                  <td
                    key={colIdx}
                    title={cellTitle(state)}
                    className={`border-b border-r border-gray-100 dark:border-gray-700/30 ${CELL_CLASS[state]}`}
                  />
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
        {LEGEND.map(({ state, labelKey }) => (
          <span key={state} className="flex items-center gap-1.5">
            <span
              className={`inline-block h-3 w-3 rounded-sm border border-gray-300 dark:border-gray-600 ${CELL_CLASS[state]}`}
            />
            {t(labelKey)}
          </span>
        ))}
      </div>
    </div>
  )
}
