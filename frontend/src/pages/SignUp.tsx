import { FormEvent, useState } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { useLanguageStore } from '@/store/language'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { PublicLayout } from '@/components/PublicLayout'
import { AxiosError } from 'axios'
import type { ApiError } from '@/types'

export function SignUp() {
  const navigate = useNavigate()
  const login = useAuthStore((s) => s.login)
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated)
  const language = useLanguageStore((s) => s.language)
  const { t } = useTranslation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [displayName, setDisplayName] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [agreedToTerms, setAgreedToTerms] = useState(false)

  if (isAuthenticated) {
    return <Navigate to="/" replace />
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await authApi.signup({ email, password, displayName, locale: language })
      const data = await authApi.signin({ email, password })
      login(data.accessToken, data.refreshToken)
      navigate('/')
    } catch (err) {
      const axErr = err instanceof AxiosError ? err : null
      const apiErr = axErr?.response?.data as ApiError | undefined
      setError(apiErr?.message ?? t('auth.registrationFailed'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <PublicLayout>
      <div className="mx-auto w-full max-w-sm rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md">
        <h1 className="mb-6 text-center text-2xl font-bold text-gray-900 dark:text-gray-100">{t('auth.createAccount')}</h1>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <Input
            label={t('auth.displayName')}
            type="text"
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            required
            minLength={2}
            maxLength={50}
          />
          <Input
            label={t('auth.email')}
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
          />
          <Input
            label={t('auth.password')}
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            minLength={8}
            autoComplete="new-password"
          />
          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
          <label className="flex items-start gap-2 text-sm text-gray-600 dark:text-gray-400 cursor-pointer">
            <input
              type="checkbox"
              checked={agreedToTerms}
              onChange={(e) => setAgreedToTerms(e.target.checked)}
              className="mt-0.5 h-4 w-4 rounded border-gray-300 text-gm-600 focus:ring-gm-500 cursor-pointer"
            />
            <span>
              {t('auth.agreeToTerms')}{' '}
              <a href="/legal#terms" target="_blank" rel="noopener noreferrer"
                 className="text-gm-600 hover:underline">{t('auth.termsOfService')}</a>
              {' '}{t('auth.and')}{' '}
              <a href="/legal#privacy" target="_blank" rel="noopener noreferrer"
                 className="text-gm-600 hover:underline">{t('auth.privacyPolicy')}</a>
            </span>
          </label>
          <Button type="submit" loading={loading} disabled={!agreedToTerms || loading} className="mt-2 w-full justify-center">
            {t('auth.createAccount')}
          </Button>
        </form>
        <p className="mt-4 text-center text-sm text-gray-600 dark:text-gray-400">
          {t('auth.hasAccount')}{' '}
          <Link to="/signin" className="font-medium text-gm-600 dark:text-gm-400 hover:text-gm-700 dark:hover:text-gm-300">
            {t('auth.signIn')}
          </Link>
        </p>
      </div>
    </PublicLayout>
  )
}
