import { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { useAuthStore } from '@/store/auth'
import { Footer } from './Footer'

interface Props {
  children: ReactNode
}

export function PublicLayout({ children }: Props) {
  const { isAuthenticated } = useAuthStore()

  return (
    <div className="min-h-screen flex flex-col bg-gray-50 dark:bg-gray-900">
      <nav className="border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-sm">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link to="/" className="text-lg font-bold text-indigo-600 dark:text-indigo-400">
            GroupMatch
          </Link>
          <div className="flex items-center gap-3">
            {isAuthenticated ? (
              <Link
                to="/"
                className="text-sm font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 transition-colors"
              >
                Open app
              </Link>
            ) : (
              <>
                <Link
                  to="/signin"
                  className="text-sm text-gray-600 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
                >
                  Sign in
                </Link>
                <Link
                  to="/signup"
                  className="text-sm font-medium bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2 rounded-lg transition-colors"
                >
                  Sign up
                </Link>
              </>
            )}
          </div>
        </div>
      </nav>

      <main className="flex-1 mx-auto w-full max-w-6xl px-4 py-6 md:py-8">
        {children}
      </main>

      <Footer />
    </div>
  )
}
