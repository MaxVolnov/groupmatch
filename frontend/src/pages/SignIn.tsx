import { FormEvent, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { AxiosError } from 'axios'
import type { ApiError } from '@/types'

export function SignIn() {
  const navigate = useNavigate()
  const login = useAuthStore((s) => s.login)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [showGuestForm, setShowGuestForm] = useState(false)
  const [guestName, setGuestName] = useState('')
  const [guestLoading, setGuestLoading] = useState(false)
  const [guestError, setGuestError] = useState('')

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const data = await authApi.signin({ email, password })
      login(data.accessToken, data.refreshToken)
      navigate('/')
    } catch (err) {
      const msg =
        err instanceof AxiosError
          ? ((err.response?.data as ApiError)?.message ?? t('auth.invalidCredentials'))
          : t('errors.somethingWrong')
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  // ── Guest section ────────────────────────────────────────────────────────────
  const submitGuest = async (e: FormEvent) => {
    e.preventDefault()
    setGuestError('')
    setGuestLoading(true)
    try {
      const data = await authApi.guest({ displayName: guestName })
      login(data.accessToken, data.refreshToken, guestName)
      navigate('/')
    } catch (err) {
      const msg =
        err instanceof AxiosError
          ? ((err.response?.data as ApiError)?.message ?? t('errors.somethingWrong'))
          : t('errors.somethingWrong')
      setGuestError(msg)
    } finally {
      setGuestLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md">
        <h1 className="mb-6 text-center text-2xl font-bold text-gray-900 dark:text-gray-100">{t('auth.signIn')}</h1>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <Input
            label={t('auth.email')}
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
          />
          <div className="flex flex-col gap-1">
            <Input
              label={t('auth.password')}
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
            />
            <Link
              to="/forgot-password"
              className="self-end text-sm text-indigo-600 dark:text-indigo-400 hover:underline"
            >
              {t('auth.forgotPassword')}
            </Link>
          </div>
          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
          <Button type="submit" loading={loading} className="mt-2 w-full justify-center">
            {t('auth.signIn')}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-gray-600 dark:text-gray-400">
          {t('auth.noAccount')}{' '}
          <Link to="/signup" className="font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300">
            {t('auth.signUp')}
          </Link>
        </p>

        <div className="mt-5 flex items-center gap-3">
          <div className="flex-1 h-px bg-gray-200 dark:bg-gray-700" />
          <span className="text-xs text-gray-400 dark:text-gray-500">{t('auth.orContinueWith')}</span>
          <div className="flex-1 h-px bg-gray-200 dark:bg-gray-700" />
        </div>

        {!showGuestForm ? (
          <button
            type="button"
            onClick={() => setShowGuestForm(true)}
            className="mt-4 w-full rounded-lg border border-gray-200 dark:border-gray-700 px-4 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
          >
            {t('auth.continueAsGuest')}
          </button>
        ) : (
          <form onSubmit={submitGuest} className="mt-4 flex flex-col gap-3">
            <Input
              label={t('auth.yourName')}
              value={guestName}
              onChange={(e) => setGuestName(e.target.value)}
              placeholder={t('auth.yourName')}
              minLength={2}
              maxLength={50}
              required
              autoFocus
            />
            {guestError && <p className="text-sm text-red-600 dark:text-red-400">{guestError}</p>}
            <Button
              type="submit"
              loading={guestLoading}
              disabled={guestName.trim().length < 2}
              className="w-full justify-center"
            >
              {t('auth.enterAsGuest')}
            </Button>
          </form>
        )}
      </div>
    </div>
  )
}
