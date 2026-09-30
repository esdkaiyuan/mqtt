import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { VueQueryPlugin } from '@tanstack/vue-query'
import { ArrowDown } from '@element-plus/icons-vue'
import router from './router'
import { queryClient } from '@/api/queryClient'
import { registerIcons } from '@/assets/svg'
import { useAuthStore } from '@/stores/auth'
import App from './App.vue'
import '@/assets/css/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
// 服务端状态（列表/统计/详情）统一走 Vue Query，获得缓存、失效与重试
app.use(VueQueryPlugin, { queryClient })

// 只注册模板中实际用到的图标，避免 `import *` 把整套图标（约 230KB）打进 bundle。
// 新增图标时在此登记即可。
const icons = { ArrowDown }
for (const [key, component] of Object.entries(icons)) {
  app.component(key, component)
}

registerIcons()

// 将 auth store 的导航事件接到 router，避免 store 与 router 相互引用
const authStore = useAuthStore()
authStore.on('navigate', (path) => {
  router.push(path)
})

app.mount('#app')