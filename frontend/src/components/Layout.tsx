import { useState, useEffect, ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/store/auth'
import { useThemeStore, applyTheme, type Theme } from '@/store/theme'
import { Button } from './Button'
import { FeedbackModal } from './FeedbackModal'
import { Footer } from './Footer'
import { NotificationBell } from './NotificationBell'
import { ProBadge } from './ProBadge'

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

/**
 * Фирменный знак в шапке.
 *
 * Два файла, а не один: прозрачные варианты знака рассчитаны на конкретный
 * фон — у `-on-dark` буква G белая, и на светлой шапке она бы исчезла.
 * Переключаем классом, а не JS: тема в SPA задаётся классом `dark` на
 * <html>, значит обе картинки уже в разметке и подмена происходит без
 * мерцания при загрузке.
 *
 * `alt=""` — знак декоративный, рядом в той же ссылке есть текст «GroupMatch».
 */
function LogoMark() {
  return (
    <>
      <img
        src="/logo-on-light.svg"
        alt=""
        width={28}
        height={28}
        className="block h-7 w-7 shrink-0 dark:hidden"
      />
      <img
        src="/logo-on-dark.svg"
        alt=""
        width={28}
        height={28}
        className="hidden h-7 w-7 shrink-0 dark:block"
      />
    </>
  )
}

function GuestBadge() {
  return (
    <span className="text-xs bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400 px-2 py-0.5 rounded-full">
      Guest
    </span>
  )
}

export function Layout({ children }: LayoutProps) {
  const { isAuthenticated, displayName, email, isGuest, role, plan, logout } = useAuthStore()
  const navigate = useNavigate()
  const { t } = useTranslation()
  const [menuOpen, setMenuOpen] = useState(false)
  const [showFeedback, setShowFeedback] = useState(false)

  const handleLogout = () => {
    setMenuOpen(false)
    logout()
    navigate('/signin')
  }

  return (
    <div className="min-h-screen bg-gray-50 dark:bg-gray-900 overflow-x-hidden flex flex-col">
      <nav className="border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-sm">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link
            to="/"
            className="flex items-center gap-2 text-lg font-bold text-gm-600 dark:text-gm-400"
            onClick={() => setMenuOpen(false)}
          >
            <LogoMark />
            <span>
              GroupMatch
              <span className="ml-1.5 align-top text-[11px] font-normal text-gray-500 dark:text-gray-400">beta</span>
            </span>
          </Link>

          <div className="flex items-center gap-2">
            {isAuthenticated && !isGuest && <NotificationBell />}
            <ThemeToggle />

            {isAuthenticated && (
              <>
                {/* Desktop */}
                <div className="hidden sm:flex items-center gap-3">
                  <Link
                    to="/profile"
                    className="flex items-center gap-1.5 text-sm text-gray-600 dark:text-gray-400 hover:text-gm-600 dark:hover:text-gm-400 transition-colors"
                  >
                    {displayName ?? email}
                    {isGuest && <GuestBadge />}
                    {!isGuest && plan === 'PRO' && <ProBadge />}
                  </Link>
                  {role === 'ADMIN' && (
                    <Link
                      to="/admin"
                      className="inline-flex items-center gap-1.5 px-3 py-1 rounded-full text-xs font-semibold bg-gm-700 hover:bg-gm-800 text-white transition-colors"
                    >
                      <span>⚙️</span>
                      <span>{t('nav.admin')}</span>
                    </Link>
                  )}
                  <Link
                    to="/pricing"
                    className="text-sm text-gray-600 dark:text-gray-400 hover:text-gm-600 dark:hover:text-gm-400 transition-colors"
                  >
                    {t('nav.pricing')}
                  </Link>
                  <button
                    onClick={() => setShowFeedback(true)}
                    className="text-sm text-gray-600 dark:text-gray-400 hover:text-gm-600 dark:hover:text-gm-400 transition-colors"
                  >
                    {`💬 ${t('nav.feedback')}`}
                  </button>
                  <Button variant="secondary" size="sm" onClick={handleLogout}>
                    {t('nav.signOut')}
                  </Button>
                </div>

                {/* Mobile hamburger */}
                <button
                  className="sm:hidden flex items-center justify-center w-11 h-11 rounded-lg text-gray-600 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700 transition-colors"
                  onClick={() => setMenuOpen((v) => !v)}
                  aria-label={menuOpen ? t('nav.closeMenu') : t('nav.openMenu')}
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
              className="flex items-center gap-1.5 text-sm text-gray-600 dark:text-gray-400 hover:text-gm-600 dark:hover:text-gm-400 transition-colors"
            >
              {displayName ?? email}
              {isGuest && <GuestBadge />}
              {!isGuest && plan === 'PRO' && <ProBadge />}
            </Link>
            {role === 'ADMIN' && (
              <Link
                to="/admin"
                className="flex items-center gap-2 px-3 py-2 rounded-lg text-sm font-semibold bg-gm-700 hover:bg-gm-800 text-white transition-colors"
                onClick={() => setMenuOpen(false)}
              >
                <span>⚙️</span>
                <span>{t('nav.adminPanel')}</span>
              </Link>
            )}
            <Link
              to="/pricing"
              onClick={() => setMenuOpen(false)}
              className="text-sm text-gray-600 dark:text-gray-400 hover:text-gm-600 dark:hover:text-gm-400 transition-colors"
            >
              {t('nav.pricing')}
            </Link>
            <button
              onClick={() => { setMenuOpen(false); setShowFeedback(true) }}
              className="text-left text-sm text-gray-600 dark:text-gray-400 hover:text-gm-600 dark:hover:text-gm-400 transition-colors"
            >
              {`💬 ${t('nav.feedback')}`}
            </button>
            <Button variant="secondary" size="sm" onClick={handleLogout} className="w-full justify-center">
              {t('nav.signOut')}
            </Button>
          </div>
        )}
      </nav>

      <main className="flex-1 mx-auto w-full max-w-6xl px-4 py-6 md:py-8">{children}</main>

      <Footer />

      <FeedbackModal open={showFeedback} onClose={() => setShowFeedback(false)} />
    </div>
  )
}
