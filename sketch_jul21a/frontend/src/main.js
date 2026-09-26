import { createApp } from 'vue'
import { createPinia } from 'pinia'
import {
  Aim, Camera, Check, Clock, Close, Compass, Connection, Coordinate, Cpu,
  DataBoard, DataLine, Delete, Document, Download, Edit, InfoFilled, List,
  Monitor, Mouse, Pointer, QuestionFilled, Refresh, RefreshRight, Select,
  Setting, Timer, TrendCharts, VideoPause, VideoPlay, View, Warning, ZoomIn
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// Element Plus 组件/指令/函数式 API 由 unplugin 按需引入（见 vite.config.js），
// 此处仅注册图标：图标来自 @element-plus/icons-vue，需全局注册后模板才能直接使用。
// 图标均在模板中静态引用，新增用法时需同步补充此列表。
const icons = {
  Aim, Camera, Check, Clock, Close, Compass, Connection, Coordinate, Cpu,
  DataBoard, DataLine, Delete, Document, Download, Edit, InfoFilled, List,
  Monitor, Mouse, Pointer, QuestionFilled, Refresh, RefreshRight, Select,
  Setting, Timer, TrendCharts, VideoPause, VideoPlay, View, Warning, ZoomIn
}

for (const [name, component] of Object.entries(icons)) {
  app.component(name, component)
}

app.use(createPinia())
app.use(router)

app.mount('#app')