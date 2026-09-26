import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'path'

// 后端服务地址（开发期代理目标），与 backend/Dockerfile 暴露的端口保持一致
const BACKEND_TARGET = process.env.VITE_PROXY_TARGET || 'http://localhost:8000'

export default defineConfig({
  plugins: [vue()],
  envPrefix: 'VITE_',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false
  },
  server: {
    port: 3000,
    host: '0.0.0.0',
    proxy: {
      // 后端路由本身已带 /api 前缀，此处不做 rewrite
      '/api': {
        target: BACKEND_TARGET,
        changeOrigin: true
      },
      '/ws': {
        target: BACKEND_TARGET,
        ws: true,
        changeOrigin: true
      }
    }
  }
})