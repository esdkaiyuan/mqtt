# Qt 快速上位机模板

一个可复制的 Qt Widgets 桌面上位机基线：通过平台 Open API 读取属性、写 desired、调用服务，
按 `model.json` 自动生成属性表与控制区。无需改 C++ 就能适配不同设备的物模型。

> 上位机不是设备，不能复用 `device_secret` 直连 MQTT；请使用 ADMIN 创建的 API Key。

## 目录

```
qt-hmi-template/
├── CMakeLists.txt          # Qt 5.14+ / Qt 6 均可
├── qt-hmi.pro              # Qt Creator / qmake 入口
├── config.example.json     # 连接配置示例
├── model.example.json      # 属性与服务模型示例
└── src/
    ├── main.cpp
    ├── mainwindow.*        # 属性表、趋势、控制区、日志
    ├── apiclient.*         # Open API 封装
    ├── sparkline.*         # 自绘趋势图
    └── hmimodel.*          # config.json / model.json 解析
```

## 快速开始

### 1. 复制模板

在仓库根目录执行：

```bash
node scripts/create-hmi.mjs \
  --out ../line1-hmi \
  --device sensor-temp-001 \
  --title "1号线温度监控" \
  --base-url http://localhost/api/external/v1
```

如果已经从产品页「物模型 → 导出」拿到 TSL，可直接让脚本生成 `model.json`：

```bash
node scripts/create-hmi.mjs \
  --out ../line1-hmi \
  --device sensor-temp-001 \
  --tsl ./product-tsl.json
```

TSL 的 `accessMode=rw` 会生成可写控件；`int/float/double` 映射为数值，
`bool` 映射为开关，`enum/struct/array` 暂按文本处理。
带必填入参的服务会被跳过并打印提示，因为模板只发送空 `params`；需要时在 Qt 代码中补参数控件。

或手动复制本目录，然后把 `config.example.json` 复制为 `config.json`，
把 `model.example.json` 复制为 `model.json`。

### 2. 配置物模型

编辑 `model.json`：

```json
{
  "properties": [
    { "identifier": "temperature", "label": "温度", "type": "number", "unit": "°C" },
    { "identifier": "power", "label": "电源", "type": "bool", "writable": true }
  ],
  "services": [
    { "identifier": "reset", "label": "复位" }
  ]
}
```

`type` 支持 `number` / `bool` / `string`；`writable=true` 的属性会生成写入控件。
属性 identifier 必须与产品物模型一致，否则接口返回 6226 或属性不显示。

### 3. 设置 API Key

```bash
# Git Bash / Linux / macOS
export PLATFORM_API_KEY="your-api-key"

# Windows PowerShell
$env:PLATFORM_API_KEY = "your-api-key"
```

API Key 只从环境变量读取，不写入 `config.json`，也不要提交到 Git。

### 4. 构建

Qt Creator：打开 `qt-hmi.pro`，选择 Kit 后构建运行。

CMake：

```bash
cmake -S . -B build
cmake --build build -j
```

Qt 5.14+ 与 Qt 6 均可；需要 `Widgets` 与 `Network` 模块。

### 5. 运行

程序按以下顺序寻找配置：

1. 环境变量 `HMI_CONFIG` 指定的文件；
2. 可执行文件同目录的 `config.json`；
3. 当前工作目录的 `config.json`。

多设备配置：`devices` 优先于单 `deviceKey`，切换时重新加载属性与趋势。

```json
{
  "title": "产线上位机",
  "baseUrl": "https://your-server/api/external/v1",
  "devices": [
    { "key": "sensor-temp-001", "label": "1号温度计" },
    { "key": "sensor-temp-002", "label": "2号温度计" }
  ],
  "pollMs": 2000,
  "model": "model.json"
}
```

`config.json` 示例：

```json
{
  "title": "1号线温度监控",
  "baseUrl": "http://localhost/api/external/v1",
  "deviceKey": "sensor-temp-001",
  "pollMs": 2000,
  "model": "model.json"
}
```

## 当前能力

- 在线状态显示；
- 属性最新值表格（按物模型 label / unit 展示）；
- 点击属性行查看最近 1 小时趋势（自绘 Sparkline，无 Qt Charts 依赖，首次数值属性自动加载）；
- 多设备切换：配置 `devices` 后顶部下拉选择，旧请求按代际丢弃，避免数据串台；
- 可写属性控制（number / bool / string）；
- 无参数服务按钮；
- 2 秒轮询、手动刷新、自动刷新开关；
- 属性请求去重，慢网络下不会叠加并发请求；
- 遇到 `429 / Retry-After` 自动暂停轮询并按秒数冷却；
- 请求错误与命令提交日志；
- API Key 缺失时只预览界面，不发请求。

## 继续魔改

1. **加图表**：引入 Qt Charts，把 `updateProperties` 中的数值追加到 `QLineSeries`。
2. **加服务参数**：把服务按钮换成 dialog，收集参数后传给 `ApiClient::callService`。
4. **告警**：轮询 `GET /alerts`，在顶部增加告警条。
5. **本地缓存**：用 `QSettings` / SQLite 保存上次值与离线队列。
6. **品牌化**：用 Qt Designer 修改 `.ui` 或直接调整 `MainWindow` 布局与 QSS。

## 安全与生产

- 生产环境使用 HTTPS；不要关闭证书校验。
- API Key 等同账号的开放 API 读写能力，泄露后立即撤销。
- 工控机场景建议限制配置文件权限、配置开机自启与日志轮转。
- 命令按钮应做防抖与二次确认，避免重复下发。
- 需要推送而不是轮询时，使用登录 JWT + SSE；API Key 不能访问 `/api/realtime/stream`。

## 与平台 Web 上位机的关系

平台 Web 上位机免安装、可远程访问；本模板适合工控机、离线演示、本地外设联动与深度定制。
两者共用同一套 Open API 与设备数据，可以并存。
