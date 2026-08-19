import { DateTime } from 'luxon'

/**
 * Работа с датами на границе «поле формы ↔ API».
 *
 * Три одинаковых копии этих функций жили в CreateMeetingModal, MeetingsTab и
 * AvailabilityTab. Копии были байт в байт равны, но именно поэтому и опасны:
 * поправить формат в одном месте и не заметить два других — вопрос времени, а
 * расхождение вылезет не ошибкой, а разными подписями на соседних вкладках.
 */

/**
 * Значение `<input type="datetime-local">` → ISO в UTC.
 *
 * Браузер отдаёт локальное время без зоны (`2026-08-22T19:30`), а API ждёт
 * момент. Зона берётся из настроек машины пользователя — это и есть та зона,
 * в которой он только что ввёл время.
 */
export function toIso(local: string): string {
  return DateTime.fromISO(local, { zone: 'local' }).toUTC().toISO()!
}

/**
 * Значение по умолчанию для `<input type="datetime-local">`: «сейчас плюс N
 * часов», в формате, который принимает поле (без зоны и без секунд).
 */
export function defaultDatetime(offsetHours: number): string {
  return DateTime.now().plus({ hours: offsetHours }).toFormat("yyyy-MM-dd'T'HH:mm")
}

/**
 * Интервал для показа человеку, в его локальной зоне. Если начало и конец в
 * один день, дата не повторяется дважды.
 */
export function fmtRange(startsAt: string, endsAt: string): string {
  const s = DateTime.fromISO(startsAt).toLocal()
  const e = DateTime.fromISO(endsAt).toLocal()
  if (s.hasSame(e, 'day')) {
    return `${s.toFormat('dd MMM yyyy, HH:mm')} – ${e.toFormat('HH:mm')}`
  }
  return `${s.toFormat('dd MMM HH:mm')} – ${e.toFormat('dd MMM HH:mm')}`
}
