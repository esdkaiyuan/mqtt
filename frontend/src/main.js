import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'
import router from './router'
import { registerIcons } from '@/assets/svg'
import { useAuthStore } from '@/stores/auth'
import App from './App.vue'
import '@/assets/css/global.css'

const app = createApp(App)

app.use(createPinia())
app.use(router)
app.use(ElementPlus, {
  size: 'default'
})

for (const [key, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(key, component)
}

registerIcons(app)

// 将 auth store 的导航事件接到 router，避免 store 与 router 相互引用
const authStore = useAuthStore()
authStore.on('navigate', (path) => {
  router.push(path)
})

app.mount('#app')