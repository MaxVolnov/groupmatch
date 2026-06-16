import { AxiosError } from 'axios'
import type { ApiError } from '@/types'

interface ErrorMessageProps {
  error: unknown
}

export function ErrorMessage({ error }: ErrorMessageProps) {
  let message = 'Something went wrong'
  if (error instanceof AxiosError) {
    const data = error.response?.data as ApiError | undefined
    message = data?.message ?? error.message
  } else if (error instanceof Error) {
    message = error.message
  }
  return (
    <div className="rounded-lg border border-red-200 dark:border-red-800 bg-red-50 dark:bg-red-900/20 px-4 py-3 text-sm text-red-700 dark:text-red-400">
      {message}
    </div>
  )
}
