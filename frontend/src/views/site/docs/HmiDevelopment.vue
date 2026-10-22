<template>
  <DocArticle eyebrow="上位机开发" :title="current.title" :desc="current.desc">
    <!-- Qt 快速上位机 -->
    <template v-if="section === 'qt'">
      <h2>为什么用 Qt 做上位机</h2>
      <p>
        平台自带 Web 上位机，适合浏览器访问与快速交付；现场工控机、离线演示、多协议外设或需要
        深度定制交互时，可以用 Qt Widgets 做桌面端。推荐从「一个窗口 + 一个通讯类」的最小工程开始魔改，
        不要一上来就堆页面和图表。
      </p>

      <h2>直接使用仓库模板</h2>
      <p>
        仓库根目录的 <code>qt-hmi-template/</code> 是可编译的最小工程，支持 Qt 5.14+ / Qt 6，
        通过 <code>config.json</code> 与 <code>model.json</code> 驱动界面。用脚手架复制一份并写入项目参数：
      </p>
      <CodeBlock language="bash" :code="scaffoldSample" />
      <p>
        脚本会生成独立的工程目录、<code>config.json</code> 与 <code>model.json</code>；
        API Key 不会写入任何文件。模板已在 Qt 5.14.2 + MinGW 下通过编译与离屏启动验证，
        Qt 6 使用同一套源码。
      </p>
      <p>
        从产品页「物模型 → 导出」拿到 TSL 后，可追加 <code>--tsl ./product-tsl.json</code>，
        脚本会按 TSL 自动生成属性与服务清单：<code>accessMode=rw</code> 生成可写控件，
        <code>int/float/double</code> 映射为数值，<code>bool</code> 映射为开关，
        <code>enum/struct/array</code> 暂按文本处理；带必填入参的服务会被跳过并提示，
        需要自行在模板中补参数控件。
      </p>
      <table>
        <thead>
          <tr><th>方案</th><th>适合场景</th><th>通讯方式</th></tr>
        </thead>
        <tbody>
          <tr><td>平台 Web 上位机</td><td>浏览器访问、快速看值控值、免安装</td><td>平台内部 REST / SSE</td></tr>
          <tr><td>Qt 桌面上位机</td><td>工控机、离线演示、外设联动、深度定制</td><td>Open API（<code>X-API-Key</code>）轮询</td></tr>
          <tr><td>Qt + 自有业务系统</td><td>需要与本地数据库 / PLC / 打印设备联动</td><td>Open API + 本地业务逻辑</td></tr>
        </tbody>
      </table>

      <DocCallout type="warning" title="不要用设备凭据直连 MQTT">
        上位机不是设备，不能复用 <code>device_secret</code> 直连 Broker；设备 ACL 只允许设备操作自己的命名空间。
        Qt 端统一通过 Open API 读写，账号范围由 API Key 所属用户决定。
      </DocCallout>

      <h2>基础模板结构</h2>
      <CodeBlock language="text" :code="treeSample" />
      <p>核心角色：<code>ApiClient</code> 负责 HTTP，<code>MainWindow</code> 负责表格 / 控制区 / 日志，<code>SparklineWidget</code> 负责趋势绘制，<code>QTimer</code> 负责轮询。</p>

      <h2>CMake 工程</h2>
      <CodeBlock language="cmake" :code="cmakeSample" />

      <h2>通讯层：ApiClient</h2>
      <p>
        用 <code>QNetworkAccessManager</code> 封装四个动作：读属性、读时序、写 desired、下发命令。
        API Key 从配置或系统凭据读取，不写进源码。
      </p>
      <CodeBlock language="cpp" :code="apiHeaderSample" />
      <CodeBlock language="cpp" :code="apiSourceSample" />

      <h2>最小窗口：先能看值，再谈美化</h2>
      <p>
        顶部是连接配置，中间是属性表，下面依次是趋势、命令区与日志。先把这个骨架跑通，
        再把属性表替换成卡片、仪表盘或 <code>QChartView</code>。
      </p>
      <p>
        仓库模板内置 <code>SparklineWidget</code>：点击数值属性行加载最近 1 小时历史，
        首次收到属性后自动选中第一个数值属性；它不依赖 Qt Charts，方便在未安装 Charts 的 Kit 上直接运行。
        通讯层对属性请求做了去重，并在收到 <code>429</code> 时按 <code>Retry-After</code>
        暂停轮询，避免慢网络或限流场景下请求叠加。
        配置 <code>devices</code> 后顶部会出现设备下拉框；切换设备会清空旧数据并重新加载，
        未完成的旧设备请求会按请求代际丢弃。
      </p>
      <CodeBlock language="cpp" :code="windowSample" />

      <h2>从模板到项目</h2>
      <ol>
        <li>复制模板，改项目名、窗口标题、品牌色与图标。</li>
        <li>把属性表的数据源换成目标设备的物模型 identifier。</li>
        <li>按业务把表格拆成卡片 / 仪表 / 趋势图，保留统一的 <code>ApiClient</code>。</li>
        <li>把按钮动作接到 <code>writeProperty</code> 或 <code>callService</code>。</li>
        <li>增加离线提示、错误日志与轮询开关，再做打包。</li>
      </ol>

      <DocCallout type="tip" title="先做 1 台设备">
        模板默认单设备、单窗口、2 秒轮询。多设备切换可以后加设备下拉框；
        先把连接、读值、控值、错误提示四条链路跑通，再扩展页面。
      </DocCallout>
    </template>

    <!-- 通讯与代码生成 -->
    <template v-else-if="section === 'api'">
      <h2>准备 API Key</h2>
      <ol>
        <li>用 ADMIN 登录工作台，进入「开发接入 → API 密钥」。</li>
        <li>创建密钥并立即保存明文；平台只保存哈希。</li>
        <li>把 Base URL、API Key、deviceKey 写入上位机配置。</li>
      </ol>
      <CodeBlock language="json" :code="configSample" />
      <p>
        Base URL 必须包含 context-path，例如 <code>https://your-server/api/external/v1</code>；
        本地调试常用 <code>http://localhost/api/external/v1</code>。
        API Key 通过环境变量或系统凭据注入，例如 <code>PLATFORM_API_KEY</code>，不要写进配置文件。
      </p>

      <h2>接口映射</h2>
      <table>
        <thead>
          <tr><th>上位机动作</th><th>开放 API</th><th>说明</th></tr>
        </thead>
        <tbody>
          <tr><td>在线状态</td><td><code>GET /devices/{deviceKey}/status</code></td><td>轮询或手动刷新</td></tr>
          <tr><td>属性看值</td><td><code>GET /devices/{deviceKey}/properties</code></td><td>返回 identifier / value / reportedAt</td></tr>
          <tr><td>趋势图</td><td><code>GET /devices/{deviceKey}/properties/history</code></td><td>按时间桶聚合，避免拉原始点</td></tr>
          <tr><td>属性控制</td><td><code>PUT /devices/{deviceKey}/shadow/desired</code></td><td>离线设备排队，上线后补发</td></tr>
          <tr><td>服务调用</td><td><code>POST /devices/{deviceKey}/command</code></td><td><code>type=service</code> + identifier</td></tr>
          <tr><td>告警 / 事件</td><td><code>GET /alerts</code>、<code>GET /devices/{deviceKey}/events</code></td><td>按账号范围返回</td></tr>
        </tbody>
      </table>

      <h2>与控制台生成代码的关系</h2>
      <p>
        「设备调试中心 → 生成上位机代码」目前提供 Python / JavaScript / Java 三种最小联调样例，
        它们与 Qt 使用同一套 Open API。可以先用生成代码验证接口与参数，再把请求迁到
        <code>QNetworkAccessManager</code>；当前没有 Qt / C++ 代码生成模板。
      </p>

      <h2>轮询还是推送</h2>
      <ul>
        <li><strong>2~5 秒轮询属性</strong>：实现最简单，适合大多数单设备 HMI。</li>
        <li><strong>趋势按需查询</strong>：切换时间范围时再请求 history，不要每秒拉全量历史。</li>
        <li><strong>SSE</strong>：需要 JWT 登录态，API Key 不能访问 <code>/api/realtime/stream</code>；Qt 端要自己解析 SSE 帧，复杂度更高。</li>
        <li><strong>命令结果</strong>：异步命令返回 commandId 后，按需轮询命令记录或提示「已提交」。</li>
      </ul>

      <h2>错误处理</h2>
      <table>
        <thead>
          <tr><th>现象</th><th>处理</th></tr>
        </thead>
        <tbody>
          <tr><td><code>401 / 3501</code></td><td>API Key 缺失或失效，停止轮询并提示重新配置</td></tr>
          <tr><td><code>403 / 2003</code></td><td>deviceKey 不属于密钥账号，检查设备归属</td></tr>
          <tr><td><code>429 / 4002</code></td><td>按 <code>Retry-After</code> 退避，降低轮询频率</td></tr>
          <tr><td>网络错误 / 超时</td><td>保留上次值并标记「连接中断」，指数退避后重试</td></tr>
        </tbody>
      </table>
      <p>
        Qt 端不要只判断 HTTP 200，还要判断响应体 <code>code == 200</code>；
        业务错误可能有非 200 的 HTTP 状态，也可能以统一响应体返回。
      </p>
    </template>

    <!-- 打包与部署 -->
    <template v-else>
      <h2>Windows 打包</h2>
      <CodeBlock language="bash" :code="windowsBuildSample" />
      <p>
        <code>windeployqt</code> 会把 Qt 运行库、平台插件和网络 TLS 依赖复制到输出目录。
        发布前在一台没有 Qt 环境的机器上试运行，确认没有缺少 DLL 或插件。
      </p>

      <h2>Linux 打包</h2>
      <CodeBlock language="bash" :code="linuxBuildSample" />
      <p>也可使用 linuxdeployqt 生成 AppImage；工控机上建议固定 Qt 版本与系统镜像，避免运行库漂移。</p>

      <h2>配置与密钥</h2>
      <p>
        Base URL、deviceKey、轮询周期可以放配置文件；API Key 不建议明文写进配置文件。
        Windows 可用凭据管理器，Linux 可用系统密钥环，或至少限制配置文件权限并单独注入环境变量。
      </p>
      <DocCallout type="danger" title="不要打包密钥">
        API Key 等同账号在开放 API 的读写能力。不要把密钥写入源码、资源文件或安装包，
        泄露后立即在「API 密钥」页撤销。
      </DocCallout>

      <h2>生产检查清单</h2>
      <ul>
        <li>启用 HTTPS，Qt 端保留证书校验；不要为了省事关闭 <code>QSslSocket</code> 校验。</li>
        <li>启动时先显示「未连接」，连接成功后再开启轮询；断网时保留最后值并标注时间。</li>
        <li>命令按钮做防抖与确认，避免重复下发；失败时给出业务码与重试入口。</li>
        <li>工控机场景配置开机自启、全屏 / Kiosk 模式与日志轮转。</li>
        <li>版本升级时同时核对 API 契约、物模型 identifier 与设备 Topic 前缀。</li>
      </ul>

      <h2>和 Web 上位机怎么选</h2>
      <p>
        需要远程访问、零安装、快速交付时用平台 Web 上位机；需要本地外设、离线缓存、
        工控机深度融合时用 Qt。两者共用开放 API，业务数据与命令语义一致，可以并存。
      </p>
    </template>
  </DocArticle>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import DocArticle from '@/components/docs/DocArticle.vue'
