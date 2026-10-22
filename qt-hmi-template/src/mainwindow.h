#pragma once

#include "hmimodel.h"

#include <QJsonArray>
#include <QJsonObject>
#include <QMainWindow>

class ApiClient;
class QCheckBox;
class QComboBox;
class QLabel;
class QPlainTextEdit;
class QTableWidget;
class QTimer;
class SparklineWidget;

class MainWindow : public QMainWindow {
    Q_OBJECT
public:
    explicit MainWindow(const HmiDefinition &definition, QWidget *parent = nullptr);

private:
    void buildUi();
    void refresh();
    bool ensureReady();
    void updateStatus(const QJsonObject &status);
    void updateProperties(const QJsonArray &properties);
    void updateTrend(const QString &identifier, const QJsonArray &points);
    void handleDeviceChanged(int index);
    void handleRateLimited(int retryAfterSeconds);
    void log(const QString &message);

    HmiDefinition m_def;
    ApiClient *m_api;
    QLabel *m_status;
    QTableWidget *m_table;
    QPlainTextEdit *m_log;
    QTimer *m_timer;
    QCheckBox *m_autoPoll;
    QComboBox *m_deviceSelector;
    SparklineWidget *m_trend;
    QString m_trendIdentifier;
    QString m_trendTitle;
    QString m_trendUnit;
    int m_pollTicks = 0;
    bool m_rateLimited = false;
};
