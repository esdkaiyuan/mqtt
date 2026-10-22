#include "apiclient.h"

#include <QDateTime>
#include <QJsonDocument>
#include <QNetworkReply>
#include <QTimer>
#include <QUrlQuery>

#include <utility>

ApiClient::ApiClient(QObject *parent)
    : QObject(parent), m_net(new QNetworkAccessManager(this))
{
}

void ApiClient::configure(const QUrl &baseUrl, const QString &apiKey, const QString &deviceKey)
{
    ++m_generation;
    m_propertiesPending = false;
    m_baseUrl = baseUrl;
    m_apiKey = apiKey;
    m_deviceKey = deviceKey;
}

bool ApiClient::isConfigured() const
{
    return m_baseUrl.isValid() && !m_apiKey.isEmpty() && !m_deviceKey.isEmpty();
}

bool ApiClient::isPropertiesPending() const
{
    return m_propertiesPending;
}

QNetworkRequest ApiClient::request(const QString &path) const
{
    QUrl url(m_baseUrl);
    QString basePath = m_baseUrl.path();
    while (basePath.endsWith(QLatin1Char('/'))) {
        basePath.chop(1);
    }
    url.setPath(basePath + path);
    return request(url);
}

QNetworkRequest ApiClient::request(const QUrl &url) const
{
    QNetworkRequest req(url);
    req.setRawHeader("X-API-Key", m_apiKey.toUtf8());
    req.setHeader(QNetworkRequest::ContentTypeHeader, QStringLiteral("application/json"));
    return req;
}

void ApiClient::fetchStatus()
{
    get(QStringLiteral("/devices/%1/status").arg(m_deviceKey), [this](const QJsonValue &data) {
        emit statusReceived(data.toObject());
    });
}

void ApiClient::fetchProperties()
{
    if (m_propertiesPending) {
        return;
    }
    m_propertiesPending = true;
    get(QStringLiteral("/devices/%1/properties").arg(m_deviceKey),
        [this](const QJsonValue &data) { emit propertiesReceived(data.toArray()); },
        [this] { m_propertiesPending = false; });
}

void ApiClient::fetchHistory(const QString &identifier, int minutes, const QString &bucket)
{
    QUrl url(m_baseUrl);
    QString basePath = m_baseUrl.path();
    while (basePath.endsWith(QLatin1Char('/'))) {
        basePath.chop(1);
    }
    url.setPath(basePath + QStringLiteral("/devices/%1/properties/history").arg(m_deviceKey));

    const QDateTime end = QDateTime::currentDateTime();
    const QDateTime start = end.addSecs(-qMax(1, minutes) * 60);
    QUrlQuery query;
    query.addQueryItem(QStringLiteral("identifiers"), identifier);
    query.addQueryItem(QStringLiteral("bucket"), bucket);
    query.addQueryItem(QStringLiteral("startTime"),
                       start.toString(QStringLiteral("yyyy-MM-dd HH:mm:ss")));
    query.addQueryItem(QStringLiteral("endTime"),
                       end.toString(QStringLiteral("yyyy-MM-dd HH:mm:ss")));
    url.setQuery(query);

    handle(m_net->get(request(url)), [this, identifier](const QJsonValue &data) {
        const QJsonArray series = data.toArray();
        const QJsonArray points = series.isEmpty()
                                      ? QJsonArray{}
                                      : series.at(0).toObject().value(QStringLiteral("points")).toArray();
        emit historyReceived(identifier, points);
    }, {}, m_generation);
}

void ApiClient::writeProperty(const QString &identifier, const QJsonValue &value)
{
    const QJsonObject payload{
        {QStringLiteral("type"), QStringLiteral("property_set")},
        {QStringLiteral("params"), QJsonObject{{identifier, value}}},
        {QStringLiteral("callType"), QStringLiteral("async")}
    };
    send("PUT", QStringLiteral("/devices/%1/shadow/desired").arg(m_deviceKey), payload,
         [this](const QJsonValue &data) {
        const QJsonObject record = data.toObject();
        emit commandAccepted(record.value(QStringLiteral("commandId")).toString(),
                             record.value(QStringLiteral("status")).toString());
    });
}

void ApiClient::callService(const QString &identifier, const QJsonObject &params)
{
    const QJsonObject payload{
        {QStringLiteral("type"), QStringLiteral("service")},
        {QStringLiteral("identifier"), identifier},
        {QStringLiteral("params"), params},
        {QStringLiteral("callType"), QStringLiteral("async")}
    };
    send("POST", QStringLiteral("/devices/%1/command").arg(m_deviceKey), payload,
         [this](const QJsonValue &data) {
        const QJsonObject record = data.toObject();
        emit commandAccepted(record.value(QStringLiteral("commandId")).toString(),
                             record.value(QStringLiteral("status")).toString());
    });
}

void ApiClient::get(const QString &path, DataHandler handler, std::function<void()> onFinished)
{
    handle(m_net->get(request(path)), std::move(handler), std::move(onFinished), m_generation);
}

void ApiClient::send(const QByteArray &method, const QString &path, const QJsonObject &payload,
                     DataHandler handler)
{
    const QNetworkRequest req = request(path);
    const QByteArray body = QJsonDocument(payload).toJson(QJsonDocument::Compact);
    QNetworkReply *reply = method == "PUT" ? m_net->put(req, body) : m_net->post(req, body);
    handle(reply, std::move(handler), {}, m_generation);
}

void ApiClient::handle(QNetworkReply *reply, DataHandler handler,
                       std::function<void()> onFinished, quint64 generation)
{
    QTimer::singleShot(10000, reply, [reply] { reply->abort(); });

    connect(reply, &QNetworkReply::finished, this,
            [this, reply, handler = std::move(handler), onFinished = std::move(onFinished),
             generation] {
        reply->deleteLater();
        if (generation != m_generation) {
            return;
        }
        const QByteArray raw = reply->readAll();
        const QJsonObject body = QJsonDocument::fromJson(raw).object();
        const int code = body.value(QStringLiteral("code")).toInt(-1);
        const int httpStatus =
            reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
        const auto finish = [&onFinished] {
            if (onFinished) {
                onFinished();
            }
        };

        if (httpStatus == 429 || code == 4002) {
            const int headerValue = reply->rawHeader("Retry-After").toInt();
            const int retryAfter = qBound(1, headerValue > 0 ? headerValue : 30, 3600);
            emit rateLimited(retryAfter);
            finish();
            return;
        }

        if (reply->error() != QNetworkReply::NoError || code != 200) {
            QString message = body.value(QStringLiteral("message")).toString();
            if (message.isEmpty()) {
                message = reply->errorString();
            }
            if (code > 0) {
                message = QStringLiteral("%1: %2").arg(code).arg(message);
            }
            emit requestFailed(message);
            finish();
            return;
        }
        handler(body.value(QStringLiteral("data")));
        finish();
    });
}
