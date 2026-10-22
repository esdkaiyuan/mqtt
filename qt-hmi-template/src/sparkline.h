#pragma once

#include <QPointF>
#include <QString>
#include <QVector>
#include <QWidget>

class SparklineWidget : public QWidget {
    Q_OBJECT
public:
    explicit SparklineWidget(QWidget *parent = nullptr);

    void setEmptyText(const QString &text);
    void setSeries(const QString &title, const QString &unit, const QVector<QPointF> &points);

protected:
    void paintEvent(QPaintEvent *event) override;

private:
    QString m_title;
    QString m_unit;
    QString m_emptyText = QStringLiteral("点击属性行加载趋势");
    QVector<QPointF> m_points;
};
