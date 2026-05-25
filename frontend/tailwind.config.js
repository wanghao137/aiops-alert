/** @type {import('tailwindcss').Config} */
export default {
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{vue,ts,tsx}'],
  theme: {
    extend: {
      colors: {
        // 监控大屏配色：以深色背景 + 高亮色为主
        bg: {
          base: '#0B1120',
          panel: '#111827',
          subtle: '#1F2937',
          hover: '#273244'
        },
        line: {
          DEFAULT: '#1F2937',
          subtle: '#374151'
        },
        text: {
          primary: '#F8FAFC',
          secondary: '#CBD5E1',
          muted: '#94A3B8'
        },
        accent: {
          DEFAULT: '#3B82F6',
          soft: '#1E3A8A'
        },
        level: {
          notice: '#0EA5E9',
          normal: '#3B82F6',
          serious: '#F59E0B',
          critical: '#EF4444',
          ok: '#10B981'
        }
      },
      fontFamily: {
        sans: ['Inter', 'PingFang SC', 'Microsoft YaHei', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace']
      },
      boxShadow: {
        glow: '0 0 0 1px rgba(59,130,246,0.5), 0 8px 24px -8px rgba(59,130,246,0.4)'
      },
      borderRadius: {
        lg: '12px'
      }
    }
  },
  plugins: []
}
