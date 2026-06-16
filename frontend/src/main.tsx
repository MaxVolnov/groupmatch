import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App.tsx'
import './index.css'

// Apply theme before render to prevent flash of wrong theme
const stored = localStorage.getItem('groupmatch-theme')
const theme = stored ? JSON.parse(stored)?.state?.theme : 'system'
const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
if (theme === 'dark' || (theme === 'system' && prefersDark)) {
  document.documentElement.classList.add('dark')
}

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
