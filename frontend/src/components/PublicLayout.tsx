import { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/store/auth'
import { Footer } from './Footer'

interface Props {
  children: ReactNode
  /**
   * Фирменный градиентный фон вместо плоского — для экранов входа и
   * регистрации. Включается точечно, а не для всего PublicLayout: этот же
   * layout использует /pricing, которому градиент не нужен.
   *
   * Фон тёмный по природе, поэтому вместе с ним включается класс `dark`.
   * Это не «тёмная тема пользователя», а свойство поверхности: карточка,
   * поля и подписи получают уже существующие тёмные варианты и остаются
   * читаемыми независимо от того, что выбрано в переключателе тем.
   */
  brandBackground?: boolean
}

export function PublicLayout({ children, brandBackground = false }: Props) {
  const { isAuthenticated } = useAuthStore()
  const { t } = useTranslation()
  const { pathname } = useLocation()

  return (
    <div
      className={
        brandBackground
          ? 'dark min-h-screen flex flex-col bg-gm-auth'
          : 'min-h-screen flex flex-col bg-gray-50 dark:bg-gray-900'
      }
    >
      <nav
        className={
          brandBackground
            ? 'border-b border-white/10'
            : 'border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 shadow-sm'
        }
      >
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link to="/" className="text-lg font-bold text-gm-600 dark:text-gm-400">
            GroupMatch
            <span className={`ml-1.5 align-top text-[11px] font-normal ${
              brandBackground ? 'text-gray-300' : 'text-gray-500 dark:text-gray-400'
            }`}>beta</span>
          </Link>
          <div className="flex items-center gap-3">
            {isAuthenticated ? (
              <Link
                to="/"
                className="text-sm font-medium text-gm-600 dark:text-gm-400 hover:text-gm-700 dark:hover:text-gm-300 transition-colors"
              >
                {t('nav.openApp')}
              </Link>
            ) : (
              <>
                {/* Don't offer the action the visitor is already on */}
                {pathname !== '/signin' && (
                  <Link
                    to="/signin"
                    className={`text-sm transition-colors ${
                      brandBackground
                        ? 'text-gray-300 hover:text-white'
                        : 'text-gray-600 dark:text-gray-400 hover:text-gm-600 dark:hover:text-gm-400'
                    }`}
                  >
                    {t('nav.signIn')}
                  </Link>
                )}
                {pathname !== '/signup' && (
                  <Link
                    to="/signup"
                    className="text-sm font-medium bg-gm-600 hover:bg-gm-700 text-white px-4 py-2 rounded-lg transition-colors"
                  >
                    {t('nav.signUp')}
                  </Link>
                )}
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
