/** @type {import('tailwindcss').Config} */
// NeuralBase 设计系统 · Cyberpunk Terminal
// 深渊蓝黑 #0A0E17 · 电光蓝 #00F0FF + 警告橙 #FF6B00 · 双霓虹限制
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  darkMode: 'class',
  theme: {
    extend: {
      colors: {
        void: '#0a0e17',
        'void-deep': '#050508',
        surface: '#0f1522',
        neon: {
          DEFAULT: '#00f0ff',
          bright: '#35f5ff',
          dim: '#00c8d6',
        },
        alert: {
          DEFAULT: '#ff6b00',
          bright: '#ff8c3a',
          dim: '#d95f00',
        },
        module: {
          kb: '#00f0ff',
          chat: '#00f0ff',
          baguwen: '#ff6b00',
          news: '#00f0ff',
        },
        semantic: {
          success: '#00f0ff',
          warning: '#ff6b00',
          danger: '#ff6b00',
          info: '#00f0ff',
        },
      },

      fontFamily: {
        sans: ['Inter', '-apple-system', 'BlinkMacSystemFont', 'PingFang SC', 'Microsoft YaHei', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'Space Mono', 'SF Mono', 'Consolas', 'monospace'],
      },

      fontSize: {
        'caption': ['11px', { lineHeight: '16px', letterSpacing: '0.08em' }],
        'body-sm': ['13px', { lineHeight: '18px' }],
        'body': ['14px', { lineHeight: '20px' }],
        'body-lg': ['16px', { lineHeight: '24px' }],
        'title': ['20px', { lineHeight: '26px', letterSpacing: '-0.01em' }],
        'title-lg': ['28px', { lineHeight: '34px', letterSpacing: '-0.015em' }],
        'display': ['44px', { lineHeight: '1.05', letterSpacing: '-0.02em', fontWeight: '700' }],
        'display-lg': ['60px', { lineHeight: '1.02', letterSpacing: '-0.025em', fontWeight: '700' }],
      },

      borderRadius: {
        nb1: '2px',
        nb2: '4px',
        nb3: '6px',
        nb4: '8px',
        'nb-card': '4px',
        pill: '999px',
      },

      boxShadow: {
        'card': '0 0 0 1px rgba(0, 240, 255, 0.06), 0 8px 24px -4px rgba(0, 0, 0, 0.5)',
        'card-hover': '0 0 0 1px rgba(0, 240, 255, 0.28), 0 0 16px rgba(0, 240, 255, 0.1), 0 12px 40px -8px rgba(0, 0, 0, 0.6)',
        'glow-neon': '0 0 10px rgba(0, 240, 255, 0.35)',
        'glow-alert': '0 0 10px rgba(255, 107, 0, 0.35)',
        'focus-neon': '0 0 0 1px rgba(0, 240, 255, 0.6), 0 0 12px rgba(0, 240, 255, 0.25)',
      },

      transitionTimingFunction: {
        spring: 'cubic-bezier(0.25, 1, 0.5, 1)',
      },
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(10px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'led-blink': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.3' },
        },
      },
      animation: {
        'fade-up': 'fade-up 0.45s cubic-bezier(0.25, 1, 0.5, 1) both',
        'led': 'led-blink 1.6s ease-in-out infinite',
      },
    },
  },
  plugins: [],
}
