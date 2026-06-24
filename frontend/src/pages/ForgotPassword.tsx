import { FormEvent, useState } from 'react'
import { Link } from 'react-router-dom'
import { authApi } from '@/api/auth'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { AxiosError } from 'axios'
import type { ApiError } from '@/types'

export function ForgotPassword() {
  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState('')

  const submit = async (e: FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      await authApi.forgotPassword(email)
      setSent(true)
    } catch (err) {
      const msg =
        err instanceof AxiosError
          ? ((err.response?.data as ApiError)?.message ?? 'Something went wrong')
          : 'Something went wrong'
      setError(msg)
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md">
        <h1 className="mb-6 text-center text-2xl font-bold text-gray-900 dark:text-gray-100">
          Forgot password
        </h1>
        {sent ? (
          <p className="text-sm text-gray-600 dark:text-gray-400 text-center">
            If an account with this email exists, we've sent a reset link. Check your inbox.
          </p>
        ) : (
          <form onSubmit={submit} className="flex flex-col gap-4">
            <Input
              label="Email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
            {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
            <Button type="submit" loading={loading} className="w-full justify-center">
              Send reset link
            </Button>
          </form>
        )}
        <p className="mt-4 text-center text-sm text-gray-600 dark:text-gray-400">
          <Link
            to="/signin"
            className="font-medium text-indigo-600 dark:text-indigo-400 hover:underline"
          >
            Back to sign in
          </Link>
        </p>
      </div>
    </div>
  )
}
