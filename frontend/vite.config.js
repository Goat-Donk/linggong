import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // @ 指向 src，方便 import '@/xxx'
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    // 开发期代理：前端统一请求 /api/xxx，代理到后端并去掉 /api 前缀
    // 这样开发期不用配 CORS，生产期 nginx 用同样的「location /api/ 反代」思路。
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        rewrite: (path) => path.replace(/^\/api/, '')
      },
      // 上传图片（/uploads/**）直接代理到后端，前端 <img src="/uploads/xxx"> 才能正常显示
      '/uploads': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
