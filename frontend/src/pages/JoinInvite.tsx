import { FormEvent, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { invitesApi } from '@/api/invites'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Spinner } from '@/components/Spinner'

export function JoinInvite() {
  const { token } = useParams<{ token: string }>()
  const navigate = useNavigate()
  const { isAuthenticated, login } = useAuthStore()
  const [error, setError] = useState('')

  // ── Authenticated path: join immediately ────────────────────────────────────
  useEffect(() => {
    if (!isAuthenticated || !token) return
    invitesApi
      .join(token)
      .then((invite) => navigate(`/groups/${invite.groupId}`))
      .catch((err) => {
        const msg = err?.response?.data?.message ?? 'This invite is invalid or has expired'
        setError(msg)
      })
  }, [isAuthenticated, token, navigate])

  // ── Unauthenticated path: guest join form ───────────────────────────────────
  const [displayName, setDisplayName] = useState('')
  const [loading, setLoading] = useState(false)

  const submitGuest = async (e: FormEvent) => {
    e.preventDefault()
    if (!token) return
    setError('')
    setLoading(true)
    try {
      const auth = await authApi.guest({ displayName })
      login(auth.accessToken, auth.refreshToken, displayName)
      const invite = await invitesApi.join(token)
      navigate(`/groups/${invite.groupId}`)
    } catch (err: unknown) {
      const msg = (err as { response?: { data?: { message?: string } } })?.response?.data?.message
        ?? 'Something went wrong. Please try again.'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

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

  if (isAuthenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="flex flex-col items-center gap-3">
          <Spinner size="lg" />
          <p className="text-sm text-gray-600 dark:text-gray-400">Joining group…</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md">
        <h1 className="mb-1 text-center text-2xl font-bold text-gray-900 dark:text-gray-100">
          You've been invited
        </h1>
        <p className="mb-6 text-center text-sm text-gray-500 dark:text-gray-400">
          Enter your name to join
        </p>
        <form onSubmit={submitGuest} className="flex flex-col gap-4">
          <Input
            label="Your name"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder="Your name"
            minLength={2}
            maxLength={50}
            required
            autoFocus
          />
          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
          <Button
            type="submit"
            loading={loading}
            disabled={displayName.trim().length < 2}
            className="w-full justify-center"
          >
            Join as guest
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-gray-600 dark:text-gray-400">
          Already have an account?{' '}
          <Link
            to={`/signin?next=/join/${token}`}
            className="font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300"
          >
            Sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
