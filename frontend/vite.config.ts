import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  },
  // preview 用于 production 部署：npm run preview 起静态服务器并代理 /api
  // 生产环境可以接 nginx，本地演示用这个 + cloudflared tunnel 直接出公网
  preview: {
    port: 4173,
    host: '0.0.0.0',
    // 允许任何 host header（cloudflared 的 *.trycloudflare.com 子域是动态的）
    // vite 5 接受 string[] 或 true；用 true 最省心
    allowedHosts: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  }
})
