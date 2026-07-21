import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import { ErrorBoundary } from './components/ErrorBoundary.tsx'
import './index.css'
import i18n from './i18n'
import { useLanguageStore } from './store/language'

// Apply theme before render to prevent flash of wrong theme
const stored = localStorage.getItem('groupmatch-theme')
const theme = stored ? JSON.parse(stored)?.state?.theme : 'system'
const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
if (theme === 'dark' || (theme === 'system' && prefersDark)) {
  document.documentElement.classList.add('dark')
}

// Restore saved language before render
const savedLang = useLanguageStore.getState().language
i18n.changeLanguage(savedLang)

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ErrorBoundary>
      <App />
    </ErrorBoundary>
  </React.StrictMode>,
)

