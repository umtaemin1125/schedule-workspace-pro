/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        ink: '#102A43',
        mint: '#0EA5A2',
        sand: '#F3EEE6'
      }
    }
  },
  plugins: []
}
