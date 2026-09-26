# 旧版静态页面（已归档）

本目录存放早期的纯静态 HTML 原型页面，**已被 `src/` 下的 Vue 3 应用完全取代**，仅作历史参考保留。

## 为什么不使用它们

- 这些页面通过 CDN 引入 Chart.js，各自独立实现数据获取与渲染，与 Vue 应用重复。
- 它们依赖 `/api/recordings`、`/api/recordings/{filename}` 等接口，这些接口所属的
  `recordings_api.py` 已随 `backend/backend/` 一并移除，因此页面上的「录制/下载」功能
  在当前后端下不可用。
- 入口为 `visualization.html`，其余页面互相链接：`statistics.html`、`overview.html`、
  `comparison.html`、`waveform-viewer.html`。

## 对应关系

| 归档页面 | 现用实现 |
| --- | --- |
| `visualization.html` | 应用首页 `src/views/Dashboard.vue` |
| `waveform-viewer.html` | `src/views/Waveform.vue` |
| `statistics.html` | `src/views/Dashboard.vue` + `src/components/StatsCards.vue` |
| `overview.html` | `src/views/ThreeDView.vue` |
| `comparison.html` | `src/views/DataAnnotation.vue` |
| `recordings.html` | `src/views/DataExport.vue` |
| `demo-generator.js` | 已被 `scripts/generate_test_data.py` 取代 |

如需直接打开这些页面，注意其中的 `API` 常量指向旧后端地址，需自行修改。