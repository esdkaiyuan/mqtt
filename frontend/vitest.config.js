import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.js'

/**
 * 复用 vite.config.js 的插件（含 Element Plus 按需导入）与 `@` 别名，
 * 避免测试环境与构建环境出现两套解析规则。
 */
export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.js'],
      include: ['src/**/__tests__/**/*.spec.js'],
      server: {
        deps: {
          // 按需引入解析器会给源码注入 `element-plus/es/components/*/style/css`
          // 这类副作用导入；若交给 Node 原生 ESM 加载会因 .css 后缀直接报错。
          // 内联后由 Vite 处理这些样式模块（css:false 下等价于空模块）。
          inline: [/element-plus/]
        }
      },
      css: false,
      coverage: {
        provider: 'v8',
        reporter: ['text', 'html'],
        reportsDirectory: './coverage',
        include: ['src/api/**/*.js', 'src/composables/**/*.js', 'src/utils/**/*.js'],
        exclude: ['src/**/__tests__/**', 'src/api/queryClient.js']
      }
    }
  })
)
