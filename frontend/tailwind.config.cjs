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
    extend: {},
  },
  plugins: [],
}
