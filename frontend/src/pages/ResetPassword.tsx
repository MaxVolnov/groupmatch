import { FormEvent, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { authApi } from '@/api/auth'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { AxiosError } from 'axios'
import type { ApiError } from '@/types'

export function ResetPassword() {
  const { t } = useTranslation()
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const [newPassword, setNewPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [success, setSuccess] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const token = searchParams.get('token') ?? ''

  if (!token) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
        <div className="w-full max-w-sm rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md text-center">
          <p className="text-4xl mb-4">❌</p>
          <p className="text-gray-900 dark:text-gray-100 font-medium mb-6">{t('auth.invalidResetLink')}</p>
          <Button onClick={() => navigate('/forgot-password')} className="w-full justify-center">
            {t('auth.requestNewLink')}
          </Button>
        </div>
      </div>
    )
  }

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    if (newPassword !== confirm) {
      setError(t('auth.passwordsDontMatch'))
      return
    }
    setError('')
    setLoading(true)
    try {
      await authApi.resetPassword(token, newPassword)
      setSuccess(true)
    } catch (err) {
      const msg =
        err instanceof AxiosError
          ? ((err.response?.data as ApiError)?.message ?? t('auth.invalidResetLink'))
          : t('errors.somethingWrong')
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  if (success) {
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
        <div className="w-full max-w-sm rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md text-center">
          <p className="text-4xl mb-4">✅</p>
          <p className="text-gray-900 dark:text-gray-100 font-medium mb-6">
            {t('auth.passwordUpdated')}
          </p>
          <Button onClick={() => navigate('/signin')} className="w-full justify-center">
            {t('auth.signIn')}
          </Button>
        </div>
      </div>
    )
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md">
        <h1 className="mb-6 text-center text-2xl font-bold text-gray-900 dark:text-gray-100">
          {t('auth.resetPasswordTitle')}
        </h1>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <Input
            label={t('auth.newPassword')}
            type="password"
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
            minLength={8}
            maxLength={128}
            required
            autoComplete="new-password"
          />
          <Input
            label={t('auth.confirmPassword')}
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            minLength={8}
            maxLength={128}
            required
            autoComplete="new-password"
          />
          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
          <Button
            type="submit"
            loading={loading}
            disabled={!token}
            className="w-full justify-center"
          >
            {t('auth.updatePassword')}
          </Button>
        </form>
      </div>
    </div>
  )
}
