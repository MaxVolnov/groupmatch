import { Component, ReactNode } from 'react'
import i18n from '@/i18n'

interface Props { children: ReactNode }
interface State { hasError: boolean }

// Class component — can't use the useTranslation() hook, so it reads
// directly off the i18n singleton instead.
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error) {
    console.error('Uncaught error:', error)
  }

  render() {
    if (this.state.hasError) {
      return (
        <div className="min-h-screen flex flex-col items-center justify-center gap-4 p-8 text-center">
          <div className="text-4xl">⚠️</div>
          <h1 className="text-xl font-semibold">{i18n.t('errorBoundary.title')}</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            {i18n.t('errorBoundary.description')}
          </p>
          <button
            onClick={() => window.location.reload()}
            className="px-4 py-2 bg-gm-600 text-white rounded-lg hover:bg-gm-700 transition-colors"
          >
            {i18n.t('errorBoundary.refresh')}
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
