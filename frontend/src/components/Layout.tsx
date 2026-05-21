import { useState, ReactNode } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuthStore } from '@/store/auth'
import { Button } from './Button'

interface LayoutProps {
  children: ReactNode
}

export function Layout({ children }: LayoutProps) {
  const { isAuthenticated, displayName, email, logout } = useAuthStore()
  const navigate = useNavigate()
  const [menuOpen, setMenuOpen] = useState(false)

  const handleLogout = () => {
    setMenuOpen(false)
    logout()
    navigate('/signin')
  }

  return (
    <div className="min-h-screen bg-gray-50 overflow-x-hidden">
      <nav className="border-b bg-white shadow-sm">
        <div className="mx-auto flex max-w-6xl items-center justify-between px-4 py-3">
          <Link
            to="/"
            className="text-lg font-bold text-indigo-600"
            onClick={() => setMenuOpen(false)}
          >
            GroupMatch
          </Link>

          {isAuthenticated && (
            <>
              {/* Desktop */}
              <div className="hidden sm:flex items-center gap-4">
                <span className="text-sm text-gray-600">{displayName ?? email}</span>
                <Button variant="secondary" size="sm" onClick={handleLogout}>
                  Sign out
                </Button>
              </div>

              {/* Mobile hamburger */}
              <button
                className="sm:hidden flex items-center justify-center w-11 h-11 rounded-lg text-gray-600 hover:bg-gray-100 transition-colors"
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

        {/* Mobile dropdown */}
        {isAuthenticated && menuOpen && (
          <div className="sm:hidden border-t bg-white px-4 py-4 flex flex-col gap-3">
            <span className="text-sm text-gray-600">{displayName ?? email}</span>
            <Button variant="secondary" size="sm" onClick={handleLogout} className="w-full justify-center">
              Sign out
            </Button>
          </div>
        )}
      </nav>

      <main className="mx-auto max-w-6xl px-4 py-6 md:py-8">{children}</main>
    </div>
  )
}
