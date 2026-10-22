#include "mainwindow.h"

#include "apiclient.h"
#include "sparkline.h"

#include <QAbstractItemView>
#include <QCheckBox>
#include <QComboBox>
#include <QDateTime>
#include <QFormLayout>
#include <QGroupBox>
#include <QHBoxLayout>
#include <QHash>
#include <QHeaderView>
#include <QJsonValue>
#include <QLabel>
#include <QLineEdit>
#include <QPlainTextEdit>
#include <QPushButton>
#include <QScrollArea>
#include <QTableWidget>
#include <QTableWidgetItem>
#include <QTime>
#include <QTimer>
#include <QVBoxLayout>

MainWindow::MainWindow(const HmiDefinition &definition, QWidget *parent)
    : QMainWindow(parent)
    , m_def(definition)
    , m_api(new ApiClient(this))
    , m_timer(new QTimer(this))
{
    setWindowTitle(m_def.title);
    resize(1080, 760);
    buildUi();

    m_timer->setInterval(m_def.pollMs);
    connect(m_timer, &QTimer::timeout, this, [this] {
        if (!m_autoPoll->isChecked()) {
            return;
        }
        m_api->fetchProperties();
        if (++m_pollTicks % 5 == 0) {
            m_api->fetchStatus();
            if (!m_trendIdentifier.isEmpty()) {
                m_api->fetchHistory(m_trendIdentifier);
            }
        }
    });
    connect(m_autoPoll, &QCheckBox::toggled, this, [this](bool checked) {
        if (checked && !m_rateLimited && ensureReady()) {
            m_timer->start();
            refresh();
        } else if (!checked) {
            m_timer->stop();
        }
    });

    connect(m_api, &ApiClient::statusReceived, this, &MainWindow::updateStatus);
    connect(m_api, &ApiClient::propertiesReceived, this, &MainWindow::updateProperties);
    connect(m_api, &ApiClient::historyReceived, this, &MainWindow::updateTrend);
    connect(m_api, &ApiClient::commandAccepted, this,
            [this](const QString &commandId, const QString &state) {
        log(QStringLiteral("命令已提交：%1（%2）").arg(commandId, state));
    });
    connect(m_api, &ApiClient::requestFailed, this,
            [this](const QString &message) { log(QStringLiteral("请求失败：%1").arg(message)); });
    connect(m_api, &ApiClient::rateLimited, this, &MainWindow::handleRateLimited);

    connect(m_deviceSelector, QOverload<int>::of(&QComboBox::currentIndexChanged),
            this, &MainWindow::handleDeviceChanged);

    const QString apiKey = qEnvironmentVariable("PLATFORM_API_KEY");
    m_api->configure(m_def.baseUrl, apiKey, m_def.deviceKey);
    if (apiKey.isEmpty()) {
        log(QStringLiteral("未设置 PLATFORM_API_KEY，界面可预览但不会请求数据。"));
        m_status->setText(QStringLiteral("未配置 API Key"));
    } else {
        m_timer->start();
        refresh();
    }
}

