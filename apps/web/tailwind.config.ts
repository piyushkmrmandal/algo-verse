import type { Config } from 'tailwindcss';

const config: Config = {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        // Backgrounds
        'bg-base': '#0A0A0B',
        'bg-surface': '#111113',
        'bg-elevated': '#18181C',
        'bg-overlay': '#1E1E24',

        // Brand
        'brand-primary': '#6366F1',
        'brand-primary-hover': '#4F46E5',
        'brand-accent': '#22D3EE',
        'brand-accent-hover': '#06B6D4',

        // Semantic
        success: '#10B981',
        'success-dim': '#064E3B',
        warning: '#F59E0B',
        'warning-dim': '#78350F',
        danger: '#EF4444',
        'danger-dim': '#7F1D1D',
        info: '#3B82F6',
        'info-dim': '#1E3A5F',

        // Text
        'text-primary': '#F4F4F5',
        'text-secondary': '#A1A1AA',
        'text-muted': '#52525B',
        'text-disabled': '#3F3F46',

        // Borders
        'border-subtle': '#27272A',
        'border-default': '#3F3F46',
        'border-strong': '#52525B',

        // Syntax highlighting
        'syntax-keyword': '#BD93F9',
        'syntax-string': '#F1FA8C',
        'syntax-number': '#FF79C6',
        'syntax-comment': '#6272A4',
        'syntax-function': '#50FA7B',
        'syntax-variable': '#FFB86C',
        'syntax-type': '#8BE9FD',
        'syntax-operator': '#FF79C6',

        // Difficulty
        easy: '#10B981',
        medium: '#F59E0B',
        hard: '#EF4444',

        // XP tiers
        'tier-bronze': '#CD7F32',
        'tier-silver': '#C0C0C0',
        'tier-gold': '#FFD700',
        'tier-platinum': '#E5E4E2',
        'tier-diamond': '#B9F2FF',
        'tier-master': '#FF6B6B',
        'tier-grandmaster': '#A855F7',
      },
      fontFamily: {
        mono: ['"JetBrains Mono"', '"Fira Code"', '"Cascadia Code"', 'monospace'],
        sans: ['"Inter Variable"', '"Inter"', 'system-ui', 'sans-serif'],
      },
      fontSize: {
        '2xs': ['0.625rem', { lineHeight: '0.875rem' }],
      },
      backgroundImage: {
        'gradient-radial': 'radial-gradient(var(--tw-gradient-stops))',
        'gradient-brand':
          'linear-gradient(135deg, #6366F1 0%, #22D3EE 100%)',
        'gradient-surface':
          'linear-gradient(180deg, #18181C 0%, #111113 100%)',
        'noise':
          "url(\"data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)' opacity='0.04'/%3E%3C/svg%3E\")",
      },
      boxShadow: {
        'glow-primary': '0 0 20px rgba(99, 102, 241, 0.3)',
        'glow-accent': '0 0 20px rgba(34, 211, 238, 0.3)',
        'glow-success': '0 0 20px rgba(16, 185, 129, 0.3)',
        'glow-danger': '0 0 20px rgba(239, 68, 68, 0.3)',
        'glass': '0 4px 24px rgba(0, 0, 0, 0.4), inset 0 1px 0 rgba(255, 255, 255, 0.05)',
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'shimmer': 'shimmer 2s linear infinite',
        'float': 'float 6s ease-in-out infinite',
        'gradient-x': 'gradient-x 4s ease infinite',
      },
      keyframes: {
        shimmer: {
          '0%': { backgroundPosition: '-200% 0' },
          '100%': { backgroundPosition: '200% 0' },
        },
        float: {
          '0%, 100%': { transform: 'translateY(0px)' },
          '50%': { transform: 'translateY(-8px)' },
        },
        'gradient-x': {
          '0%, 100%': { backgroundPosition: '0% 50%' },
          '50%': { backgroundPosition: '100% 50%' },
        },
      },
      transitionTimingFunction: {
        spring: 'cubic-bezier(0.34, 1.56, 0.64, 1)',
        'spring-gentle': 'cubic-bezier(0.25, 0.46, 0.45, 0.94)',
      },
      backdropBlur: {
        xs: '2px',
      },
    },
  },
  plugins: [],
};

export default config;
