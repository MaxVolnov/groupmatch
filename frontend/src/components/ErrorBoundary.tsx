import { Component, ReactNode } from 'react'

interface Props { children: ReactNode }
interface State { hasError: boolean }

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
          <h1 className="text-xl font-semibold">Something went wrong</h1>
          <p className="text-sm text-gray-500 dark:text-gray-400">
            An unexpected error occurred. Please refresh the page.
          </p>
          <button
            onClick={() => window.location.reload()}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 transition-colors"
          >
            Refresh page
          </button>
        </div>
      )
    }
    return this.props.children
  }
}
