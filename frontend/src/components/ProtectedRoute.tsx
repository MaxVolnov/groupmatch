import { Navigate } from 'react-router-dom'
import { useAuthStore } from '@/store/auth'
import { ReactNode } from 'react'

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  if (!isAuthenticated) return <Navigate to="/signin" replace />
  return <>{children}</>
}
