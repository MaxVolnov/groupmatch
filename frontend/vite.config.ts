import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  // GitHub Pages serves from /<repo-name>/ in production
  base: mode === 'production' ? '/groupmatch/' : '/',
  server: {
    port: 3000,
    proxy: mode === 'development'
      ? {
          '/api': {
            target: 'http://localhost:8080',
            changeOrigin: true,
          },
        }
      : undefined,
  },
}))
