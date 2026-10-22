#include "hmimodel.h"
#include "mainwindow.h"

#include <QApplication>
#include <QDir>
#include <QFile>
#include <QMessageBox>

namespace {

QString resolveConfigPath()
{
    const QString fromEnv = qEnvironmentVariable("HMI_CONFIG");
    if (!fromEnv.isEmpty()) {
        return fromEnv;
    }

    const QString besideBinary = QDir(QCoreApplication::applicationDirPath())
                                     .filePath(QStringLiteral("config.json"));
    if (QFile::exists(besideBinary)) {
        return besideBinary;
    }
    return QDir::current().filePath(QStringLiteral("config.json"));
}

} // namespace

int main(int argc, char *argv[])
{
    QApplication app(argc, argv);
    QApplication::setApplicationName(QStringLiteral("MQTT HMI"));
    QApplication::setOrganizationName(QStringLiteral("MQTT Cloud"));

    const QString configPath = resolveConfigPath();
    HmiDefinition definition;
    QString error;
    if (!HmiConfig::load(configPath, &definition, &error)) {
        QMessageBox::critical(
            nullptr, QStringLiteral("无法启动上位机"),
            QStringLiteral("%1\n\n配置文件：%2\n可参考 config.example.json 与 model.example.json。")
                .arg(error, configPath));
        return 1;
    }

    MainWindow window(definition);
    window.show();
    return app.exec();
}
