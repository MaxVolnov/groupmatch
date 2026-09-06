import { useTranslation } from 'react-i18next'
import { resolveErrorLines } from '@/utils/apiError'

interface ErrorMessageProps {
  error: unknown
}

/**
 * Показ ошибки запроса.
 *
 * Разбор живёт в `utils/apiError.ts` и общий с экранами, которые держат текст
 * ошибки в своём состоянии. Раньше он был здесь, и шесть страниц входа,
 * регистрации и восстановления пароля повторяли его каждая по-своему — с
 * разными фолбэками на случай, когда ответа нет вовсе.
 */
export function ErrorMessage({ error }: ErrorMessageProps) {
  const { t } = useTranslation()
  const lines = resolveErrorLines(error, t)

  return (
    <div className="rounded-lg border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20 px-4 py-3 text-sm text-red-700 dark:text-red-400">
      {lines.map((line, i) => (
        <p key={i} className={i > 0 ? 'mt-1' : undefined}>
          {line}
        </p>
      ))}
    </div>
  )
}
