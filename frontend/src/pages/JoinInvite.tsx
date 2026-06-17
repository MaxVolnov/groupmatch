import { useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { invitesApi } from '@/api/invites'
import { Spinner } from '@/components/Spinner'

export function JoinInvite() {
  const { token } = useParams<{ token: string }>()
  const navigate = useNavigate()
  const [error, setError] = useState('')

  useEffect(() => {
    if (!token) return
    invitesApi
      .join(token)
      .then((invite) => navigate(`/groups/${invite.groupId}`))
      .catch((err) => {
        const msg = err?.response?.data?.message ?? 'This invite is invalid or has expired'
        setError(msg)
      })
  }, [token, navigate])

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md text-center">
          <p className="text-lg font-semibold text-red-600 dark:text-red-400 mb-2">Unable to join</p>
          <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">{error}</p>
          <a href="/" className="text-indigo-600 dark:text-indigo-400 hover:underline text-sm">
            Go to dashboard
          </a>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900">
      <div className="flex flex-col items-center gap-3">
        <Spinner size="lg" />
        <p className="text-sm text-gray-600 dark:text-gray-400">Joining group…</p>
      </div>
    </div>
  )
}
