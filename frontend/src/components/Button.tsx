import { ButtonHTMLAttributes } from 'react'
import { Spinner } from './Spinner'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
  size?: 'sm' | 'md'
  loading?: boolean
}

const variants = {
  // Один и тот же gm-600 в обеих темах. Раньше в тёмной кнопка светлела до
  // indigo-500, механически это стало gm-500 (#2D86D8) — а на нём белый текст
  // даёт всего 3.81:1, ниже порога AA. На gm-600 — 6.20:1, и на тёмном фоне
  // насыщенный синий и так читается как активный элемент.
  primary:
    'bg-gm-600 text-white hover:bg-gm-700 disabled:bg-gm-300 dark:disabled:bg-gm-800',
  secondary:
    'bg-white text-gray-700 border border-gray-300 hover:bg-gray-50 dark:bg-gray-800 dark:text-gray-200 dark:border-gray-600 dark:hover:bg-gray-700',
  danger:
    'bg-red-600 text-white hover:bg-red-700 disabled:bg-red-300 dark:bg-red-700 dark:hover:bg-red-800',
  ghost:
    'text-gray-600 hover:text-gray-900 hover:bg-gray-100 dark:text-gray-400 dark:hover:text-gray-100 dark:hover:bg-gray-700',
}

const sizes = {
  sm: 'px-3 py-1.5 text-sm min-h-[44px]',
  md: 'px-4 py-2 text-sm min-h-[44px]',
}

export function Button({
  variant = 'primary',
  size = 'md',
  loading,
  disabled,
  children,
  className = '',
  ...props
}: ButtonProps) {
  return (
    <button
      {...props}
      disabled={disabled || loading}
      className={`inline-flex items-center gap-2 rounded-lg font-medium transition-colors focus:outline-none focus:ring-2 focus:ring-gm-500 focus:ring-offset-1 dark:focus:ring-offset-gray-800 disabled:cursor-not-allowed ${variants[variant]} ${sizes[size]} ${className}`}
    >
      {loading && <Spinner size="sm" />}
      {children}
    </button>
  )
}
