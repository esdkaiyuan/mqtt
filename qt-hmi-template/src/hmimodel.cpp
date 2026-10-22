#include "hmimodel.h"

#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonParseError>

#include <algorithm>

namespace {

bool readJson(const QString &path, QJsonDocument *document, QString *error)
{
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly)) {
        *error = QStringLiteral("无法读取 %1").arg(path);
        return false;
    }

    QJsonParseError parseError;
    *document = QJsonDocument::fromJson(file.readAll(), &parseError);
    if (parseError.error != QJsonParseError::NoError) {
        *error = QStringLiteral("%1 不是合法 JSON: %2").arg(path, parseError.errorString());
        return false;
    }
    if (!document->isObject()) {
        *error = QStringLiteral("%1 的根节点必须是对象").arg(path);
        return false;
    }
    return true;
}

QString text(const QJsonObject &object, const QString &key, const QString &fallback = {})
{
    const QJsonValue value = object.value(key);
    return value.isString() ? value.toString() : fallback;
}

} // namespace

bool HmiConfig::load(const QString &configPath, HmiDefinition *out, QString *error)
{
    QJsonDocument configDoc;
    if (!readJson(configPath, &configDoc, error)) {
        return false;
    }

    const QJsonObject root = configDoc.object();
    out->title = text(root, QStringLiteral("title"), QStringLiteral("MQTT HMI"));
    out->baseUrl = QUrl(text(root, QStringLiteral("baseUrl")));
    out->deviceKey = text(root, QStringLiteral("deviceKey")).trimmed();
    out->pollMs = qBound(500, root.value(QStringLiteral("pollMs")).toInt(2000), 60000);

    const QJsonArray devices = root.value(QStringLiteral("devices")).toArray();
    for (const QJsonValue &value : devices) {
        QString key;
        QString label;
        if (value.isString()) {
            key = value.toString().trimmed();
            label = key;
        } else if (value.isObject()) {
            const QJsonObject item = value.toObject();
            key = text(item, QStringLiteral("key"), text(item, QStringLiteral("deviceKey"))).trimmed();
            label = text(item, QStringLiteral("label"), key);
        }
        if (!key.isEmpty() && key != QStringLiteral("<deviceKey>")) {
            out->devices.append({key, label.isEmpty() ? key : label});
        }
    }
    if (out->devices.isEmpty() && !out->deviceKey.isEmpty()) {
        out->devices.append({out->deviceKey, out->deviceKey});
    }
    if (out->deviceKey.isEmpty() && !out->devices.isEmpty()) {
        out->deviceKey = out->devices.first().key;
    }
    if (!out->deviceKey.isEmpty()) {
        const bool present = std::any_of(out->devices.cbegin(), out->devices.cend(),
                                         [&out](const DeviceEntry &device) {
            return device.key == out->deviceKey;
        });
        if (!present) {
            out->devices.append({out->deviceKey, out->deviceKey});
        }
    }

    if (!out->baseUrl.isValid() || out->baseUrl.scheme().isEmpty()
        || out->baseUrl.host().isEmpty()) {
        *error = QStringLiteral("config.json 的 baseUrl 必须是完整 URL，例如 https://host/api/external/v1");
        return false;
    }
    if (out->deviceKey.isEmpty() || out->deviceKey == QStringLiteral("<deviceKey>")) {
        *error = QStringLiteral("config.json 至少需要配置 deviceKey 或 devices");
        return false;
    }

    const QString modelName = text(root, QStringLiteral("model"), QStringLiteral("model.json"));
    const QFileInfo configInfo(configPath);
    const QString modelPath = QDir(configInfo.absolutePath()).filePath(modelName);

    QJsonDocument modelDoc;
    if (!readJson(modelPath, &modelDoc, error)) {
        return false;
    }

    const QJsonObject modelRoot = modelDoc.object();
    const QJsonArray properties = modelRoot.value(QStringLiteral("properties")).toArray();
    for (const QJsonValue &value : properties) {
        const QJsonObject item = value.toObject();
        const QString identifier = text(item, QStringLiteral("identifier")).trimmed();
        if (identifier.isEmpty()) {
            continue;
        }
        PropertyDef property;
        property.identifier = identifier;
        property.label = text(item, QStringLiteral("label"), identifier);
        property.type = text(item, QStringLiteral("type"), QStringLiteral("string")).toLower();
        property.unit = text(item, QStringLiteral("unit"));
        property.writable = item.value(QStringLiteral("writable")).toBool(false);
        out->properties.append(property);
    }

    const QJsonArray services = modelRoot.value(QStringLiteral("services")).toArray();
    for (const QJsonValue &value : services) {
        const QJsonObject item = value.toObject();
        const QString identifier = text(item, QStringLiteral("identifier")).trimmed();
        if (identifier.isEmpty()) {
            continue;
        }
        out->services.append({identifier, text(item, QStringLiteral("label"), identifier)});
    }

    return true;
}