void MainWindow::buildUi()
{
    auto *central = new QWidget(this);
    auto *layout = new QVBoxLayout(central);
    layout->setContentsMargins(20, 16, 20, 16);
    layout->setSpacing(12);

    auto *top = new QHBoxLayout;
    auto *title = new QLabel(QStringLiteral("<b>%1</b>").arg(m_def.title.toHtmlEscaped()), central);
    title->setTextFormat(Qt::RichText);
    auto *deviceLabel = new QLabel(QStringLiteral("设备："), central);
    m_deviceSelector = new QComboBox(central);
    for (const DeviceEntry &device : m_def.devices) {
        m_deviceSelector->addItem(device.label, device.key);
    }
    const int selected = m_deviceSelector->findData(m_def.deviceKey);
    if (selected >= 0) {
        m_deviceSelector->setCurrentIndex(selected);
    }
    m_deviceSelector->setEnabled(m_def.devices.size() > 1);
    m_status = new QLabel(QStringLiteral("等待连接"), central);
    m_status->setMinimumWidth(110);
    auto *refreshButton = new QPushButton(QStringLiteral("刷新"), central);
    m_autoPoll = new QCheckBox(QStringLiteral("自动刷新"), central);
    m_autoPoll->setChecked(true);
    top->addWidget(title);
    top->addSpacing(12);
    top->addWidget(deviceLabel);
    top->addWidget(m_deviceSelector);
    top->addWidget(m_status);
    top->addStretch();
    top->addWidget(m_autoPoll);
    top->addWidget(refreshButton);
    layout->addLayout(top);

    m_table = new QTableWidget(0, 4, central);
    m_table->setHorizontalHeaderLabels(
        {QStringLiteral("属性"), QStringLiteral("值"), QStringLiteral("单位"), QStringLiteral("上报时间")});
    m_table->horizontalHeader()->setSectionResizeMode(0, QHeaderView::Stretch);
    m_table->horizontalHeader()->setSectionResizeMode(1, QHeaderView::ResizeToContents);
    m_table->horizontalHeader()->setSectionResizeMode(2, QHeaderView::ResizeToContents);
    m_table->horizontalHeader()->setStretchLastSection(true);
    m_table->verticalHeader()->setVisible(false);
    m_table->setEditTriggers(QAbstractItemView::NoEditTriggers);
    m_table->setSelectionBehavior(QAbstractItemView::SelectRows);
    m_table->setAlternatingRowColors(true);
    layout->addWidget(m_table, 2);

    m_trend = new SparklineWidget(central);
    layout->addWidget(m_trend);

    connect(m_table, &QTableWidget::cellClicked, this, [this](int row, int) {
        QTableWidgetItem *nameItem = m_table->item(row, 0);
        QTableWidgetItem *unitItem = m_table->item(row, 2);
        if (!nameItem) {
            return;
        }
        const QString identifier = nameItem->data(Qt::UserRole).toString();
        if (identifier.isEmpty()) {
            return;
        }
        bool numeric = false;
        for (const PropertyDef &property : m_def.properties) {
            if (property.identifier == identifier) {
                numeric = property.type == QStringLiteral("number");
                break;
            }
        }
        m_trendTitle = nameItem->text();
        m_trendUnit = unitItem ? unitItem->text() : QString();
        if (!numeric) {
            m_trendIdentifier.clear();
            m_trend->setEmptyText(QStringLiteral("该属性不是数值类型，暂不支持趋势"));
            m_trend->setSeries(m_trendTitle, m_trendUnit, {});
            log(QStringLiteral("%1 不是数值属性").arg(identifier));
            return;
        }

        m_trendIdentifier = identifier;
        m_trend->setEmptyText(QStringLiteral("正在加载趋势..."));
        m_trend->setSeries(m_trendTitle, m_trendUnit, {});
        if (ensureReady()) {
            m_api->fetchHistory(identifier);
        }
    });

    auto *controlBox = new QGroupBox(QStringLiteral("控制"), central);
    auto *form = new QFormLayout(controlBox);
    form->setFieldGrowthPolicy(QFormLayout::AllNonFixedFieldsGrow);

    int writableCount = 0;
    for (const PropertyDef &property : m_def.properties) {
        if (!property.writable) {
            continue;
        }
        ++writableCount;

        auto *row = new QWidget(controlBox);
        auto *rowLayout = new QHBoxLayout(row);
        rowLayout->setContentsMargins(0, 0, 0, 0);

        QLineEdit *line = nullptr;
        QCheckBox *check = nullptr;
        if (property.type == QStringLiteral("bool")) {
            check = new QCheckBox(QStringLiteral("开启"), row);
        } else {
            line = new QLineEdit(row);
            line->setPlaceholderText(property.type == QStringLiteral("number")
                                         ? QStringLiteral("输入数值")
                                         : QStringLiteral("输入文本"));
        }
        auto *writeButton = new QPushButton(QStringLiteral("写入"), row);

        if (check) {
            rowLayout->addWidget(check, 1);
        } else {
            rowLayout->addWidget(line, 1);
        }
        rowLayout->addWidget(writeButton);

        const QString identifier = property.identifier;
        const QString type = property.type;
        connect(writeButton, &QPushButton::clicked, this,
                [this, identifier, type, line, check] {
            if (!ensureReady()) {
                return;
            }
            QJsonValue value;
            if (type == QStringLiteral("bool")) {
                value = check->isChecked();
            } else {
                const QString text = line->text().trimmed();
                bool ok = false;
                const double number = text.toDouble(&ok);
                value = (type == QStringLiteral("number") && ok) ? QJsonValue(number)
                                                                  : QJsonValue(text);
            }
            m_api->writeProperty(identifier, value);
            log(QStringLiteral("写入 %1 = %2").arg(identifier, value.toVariant().toString()));
        });

        form->addRow(property.label + (property.unit.isEmpty() ? QString() : QStringLiteral("（%1）").arg(property.unit)), row);
    }

    for (const ServiceDef &service : m_def.services) {
        auto *button = new QPushButton(QStringLiteral("调用"), controlBox);
        connect(button, &QPushButton::clicked, this, [this, identifier = service.identifier] {
            if (!ensureReady()) {
                return;
            }
            m_api->callService(identifier);
            log(QStringLiteral("调用服务 %1").arg(identifier));
        });
        form->addRow(service.label, button);
    }

    if (writableCount == 0 && m_def.services.isEmpty()) {
        form->addRow(new QLabel(QStringLiteral("model.json 中未配置控制项"), controlBox));
    }

    auto *scroll = new QScrollArea(central);
    scroll->setWidgetResizable(true);
    scroll->setWidget(controlBox);
    layout->addWidget(scroll, 1);

    m_log = new QPlainTextEdit(central);
    m_log->setReadOnly(true);
    m_log->setMaximumBlockCount(2000);
    m_log->setPlaceholderText(QStringLiteral("通讯日志"));
    layout->addWidget(m_log, 1);

    setCentralWidget(central);
    connect(refreshButton, &QPushButton::clicked, this, &MainWindow::refresh);
}

