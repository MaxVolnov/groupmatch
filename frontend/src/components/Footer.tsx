import { useState } from 'react'
import { Link } from 'react-router-dom'
import { FeedbackModal } from './FeedbackModal'

export function Footer() {
  const [showFeedback, setShowFeedback] = useState(false)

  return (
    <footer className="bg-gray-900 text-gray-400 border-t border-gray-800">
      <div className="mx-auto max-w-6xl px-4 py-10">
        <div className="grid gap-8 sm:grid-cols-3">
          {/* Brand */}
          <div>
            <p className="text-white font-bold mb-2">GroupMatch</p>
            <p className="text-sm mb-4">Group scheduling made simple.</p>
            <p className="text-xs">© 2026 Max Wave Studio</p>
            <p className="text-xs">ИНН: 771887947687</p>
            <a
              href="mailto:volnov.max@yandex.ru"
              className="text-xs hover:text-white transition-colors"
            >
              volnov.max@yandex.ru
            </a>
          </div>

          {/* Product */}
          <div>
            <p className="text-white font-medium mb-3 text-sm">Product</p>
            <ul className="flex flex-col gap-2 text-sm">
              <li>
                <Link to="/pricing" className="hover:text-white transition-colors">
                  Pricing
                </Link>
              </li>
              <li>
                <button
                  onClick={() => setShowFeedback(true)}
                  className="hover:text-white transition-colors"
                >
                  Feedback →
                </button>
              </li>
            </ul>
          </div>

          {/* Company */}
          <div>
            <p className="text-white font-medium mb-3 text-sm">Company</p>
            <ul className="flex flex-col gap-2 text-sm">
              <li>
                <Link to="/about" className="hover:text-white transition-colors">
                  About
                </Link>
              </li>
              <li>
                <Link to="/legal" className="hover:text-white transition-colors">
                  Legal
                </Link>
              </li>
              <li>
                <span className="cursor-not-allowed text-gray-600" title="Coming soon">
                  For Teams
                </span>
              </li>
            </ul>
          </div>
        </div>
      </div>

      <FeedbackModal open={showFeedback} onClose={() => setShowFeedback(false)} />
    </footer>
  )
}
