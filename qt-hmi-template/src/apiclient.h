#pragma once

#include <QByteArray>
#include <QJsonArray>
#include <QJsonObject>
#include <QJsonValue>
#include <QNetworkAccessManager>
#include <QNetworkRequest>
#include <QObject>
#include <QUrl>

#include <functional>

class QNetworkReply;

class ApiClient : public QObject {
    Q_OBJECT
public:
    explicit ApiClient(QObject *parent = nullptr);

    void configure(const QUrl &baseUrl, const QString &apiKey, const QString &deviceKey);
    bool isConfigured() const;
    bool isPropertiesPending() const;

    void fetchStatus();
    void fetchProperties();
    void fetchHistory(const QString &identifier, int minutes = 60, const QString &bucket = QStringLiteral("1m"));
    void writeProperty(const QString &identifier, const QJsonValue &value);
    void callService(const QString &identifier, const QJsonObject &params = {});

signals:
    void statusReceived(const QJsonObject &status);
    void propertiesReceived(const QJsonArray &properties);
    void historyReceived(const QString &identifier, const QJsonArray &points);
    void commandAccepted(const QString &commandId, const QString &state);
    void requestFailed(const QString &message);
    void rateLimited(int retryAfterSeconds);

private:
    using DataHandler = std::function<void(const QJsonValue &)>;

    QNetworkRequest request(const QString &path) const;
    QNetworkRequest request(const QUrl &url) const;
    void get(const QString &path, DataHandler handler, std::function<void()> onFinished = {});
    void send(const QByteArray &method, const QString &path, const QJsonObject &payload,
              DataHandler handler);
    void handle(QNetworkReply *reply, DataHandler handler,
                std::function<void()> onFinished, quint64 generation);

    QNetworkAccessManager *m_net;
    QUrl m_baseUrl;
    QString m_apiKey;
    QString m_deviceKey;
    bool m_propertiesPending = false;
    quint64 m_generation = 0;
};
