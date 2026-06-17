import { useState, useEffect, ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/auth'
import { useThemeStore, applyTheme, type Theme } from '@/store/theme'
import { Button } from './Button'
import { FeedbackModal } from './FeedbackModal'

interface LayoutProps {
  children: ReactNode
}

const THEME_CYCLE: Theme[] = ['light', 'dark', 'system']

const THEME_ICON: Record<Theme, string> = {
  light: '☀️',
  dark: '🌙',
  system: '💻',
}

function ThemeToggle() {
  const { theme, setTheme } = useThemeStore()

  useEffect(() => {
    applyTheme(theme)
    if (theme !== 'system') return
    const mq = window.matchMedia('(prefers-color-scheme: dark)')
    const handler = () => applyTheme('system')
    mq.addEventListener('change', handler)
    return () => mq.removeEventListener('change', handler)
  }, [theme])

  const next = THEME_CYCLE[(THEME_CYCLE.indexOf(theme) + 1) % THEME_CYCLE.length]

  return (
    <button
      onClick={() => setTheme(next)}
      title={`Theme: ${theme} — click for ${next}`}
      className="flex items-center justify-center w-9 h-9 rounded-lg hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors text-base"
      aria-label={`Switch to ${next} theme`}
    >
      {THEME_ICON[theme]}
    </button>
  )
}

export function Layout({ children }: LayoutProps) {
  const { isAuthenticated, displayName, email, logout } = useAuthStore()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)
  const [showFeedback, setShowFeedback] = useState(false)

  const handleLogout = () => {
    setMenuOpen(false)
    logout()
    navigate('/signin')
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900 overflow-x-hidden">
      <nav className="border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-sm">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link
            to="/"
            className="text-lg font-bold text-indigo-600 dark:text-indigo-400"
            onClick={() => setMenuOpen(false)}
          >
            GroupMatch
          </Link>

          <div className="flex items-center gap-2">
            <ThemeToggle />

            {isAuthenticated && (
              <>
                {/* Desktop */}
                <div className="hidden sm:flex items-center gap-3">
                  <Link
                    to="/profile"
                    className="text-sm text-gray-600 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
                  >
                    {displayName ?? email}
                  </Link>
                  <button
                    onClick={() => setShowFeedback(true)}
                    className="text-sm text-gray-600 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
                  >
                    💬 Feedback
                  </button>
                  <Button variant="secondary" size="sm" onClick={handleLogout}>
                    Sign out
                  </Button>
                </div>

                {/* Mobile hamburger */}
                <button
                  className="sm:hidden flex items-center justify-center w-11 h-11 rounded-lg text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                  onClick={() => setMenuOpen((v) => !v)}
                  aria-label={menuOpen ? 'Close menu' : 'Open menu'}
                >
                  {menuOpen ? (
                    <span className="text-lg leading-none">✕</span>
                  ) : (
                    <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                      <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
                    </svg>
                  )}
                </button>
              </>
            )}
          </div>
        </div>

        {/* Mobile dropdown */}
        {isAuthenticated && menuOpen && (
          <div className="sm:hidden border-t border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-4 flex flex-col gap-3">
            <Link
              to="/profile"
              onClick={() => setMenuOpen(false)}
              className="text-sm text-gray-600 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
            >
              {displayName ?? email}
            </Link>
            <button
              onClick={() => { setMenuOpen(false); setShowFeedback(true) }}
              className="text-left text-sm text-gray-600 dark:text-gray-400 hover:text-indigo-600 dark:hover:text-indigo-400 transition-colors"
            >
              💬 Feedback
            </button>
            <Button variant="secondary" size="sm" onClick={handleLogout} className="w-full justify-center">
              Sign out
            </Button>
          </div>
        )}
      </nav>

      <main className="mx-auto max-w-6xl px-4 py-6 md:py-8">{children}</main>

      <FeedbackModal open={showFeedback} onClose={() => setShowFeedback(false)} />
    </div>
  )
}
