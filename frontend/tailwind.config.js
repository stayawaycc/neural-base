/** @type {import('tailwindcss').Config} */
// NeuralBase 设计系统 · 深色玻璃拟态 + 霓虹渐变
// 设计哲学:8px 基准网格、5 级圆角、6 级字号、霓虹青色主色 + 紫色强调
export default {
  content: ['./index.html', './src/**/*.{vue,ts}'],
  darkMode: 'class',
  theme: {
    extend: {
      /* ---- 色彩系统:主色/强调/模块专属/语义 ---- */
      colors: {
        brand: {
          // 主色:霓虹青
          DEFAULT: '#06b6d4',
          light: '#22d3ee',
          dark: '#0e7490',
          // 强调:霓虹紫
          accent: '#6366f1',
          'accent-light': '#a78bfa',
        },
        // 模块专属色
        module: {
          kb: '#0891b2',
          chat: '#7c3aed',
          baguwen: '#d97706',
          news: '#059669',
        },
        // 语义色(与 naive-ui 对齐)
        semantic: {
          success: '#059669',
          warning: '#d97706',
          danger: '#dc2626',
          info: '#7c3aed',
        },
      },

      /* ---- 字体:Inter + 中文系统栈 ---- */
      fontFamily: {
        sans: ['Inter', 'PingFang SC', 'Microsoft YaHei', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'Consolas', 'monospace'],
      },

      /* ---- 6 级字号层级(caption → display) ---- */
      fontSize: {
        'caption': ['12px', { lineHeight: '18px' }],
        'body-sm': ['13px', { lineHeight: '20px' }],
        'body': ['14px', { lineHeight: '22px' }],
        'body-lg': ['15px', { lineHeight: '24px' }],
        'title': ['18px', { lineHeight: '26px', letterSpacing: '-0.01em' }],
        'title-lg': ['24px', { lineHeight: '32px', letterSpacing: '-0.02em' }],
      },

      /* ---- 5 级圆角体系(8px 基准网格) ---- */
      borderRadius: {
        nb1: '6px', // 徽章/小元素
        nb2: '8px', // 紧凑控件
        nb3: '12px', // 输入框/按钮
        nb4: '16px', // 卡片
        nb5: '24px', // 大容器/浮层
      },

      /* ---- 玻璃阴影 ---- */
      boxShadow: {
        'glass': '0 1px 2px rgba(15,23,42,0.04), 0 4px 12px -4px rgba(15,23,42,0.05)',
        'glass-hover': '0 4px 10px -2px rgba(15,23,42,0.06), 0 12px 24px -12px rgba(15,23,42,0.1)',
        'neon': '0 0 20px -4px rgba(34,211,238,0.45), 0 0 40px -12px rgba(99,102,241,0.35)',
      },

      /* ---- 动效:入场/呼吸 ---- */
      keyframes: {
        'fade-up': {
          '0%': { opacity: '0', transform: 'translateY(12px)' },
          '100%': { opacity: '1', transform: 'translateY(0)' },
        },
        'neon-pulse': {
          '0%, 100%': { opacity: '0.6' },
          '50%': { opacity: '1' },
        },
        shimmer: {
          '0%': { transform: 'translateX(-100%)' },
          '100%': { transform: 'translateX(100%)' },
        },
      },
      animation: {
        'fade-up': 'fade-up 0.4s ease both',
        'neon-pulse': 'neon-pulse 3s ease-in-out infinite',
        shimmer: 'shimmer 1.6s infinite',
      },
    },
  },
  plugins: [],
}
