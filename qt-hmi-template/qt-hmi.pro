QT += core gui widgets network
CONFIG += c++17
TEMPLATE = app
TARGET = mqtt-hmi

INCLUDEPATH += src

SOURCES += \
    src/main.cpp \
    src/mainwindow.cpp \
    src/apiclient.cpp \
    src/hmimodel.cpp \
    src/sparkline.cpp

HEADERS += \
    src/mainwindow.h \
    src/apiclient.h \
    src/hmimodel.h \
    src/sparkline.h