import CodeBlock from '@/components/docs/CodeBlock.vue'
import DocCallout from '@/components/docs/DocCallout.vue'

const route = useRoute()

const sections = {
  qt: { title: 'Qt 快速上位机', desc: '从最小 Qt Widgets 工程开始，按业务魔改桌面 HMI' },
  api: { title: '通讯与代码生成', desc: 'Open API 映射、轮询策略、错误处理与代码生成复用' },
  deploy: { title: '打包与部署', desc: 'Windows / Linux 发布、密钥管理与生产检查清单' }
}

const section = computed(() => route.params.section || 'qt')
const current = computed(() => sections[section.value] || sections.deploy)

const scaffoldSample = `node scripts/create-hmi.mjs \\
  --out ../line1-hmi \\
  --device sensor-temp-001 \\
  --title "1号线温度监控" \\
  --base-url http://localhost/api/external/v1

# 生成后按实际物模型编辑 model.json
# 设置 API Key 后启动，Key 不进入 config.json`

const treeSample = `qt-hmi/
├── CMakeLists.txt
├── qt-hmi.pro
├── config.json
├── model.json
├── README.md
└── src/
    ├── main.cpp
    ├── mainwindow.h / mainwindow.cpp
    ├── apiclient.h / apiclient.cpp
    ├── sparkline.h / sparkline.cpp           # 自绘趋势图
    └── hmimodel.h / hmimodel.cpp             # config / model 解析`

