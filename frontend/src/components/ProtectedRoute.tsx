import { Navigate } from 'react-router-dom'
import { useAuthStore } from '@/store/auth'
import { ReactNode } from 'react'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken)
  if (!accessToken) return <Navigate to="/signin" replace />
  return <>{children}</>
}

export function AdminRoute({ children }: { children: ReactNode }) {
  const { isAuthenticated, role } = useAuthStore()
  if (!isAuthenticated) return <Navigate to="/signin" replace />
  if (role !== 'ADMIN') return <Navigate to="/" replace />
  return <>{children}</>
}
