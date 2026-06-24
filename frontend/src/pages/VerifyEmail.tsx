import { useEffect, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { Button } from '@/components/Button'

export function VerifyEmail() {
  const [searchParams] = useSearchParams()
  const navigate = useNavigate()
  const accessToken = useAuthStore((s) => s.accessToken)
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading')
  const [resendSent, setResendSent] = useState(false)

  useEffect(() => {
    const token = searchParams.get('token')
    if (!token) {
      setStatus('error')
      return
    }
    authApi.verifyEmail(token)
      .then(() => setStatus('success'))
      .catch(() => setStatus('error'))
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const handleResend = async () => {
    await authApi.resendVerification()
    setResendSent(true)
  }

  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 dark:bg-gray-900 px-4">
      <div className="w-full max-w-sm rounded-xl bg-white dark:bg-gray-800 p-8 shadow-md text-center">
        {status === 'loading' && (
          <p className="text-gray-600 dark:text-gray-400">Confirming your email...</p>
        )}
        {status === 'success' && (
          <>
            <p className="text-4xl mb-4">✅</p>
            <p className="text-gray-900 dark:text-gray-100 font-medium mb-6">
              Email confirmed! You can now use all features.
            </p>
            <Button onClick={() => navigate('/')} className="w-full justify-center">
              Back to app
            </Button>
          </>
        )}
        {status === 'error' && (
          <>
            <p className="text-4xl mb-4">❌</p>
            <p className="text-gray-900 dark:text-gray-100 font-medium mb-6">
              Link is invalid or expired. Request a new one.
            </p>
            <div className="flex flex-col gap-3">
              <Button onClick={() => navigate('/')} className="w-full justify-center">
                Back to app
              </Button>
              {accessToken && !resendSent && (
                <button
                  onClick={handleResend}
                  className="text-sm text-indigo-600 dark:text-indigo-400 hover:underline"
                >
                  Resend verification email
                </button>
              )}
              {resendSent && (
                <p className="text-sm text-green-600 dark:text-green-400">Verification email sent.</p>
              )}
            </div>
          </>
        )}
      </div>
    </div>
  )
}
