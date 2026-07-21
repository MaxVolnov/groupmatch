import { useTranslation } from 'react-i18next'
import { PublicLayout } from '@/components/PublicLayout'

export function About() {
  const { t } = useTranslation()
  return (
    <PublicLayout>
      <div className="max-w-2xl mx-auto py-12">
        <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-6">
          {t('about.title')}
        </h1>
        <p className="text-gray-600 dark:text-gray-400 mb-4">
          {t('about.description')}
        </p>
        <p className="text-gray-600 dark:text-gray-400 mb-8">
          {t('about.builtBy')}
        </p>
        <div className="border-t border-gray-200 dark:border-gray-700 pt-6">
          <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100 uppercase tracking-wide mb-3">
            {t('about.contact')}
          </h2>
          <p className="text-sm text-gray-600 dark:text-gray-400">
            {t('about.emailLabel')}:{' '}
            <a
              href="mailto:volnov.max@yandex.ru"
              className="text-indigo-600 dark:text-indigo-400 hover:underline"
            >
              volnov.max@yandex.ru
            </a>
          </p>
          <p className="text-sm text-gray-600 dark:text-gray-400">
            {t('about.phoneLabel')}:{' '}
            <a
              href="tel:+79201655073"
              className="text-indigo-600 dark:text-indigo-400 hover:underline"
            >
              +7 (920) 165-50-73
            </a>
          </p>
        </div>
      </div>
    </PublicLayout>
  )
}
