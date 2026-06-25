import { PublicLayout } from '@/components/PublicLayout'

export function About() {
  return (
    <PublicLayout>
      <div className="max-w-2xl mx-auto py-12">
        <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100 mb-6">
          About GroupMatch
        </h1>
        <p className="text-gray-600 dark:text-gray-400 mb-4">
          GroupMatch is a group scheduling tool that helps teams find the best time to meet.
          Mark your availability, see the overlap on a heatmap, and schedule meetings — no
          back-and-forth emails needed.
        </p>
        <p className="text-gray-600 dark:text-gray-400 mb-8">
          Built by Maxim Volnov as an indie project.
        </p>
        <div className="border-t border-gray-200 dark:border-gray-700 pt-6">
          <h2 className="text-sm font-semibold text-gray-900 dark:text-gray-100 uppercase tracking-wide mb-3">
            Contact
          </h2>
          <p className="text-sm text-gray-600 dark:text-gray-400">
            Email:{' '}
            <a
              href="mailto:volnov.max@yandex.ru"
              className="text-indigo-600 dark:text-indigo-400 hover:underline"
            >
              volnov.max@yandex.ru
            </a>
          </p>
          <p className="text-sm text-gray-600 dark:text-gray-400">
            Phone:{' '}
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
