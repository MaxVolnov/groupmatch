import { AxiosError } from 'axios'
import type { ApiError } from '@/types'

interface ErrorMessageProps {
  error: unknown
}

function resolveMessage(error: unknown): string {
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
          return `Too many attempts. Please wait ${minutes} min. and try again.`
        }
      }
      return 'Too many attempts. Please wait a moment and try again.'
    }

    if (backendMsg) return backendMsg

    if (!error.response) {
      return 'Cannot connect to the server. Check your connection.'
    }
    if (status === 404) return 'Not found.'
    if (status === 500 || status === 502 || status === 503) {
      return 'Something went wrong on the server. Please try again later.'
    }
    return error.message
  }
  if (error instanceof Error) return error.message
  return 'Something went wrong'
}

export function ErrorMessage({ error }: ErrorMessageProps) {
  const message = resolveMessage(error)
  return (
    <div className="rounded-lg border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20 px-4 py-3 text-sm text-red-700 dark:text-red-400">
      {message}
    </div>
  )
}