const cmakeSample = `cmake_minimum_required(VERSION 3.16)
project(mqtt-hmi VERSION 0.1 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(QT NAMES Qt6 Qt5 REQUIRED COMPONENTS Widgets Network)
find_package(Qt\${QT_VERSION_MAJOR} REQUIRED COMPONENTS Widgets Network)

add_executable(mqtt-hmi
    src/main.cpp
    src/mainwindow.cpp src/mainwindow.h
    src/apiclient.cpp src/apiclient.h
    src/hmimodel.cpp src/hmimodel.h
    src/sparkline.cpp src/sparkline.h
)

target_include_directories(mqtt-hmi PRIVATE src)
target_link_libraries(mqtt-hmi PRIVATE
    Qt\${QT_VERSION_MAJOR}::Widgets
    Qt\${QT_VERSION_MAJOR}::Network
)`

const apiHeaderSample = `#pragma once

#include <QJsonArray>
#include <QJsonObject>
#include <QJsonValue>
#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QObject>
#include <QUrl>

class ApiClient : public QObject {
    Q_OBJECT
public:
    explicit ApiClient(QObject *parent = nullptr);

    void configure(const QUrl &baseUrl, const QString &apiKey, const QString &deviceKey);
    bool isConfigured() const;

    void fetchStatus();
    void fetchProperties();
    void writeProperty(const QString &identifier, const QJsonValue &value);
    void callService(const QString &identifier, const QJsonObject &params = {});

signals:
    void statusReceived(const QJsonObject &status);
    void propertiesReceived(const QJsonArray &properties);
    void commandAccepted(const QString &commandId, const QString &state);
    void requestFailed(const QString &message);

private:
    QNetworkRequest request(const QString &path) const;

    QNetworkAccessManager *m_net;
    QUrl m_baseUrl;
    QString m_apiKey;
    QString m_deviceKey;
};`

