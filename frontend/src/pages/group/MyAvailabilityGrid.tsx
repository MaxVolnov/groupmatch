import { useTranslation } from 'react-i18next'
import type { DragHighlight, SelectionHandle } from '@/hooks/useDragSelection'
import type { CellState, OwnGrid } from '@/utils/ownGrid'

/**
 * Сетка своего времени.
 *
 * Компонент ничего не решает: раскладка приходит готовой из `buildOwnGrid`,
 * обработчики жеста — из `useDragSelection`, а здесь только разметка и цвета.
 * Разделение то же, что и во всей цепочке: картинка проверяется глазами, а
 * решения под ней — тестами, и в одном файле они лишают друг друга оракула.
 */

interface Props {
  grid: OwnGrid
  /**
   * Подсветка на время жеста. Возвращает состояние для ячейки или `null`,
   * если она в выделение не входит.
   */
  highlightAt?: (row: number, col: number) => DragHighlight | null
  /** Обработчики протяжки, навешиваются на контейнер таблицы. */
  gridProps?: React.HTMLAttributes<HTMLDivElement>
  /**
   * Углы тач-выделения, куда сажаются ручки. `null` — ручек нет (мышиный
   * жест их не показывает: там выделение живёт только пока зажата кнопка).
   */
  handles?: { start: { row: number; col: number }; end: { row: number; col: number } } | null
  handleProps?: (handle: SelectionHandle) => React.HTMLAttributes<HTMLElement>
}

/**
 * Ручка растягивания тач-выделения.
 *
 * Видимый маркер маленький — ячейка на 375px узкая, и крупный кружок закрыл бы
 * то, что человек выделяет. Зона захвата при этом 44×44: она даётся
 * псевдоэлементом `::before`, который в поток не попадает и ничего не
 * загораживает, но попадания принимает на себя. Раздувать сам маркер ради
 * попадаемости — значит менять видимое ради невидимого.
 *
 * `touch-none` (`touch-action: none`) висит **только здесь**. На контейнере
 * сетки его нет и быть не должно: там нативный скролл, и это единственный
 * способ листать сутки на телефоне.
 */
function Handle({ position, props }: { position: 'start' | 'end'; props?: React.HTMLAttributes<HTMLElement> }) {
  const corner = position === 'start' ? '-left-1 -top-1' : '-bottom-1 -right-1'
  return (
    <span
      {...props}
      role="slider"
      tabIndex={-1}
      aria-label={position === 'start' ? 'Начало выделения' : 'Конец выделения'}
      className={`absolute ${corner} z-20 h-3 w-3 touch-none rounded-full bg-gm-600 ring-2 ring-white dark:bg-gm-300 dark:ring-gray-800 before:absolute before:-inset-4 before:content-['']`}
    />
  )
}

/**
 * Подсветка перекрывает постоянную заливку ячейки, а не смешивается с ней:
 * пока идёт жест, человеку важно, что произойдёт по отпусканию, а не что там
 * было.
 *
 * Семь состояний на одну шкалу — тесно, поэтому разводятся не все пары, а те,
 * что попадают в один взгляд: три подсветки между собой и четыре постоянных
 * между собой. Пары из разных наборов (подсветка против покоя) могут стоять
 * рядом по светлоте — они и означают одно и то же «здесь ничего не
 * изменится».
 *
 * Первая версия этого не различала: `create` был `gm-400`, а `partial` в
 * покое — `gm-300`, соседняя ступень. На снимке стенда подсвеченная ячейка и
 * невыделенный «только в списке» оказались почти одним цветом.
 */
const HIGHLIGHT_CLASS: Record<DragHighlight, string> = {
  create: 'bg-gm-400 dark:bg-gm-400',
  unchanged: 'bg-gm-100 dark:bg-gm-800',
  blocked: 'bg-gm-700 dark:bg-gm-600 ring-1 ring-inset ring-gm-400 dark:ring-gm-100',
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
  // Рамка — общий признак «только в списке» у partial и series; светлота при
  // этом разная, чтобы они не сливались ещё и между собой.
  partial: 'bg-gm-200 dark:bg-gm-800 ring-1 ring-inset ring-gm-500 dark:ring-gm-400',
  busy: 'bg-gm-600 dark:bg-gm-500',
  series: 'bg-gm-900 dark:bg-gm-200 ring-1 ring-inset ring-gm-400 dark:ring-gm-700',
}

/** Ключи перечислены явно, а не собираются из имени состояния: собранный
 *  на лету ключ не находится грепом, и при переименовании локали он молча
 *  превращается в подпись вида `group.availabilityTab.grid.legendBusy`. */
const LEGEND = [
  { state: 'busy', labelKey: 'group.availabilityTab.grid.legendBusy' },
  { state: 'series', labelKey: 'group.availabilityTab.grid.legendSeries' },
  { state: 'partial', labelKey: 'group.availabilityTab.grid.legendPartial' },
] as const satisfies readonly { state: CellState; labelKey: string }[]

export function MyAvailabilityGrid({ grid, highlightAt, gridProps, handles, handleProps }: Props) {
  const { t } = useTranslation()
  const { cells, timeLabels, weekStart } = grid

  const cellTitle = (state: CellState): string | undefined => {
    if (state === 'busy') return t('group.availabilityTab.grid.busyHint')
    if (state === 'series') return t('group.availabilityTab.grid.seriesHint')
    if (state === 'partial') return t('group.availabilityTab.grid.partialHint')
    return undefined
  }

  return (
    <div>
      {/* select-none: без него протяжка красит подписи времени синим
          выделением текста поверх собственной подсветки. */}
      <div
        {...gridProps}
        className="select-none overflow-x-auto rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800"
      >
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
                {row.map((state, colIdx) => {
                  const highlight = highlightAt?.(rowIdx, colIdx) ?? null
                  const isStart = handles?.start.row === rowIdx && handles.start.col === colIdx
                  const isEnd = handles?.end.row === rowIdx && handles.end.col === colIdx
                  return (
                    <td
                      key={colIdx}
                      // Координаты на самой ячейке: во время протяжки события
                      // перенаправлены на контейнер захватом указателя, и
                      // ячейка ищется по координатам курсора, а не по target.
                      data-row={rowIdx}
                      data-col={colIdx}
                      title={cellTitle(state)}
                      aria-label={cellTitle(state)}
                      className={`relative border-b border-r border-gray-100 dark:border-gray-700/30 ${
                        highlight ? HIGHLIGHT_CLASS[highlight] : CELL_CLASS[state]
                      }`}
                    >
                      {isStart && <Handle position="start" props={handleProps?.('start')} />}
                      {isEnd && <Handle position="end" props={handleProps?.('end')} />}
                    </td>
                  )
                })}
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
