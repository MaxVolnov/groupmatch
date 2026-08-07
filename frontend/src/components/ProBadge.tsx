import { useTranslation } from 'react-i18next'

/**
 * Бейдж тарифа Pro рядом с именем пользователя.
 *
 * Форма скопирована с GuestBadge в Layout.tsx, отличается только цветом —
 * индиго, тот же оттенок, что у метки Pro в Profile, чтобы не заводить
 * пятый акцентный цвет.
 */
export function ProBadge() {
  const { t } = useTranslation()
  return (
    <span
      title={t('trial.badgeHint')}
      className="text-xs bg-gm-50 dark:bg-gm-900/30 text-gm-600 dark:text-gm-400 px-2 py-0.5 rounded-full"
    >
      {t('trial.badge')}
    </span>
  )
}
