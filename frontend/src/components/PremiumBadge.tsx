import { useTranslation } from 'react-i18next'

/**
 * Бейдж премиум-доступа рядом с именем пользователя.
 *
 * Форма скопирована с GuestBadge в Layout.tsx, отличается только цветом —
 * индиго, тот же оттенок, что у метки Pro в Profile, чтобы не заводить
 * пятый акцентный цвет.
 */
export function PremiumBadge() {
  const { t } = useTranslation()
  return (
    <span
      title={t('premium.badgeHint')}
      className="text-xs bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 px-2 py-0.5 rounded-full"
    >
      {t('premium.badge')}
    </span>
  )
}
