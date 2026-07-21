import { Link } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { PublicLayout } from '@/components/PublicLayout'

const FREE_FEATURES = [
  'pricing.freeFeatures.groups',
  'pricing.freeFeatures.membership',
  'pricing.freeFeatures.heatmap',
  'pricing.freeFeatures.scheduling',
  'pricing.freeFeatures.notifications',
]

const PRO_FEATURES = [
  'pricing.proFeatures.groups',
  'pricing.proFeatures.membership',
  'pricing.proFeatures.everything',
  'pricing.proFeatures.support',
]

export function Pricing() {
  const { t } = useTranslation()
  return (
    <PublicLayout>
      <div className="py-12">
        <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100 text-center mb-3">
          {t('pricing.title')}
        </h1>
        <p className="text-center text-gray-500 dark:text-gray-400 mb-12">
          {t('pricing.subtitle')}
        </p>

        <div className="grid gap-6 sm:grid-cols-2 max-w-3xl mx-auto">
          {/* FREE */}
          <div className="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-8 flex flex-col">
            <p className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1">
              {t('pricing.free')}
            </p>
            <p className="text-4xl font-bold text-gray-900 dark:text-gray-100 mb-1">0 ₽</p>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">{t('pricing.alwaysFree')}</p>
            <ul className="flex flex-col gap-2 mb-8 flex-1">
              {FREE_FEATURES.map((f) => (
                <li key={f} className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
                  <span className="text-green-500 shrink-0">✓</span>
                  {t(f)}
                </li>
              ))}
            </ul>
            <Link
              to="/signup"
              className="block text-center rounded-lg border border-indigo-600 text-indigo-600 dark:text-indigo-400 dark:border-indigo-400 px-4 py-2.5 text-sm font-medium hover:bg-indigo-50 dark:hover:bg-indigo-900/20 transition-colors"
            >
              {t('pricing.getStarted')}
            </Link>
          </div>

          {/* PRO */}
          <div className="rounded-2xl border-2 border-indigo-500 bg-white dark:bg-gray-800 p-8 flex flex-col relative">
            <span className="absolute top-4 right-4 bg-indigo-600 text-white text-xs font-semibold px-2.5 py-1 rounded-full">
              {t('pricing.mostPopular')}
            </span>
            <p className="text-sm font-semibold text-indigo-600 dark:text-indigo-400 uppercase tracking-wide mb-1">
              {t('pricing.pro')}
            </p>
            <div className="mb-1">
              <span className="text-4xl font-bold text-gray-900 dark:text-gray-100">199 ₽</span>
              <span className="text-xl font-normal text-gray-500 dark:text-gray-400"> {t('pricing.perMonth')}</span>
            </div>
            <p className="text-sm mb-6">
              <span className="font-medium text-gray-900 dark:text-gray-100">{`1 490 ₽ ${t('pricing.perYear')}`}</span>
              <span className="text-gray-400 dark:text-gray-500 ml-2">{t('pricing.save38')}</span>
            </p>
            <p className="text-sm text-gray-500 dark:text-gray-400 -mt-4 mb-6">{t('pricing.forPowerUsers')}</p>
            <ul className="flex flex-col gap-2 mb-8 flex-1">
              {PRO_FEATURES.map((f) => (
                <li key={f} className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
                  <span className="text-green-500 shrink-0">✓</span>
                  {t(f)}
                </li>
              ))}
            </ul>
            <Link
              to="/profile"
              className="block text-center rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2.5 text-sm font-medium transition-colors"
            >
              {t('pricing.upgradeToPro')}
            </Link>
            <p className="text-xs text-center text-gray-500 mt-2">{t('pricing.comingSoon')}</p>
          </div>
        </div>

        <p className="text-center text-sm text-gray-500 dark:text-gray-400 mt-10">
          {t('pricing.footerNote')}
        </p>
      </div>
    </PublicLayout>
  )
}
