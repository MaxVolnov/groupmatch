import { Link } from 'react-router-dom'
import { PublicLayout } from '@/components/PublicLayout'

const FREE_FEATURES = [
  'Up to 3 groups as owner',
  'Unlimited group membership',
  'Availability heatmap',
  'Meeting scheduling',
  'Email notifications',
]

const PRO_FEATURES = [
  'Unlimited groups as owner',
  'Unlimited group membership',
  'Everything in Free',
  'Priority support',
]

export function Pricing() {
  return (
    <PublicLayout>
      <div className="py-12">
        <h1 className="text-3xl font-bold text-gray-900 dark:text-gray-100 text-center mb-3">
          Simple pricing
        </h1>
        <p className="text-center text-gray-500 dark:text-gray-400 mb-12">
          No surprises. Cancel anytime.
        </p>

        <div className="grid gap-6 sm:grid-cols-2 max-w-3xl mx-auto">
          {/* FREE */}
          <div className="rounded-2xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-800 p-8 flex flex-col">
            <p className="text-sm font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1">
              Free
            </p>
            <p className="text-4xl font-bold text-gray-900 dark:text-gray-100 mb-1">0 ₽</p>
            <p className="text-sm text-gray-500 dark:text-gray-400 mb-6">Always free</p>
            <ul className="flex flex-col gap-2 mb-8 flex-1">
              {FREE_FEATURES.map((f) => (
                <li key={f} className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
                  <span className="text-green-500 shrink-0">✓</span>
                  {f}
                </li>
              ))}
            </ul>
            <Link
              to="/signup"
              className="block text-center rounded-lg border border-indigo-600 text-indigo-600 dark:text-indigo-400 dark:border-indigo-400 px-4 py-2.5 text-sm font-medium hover:bg-indigo-50 dark:hover:bg-indigo-900/20 transition-colors"
            >
              Get started
            </Link>
          </div>

          {/* PRO */}
          <div className="rounded-2xl border-2 border-indigo-500 bg-white dark:bg-gray-800 p-8 flex flex-col relative">
            <span className="absolute top-4 right-4 bg-indigo-600 text-white text-xs font-semibold px-2.5 py-1 rounded-full">
              Most popular
            </span>
            <p className="text-sm font-semibold text-indigo-600 dark:text-indigo-400 uppercase tracking-wide mb-1">
              Pro
            </p>
            <div className="mb-1">
              <span className="text-4xl font-bold text-gray-900 dark:text-gray-100">199 ₽</span>
              <span className="text-xl font-normal text-gray-500 dark:text-gray-400"> / month</span>
            </div>
            <p className="text-sm mb-6">
              <span className="font-medium text-gray-900 dark:text-gray-100">1 490 ₽ / year</span>
              <span className="text-gray-400 dark:text-gray-500 ml-2">save 38%</span>
            </p>
            <p className="text-sm text-gray-500 dark:text-gray-400 -mt-4 mb-6">For power users</p>
            <ul className="flex flex-col gap-2 mb-8 flex-1">
              {PRO_FEATURES.map((f) => (
                <li key={f} className="flex items-center gap-2 text-sm text-gray-700 dark:text-gray-300">
                  <span className="text-green-500 shrink-0">✓</span>
                  {f}
                </li>
              ))}
            </ul>
            <Link
              to="/profile"
              className="block text-center rounded-lg bg-indigo-600 hover:bg-indigo-700 text-white px-4 py-2.5 text-sm font-medium transition-colors"
            >
              Upgrade to Pro
            </Link>
          </div>
        </div>

        <p className="text-center text-sm text-gray-500 dark:text-gray-400 mt-10">
          Membership in groups is always free — you only pay to create more than 3 groups.
        </p>
      </div>
    </PublicLayout>
  )
}
