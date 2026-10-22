#include "sparkline.h"

#include <QDateTime>
#include <QLinearGradient>
#include <QPainter>
#include <QPainterPath>

#include <algorithm>

SparklineWidget::SparklineWidget(QWidget *parent)
    : QWidget(parent)
{
    setMinimumHeight(160);
}

void SparklineWidget::setEmptyText(const QString &text)
{
    m_emptyText = text;
    update();
}

void SparklineWidget::setSeries(const QString &title, const QString &unit,
                                const QVector<QPointF> &points)
{
    m_title = title;
    m_unit = unit;
    m_points = points;
    update();
}

void SparklineWidget::paintEvent(QPaintEvent *event)
{
    Q_UNUSED(event)

    QPainter painter(this);
    painter.setRenderHint(QPainter::Antialiasing);
    painter.fillRect(rect(), palette().base());

    const QRectF panel = rect().adjusted(0.5, 0.5, -0.5, -0.5);
    painter.setPen(QPen(QColor(QStringLiteral("#E5E6EB")), 1));
    painter.drawRoundedRect(panel, 8, 8);

    const QRectF plot = rect().adjusted(56, 40, -20, -30);
    painter.setPen(QColor(QStringLiteral("#1D2129")));
    painter.drawText(QRectF(18, 12, width() - 36, 20),
                     Qt::AlignLeft | Qt::AlignVCenter,
                     m_title.isEmpty() ? QStringLiteral("趋势") : m_title);

    if (m_points.isEmpty()) {
        painter.setPen(QColor(QStringLiteral("#86909C")));
        painter.drawText(plot, Qt::AlignCenter, m_emptyText);
        return;
    }

    double minValue = m_points.first().y();
    double maxValue = m_points.first().y();
    for (const QPointF &point : m_points) {
        minValue = std::min(minValue, point.y());
        maxValue = std::max(maxValue, point.y());
    }
    if (qFuzzyCompare(minValue, maxValue)) {
        minValue -= 1;
        maxValue += 1;
    }
    const double padding = (maxValue - minValue) * 0.12;
    minValue -= padding;
    maxValue += padding;

    const qint64 minTime = qint64(m_points.first().x());
    const qint64 maxTime = qint64(m_points.last().x());
    const double timeSpan = std::max<qint64>(1, maxTime - minTime);
    const double valueSpan = maxValue - minValue;

    painter.setPen(QPen(QColor(QStringLiteral("#F2F3F5")), 1));
    for (int i = 0; i <= 3; ++i) {
        const double ratio = i / 3.0;
        const double y = plot.top() + plot.height() * ratio;
        painter.drawLine(QPointF(plot.left(), y), QPointF(plot.right(), y));
        painter.setPen(QColor(QStringLiteral("#86909C")));
        const double labelValue = maxValue - valueSpan * ratio;
        painter.drawText(QRectF(6, y - 8, 46, 16), Qt::AlignRight | Qt::AlignVCenter,
                         QString::number(labelValue, 'f', 1));
        painter.setPen(QPen(QColor(QStringLiteral("#F2F3F5")), 1));
    }

    QPainterPath line;
    QVector<QPointF> mapped;
    mapped.reserve(m_points.size());
    for (const QPointF &point : m_points) {
        const double x = plot.left() + (point.x() - minTime) / timeSpan * plot.width();
        const double y = plot.bottom() - (point.y() - minValue) / valueSpan * plot.height();
        mapped.append(QPointF(x, y));
    }
    line.moveTo(mapped.first());
    for (int i = 1; i < mapped.size(); ++i) {
        line.lineTo(mapped.at(i));
    }

    QPainterPath area = line;
    area.lineTo(mapped.last().x(), plot.bottom());
    area.lineTo(mapped.first().x(), plot.bottom());
    area.closeSubpath();
    QLinearGradient gradient(0, plot.top(), 0, plot.bottom());
    gradient.setColorAt(0, QColor(22, 93, 255, 68));
    gradient.setColorAt(1, QColor(22, 93, 255, 0));
    painter.fillPath(area, gradient);

    painter.setPen(QPen(QColor(QStringLiteral("#165DFF")), 2));
    painter.drawPath(line);
    painter.setBrush(QColor(QStringLiteral("#165DFF")));
    painter.drawEllipse(mapped.last(), 3.5, 3.5);
    painter.setBrush(Qt::NoBrush);

    const QString lastValue = QStringLiteral("%1 %2")
                                  .arg(m_points.last().y(), 0, 'f', 2)
                                  .arg(m_unit);
    painter.setPen(QColor(QStringLiteral("#165DFF")));
    painter.drawText(QRectF(18, 12, width() - 36, 20),
                     Qt::AlignRight | Qt::AlignVCenter, lastValue);

    const QString leftTime = QDateTime::fromMSecsSinceEpoch(minTime).toString(QStringLiteral("HH:mm"));
    const QString rightTime = QDateTime::fromMSecsSinceEpoch(maxTime).toString(QStringLiteral("HH:mm"));
    painter.setPen(QColor(QStringLiteral("#86909C")));
    painter.drawText(QRectF(plot.left(), plot.bottom() + 8, 80, 16), Qt::AlignLeft, leftTime);
    painter.drawText(QRectF(plot.right() - 80, plot.bottom() + 8, 80, 16), Qt::AlignRight, rightTime);
}
