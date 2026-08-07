import { FormEvent, useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { invitesApi } from '@/api/invites'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Spinner } from '@/components/Spinner'

export function JoinInvite() {
  const { t } = useTranslation()
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
        const msg = err?.response?.data?.message ?? t('auth.invalidInviteMessage')
        setError(msg)
      })
  }, [isAuthenticated, token, navigate, t])

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
        ?? t('errors.somethingWrong')
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  if (error) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md text-center">
          <p className="text-lg font-semibold text-red-600 dark:text-red-400 mb-2">{t('auth.unableToJoin')}</p>
          <p className="text-sm text-gray-600 dark:text-gray-400 mb-4">{error}</p>
          <Link to="/" className="text-gm-600 dark:text-gm-400 hover:underline text-sm">
            {t('auth.goToDashboard')}
          </Link>
        </div>
      </div>
    )
  }

  if (isAuthenticated) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900">
        <div className="flex flex-col items-center gap-3">
          <Spinner size="lg" />
          <p className="text-sm text-gray-600 dark:text-gray-400">{t('auth.joiningGroup')}</p>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md">
        <h1 className="mb-1 text-center text-2xl font-bold text-gray-900 dark:text-gray-100">
          {t('auth.invitedTitle')}
        </h1>
        <p className="mb-6 text-center text-sm text-gray-500 dark:text-gray-400">
          {t('auth.invitedSubtitle')}
        </p>
        <form onSubmit={submitGuest} className="flex flex-col gap-4">
          <Input
            label={t('auth.yourName')}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            placeholder={t('auth.yourName')}
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
            {t('auth.joinAsGuest')}
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-gray-600 dark:text-gray-400">
          {t('auth.hasAccount')}{' '}
          <Link
            to={`/signin?next=/join/${token}`}
            className="font-medium text-gm-600 dark:text-gm-400 hover:text-gm-700 dark:hover:text-gm-300"
          >
            {t('auth.signIn')}
          </Link>
        </p>
      </div>
    </div>
  )
}
