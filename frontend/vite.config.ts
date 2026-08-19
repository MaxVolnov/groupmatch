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
  // Всегда от корня. Раньше здесь была развилка под GitHub Pages, где сайт
  // жил в /<repo-name>/, и прод-сборка по умолчанию уходила на /groupmatch/.
  // Площадки больше нет, а дефолт оставался миной: любая сборка вне основного
  // проекта Vercel — preview, локальная, форк — давала белый экран, причём
  // молча: сборка успешна, ломаются только пути к ассетам.
  base: '/',
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
