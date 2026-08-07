interface SpinnerProps {
  size?: 'sm' | 'md' | 'lg'
}

const sizes = { sm: 'h-4 w-4', md: 'h-8 w-8', lg: 'h-12 w-12' }

export function Spinner({ size = 'md' }: SpinnerProps) {
  return (
    <div
      className={`${sizes[size]} animate-spin rounded-full border-2 border-gray-200 dark:border-gray-700 border-t-gm-600 dark:border-t-gm-400`}
      role="status"
    />
  )
}