void MainWindow::refresh()
{
    if (m_rateLimited) {
        log(QStringLiteral("限流冷却中，请稍后再刷新"));
        return;
    }
    if (!ensureReady()) {
        return;
    }
    if (m_api->isPropertiesPending()) {
        log(QStringLiteral("属性请求仍在进行，等待当前请求完成"));
    }
    m_api->fetchStatus();
    m_api->fetchProperties();
}

bool MainWindow::ensureReady()
{
    if (m_api->isConfigured()) {
        return true;
    }
    log(QStringLiteral("未就绪：请设置环境变量 PLATFORM_API_KEY 后重启程序。"));
    m_status->setText(QStringLiteral("未配置 API Key"));
    return false;
}

void MainWindow::updateStatus(const QJsonObject &status)
{
    const bool online = status.value(QStringLiteral("online")).toBool();
    m_status->setText(online ? QStringLiteral("● 在线") : QStringLiteral("● 离线"));
    m_status->setStyleSheet(online ? QStringLiteral("color:#00B42A;font-weight:600;")
                                   : QStringLiteral("color:#F53F3F;font-weight:600;"));
    const QString lastSeen = status.value(QStringLiteral("lastSeen")).toString();
    m_status->setToolTip(lastSeen.isEmpty() ? QString() : QStringLiteral("最后在线：%1").arg(lastSeen));
}

