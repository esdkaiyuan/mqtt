#pragma once

#include <QList>
#include <QString>
#include <QUrl>

struct PropertyDef {
    QString identifier;
    QString label;
    QString type = "string";
    QString unit;
    bool writable = false;
};

struct ServiceDef {
    QString identifier;
    QString label;
};

struct DeviceEntry {
    QString key;
    QString label;
};

struct HmiDefinition {
    QString title = "MQTT HMI";
    QUrl baseUrl;
    QString deviceKey;
    QList<DeviceEntry> devices;
    int pollMs = 2000;
    QList<PropertyDef> properties;
    QList<ServiceDef> services;
};

class HmiConfig {
public:
    static bool load(const QString &configPath, HmiDefinition *out, QString *error);
};
