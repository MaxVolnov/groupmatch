import { useState } from 'react'
import { useTranslation } from 'react-i18next'

const DISMISSED_KEY = 'groupmatch-trial-banner-dismissed'

function alreadyDismissed(): boolean {
  try {
    return localStorage.getItem(DISMISSED_KEY) === '1'
  } catch {
    return false
  }
}

/**
 * Одноразовый баннер про псевдо-премиум: показывается после регистрации,
 * закрывается крестиком и больше не возвращается (флаг в localStorage —
 * отдельного поля на пользователе для этого заводить не за чем).
 *
 * Форма — как у гостевого баннера на Dashboard.
 *
 * @param trialExpiresAt ISO-дата окончания триала; null/прошедшая — не показываем
 */
export function TrialBanner({ trialExpiresAt }: { trialExpiresAt?: string | null }) {
  const { t, i18n } = useTranslation()
  const [dismissed, setDismissed] = useState(alreadyDismissed)

  if (dismissed || !trialExpiresAt) return null

  const expiresAt = new Date(trialExpiresAt)
  if (Number.isNaN(expiresAt.getTime()) || expiresAt.getTime() <= Date.now()) return null

  const dismiss = () => {
    try {
      localStorage.setItem(DISMISSED_KEY, '1')
    } catch {
      // приватный режим — баннер просто вернётся при следующей загрузке
    }
    setDismissed(true)
  }

  const until = expiresAt.toLocaleDateString(i18n.language === 'en' ? 'en-US' : 'ru-RU', {
    day: 'numeric',
    month: 'long',
    year: 'numeric',
  })

  return (
    <div className="mb-4 flex items-start justify-between gap-4 rounded-lg border border-gm-200 dark:border-gm-800 bg-gm-50 dark:bg-gm-900/20 px-4 py-3 text-sm text-gm-800 dark:text-gm-300">
      <span>
        {t('trial.bannerTitle')} {t('trial.bannerUntil', { date: until })}{' '}
        <span className="opacity-90">{t('trial.bannerAfter')}</span>
      </span>
      <button
        onClick={dismiss}
        aria-label={t('trial.bannerDismiss')}
        className="shrink-0 rounded px-2 text-gm-500 transition-colors hover:text-gm-700 dark:hover:text-gm-200"
      >
        ✕
      </button>
    </div>
  )
}
