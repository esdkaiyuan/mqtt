cmake_minimum_required(VERSION 3.16)
project(mqtt-hmi VERSION 0.1 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_AUTOMOC ON)

find_package(QT NAMES Qt6 Qt5 REQUIRED COMPONENTS Widgets Network)
find_package(Qt${QT_VERSION_MAJOR} REQUIRED COMPONENTS Widgets Network)

add_executable(mqtt-hmi
    src/main.cpp
    src/mainwindow.cpp
    src/mainwindow.h
    src/apiclient.cpp
    src/apiclient.h
    src/hmimodel.cpp
    src/hmimodel.h
    src/sparkline.cpp
    src/sparkline.h
)

target_include_directories(mqtt-hmi PRIVATE src)
target_link_libraries(mqtt-hmi PRIVATE
    Qt${QT_VERSION_MAJOR}::Widgets
    Qt${QT_VERSION_MAJOR}::Network
)
