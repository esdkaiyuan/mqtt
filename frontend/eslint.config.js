import js from '@eslint/js'
import vue from 'eslint-plugin-vue'
import globals from 'globals'

/**
 * ESLint 平面配置（ESLint 9+）。
 *
 * 说明：
 * - Element Plus 的命令式 API 由 unplugin-auto-import 按需注入，不在源码中 import，
 *   因此这里把它们声明为只读全局变量，避免 no-undef 误报。
 * - `__dirname` 仅出现在 vite.config.js（Vite 打包配置时会注入 CommonJS 垫片）。
 */
export default [
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', 'src/components.d.ts', 'auto-imports.d.ts']
  },
  js.configs.recommended,
  ...vue.configs['flat/recommended'],
  {
    files: ['**/*.{js,vue}'],
    languageOptions: {
      ecmaVersion: 'latest',
      sourceType: 'module',
      globals: {
        ...globals.browser,
        ...globals.node,
        __dirname: 'readonly',
        ElMessage: 'readonly',
        ElMessageBox: 'readonly',
        ElNotification: 'readonly',
        ElLoading: 'readonly'
      }
    },
    rules: {
      // 组件名与模板风格：本项目既有单文件页面（Dashboard 等），不做强制多词命名
      'vue/multi-word-component-names': 'off',
      // 关闭纯排版类规则，避免与既有代码风格产生大量噪音
      'vue/max-attributes-per-line': 'off',
      'vue/singleline-html-element-content-newline': 'off',
      'vue/html-self-closing': 'off',
      'vue/attributes-order': 'off',
      'vue/html-indent': 'off',
      'vue/html-closing-bracket-newline': 'off',
      'no-unused-vars': ['error', { argsIgnorePattern: '^_', caughtErrors: 'none' }]
    }
  },
  {
    // 测试文件使用 Vitest 全局 API（describe / it / expect / vi 等）
    files: ['src/**/__tests__/**/*.js', 'src/test/**/*.js'],
    languageOptions: {
      globals: { ...globals.node, ...globals.vitest }
    }
  }
]
