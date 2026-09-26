import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import path from 'path'

// 后端服务地址（开发期代理目标），与 backend/Dockerfile 暴露的端口保持一致
const BACKEND_TARGET = process.env.VITE_PROXY_TARGET || 'http://localhost:8000'

export default defineConfig({
  plugins: [
    vue(),
    // 按需引入 ElMessage / ElMessageBox 等函数式 API（含各自样式）
    AutoImport({
      resolvers: [ElementPlusResolver()],
      dts: false
    }),
    // 按需引入 <el-*> 组件与 v-loading 等指令（含各自样式）
    Components({
      resolvers: [ElementPlusResolver()],
      dts: false
    })
  ],
  envPrefix: 'VITE_',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    rollupOptions: {
      output: {
        // 第三方库单独分包，便于浏览器长期缓存（业务代码变更不再使其失效）
        manualChunks: {
          'vue-vendor': ['vue', 'vue-router', 'pinia'],
          'element-plus': ['element-plus', '@element-plus/icons-vue']
        }
      }
    }
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