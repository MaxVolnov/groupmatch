import { Navigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '@/store/auth'
import { ReactNode } from 'react'
import { withNext } from '@/utils/nextPath'

/**
 * Отправляя на вход, запоминаем, откуда пришли: иначе человек, перешедший по
 * ссылке на конкретную группу или приглашение, после входа оказывается на
 * дашборде и не понимает, куда делось то, что он открывал.
 */
function signInWithReturnTo(pathname: string, search: string): string {
  return withNext('/signin', `${pathname}${search}`)
}

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken)
  const { pathname, search } = useLocation()
  if (!accessToken) return <Navigate to={signInWithReturnTo(pathname, search)} replace />
  return <>{children}</>
}

export function AdminRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, role } = useAuthStore()
  const { pathname, search } = useLocation()
  if (!isAuthenticated) return <Navigate to={signInWithReturnTo(pathname, search)} replace />
  // Вошёл, но не админ — возвращать его на этот же адрес после входа
  // бессмысленно, он снова упрётся в ту же проверку.
  if (role !== 'ADMIN') return <Navigate to="/" replace />
  return <>{children}</>
}
