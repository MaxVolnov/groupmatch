import { AxiosError } from 'axios'
import { useTranslation } from 'react-i18next'
import type { ApiError } from '@/types'

interface ErrorMessageProps {
  error: unknown
}

type TranslateFn = (key: string, options?: Record<string, unknown>) => string

function resolveMessage(error: unknown, t: TranslateFn): string {
  if (error instanceof AxiosError) {
    const status = error.response?.status
    const data = error.response?.data as ApiError | undefined
    const backendMsg = data?.message?.trim()

    if (status === 429) {
      const retryAfter = error.response?.headers?.['retry-after']
      if (retryAfter) {
        const seconds = parseInt(retryAfter, 10)
        if (!isNaN(seconds)) {
          const minutes = Math.ceil(seconds / 60)
          return t('errors.tooManyRequestsMinutes', { minutes })
        }
      }
      return t('errors.tooManyRequestsGeneric')
    }

    if (backendMsg) return backendMsg

    if (!error.response) {
      return t('errors.cannotConnect')
    }
    if (status === 404) return t('errors.notFound')
    if (status === 500 || status === 502 || status === 503) {
      return t('errors.server')
    }
    return error.message
  }
  if (error instanceof Error) return error.message
  return t('errors.somethingWrong')
}

export function ErrorMessage({ error }: ErrorMessageProps) {
  const { t } = useTranslation()
  const message = resolveMessage(error, t)
  return (
    <div className="rounded-lg border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20 px-4 py-3 text-sm text-red-700 dark:text-red-400">
      {message}
    </div>
  )
}
