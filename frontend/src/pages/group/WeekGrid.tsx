import { useTranslation } from 'react-i18next'
import type { DragHighlight, SelectionHandle } from '@/hooks/useDragSelection'
import type { CellState, OwnGrid } from '@/utils/ownGrid'

/**
 * Недельная сетка группы — одна на весь экран группы.
 *
 * Раньше сеток было две: агрегат по группе на «Когда все свободны» и
 * отдельная сетка своего времени на «Моей доступности». Исходная жалоба
 * пользователя была ровно в том, что таблица и место ввода — разные экраны;
 * вторая сетка эту путаницу удвоила. Теперь одна таблица показывает оба слоя
 * сразу: фон — сколько человек свободно, вставка внутри ячейки — «я здесь
 * есть».
 *
 * Компонент ничего не решает: раскладка приходит из `buildOwnGrid`, агрегат —
 * из ответа теплокарты, обработчики жеста — из `useDragSelection`.
 */

export interface CellCounts {
  /** `counts[row][col]` — сколько человек свободно. */
  counts: number[][]
  max: number
  /** Имена свободных, если группа их показывает. Для подсказки по наведению. */
  namesAt?: (row: number, col: number) => string[] | null
}

interface Props {
  /** Мои слоты, разложенные по ячейкам. Отсюда же берётся `spec`. */
  grid: OwnGrid
  aggregate: CellCounts
  highlightAt?: (row: number, col: number) => DragHighlight | null
  /** Обработчики протяжки. Не передаются в режиме создания встречи. */
  gridProps?: React.HTMLAttributes<HTMLDivElement>
  handles?: { start: { row: number; col: number }; end: { row: number; col: number } } | null
  handleProps?: (handle: SelectionHandle) => React.HTMLAttributes<HTMLElement>
  /**
   * Клик по ячейке в режиме создания встречи. Взаимоисключим с `gridProps`:
   * один и тот же жест не может значить два разных действия.
   */
  onCellActivate?: (row: number, col: number) => void
}

const DAY_KEYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']

/** Фон ячейки — плотность группы. Шкала та же, что была в теплокарте. */
function intensityClass(count: number, max: number): string {
  if (count === 0 || max === 0) return 'bg-white dark:bg-gray-800'
  const ratio = count / max
  if (ratio >= 0.8) return 'bg-green-500 dark:bg-green-500'
  if (ratio >= 0.6) return 'bg-green-400 dark:bg-green-600'
  if (ratio >= 0.4) return 'bg-green-300 dark:bg-green-700'
  if (ratio >= 0.2) return 'bg-green-200 dark:bg-green-800'
  return 'bg-green-100 dark:bg-green-900'
}

/**
 * Моё время — синяя вставка внутри зелёной ячейки, а не замена её фона.
 *
 * Два слоя на одной клетке приходится разделять по разным осям: агрегат несёт
 * зелёный оттенок, «моё» — синий и форму (вставка меньше клетки, вокруг видна
 * рамка фона). Пытаться уложить оба смысла в одну шкалу светлоты бесполезно:
 * пять уровней плотности и три состояния своего слота в неё не помещаются.
 *
 * Три «моих» состояния разведены между собой светлотой через ступень — та же
 * палитра, что проверялась на снимках прошлого захода.
 */
const MINE_CLASS: Record<Exclude<CellState, 'free'>, string> = {
  busy: 'bg-gm-600 dark:bg-gm-500',
  series: 'bg-gm-900 dark:bg-gm-200',
  // gm-300, а не gm-200: на снимке стенда бледно-голубая вставка поверх
  // светло-зелёного фона агрегата почти сливалась с ним. В тёмной теме
  // gm-800 контрастен и без правки.
  partial: 'bg-gm-300 dark:bg-gm-800',
}

/**
 * Подсветка на время жеста. Кладётся поверх обоих слоёв.
 *
 * `erase` намеренно выпадает из синей шкалы: стирание — единственное здесь
 * необратимое действие, и оно обязано читаться как другое по роду, а не как
 * ещё один оттенок того же. Красный тут семантический, а не фирменный —
 * ровно как зелёный у плотности.
 */
const HIGHLIGHT_CLASS: Record<DragHighlight, string> = {
  create: 'bg-gm-400 dark:bg-gm-400',
  unchanged: 'bg-gm-100 dark:bg-gm-800',
  blocked: 'bg-gm-700 dark:bg-gm-600 ring-1 ring-inset ring-gm-400 dark:ring-gm-100',
  erase: 'bg-red-400 dark:bg-red-500',
}

