import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { resolve } from 'node:path'

export default defineConfig(({ mode }) => ({
  plugins: [react()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, './src'),
    },
  },
  // GitHub Pages serves from /<repo-name>/ in production; Vercel serves from root
  base: process.env.VITE_DEPLOY_TARGET === 'vercel' ? '/' : (mode === 'production' ? '/groupmatch/' : '/'),
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
  build: {
    rollupOptions: {
      input: {
        main: resolve(__dirname, 'index.html'),
        promo: resolve(__dirname, 'promo.html'),
        legal: resolve(__dirname, 'legal.html'),
        about: resolve(__dirname, 'about.html'),
      },
    },
  },
}))