void MainWindow::updateProperties(const QJsonArray &properties)
{
    QHash<QString, PropertyDef> definitions;
    for (const PropertyDef &property : m_def.properties) {
        definitions.insert(property.identifier, property);
    }

    m_table->setRowCount(properties.size());
    for (int row = 0; row < properties.size(); ++row) {
        const QJsonObject property = properties.at(row).toObject();
        const QString identifier = property.value(QStringLiteral("identifier")).toString();
        const PropertyDef definition = definitions.value(identifier);

        m_table->setItem(row, 0,
                         new QTableWidgetItem(definition.label.isEmpty() ? identifier
                                                                         : definition.label));
        auto *nameItem = new QTableWidgetItem(definition.label.isEmpty() ? identifier
                                                                         : definition.label);
        nameItem->setData(Qt::UserRole, identifier);
        m_table->setItem(row, 0, nameItem);

        m_table->setItem(row, 1,
                         new QTableWidgetItem(property.value(QStringLiteral("value")).toString()));
        m_table->setItem(row, 2, new QTableWidgetItem(definition.unit));
        m_table->setItem(row, 3,
                         new QTableWidgetItem(property.value(QStringLiteral("reportedAt")).toString()));
    }

    if (m_trendIdentifier.isEmpty()) {
        for (int row = 0; row < properties.size(); ++row) {
            const QString identifier =
                m_table->item(row, 0)->data(Qt::UserRole).toString();
            if (definitions.value(identifier).type != QStringLiteral("number")) {
                continue;
            }
            m_trendIdentifier = identifier;
            m_trendTitle = m_table->item(row, 0)->text();
            m_trendUnit = m_table->item(row, 2)->text();
            m_table->selectRow(row);
            m_trend->setEmptyText(QStringLiteral("正在加载趋势..."));
            m_api->fetchHistory(identifier);
            break;
        }
    }
}

void MainWindow::updateTrend(const QString &identifier, const QJsonArray &points)
{
    if (identifier != m_trendIdentifier) {
        return;
    }

    QVector<QPointF> series;
    series.reserve(points.size());
    for (const QJsonValue &value : points) {
        const QJsonObject point = value.toObject();
        const QString timeText = point.value(QStringLiteral("time")).toString();
        QDateTime time = QDateTime::fromString(timeText, Qt::ISODate);
        if (!time.isValid()) {
            time = QDateTime::fromString(timeText, QStringLiteral("yyyy-MM-dd HH:mm:ss"));
        }
        if (!time.isValid()) {
            continue;
        }
        const QJsonValue average = point.value(QStringLiteral("avg"));
        if (!average.isDouble()) {
            continue;
        }
        series.append(QPointF(double(time.toMSecsSinceEpoch()), average.toDouble()));
    }
    m_trend->setEmptyText(series.isEmpty() ? QStringLiteral("该时间段暂无趋势数据")
                                          : QString());
    m_trend->setSeries(m_trendTitle, m_trendUnit, series);
}

void MainWindow::handleDeviceChanged(int index)
{
    const QString key = m_deviceSelector->itemData(index).toString();
    if (key.isEmpty() || key == m_def.deviceKey) {
        return;
    }
    m_def.deviceKey = key;
    m_trendIdentifier.clear();
    m_trendTitle.clear();
    m_trendUnit.clear();
    m_trend->setEmptyText(QStringLiteral("点击属性行加载趋势"));
    m_trend->setSeries(QString(), QString(), {});
    m_table->setRowCount(0);
    m_pollTicks = 0;
    m_api->configure(m_def.baseUrl, qEnvironmentVariable("PLATFORM_API_KEY"), key);
    log(QStringLiteral("切换设备：%1").arg(m_deviceSelector->currentText()));
    refresh();
}

void MainWindow::handleRateLimited(int retryAfterSeconds)
{
    if (m_rateLimited) {
        return;
    }
    m_rateLimited = true;
    m_timer->stop();
    m_status->setText(QStringLiteral("● 限流中"));
    m_status->setStyleSheet(QStringLiteral("color:#FF7D00;font-weight:600;"));
    log(QStringLiteral("请求被限流，%1 秒后恢复轮询").arg(retryAfterSeconds));

    QTimer::singleShot(retryAfterSeconds * 1000, this, [this] {
        m_rateLimited = false;
        m_status->setText(QStringLiteral("等待连接"));
        if (m_autoPoll->isChecked()) {
            m_timer->start();
            refresh();
        }
    });
}

void MainWindow::log(const QString &message)
{
    m_log->appendPlainText(QStringLiteral("[%1] %2")
                               .arg(QTime::currentTime().toString(QStringLiteral("HH:mm:ss")), message));
}
