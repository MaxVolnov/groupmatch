import { FormEvent, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { invitesApi } from '@/api/invites'
import { authApi } from '@/api/auth'
import { useAuthStore } from '@/store/auth'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { PublicLayout } from '@/components/PublicLayout'
import { Skeleton } from '@/components/Skeleton'
import { Spinner } from '@/components/Spinner'
import { isInAppBrowser } from '@/utils/inAppBrowser'

/** Общие классы карточки — те же, что на экранах входа и регистрации. */
const CARD = 'mx-auto w-full max-w-sm rounded-xl border border-white/10 bg-gm-900/70 p-8 shadow-xl backdrop-blur-sm'

export function JoinInvite() {
  const { t } = useTranslation()
  const { token } = useParams<{ token: string }>()
  const navigate = useNavigate()
  const { isAuthenticated, login } = useAuthStore()
  const [error, setError] = useState('')

  /**
   * Что за приглашение — узнаём до всякой формы. Раньше протухшая ссылка
   * обнаруживалась только после того, как гость уже завёл аккаунт: он вводил
   * имя, жал кнопку и получал ошибку, оставаясь с пустым профилем на руках.
   */
  const { data: preview, isLoading: previewLoading } = useQuery({
    queryKey: ['invite-preview', token],
    queryFn: () => invitesApi.preview(token!),
    enabled: !!token,
    retry: false,
  })

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
  const [nameTaken, setNameTaken] = useState(false)
  const [copied, setCopied] = useState(false)

  const inApp = useMemo(() => isInAppBrowser(), [])

  /**
   * Сверяем имя, когда человек закончил его вводить. Не блокируем и не мешаем
   * печатать — просто предупреждаем о втором гостевом аккаунте.
   */
  const checkName = async () => {
    const value = displayName.trim()
    if (!token || value.length < 2) {
      setNameTaken(false)
      return
    }
    try {
      setNameTaken(await invitesApi.nameTaken(token, value))
    } catch {
      // Подсказка необязательная: если проверка не прошла, молча живём без неё.
      setNameTaken(false)
    }
  }

  const copyLink = async () => {
    try {
      await navigator.clipboard.writeText(window.location.href)
      setCopied(true)
      setTimeout(() => setCopied(false), 2000)
    } catch {
      setCopied(false)
    }
  }

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

  // ── Состояния ───────────────────────────────────────────────────────────────

  if (error) {
    return (
      <PublicLayout brandBackground>
        <div className={`${CARD} text-center`}>
          <p className="mb-2 text-lg font-semibold text-red-400">{t('auth.unableToJoin')}</p>
          <p className="mb-4 text-sm text-gray-400">{error}</p>
          <Link to="/" className="text-sm text-gm-400 hover:underline">
            {t('auth.goToDashboard')}
          </Link>
        </div>
      </PublicLayout>
    )
  }

  if (isAuthenticated) {
    return (
      <PublicLayout brandBackground>
        <div className={`${CARD} flex flex-col items-center gap-3`}>
          <Spinner size="lg" />
          <p className="text-sm text-gray-400">{t('auth.joiningGroup')}</p>
        </div>
      </PublicLayout>
    )
  }

  if (previewLoading) {
    return (
      <PublicLayout brandBackground>
        <div className={CARD}>
          <Skeleton className="mx-auto mb-3 h-6 w-3/4" />
          <Skeleton className="mx-auto mb-6 h-4 w-1/2" />
          <Skeleton className="mb-4 h-10 w-full" />
          <Skeleton className="h-10 w-full" />
          <p className="mt-4 text-center text-xs text-gray-500">{t('auth.inviteLoading')}</p>
        </div>
      </PublicLayout>
    )
  }

  /**
   * Мёртвая ссылка — экран без формы. Заводить аккаунт по приглашению, которое
   * уже не сработает, человеку незачем: он останется с гостевым профилем без
   * группы и без понимания, что произошло.
   */
  if (preview && !preview.valid) {
    const reason = preview.reason ?? 'not_found'
    return (
      <PublicLayout brandBackground>
        <div className={`${CARD} text-center`}>
          <p className="mb-2 text-lg font-semibold text-gray-100">{t('auth.inviteInvalidTitle')}</p>
          <p className="mb-6 text-sm text-gray-400">{t(`auth.inviteReason.${reason}`)}</p>
          <Link to="/" className="text-sm text-gm-400 hover:underline">
            {t('auth.goToDashboard')}
          </Link>
        </div>
      </PublicLayout>
    )
  }

  const groupName = preview?.groupName
  const inviterName = preview?.inviterName

  return (
    <PublicLayout brandBackground>
      <div className={CARD}>
        <h1 className="mb-1 text-center text-2xl font-bold text-gray-100">
          {t('auth.invitedTitle')}
        </h1>
        <p className="mb-6 text-center text-sm text-gray-400">
          {groupName
            ? inviterName
              ? t('auth.invitedBy', { name: inviterName, group: groupName })
              : t('auth.invitedToGroup', { group: groupName })
            : t('auth.invitedSubtitle')}
        </p>

        {inApp && (
          <div className="mb-5 rounded-lg border border-amber-500/30 bg-amber-500/10 p-3">
            <p className="text-xs leading-relaxed text-amber-200">{t('auth.inAppBrowserWarning')}</p>
            <button
              type="button"
              onClick={copyLink}
              className="mt-2 min-h-[44px] w-full rounded-lg border border-amber-500/40 px-3 text-xs font-medium text-amber-100 transition-colors hover:bg-amber-500/20"
            >
              {copied ? t('auth.linkCopied') : t('auth.copyLink')}
            </button>
          </div>
        )}

        <form onSubmit={submitGuest} className="flex flex-col gap-4">
          <Input
            label={t('auth.yourName')}
            value={displayName}
            onChange={(e) => setDisplayName(e.target.value)}
            onBlur={checkName}
            placeholder={t('auth.yourName')}
            minLength={2}
            maxLength={50}
            required
            autoFocus
          />
          {nameTaken && (
            <p className="text-xs leading-relaxed text-amber-300">{t('auth.nameTakenWarning')}</p>
          )}
          <Button
            type="submit"
            loading={loading}
            disabled={displayName.trim().length < 2}
            className="w-full justify-center"
          >
            {t('auth.joinAsGuest')}
          </Button>
        </form>

        <p className="mt-4 text-center text-sm text-gray-400">
          {t('auth.hasAccount')}{' '}
          <Link
            to={`/signin?next=${encodeURIComponent(`/join/${token}`)}`}
            className="font-medium text-gm-400 hover:text-gm-300"
          >
            {t('auth.signIn')}
          </Link>
        </p>
      </div>
    </PublicLayout>
  )
}
