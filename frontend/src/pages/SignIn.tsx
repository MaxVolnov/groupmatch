import { FormEvent, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { AxiosError } from 'axios'
import type { ApiError } from '@/types'

export function SignIn() {
  const navigate = useNavigate()
  const login = useAuthStore((s) => s.login)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

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
          ? ((err.response?.data as ApiError)?.message ?? 'Invalid credentials')
          : 'Something went wrong'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  // ── Guest section ────────────────────────────────────────────────────────────
  const [showGuestForm, setShowGuestForm] = useState(false)
  const [guestName, setGuestName] = useState('')
  const [guestLoading, setGuestLoading] = useState(false)
  const [guestError, setGuestError] = useState('')

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
          ? ((err.response?.data as ApiError)?.message ?? 'Something went wrong')
          : 'Something went wrong'
      setGuestError(msg)
    } finally {
      setGuestLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md">
        <h1 className="mb-6 text-center text-2xl font-bold text-gray-900 dark:text-gray-100">Sign in</h1>
        <form onSubmit={submit} className="flex flex-col gap-4">
          <Input
            label="Email"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
            autoComplete="email"
          />
          <Input
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
            autoComplete="current-password"
          />
          {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
          <Button type="submit" loading={loading} className="mt-2 w-full justify-center">
            Sign in
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-gray-600 dark:text-gray-400">
          No account?{' '}
          <Link to="/signup" className="font-medium text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300">
            Sign up
          </Link>
        </p>

        <div className="mt-5 flex items-center gap-3">
          <div className="flex-1 h-px bg-gray-200 dark:bg-gray-700" />
          <span className="text-xs text-gray-400 dark:text-gray-500">or</span>
          <div className="flex-1 h-px bg-gray-200 dark:bg-gray-700" />
        </div>

        {!showGuestForm ? (
          <button
            type="button"
            onClick={() => setShowGuestForm(true)}
            className="mt-4 w-full rounded-lg border border-gray-200 dark:border-gray-700 px-4 py-2 text-sm text-gray-600 dark:text-gray-400 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
          >
            Continue as guest
          </button>
        ) : (
          <form onSubmit={submitGuest} className="mt-4 flex flex-col gap-3">
            <Input
              label="Your name"
              value={guestName}
              onChange={(e) => setGuestName(e.target.value)}
              placeholder="Your name"
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
              Enter as guest
            </Button>
          </form>
        )}
      </div>
    </div>
  )
}
