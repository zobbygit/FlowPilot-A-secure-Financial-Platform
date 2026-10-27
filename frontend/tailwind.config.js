const token = (name) => `rgb(var(--fp-${name}) / <alpha-value>)`;

/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        bg: token('bg'), surface: token('surface'), 'surface-2': token('surface-2'),
        line: token('line'), ink: token('ink'), muted: token('muted'),
        primary: token('primary'), accent: token('accent'), live: token('live'),
        success: token('success'), danger: token('danger'), warning: token('warning'),
      },
      fontFamily: { sans: ['Inter', 'ui-sans-serif', 'system-ui', 'Segoe UI', 'Roboto', 'sans-serif'] },
      boxShadow: { glow: '0 0 0 1px rgb(var(--fp-accent) / .35), 0 12px 40px -12px rgb(var(--fp-accent) / .45)' },
    },
  },
  plugins: [],
};
