import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import {
  Aim, Camera, Check, Clock, Close, Compass, Connection, Coordinate, Cpu,
  DataBoard, DataLine, Delete, Document, Download, Edit, InfoFilled, List,
  Monitor, Mouse, Pointer, QuestionFilled, Refresh, RefreshRight, Select,
  Setting, Timer, TrendCharts, VideoPause, VideoPlay, View, Warning, ZoomIn
} from '@element-plus/icons-vue'
import App from './App.vue'
import router from './router'

const app = createApp(App)

// 按需注册：仅打包模板中实际使用的图标（包内共导出 293 个）。
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
app.use(ElementPlus)

app.mount('#app')