/**
 * Ручка растягивания тач-выделения.
 *
 * Видимый маркер маленький — ячейка на 375px узкая, и крупный кружок закрыл бы
 * то, что человек выделяет. Зона захвата при этом 44×44: её даёт
 * псевдоэлемент `::before`, который в поток не попадает и ничего не
 * загораживает, но попадания принимает на себя.
 *
 * `touch-none` висит только здесь. На контейнере сетки его нет и быть не
 * должно: там нативный скролл — единственный способ листать сутки на телефоне.
 */
function Handle({ position, props }: { position: SelectionHandle; props?: React.HTMLAttributes<HTMLElement> }) {
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

export function WeekGrid({ grid, aggregate, highlightAt, gridProps, handles, handleProps, onCellActivate }: Props) {
  const { t } = useTranslation()
  const { cells, timeLabels, weekStart } = grid

  const title = (row: number, col: number): string | undefined => {
    const parts: string[] = []
    const count = aggregate.counts[row]?.[col] ?? 0
    if (count > 0) {
      const names = aggregate.namesAt?.(row, col)
      parts.push(`${count} ${t('group.heatmapTab.available')}${names ? ': ' + names.join(', ') : ''}`)
    }
    const mine = cells[row][col]
    if (mine === 'busy') parts.push(t('group.availabilityTab.grid.busyHint'))
    if (mine === 'series') parts.push(t('group.availabilityTab.grid.seriesHint'))
    if (mine === 'partial') parts.push(t('group.availabilityTab.grid.partialHint'))
    if (onCellActivate && count > 0) parts.push(t('group.heatmapTab.clickToSchedule'))
    return parts.length ? parts.join(' · ') : undefined
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
                {row.map((mine, colIdx) => {
                  const count = aggregate.counts[rowIdx]?.[colIdx] ?? 0
                  const highlight = highlightAt?.(rowIdx, colIdx) ?? null
                  const clickable = onCellActivate && count > 0
                  return (
                    <td
                      key={colIdx}
                      // Координаты на самой ячейке: во время протяжки события
                      // перенаправлены на контейнер захватом указателя, и
                      // ячейка ищется по координатам курсора, а не по target.
                      data-row={rowIdx}
                      data-col={colIdx}
                      title={title(rowIdx, colIdx)}
                      onClick={clickable ? () => onCellActivate(rowIdx, colIdx) : undefined}
                      className={`relative border-b border-r border-gray-100 dark:border-gray-700/30 ${intensityClass(count, aggregate.max)} ${
                        clickable ? 'cursor-pointer hover:ring-2 hover:ring-gm-400 hover:ring-inset' : ''
                      }`}
                    >
                      {mine !== 'free' && (
                        <span
                          className={`pointer-events-none absolute inset-[2px] rounded-[1px] ${MINE_CLASS[mine]}`}
                        />
                      )}
                      {highlight && (
                        <span className={`pointer-events-none absolute inset-0 ${HIGHLIGHT_CLASS[highlight]}`} />
                      )}
                      {handles?.start.row === rowIdx && handles.start.col === colIdx && (
                        <Handle position="start" props={handleProps?.('start')} />
                      )}
                      {handles?.end.row === rowIdx && handles.end.col === colIdx && (
                        <Handle position="end" props={handleProps?.('end')} />
                      )}
                    </td>
                  )
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-gray-500 dark:text-gray-400">
        <span className="flex items-center gap-1.5">
          <span className="inline-block h-3 w-3 rounded-sm border border-gray-300 bg-green-400 dark:border-gray-600 dark:bg-green-600" />
          {t('group.heatmapTab.othersFree')}
        </span>
        {(['busy', 'series', 'partial'] as const).map((state) => (
          <span key={state} className="flex items-center gap-1.5">
            <span className="relative inline-block h-3 w-3 rounded-sm border border-gray-300 bg-white dark:border-gray-600 dark:bg-gray-800">
              <span className={`absolute inset-[1px] ${MINE_CLASS[state]}`} />
            </span>
            {t(`group.availabilityTab.grid.legend${state === 'busy' ? 'Busy' : state === 'series' ? 'Series' : 'Partial'}`)}
          </span>
        ))}
      </div>
    </div>
  )
}
