# 归档区

存放已从主目录移除、但不宜直接删除的历史产物。

## backend-artifacts/

原 `backend/backend/` 目录下的采集数据。该目录本身是早期 `main_fixed.py` 系列的
开发副本，代码已随重构删除，数据保留于此以备查。

| 文件 | 说明 |
| --- | --- |
| `motion_data.db` | SQLite 数据库快照（约 6.7 MB） |
| `recordings/record_log.json` | 录制清单 |
| `recordings/recording_*.csv` | 2026-07-21 录制的原始运动数据 |

> 注意：这些是二进制/数据文件，已被 `.gitignore` 的 `*.db`、`*.csv`、`data/`
> 规则排除，**不会进入版本库**。它们只存在于本地工作区，请勿依赖 git 恢复。
> 确认无用后可直接删除本目录。

## firmware-legacy/

早期与主项目（MQTT 自建站点，Spring Boot + MQTT Broker）配套、或已被取代的固件。
本子项目后端只提供 WebSocket 接入（无 MQTT 客户端依赖），这些固件**无法**直接向本后端上报数据。

| 文件 | 原位置 | 说明 |
| --- | --- | --- |
| `sketch_jul21a.ino` | 项目根目录 | Arduino IDE 入口，MQTT 上报；且 `#include` 的头文件已不存在，无法编译 |
| `ESP32_Fall_Detection/` | `arduino_firmware/` | MQTT 连通性测试固件（每秒上报递增数字 1~1000），与业务无关 |

当前可用固件见 `esp32_firmware/`（PlatformIO，推荐）与
`arduino_firmware/ESP32_Raw_Data/`（Arduino IDE），两者均通过 WebSocket 上报。