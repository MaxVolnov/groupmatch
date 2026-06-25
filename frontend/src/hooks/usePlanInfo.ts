import { useQuery } from '@tanstack/react-query'
import { meApi } from '@/api/me'
import { useAuthStore } from '@/store/auth'

export function usePlanInfo() {
  const { isAuthenticated, isGuest } = useAuthStore()
  return useQuery({
    queryKey: ['planInfo'],
    queryFn: meApi.getPlanInfo,
    enabled: isAuthenticated && !isGuest,
    staleTime: 60_000,
  })
}