const apiSourceSample = `#include "apiclient.h"

#include <QJsonDocument>
#include <QNetworkReply>
#include <QNetworkRequest>

ApiClient::ApiClient(QObject *parent)
    : QObject(parent), m_net(new QNetworkAccessManager(this)) {}

void ApiClient::configure(const QUrl &baseUrl, const QString &apiKey,
                          const QString &deviceKey) {
    m_baseUrl = baseUrl;          // https://your-server/api/external/v1
    m_apiKey = apiKey;
    m_deviceKey = deviceKey;
}

bool ApiClient::isConfigured() const {
    return m_baseUrl.isValid() && !m_apiKey.isEmpty() && !m_deviceKey.isEmpty();
}

QNetworkRequest ApiClient::request(const QString &path) const {
    QUrl url(m_baseUrl);
    url.setPath(m_baseUrl.path() + path);
    QNetworkRequest req(url);
    req.setRawHeader("X-API-Key", m_apiKey.toUtf8());
    req.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
    return req;
}

void ApiClient::fetchStatus() {
    auto *reply = m_net->get(request(QString("/devices/%1/status").arg(m_deviceKey)));
    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        reply->deleteLater();
        if (reply->error() != QNetworkReply::NoError) {
            emit requestFailed(reply->errorString());
            return;
        }
        const auto body = QJsonDocument::fromJson(reply->readAll()).object();
        if (body.value("code").toInt() != 200) {
            emit requestFailed(body.value("message").toString());
            return;
        }
        emit statusReceived(body.value("data").toObject());
    });
}

void ApiClient::fetchProperties() {
    auto *reply = m_net->get(request(QString("/devices/%1/properties").arg(m_deviceKey)));
    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        reply->deleteLater();
        if (reply->error() != QNetworkReply::NoError) {
            emit requestFailed(reply->errorString());
            return;
        }
        const auto body = QJsonDocument::fromJson(reply->readAll()).object();
        if (body.value("code").toInt() != 200) {
            emit requestFailed(body.value("message").toString());
            return;
        }
        emit propertiesReceived(body.value("data").toArray());
    });
}

void ApiClient::writeProperty(const QString &identifier, const QJsonValue &value) {
    const QJsonObject params{{identifier, value}};
    const QJsonObject payload{
        {"type", "property_set"},
        {"params", params},
        {"callType", "async"}
    };
    auto *reply = m_net->put(
        request(QString("/devices/%1/shadow/desired").arg(m_deviceKey)),
        QJsonDocument(payload).toJson(QJsonDocument::Compact));
    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        reply->deleteLater();
        if (reply->error() != QNetworkReply::NoError) {
            emit requestFailed(reply->errorString());
            return;
        }
        const auto body = QJsonDocument::fromJson(reply->readAll()).object();
        if (body.value("code").toInt() != 200) {
            emit requestFailed(body.value("message").toString());
            return;
        }
        const auto data = body.value("data").toObject();
        emit commandAccepted(data.value("commandId").toString(),
                             data.value("status").toString());
    });
}

void ApiClient::callService(const QString &identifier, const QJsonObject &params) {
    const QJsonObject payload{
        {"type", "service"},
        {"identifier", identifier},
        {"params", params},
        {"callType", "async"}
    };
    auto *reply = m_net->post(
        request(QString("/devices/%1/command").arg(m_deviceKey)),
        QJsonDocument(payload).toJson(QJsonDocument::Compact));
    connect(reply, &QNetworkReply::finished, this, [this, reply] {
        reply->deleteLater();
        if (reply->error() != QNetworkReply::NoError) {
            emit requestFailed(reply->errorString());
            return;
        }
        const auto body = QJsonDocument::fromJson(reply->readAll()).object();
        if (body.value("code").toInt() != 200) {
            emit requestFailed(body.value("message").toString());
            return;
        }
        const auto data = body.value("data").toObject();
        emit commandAccepted(data.value("commandId").toString(),
                             data.value("status").toString());
    });
}`

