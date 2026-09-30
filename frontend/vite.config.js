import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import path from 'path'
import fs from 'node:fs'

/**
 * 少数 <el-*> 组件并非独立目录，而是父组件的子导出，这里给出目录归并表。
 * 例如 ElOption 由 select 目录导出，ElDropdownItem/ElDropdownMenu 由 dropdown 目录导出。
 */
const EP_DIR_ALIAS = {
  option: 'select',
  'option-group': 'select',
  'menu-item': 'menu',
  'sub-menu': 'menu',
  'dropdown-item': 'dropdown',
  'dropdown-menu': 'dropdown'
}

function kebab(str) {
  return str.replace(/([a-z0-9])([A-Z])/g, '$1-$2').toLowerCase()
}

/**
 * Element Plus 按需引入解析器。
 *
 * 为什么不直接用 unplugin-vue-components 自带的 ElementPlusResolver：
 * 它对 element-plus >= 1.1.0-beta.1 一律返回整包 barrel 入口 `element-plus/es`
 * （见 node_modules/unplugin-vue-components/dist/resolvers.mjs 的 resolveComponent$1）。
 * 而该 barrel（es/index.mjs）顶层存在
 *   const install = defaults_default.install
 * 以及 defaults.mjs 顶层的 makeInstaller([...全部组件, ...全部插件]) 这类
 * Rollup 无法安全摇掉的顶层调用，于是「只用到 10 个 <el-*> 组件」却把
 * 99 个组件目录、933 个模块（约 1.8MB 源码）整体打进 bundle。
 * 实测 moduleSideEffects / propertyReadSideEffects 等 treeshake 参数均无法切断该链条。
 *
 * 这里直接指向细粒度入口 `element-plus/es/components/<dir>/index.mjs`，从源头绕开 barrel。
 * element-plus 的 package.json exports 已声明 "./es/*" 与 "./es/*.mjs"，子路径可解析。
 */
function ElementPlusOnDemand() {
  const esComponentsDir = path.resolve(__dirname, 'node_modules/element-plus/es/components')
  return {
    type: 'component',
    resolve(name) {
      if (!/^El[A-Z]/.test(name)) return
      const dir = EP_DIR_ALIAS[kebab(name.slice(2))] || kebab(name.slice(2))
      if (!fs.existsSync(path.join(esComponentsDir, dir, 'index.mjs'))) return
      return {
        name,
        from: `element-plus/es/components/${dir}/index.mjs`,
        sideEffects: [
          'element-plus/es/components/base/style/css',
          `element-plus/es/components/${dir}/style/css`
        ]
      }
    }
  }
}

export default defineConfig({
  plugins: [
    vue(),
    // 按需自动导入 Element Plus 的命令式 API（ElMessage / ElMessageBox 等）及其样式
    AutoImport({ resolvers: [ElementPlusOnDemand()] }),
    // 按需自动注册 <el-*> 组件并引入对应组件级 CSS，替代 main.js 的全量引入
    Components({ resolvers: [ElementPlusOnDemand()] })
  ],
  envPrefix: 'VITE_',
  resolve: {
    alias: {
      '@': path.resolve(__dirname, 'src')
    }
  },
  build: {
    outDir: 'dist',
    assetsDir: 'assets',
    sourcemap: false,
    chunkSizeWarningLimit: 800,
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (!id.includes('node_modules')) return
          if (id.includes('echarts') || id.includes('zrender')) return 'echarts'
          if (id.includes('element-plus') || id.includes('@element-plus')) return 'element-plus'
          return 'vendor'
        }
      }
    }
  },
  server: {
    port: 3000,
    host: '0.0.0.0',
    proxy: {
      // 用正则键精确匹配 /api/xxx，避免把 SPA 路由 /api-docs 也代理到后端（与 nginx 的 ^~ /api/ 保持一致）
      '^/api/': {
        // 本机开发若后端容器未映射宿主机端口，可用 VITE_DEV_PROXY_TARGET=http://localhost:80 经 nginx 转发
        target: process.env.VITE_DEV_PROXY_TARGET || 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})
