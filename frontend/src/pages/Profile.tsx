import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { meApi } from '@/api/me'
import { paymentsApi } from '@/api/payments'
import { preferencesApi } from '@/api/preferences'
import { useAuthStore } from '@/store/auth'
import { useLanguageStore } from '@/store/language'
import { usePlanInfo } from '@/hooks/usePlanInfo'
import { Layout } from '@/components/Layout'
import { Button } from '@/components/Button'
import { Input } from '@/components/Input'
import { Skeleton } from '@/components/Skeleton'
import { ErrorMessage } from '@/components/ErrorMessage'
import { TIMEZONES } from '@/utils/timezones'
import type { NotificationPreferences } from '@/types'

export function Profile() {
  const qc = useQueryClient()
  const { isGuest, setProfile } = useAuthStore()
  const upgradeGuest = useAuthStore((s) => s.upgradeGuest)
  const { language, setLanguage } = useLanguageStore()
  const { t } = useTranslation()

  const { data, isLoading, error: loadError } = useQuery({
    queryKey: ['me'],
    queryFn: meApi.get,
  })

  const [displayName, setDisplayName] = useState('')
  const [tzId, setTzId] = useState('')

  useEffect(() => {
    if (data) {
      setDisplayName(data.displayName)
      setTzId(data.tzId)
    }
  }, [data])

  const update = useMutation({
    mutationFn: () => meApi.update({ displayName, tzId }),
    onSuccess: (updated) => {
      setProfile(updated.id, updated.email, updated.displayName, updated.role, updated.plan)
      qc.invalidateQueries({ queryKey: ['me'] })
    },
  })

  const { data: prefs } = useQuery({
    queryKey: ['notification-preferences'],
    queryFn: preferencesApi.get,
    enabled: !isGuest,
  })

  const updatePrefs = useMutation({
    mutationFn: preferencesApi.update,
    onSuccess: () => qc.invalidateQueries({ queryKey: ['notification-preferences'] }),
  })

  const toggle = (key: keyof NotificationPreferences) => {
    if (!prefs) return
    updatePrefs.mutate({ [key]: !prefs[key] })
  }

  const { data: planInfo } = usePlanInfo()

  const { data: subscription } = useQuery({
    queryKey: ['subscription'],
    queryFn: paymentsApi.getSubscription,
    enabled: !isGuest,
  })

  const createPayment = useMutation({
    mutationFn: paymentsApi.createPayment,
    onSuccess: (res) => {
      if (res.confirmationUrl) {
        window.location.href = res.confirmationUrl
      } else {
        alert(t('profile.paymentNotConfigured'))
      }
    },
  })

  useEffect(() => {
    const params = new URLSearchParams(window.location.search)
    if (params.get('payment') === 'success') {
      qc.invalidateQueries({ queryKey: ['me'] })
      qc.invalidateQueries({ queryKey: ['planInfo'] })
      qc.invalidateQueries({ queryKey: ['subscription'] })
      window.history.replaceState({}, '', window.location.pathname)
    }
  }, [qc])

  const [upgradeEmail, setUpgradeEmail] = useState('')
  const [upgradePassword, setUpgradePassword] = useState('')
  const [upgradeDisplayName, setUpgradeDisplayName] = useState('')
  const [upgradeError, setUpgradeError] = useState<string | null>(null)
  const [upgradeSuccess, setUpgradeSuccess] = useState(false)
  const [upgradeLoading, setUpgradeLoading] = useState(false)

  const handleUpgrade = async () => {
    setUpgradeError(null)
    if (upgradePassword.length < 8) {
      setUpgradeError(t('profile.passwordTooShort'))
      return
    }
    setUpgradeLoading(true)
    try {
      await upgradeGuest({ email: upgradeEmail, password: upgradePassword, displayName: upgradeDisplayName })
      setUpgradeSuccess(true)
    } catch (e: unknown) {
      const err = e as { response?: { data?: { message?: string } } }
      setUpgradeError(err?.response?.data?.message ?? t('errors.somethingWrong'))
    } finally {
      setUpgradeLoading(false)
    }
  }

  return (
    <Layout>
      <div className="max-w-lg">
        <div className="flex items-center gap-3 mb-6">
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">{t('profile.myProfile')}</h1>
          {isGuest && (
            <span className="text-xs bg-gray-100 dark:bg-gray-700 text-gray-500 dark:text-gray-400 px-2 py-0.5 rounded-full">
              {t('profile.guest')}
            </span>
          )}
        </div>
        {isGuest && (
          <p className="mb-4 text-sm text-gray-500 dark:text-gray-400 rounded-lg border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 px-4 py-3">
            {t('profile.guestNote')}
          </p>
        )}

        {loadError && <ErrorMessage error={loadError} />}

        {isLoading ? (
          <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6 flex flex-col gap-4">
            <Skeleton className="h-5 w-24" />
            <Skeleton className="h-10 w-full" />
            <Skeleton className="h-5 w-20" />
            <Skeleton className="h-10 w-full" />
          </div>
        ) : data && (
          <div className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-6 flex flex-col gap-5">
            {/* Read-only info */}
            <div className="flex flex-col gap-1">
              <span className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('profile.email')}</span>
              <span className="text-sm text-gray-900 dark:text-gray-100">{data.email}</span>
            </div>

            {/* Language switcher */}
            <div>
              <label className="block text-sm font-medium text-gray-700 dark:text-gray-300 mb-1">
                {t('profile.language')}
              </label>
              <div className="flex gap-2">
                <button
                  onClick={() => setLanguage('ru')}
                  className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                    language === 'ru'
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'
                  }`}
                >
                  {t('profile.languageRu')}
                </button>
                <button
                  onClick={() => setLanguage('en')}
                  className={`px-4 py-2 rounded-lg text-sm font-medium transition-colors ${
                    language === 'en'
                      ? 'bg-blue-600 text-white'
                      : 'bg-gray-100 dark:bg-gray-700 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-600'
                  }`}
                >
                  {t('profile.languageEn')}
                </button>
              </div>
            </div>

            {import.meta.env.VITE_MONETIZATION_ENABLED !== 'true' && (
              <div className="flex flex-col gap-1">
                <span className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">
                  {t('profile.plan')}
                </span>
                <span className="text-sm text-gray-900 dark:text-gray-100">
                  {data.plan === 'PRO' ? t('profile.pro') : t('profile.free')}
                </span>
              </div>
            )}
            {import.meta.env.VITE_MONETIZATION_ENABLED === 'true' && (
              <div className="flex flex-col gap-3">
                <span className="text-xs font-medium text-gray-500 dark:text-gray-400 uppercase tracking-wide">
                  {t('profile.planBilling')}
                </span>
                {data.plan === 'FREE' && (
                  <div className="flex items-center justify-between gap-3 flex-wrap">
                    <div>
                      <span className="text-sm font-medium text-gray-900 dark:text-gray-100">{t('profile.free')}</span>
                      <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                        {planInfo
                          ? `${planInfo.ownedGroups} / ${planInfo.groupLimit} ${t('profile.groupsUsed')}`
                          : t('profile.upToGroups')}
                      </p>
                    </div>
                    <div className="flex gap-2 flex-wrap">
                      <Button
                        size="sm"
                        variant="primary"
                        loading={createPayment.isPending}
                        onClick={() => createPayment.mutate({ periodMonths: 1 })}
                      >
                        {t('profile.monthly')}
                      </Button>
                      <Button
                        size="sm"
                        variant="secondary"
                        loading={createPayment.isPending}
                        onClick={() => createPayment.mutate({ periodMonths: 12 })}
                      >
                        {t('profile.yearly')}
                      </Button>
                    </div>
                  </div>
                )}
                {data.plan === 'PRO' && (
                  <div className="flex items-center justify-between">
                    <div>
                      <span className="inline-flex items-center gap-1.5 text-sm font-medium text-indigo-600 dark:text-indigo-400">
                        <span>⚡</span> {t('pricing.pro')}
                      </span>
                      {subscription?.status === 'ACTIVE' && subscription.expiresAt && (
                        <p className="text-xs text-gray-500 dark:text-gray-400 mt-0.5">
                          {t('profile.renews')} {new Date(subscription.expiresAt).toLocaleDateString('ru-RU')}
                        </p>
                      )}
                      {subscription?.status === 'EXPIRED' && (
                        <p className="text-xs text-red-500 mt-0.5">{t('profile.subscriptionExpired')}</p>
                      )}
                    </div>
                    <span className="text-xs text-gray-400 dark:text-gray-500">{t('profile.unlimitedGroups')}</span>
                  </div>
                )}
              </div>
            )}

            <div className="border-t border-gray-200 dark:border-gray-700" />

            {/* Editable fields */}
            <Input
              label={t('profile.displayName')}
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              minLength={2}
              maxLength={50}
              required
            />

            <div className="flex flex-col gap-1">
              <label className="text-sm font-medium text-gray-700 dark:text-gray-300">{t('profile.timezone')}</label>
              <select
                value={tzId}
                onChange={(e) => setTzId(e.target.value)}
                className="rounded-lg border border-gray-300 dark:border-gray-600 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 bg-white dark:bg-gray-700 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                {TIMEZONES.map((tz) => (
                  <option key={tz.value} value={tz.value}>{tz.label}</option>
                ))}
              </select>
            </div>

            {update.error && <ErrorMessage error={update.error} />}
            {update.isSuccess && (
              <p className="text-sm text-green-600 dark:text-green-400">{t('profile.saved')}</p>
            )}

            <Button
              loading={update.isPending}
              disabled={!displayName.trim()}
              onClick={() => update.mutate()}
              className="w-full justify-center"
            >
              {t('profile.saveChanges')}
            </Button>
          </div>
        )}

        {isGuest && (
          <section className="rounded-xl border border-indigo-200 dark:border-indigo-800 bg-indigo-50/40 dark:bg-indigo-900/10 p-5 mt-6">
            <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-1">
              {t('profile.setUpAccount')}
            </h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-4">
              {t('profile.setUpAccountDesc')}
            </p>
            {upgradeSuccess ? (
              <p className="text-sm text-green-600 dark:text-green-400">
                {t('profile.accountCreated')}
              </p>
            ) : (
              <div className="space-y-3">
                <input
                  type="text"
                  placeholder={t('profile.namePlaceholder')}
                  value={upgradeDisplayName}
                  onChange={(e) => setUpgradeDisplayName(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
                <input
                  type="email"
                  placeholder={t('profile.emailPlaceholder')}
                  value={upgradeEmail}
                  onChange={(e) => setUpgradeEmail(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
                <input
                  type="password"
                  placeholder={t('profile.passwordPlaceholder')}
                  value={upgradePassword}
                  onChange={(e) => setUpgradePassword(e.target.value)}
                  className="w-full rounded-lg border border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-800 px-3 py-2 text-sm text-gray-900 dark:text-gray-100 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                />
                {upgradeError && (
                  <p className="text-sm text-red-600 dark:text-red-400">{upgradeError}</p>
                )}
                <button
                  onClick={handleUpgrade}
                  disabled={upgradeLoading || !upgradeEmail || !upgradePassword || !upgradeDisplayName}
                  className="w-full rounded-lg bg-indigo-600 hover:bg-indigo-700 disabled:opacity-50 px-4 py-2 text-sm font-medium text-white transition-colors"
                >
                  {upgradeLoading ? t('profile.settingUp') : t('profile.createAccount')}
                </button>
              </div>
            )}
          </section>
        )}

        {!isGuest && (
          <section className="rounded-xl border border-gray-200 dark:border-gray-700 p-5 mt-6">
            <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100 mb-4">
              {t('profile.notificationPreferences')}
            </h2>
            {prefs ? (
              <div className="space-y-3">
                {(
                  [
                    ['emailMemberJoined',    'profile.emailMemberJoined'],
                    ['emailMeetingReminder', 'profile.emailMeetingReminder'],
                    ['inappMemberJoined',    'profile.inappMemberJoined'],
                    ['inappMeetingCreated',  'profile.inappMeetingCreated'],
                  ] as const
                ).map(([key, labelKey]) => (
                  <label key={key} className="flex items-center justify-between cursor-pointer">
                    <span className="text-sm text-gray-700 dark:text-gray-300">{t(labelKey)}</span>
                    <button
                      role="switch"
                      aria-checked={prefs[key]}
                      onClick={() => toggle(key)}
                      className={`relative inline-flex h-5 w-9 shrink-0 rounded-full transition-colors ${
                        prefs[key] ? 'bg-indigo-600' : 'bg-gray-300 dark:bg-gray-600'
                      }`}
                    >
                      <span
                        className={`inline-block h-4 w-4 mt-0.5 rounded-full bg-white shadow transition-transform ${
                          prefs[key] ? 'translate-x-4' : 'translate-x-0.5'
                        }`}
                      />
                    </button>
                  </label>
                ))}
              </div>
            ) : (
              <p className="text-sm text-gray-400">{t('common.loading')}</p>
            )}
          </section>
        )}
      </div>
    </Layout>
  )
}
