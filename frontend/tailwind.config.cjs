/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './index.html',
    './promo.html',
    './legal.html',
    './about.html',
    './src/**/*.{ts,tsx}',
    './src/promo/**/*.{ts,js}',
    './src/legal/**/*.{ts,js}',
    './src/about/**/*.{ts,js}',
    './src/shared/**/*.{ts,js}',
  ],
  darkMode: 'class',
  theme: {
    extend: {
      // Фирменная палитра как токены Tailwind. Значения обязаны совпадать с
      // CSS-переменными в src/shared/brand.css — там они для статических
      // страниц, здесь для SPA, который на Tailwind-утилитах.
      //
      // Сейчас SPA свёрстан на встроенных indigo/gray и эти токены почти не
      // использует: перевод интерфейса на фирменные цвета — отдельная
      // дизайнерская задача, а не побочный эффект замены ассетов.
      colors: {
        gm: {
          bg: '#040929',
          surface: '#0C1540',
          border: '#1A2757',
          accent: '#055EC2',
          'accent-hover': '#0867D0',
          'accent-light': '#5DB7F4',
          'accent-deep': '#143291',
          muted: '#9AA8C7',
        },
      },
    },
  },
  plugins: [],
}