const windowSample = `#include "mainwindow.h"
#include "apiclient.h"


#include <QHeaderView>
#include <QJsonArray>
#include <QJsonObject>
#include <QLineEdit>
#include <QPlainTextEdit>
#include <QPushButton>
#include <QTableWidget>
#include <QTableWidgetItem>
#include <QTimer>
#include <QVBoxLayout>

MainWindow::MainWindow(QWidget *parent) : QMainWindow(parent) {
    auto *central = new QWidget(this);
    auto *layout = new QVBoxLayout(central);

    auto *deviceKey = new QLineEdit("<deviceKey>", this);
    auto *identifier = new QLineEdit("temperature", this);
    auto *value = new QLineEdit("26.5", this);
    auto *send = new QPushButton("下发属性", this);
    auto *log = new QPlainTextEdit(this);
    log->setReadOnly(true);

    auto *table = new QTableWidget(0, 3, this);
    table->setHorizontalHeaderLabels({"属性", "值", "上报时间"});
    table->horizontalHeader()->setStretchLastSection(true);

    layout->addWidget(deviceKey);
    layout->addWidget(table);
    layout->addWidget(identifier);
    layout->addWidget(value);
    layout->addWidget(send);
    layout->addWidget(log);
    setCentralWidget(central);

    const QUrl apiBase("https://your-server/api/external/v1");
    const QString apiKey = qEnvironmentVariable("PLATFORM_API_KEY");
    auto *api = new ApiClient(this);
    api->configure(apiBase, apiKey, deviceKey->text());

    connect(api, &ApiClient::propertiesReceived, this,
            [table](const QJsonArray &rows) {
        table->setRowCount(rows.size());
        for (int i = 0; i < rows.size(); ++i) {
            const auto row = rows.at(i).toObject();
            table->setItem(i, 0, new QTableWidgetItem(row.value("identifier").toString()));
            table->setItem(i, 1, new QTableWidgetItem(row.value("value").toString()));
            table->setItem(i, 2, new QTableWidgetItem(row.value("reportedAt").toString()));
        }
    });
    connect(api, &ApiClient::requestFailed, this,
            [log](const QString &message) { log->appendPlainText("错误: " + message); });
    connect(send, &QPushButton::clicked, this, [api, identifier, value] {
        api->writeProperty(identifier->text(), value->text().toDouble());
    });
    connect(deviceKey, &QLineEdit::editingFinished, this,
            [api, apiBase, apiKey, deviceKey] {
        api->configure(apiBase, apiKey, deviceKey->text());
        api->fetchProperties();
    });

    auto *timer = new QTimer(this);
    timer->setInterval(2000);
    connect(timer, &QTimer::timeout, api, &ApiClient::fetchProperties);
    timer->start();

    api->fetchStatus();
    api->fetchProperties();
}`

const configSample = `{
  "baseUrl": "https://your-server/api/external/v1",
  "deviceKey": "sensor-temp-001",
  "pollMs": 2000
}`

const windowsBuildSample = `cmake -S . -B build -G "Visual Studio 17 2022" -A x64
cmake --build build --config Release

# 发布目录需要 Qt 运行库与插件
windeployqt build/Release/mqtt-hmi.exe
# 将 exe、Qt 依赖和配置模板一起打包；API Key 不入包`

const linuxBuildSample = `cmake -S . -B build -DCMAKE_BUILD_TYPE=Release
cmake --build build -j

# 生成 AppImage：按发行版选择 linuxdeployqt /
# linuxdeploy + linuxdeploy-plugin-qt，参数以工具版本文档为准`
</script>
