import { AxiosError } from 'axios'
import type { ApiError } from '@/types'

/**
 * Что показывать человеку, когда запрос не удался.
 *
 * ЗАЧЕМ ОТДЕЛЬНЫЙ МОДУЛЬ. Разбор был скопирован в шесть экранов, и в каждом
 * своим фолбэком: на входе «Неверный email или пароль», при регистрации «Не
 * удалось зарегистрироваться», в остальных «Что-то пошло не так». Пока сервер
 * отвечает, разница незаметна. Когда он не отвечает — а из-за фильтрации
 * трафика соединение периодически не устанавливается, — интерфейс начинает
 * обвинять человека в том, чего он не делал: пользователь пишет в поддержку,
 * решив что сломан его аккаунт, а владелец продукта полдня ищет ошибку в
 * пароле, которого не было. Оба случая уже произошли.
 *
 * Правило одно и живёт здесь:
 *   — ответа нет вовсе (таймаут, обрыв, недоступная сеть, отказ CORS)
 *     → говорим про связь, и никогда про учётные данные или отказ операции;
 *   — сервер ответил → показываем то, что он сказал.
 */

export type TranslateFn = (key: string, options?: Record<string, unknown>) => string

/** Сообщение о недоступности сервера — одно на весь фронтенд. */
const NETWORK_KEY = 'errors.cannotConnect'

/**
 * Ответа не было.
 *
 * У axios это одинаково выглядит для обрыва соединения, таймаута
 * (`ECONNABORTED`), недоступной сети и отказа CORS: `error.response`
 * отсутствует. Различать их между собой человеку незачем — все они означают
 * «до сервера не дошли», а подробности вида `ERR_NETWORK` ему ничего не дают.
 */
export function isNetworkFailure(error: unknown): boolean {
  return error instanceof AxiosError && !error.response
}

/**
 * Строки для показа. Первая — основная, дальше подробности от сервера.
 *
 * Массив, а не строка: у ошибки валидации подробностей бывает несколько (и
 * короткий пароль, и слишком короткое имя одновременно), и показывать надо
 * все. Раньше `details` выбрасывались целиком, и человек видел «Invalid input»
 * — по-английски и ни о чём, хотя сервер прислал конкретное требование.
 *
 * @param fallbackKey что показать, если сервер ответил, но сказать нечего.
 *        На сетевую ошибку не влияет: там сообщение всегда про связь.
 */
export function resolveErrorLines(
  error: unknown,
  t: TranslateFn,
  fallbackKey = 'errors.somethingWrong',
): string[] {
  // Первым делом и раньше всего остального. Любая ветка ниже говорила бы о
  // причине, которой не было.
  if (isNetworkFailure(error)) return [t(NETWORK_KEY)]

  if (error instanceof AxiosError) {
    const status = error.response?.status
    const data = error.response?.data as ApiError | undefined

    // 429 разбирается отдельно: у него есть Retry-After, и «подождите 3 минуты»
    // полезнее, чем текст сервера.
    if (status === 429) return [tooManyRequests(error, t)]

    const lines: string[] = []
    const backendMsg = data?.message?.trim()
    if (backendMsg) lines.push(backendMsg)

    lines.push(...detailLines(data))

    if (lines.length > 0) return lines
    if (status === 404) return [t('errors.notFound')]
    if (status !== undefined && status >= 500) return [t('errors.server')]
    return [t(fallbackKey)]
  }

  if (error instanceof Error && error.message) return [error.message]
  return [t('errors.somethingWrong')]
}

/** Однострочная форма для мест, где под ошибку отведена строка состояния. */
export function resolveErrorMessage(
  error: unknown,
  t: TranslateFn,
  fallbackKey?: string,
): string {
  return resolveErrorLines(error, t, fallbackKey).join('\n')
}

/**
 * Подробности валидации: `details` — это карта «поле → требование»
 * (см. GlobalExceptionHandler.handleValidationExceptions).
 *
 * Порядок по имени поля. У `HashMap` на стороне сервера порядок произвольный,
 * и без сортировки две одинаковые ошибки показывались бы человеку по-разному
 * от запроса к запросу.
 */
function detailLines(data: ApiError | undefined): string[] {
  const details = data?.details
  if (!details || typeof details !== 'object') return []
  return Object.keys(details)
    .sort()
    .map((field) => String(details[field]).trim())
    .filter((line) => line.length > 0)
}

function tooManyRequests(error: AxiosError, t: TranslateFn): string {
  const retryAfter = error.response?.headers?.['retry-after']
  if (retryAfter) {
    const seconds = parseInt(String(retryAfter), 10)
    if (!isNaN(seconds)) {
      return t('errors.tooManyRequestsMinutes', { minutes: Math.ceil(seconds / 60) })
    }
  }
  return t('errors.tooManyRequestsGeneric')
}
