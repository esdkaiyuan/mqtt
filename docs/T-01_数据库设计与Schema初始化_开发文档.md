# T-01：数据库设计与Schema初始化 - 开发文档
> 版本：v2.0（精细化修订）  修订日期：2026-08-06

---

## 1. 子任务概述

**目标：** 设计并实现MQTT云平台系统的完整数据库Schema，编写可直接执行的MySQL初始化脚本，包含表结构、索引、外键、默认数据、数据保留策略说明。

**依赖：** 无（本子任务为P0，最先执行，其他所有子任务的上游）

**产出物：**
- `scripts/init-mysql.sql` — 可直接在MySQL 8.x上执行的完整初始化脚本
- 本开发文档（含表设计说明、字段 rationale、索引策略、数据保留策略）

**下游影响：** T-02（实体类映射）、T-03（用户表）、T-04（设备表）、T-05（消息/历史表）、T-06~T-08（前端页面数据展示）

---

## 2. 表设计详情

### 2.1 用户认证表 — sys_user

**设计 rationale：**
- 使用BIGINT主键而非INT，支持未来千万级用户规模
- role使用ENUM而非单独的角色表，当前系统角色固定为三级（ADMIN/OPERATOR/VIEWER），ENUM更简洁高效；若未来需要动态角色，可扩展为sys_role + sys_user_role关联表
- status使用ENUM，支持账号锁定机制（连续登录失败N次后LOCKED）
- metadata字段预留JSON类型，未来可扩展用户偏好设置

```sql
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，自增',
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录用户名，全局唯一',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt加密后的密码（60字符）',
    email VARCHAR(100) UNIQUE COMMENT '用户邮箱，可用于密码重置',
    phone VARCHAR(20) UNIQUE COMMENT '手机号，可用于双因素认证',
    role ENUM('ADMIN', 'OPERATOR', 'VIEWER') DEFAULT 'VIEWER' COMMENT '用户角色：ADMIN=全权管理，OPERATOR=设备操作，VIEWER=只读',
    status ENUM('ACTIVE', 'DISABLED', 'LOCKED') DEFAULT 'ACTIVE' COMMENT '账号状态：ACTIVE=正常，DISABLED=禁用，LOCKED=锁定',
    last_login DATETIME COMMENT '最后登录时间戳，用于活跃度统计',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '账号信息最后更新时间',
    INDEX idx_username (username),
    INDEX idx_role (role),
    INDEX idx_status (status),
    INDEX idx_last_login (last_login)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表，存储平台所有注册用户及权限信息';
```

**索引策略说明：**
- `idx_username`：登录查询使用，UNIQUE约束已包含索引，此处显式声明便于维护
- `idx_role`：按角色筛选用户列表时使用
- `idx_status`：查询活跃/锁定账号时使用
- `idx_last_login`：管理员查看活跃用户统计时使用

### 2.2 设备表 — device

**设计 rationale：**
- device_key使用VARCHAR(100)而非UUID，方便设备端硬编码和人工输入
- device_type使用VARCHAR(50)而非ENUM，支持设备类型动态扩展（新增设备类型无需修改表结构）
- topic存储MQTT Topic路径，支持通配符订阅
- metadata使用JSON类型，不同设备类型可存储不同结构的扩展数据（如传感器存单位、阈值；网关存子设备列表）
- 外键ON DELETE CASCADE确保删除用户时其设备级联删除，避免数据残留

```sql
CREATE TABLE IF NOT EXISTS device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，自增',
    device_name VARCHAR(100) NOT NULL COMMENT '设备显示名称，用于前端展示',
    device_key VARCHAR(100) NOT NULL UNIQUE COMMENT '设备唯一标识符，MQTT客户端ID通常与此一致',
    device_type VARCHAR(50) NOT NULL COMMENT '设备类型：sensor=传感器，gateway=网关，actuator=执行器',
    topic VARCHAR(255) NOT NULL COMMENT 'MQTT Topic路径，格式：device/{device_key}/data',
    description TEXT COMMENT '设备描述信息，可空',
    owner_id BIGINT NOT NULL COMMENT '所属用户ID，外键关联sys_user.id',
    status ENUM('ONLINE', 'OFFLINE', 'INACTIVE') DEFAULT 'INACTIVE' COMMENT '设备连接状态：ONLINE=在线，OFFLINE=离线，INACTIVE=未激活',
    last_seen DATETIME COMMENT '设备最后上报数据的时间，用于判断在线状态',
    metadata JSON COMMENT '设备扩展元数据，JSON格式，不同设备类型结构不同',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '设备注册时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '设备信息最后更新时间',
    INDEX idx_device_key (device_key),
    INDEX idx_owner (owner_id),
    INDEX idx_status (status),
    INDEX idx_last_seen (last_seen),
    INDEX idx_device_type (device_type),
    FOREIGN KEY (owner_id) REFERENCES sys_user(id) ON DELETE CASCADE COMMENT '设备所有者，用户删除时级联删除设备'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备表，存储所有MQTT设备的信息、状态及归属';
```

**索引策略说明：**
- `idx_device_key`：设备上报时按device_key查找设备
- `idx_owner`：查询某用户的所有设备
- `idx_status`：筛选在线/离线设备
- `idx_last_seen`：定时任务扫描超时设备（超过5分钟未更新）
- `idx_device_type`：按设备类型统计或筛选

### 2.3 设备状态历史表 — device_status_history

**设计 rationale：**
- 独立表存储状态变更历史，避免device表频繁更新（状态每5分钟检查一次）
- timestamp字段独立存储，与device.last_seen解耦
- 复合索引(device_id, timestamp)支持按设备查询时间范围内的状态变更

```sql
CREATE TABLE IF NOT EXISTS device_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，自增',
    device_id BIGINT NOT NULL COMMENT '设备ID，外键关联device.id',
    status ENUM('ONLINE', 'OFFLINE') NOT NULL COMMENT '设备状态：ONLINE=在线，OFFLINE=离线（INACTIVE不记录历史）',
    timestamp DATETIME NOT NULL COMMENT '状态变更发生的时间点',
    INDEX idx_device_id (device_id),
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_timestamp (timestamp),
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE COMMENT '设备状态历史，设备删除时级联删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备状态变更历史表，记录设备在线/离线的切换时间点';
```

**数据保留策略：**
- 默认保留180天（约6个月）
- 超过180天的记录由定时任务归档到`device_status_history_archive`表或删除

### 2.4 消息表 — message

**设计 rationale：**
- 单表存储所有消息（发布+订阅），direction字段区分方向
- device_id可为NULL（外部系统通过MQTT发送的消息无关联设备）
- sent_at由发送方设置，received_at由服务端设置，支持时区差异场景
- 高频写入表，需要注意写入性能；compound索引优化常见查询

```sql
CREATE TABLE IF NOT EXISTS message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，自增',
    topic VARCHAR(255) NOT NULL COMMENT 'MQTT Topic，如 device/sensor-001/data',
    direction ENUM('PUBLISH', 'SUBSCRIBE') NOT NULL COMMENT '消息方向：PUBLISH=平台发布到设备，SUBSCRIBE=设备上报到平台',
    payload TEXT COMMENT '消息载荷内容，纯文本或JSON字符串',
    qos INT DEFAULT 0 COMMENT 'MQTT QoS等级：0=最多一次，1=至少一次，2=恰好一次',
    device_id BIGINT COMMENT '关联设备ID，外键关联device.id；外部系统消息可为空',
    sent_at DATETIME NOT NULL COMMENT '消息发送时间（发送方时间）',
    received_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '消息接收时间（服务端入库时间）',
    INDEX idx_topic (topic),
    INDEX idx_device (device_id),
    INDEX idx_sent_at (sent_at),
    INDEX idx_direction (direction),
    INDEX idx_device_sent (device_id, sent_at),
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE SET NULL COMMENT '消息关联设备，设备删除时消息保留但device_id置NULL'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表，存储所有MQTT消息的收发记录';
```

**数据保留策略：**
- 默认保留90天（约3个月）
- 超过90天的记录由定时任务归档或删除
- 高频设备（每秒上报）需考虑分区表（按月份分区）

**索引策略说明：**
- `idx_topic`：按Topic搜索消息
- `idx_device`：按设备查询消息
- `idx_sent_at`：按时间范围查询
- `idx_direction`：筛选发布/订阅方向
- `idx_device_sent`：复合索引，优化"查询某设备在某时间范围内的消息"场景

### 2.5 历史记录表 — history_record

**设计 rationale：**
- 与message表分离：message记录原始MQTT消息（含QoS、方向等元数据），history_record存储经过处理的业务数据
- payload使用JSON类型，方便存储结构化数据（如传感器温度、湿度等字段）
- 复合索引(device_id, timestamp)是核心查询路径

```sql
CREATE TABLE IF NOT EXISTS history_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID，自增',
    device_id BIGINT NOT NULL COMMENT '设备ID，外键关联device.id',
    topic VARCHAR(255) NOT NULL COMMENT 'Topic路径',
    payload JSON COMMENT '数据载荷，JSON格式，包含业务数据字段',
    timestamp DATETIME NOT NULL COMMENT '数据记录时间（设备上报时间）',
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_timestamp (timestamp),
    INDEX idx_device_topic (device_id, topic),
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE COMMENT '历史记录关联设备，设备删除时级联删除'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史记录表，存储设备上报的业务数据，用于历史趋势查询';
```

**数据保留策略：**
- 默认保留365天（1年）
- 超过365天的记录由定时任务归档
- 对于高频设备（每分钟上报），建议按月份做分区表

### 2.6 数据保留策略总览

| 表名 | 保留期限 | 归档策略 | 备注 |
|------|----------|----------|------|
| sys_user | 永久 | 不归档 | 用户数据不删除 |
| device | 永久 | 删除标记（逻辑删除） | 使用deleted字段 |
| device_status_history | 180天 | 超过期限删除 | 状态变更历史无需长期保留 |
| message | 90天 | 超过期限删除或归档 | 高频表，注意性能 |
| history_record | 365天 | 超过期限归档 | 业务数据，长期保留价值高 |

### 2.7 连接池配置建议

在`application.yml`中推荐的HikariCP配置（T-02实现）：

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20              # 最大连接数，根据并发量调整
      minimum-idle: 5                     # 最小空闲连接数
      connection-timeout: 30000           # 连接超时30秒
      idle-timeout: 600000                # 空闲连接10分钟过期
      max-lifetime: 1800000               # 连接最大生命周期30分钟
      connection-test-query: SELECT 1     # MySQL连接验证
```

---

## 3. 初始化脚本完整内容

**文件路径：** `scripts/init-mysql.sql`

```sql
-- ============================================================
-- MQTT云平台系统 - MySQL数据库初始化脚本
-- 适用版本：MySQL 8.0+
-- 执行方式：mysql -u root -p < scripts/init-mysql.sql
-- ============================================================

-- 1. 创建数据库
CREATE DATABASE IF NOT EXISTS mqtt_cloud
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

-- 切换到业务数据库
USE mqtt_cloud;

-- 2. 创建用户认证表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE COMMENT '登录用户名',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt加密密码',
    email VARCHAR(100) UNIQUE COMMENT '邮箱',
    phone VARCHAR(20) UNIQUE COMMENT '手机号',
    role ENUM('ADMIN', 'OPERATOR', 'VIEWER') DEFAULT 'VIEWER' COMMENT '角色',
    status ENUM('ACTIVE', 'DISABLED', 'LOCKED') DEFAULT 'ACTIVE' COMMENT '账号状态',
    last_login DATETIME COMMENT '最后登录时间',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_username (username),
    INDEX idx_role (role),
    INDEX idx_status (status),
    INDEX idx_last_login (last_login)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- 3. 创建设备表
CREATE TABLE IF NOT EXISTS device (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_name VARCHAR(100) NOT NULL COMMENT '设备名称',
    device_key VARCHAR(100) NOT NULL UNIQUE COMMENT '设备唯一标识',
    device_type VARCHAR(50) NOT NULL COMMENT '设备类型',
    topic VARCHAR(255) NOT NULL COMMENT 'MQTT Topic',
    description TEXT COMMENT '设备描述',
    owner_id BIGINT NOT NULL COMMENT '所属用户ID',
    status ENUM('ONLINE', 'OFFLINE', 'INACTIVE') DEFAULT 'INACTIVE' COMMENT '设备状态',
    last_seen DATETIME COMMENT '最后上线时间',
    metadata JSON COMMENT '设备扩展元数据',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_device_key (device_key),
    INDEX idx_owner (owner_id),
    INDEX idx_status (status),
    INDEX idx_last_seen (last_seen),
    INDEX idx_device_type (device_type),
    FOREIGN KEY (owner_id) REFERENCES sys_user(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备表';

-- 4. 创建设备状态历史表
CREATE TABLE IF NOT EXISTS device_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '设备ID',
    status ENUM('ONLINE', 'OFFLINE') NOT NULL COMMENT '状态',
    timestamp DATETIME NOT NULL COMMENT '状态变更时间',
    INDEX idx_device_id (device_id),
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_timestamp (timestamp),
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备状态历史表';

-- 5. 创建消息表
CREATE TABLE IF NOT EXISTS message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    topic VARCHAR(255) NOT NULL COMMENT 'MQTT Topic',
    direction ENUM('PUBLISH', 'SUBSCRIBE') NOT NULL COMMENT '消息方向',
    payload TEXT COMMENT '消息载荷',
    qos INT DEFAULT 0 COMMENT 'QoS等级',
    device_id BIGINT COMMENT '关联设备ID',
    sent_at DATETIME NOT NULL COMMENT '消息发送时间',
    received_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '消息接收时间',
    INDEX idx_topic (topic),
    INDEX idx_device (device_id),
    INDEX idx_sent_at (sent_at),
    INDEX idx_direction (direction),
    INDEX idx_device_sent (device_id, sent_at),
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表';

-- 6. 创建历史记录表
CREATE TABLE IF NOT EXISTS history_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL COMMENT '设备ID',
    topic VARCHAR(255) NOT NULL COMMENT 'Topic',
    payload JSON COMMENT '数据载荷',
    timestamp DATETIME NOT NULL COMMENT '记录时间',
    INDEX idx_device_timestamp (device_id, timestamp),
    INDEX idx_timestamp (timestamp),
    INDEX idx_device_topic (device_id, topic),
    FOREIGN KEY (device_id) REFERENCES device(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='历史记录表';

-- 7. 插入默认管理员账号
-- 用户名：admin，密码：admin123（BCrypt加密后的值）
INSERT INTO sys_user (username, password, email, role, status)
VALUES (
    'admin',
    '$2a$10$GbLOksTglzegx2KgJiLL4eXZg8nAMlAySBgU/OyyW9vgsTM/TTXd6',
    'admin@mqtt-cloud.local',
    'ADMIN',
    'ACTIVE'
)
ON DUPLICATE KEY UPDATE username = username;

-- 8. 插入测试数据（可选，开发环境使用）
-- 测试用户
INSERT IGNORE INTO sys_user (username, password, email, role, status)
VALUES
    ('operator1', '$2a$10$GbLOksTglzegx2KgJiLL4eXZg8nAMlAySBgU/OyyW9vgsTM/TTXd6', 'op1@test.local', 'OPERATOR', 'ACTIVE'),
    ('viewer1', '$2a$10$GbLOksTglzegx2KgJiLL4eXZg8nAMlAySBgU/OyyW9vgsTM/TTXd6', 'viewer1@test.local', 'VIEWER', 'ACTIVE');

-- 测试设备（关联admin用户，id=1）
INSERT IGNORE INTO device (device_name, device_key, device_type, topic, description, owner_id, status, metadata)
VALUES
    ('温度传感器-01', 'sensor-temp-001', 'sensor', 'device/sensor-temp-001/data', '客厅温度传感器', 1, 'INACTIVE', '{"unit":"°C","min":-20,"max":80}'),
    ('湿度传感器-01', 'sensor-hum-001', 'sensor', 'device/sensor-hum-001/data', '客厅湿度传感器', 1, 'INACTIVE', '{"unit":"%","min":0,"max":100}'),
    ('智能开关-01', 'switch-001', 'actuator', 'device/switch-001/control', '客厅主灯开关', 1, 'INACTIVE', '{"type":"relay","channels":1}');

-- 完成提示
SELECT 'MQTT云平台数据库初始化完成！' AS message;
SELECT COUNT(*) AS user_count FROM sys_user;
SELECT COUNT(*) AS device_count FROM device;
```

**密码说明：** `admin123` 经BCrypt加密后的固定值为 `$2a$10$GbLOksTglzegx2KgJiLL4eXZg8nAMlAySBgU/OyyW9vgsTM/TTXd6`，可直接在初始化脚本中使用。

---

## 4. 开发内容检测环节

### DET-01-01：表结构完整性检测

**检测人：** 开发者自检 + 数据库管理员核查

**执行环境：** MySQL 8.0+ 实例

| 检查项 | 检测内容 | SQL验证语句 | 标准 | 结果 |
|--------|----------|-------------|------|------|
| 表数量 | 是否创建了全部5个表 | `SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mqtt_cloud';` | 返回5 | ___ |
| sys_user字段 | 字段数量、类型、注释完整 | `SHOW FULL COLUMNS FROM sys_user;` | 11个字段、类型正确、全部有COMMENT | ___ |
| device字段 | 字段数量、类型、注释完整 | `SHOW FULL COLUMNS FROM device;` | 11个字段、type为ENUM、metadata为JSON | ___ |
| device_status_history字段 | 字段数量、类型、注释完整 | `SHOW FULL COLUMNS FROM device_status_history;` | 4个字段 | ___ |
| message字段 | 字段数量、类型、注释完整 | `SHOW FULL COLUMNS FROM message;` | 8个字段 | ___ |
| history_record字段 | 字段数量、类型、注释完整 | `SHOW FULL COLUMNS FROM history_record;` | 5个字段 | ___ |
| 字符集 | 所有表使用utf8mb4 | `SELECT TABLE_NAME, TABLE_COLLATION FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA='mqtt_cloud';` | 全部为utf8mb4_unicode_ci | ___ |

### DET-01-02：外键与索引检测

**检测人：** 开发者自检

| 检查项 | 检测内容 | SQL验证语句 | 标准 | 结果 |
|--------|----------|-------------|------|------|
| 外键存在 | device.owner_id关联sys_user | `SELECT TABLE_NAME, COLUMN_NAME, CONSTRAINT_NAME, REFERENCED_TABLE_NAME FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE WHERE TABLE_SCHEMA='mqtt_cloud' AND REFERENCED_TABLE_NAME IS NOT NULL;` | 返回2个外键 | ___ |
| 外键级联 | owner_id ON DELETE CASCADE | `SHOW CREATE TABLE device;` | 包含ON DELETE CASCADE | ___ |
| 外键级联 | device_status_history ON DELETE CASCADE | `SHOW CREATE TABLE device_status_history;` | 包含ON DELETE CASCADE | ___ |
| device索引 | 5个索引存在 | `SHOW INDEX FROM device;` | Key_name数量=5 | ___ |
| message索引 | 5个索引存在 | `SHOW INDEX FROM message;` | Key_name数量=5 | ___ |
| history_record索引 | 3个索引存在 | `SHOW INDEX FROM history_record;` | Key_name数量=3 | ___ |
| 外键数据完整性 | 插入非法owner_id报错 | `INSERT INTO device (device_name,device_key,device_type,topic,owner_id) VALUES ('test','test-key','test','test/topic',9999);` | 报错：Cannot add or update a child row | ___ |

### DET-01-03：初始化脚本执行检测

**检测人：** 开发者自检

**前置条件：** 全新MySQL 8.0实例，无mqtt_cloud数据库

| 检查项 | 检测内容 | 验证语句/操作 | 标准 | 结果 |
|--------|----------|---------------|------|------|
| 脚本可执行 | 整脚本无语法错误 | `mysql -u root -p < scripts/init-mysql.sql` | 执行无报错 | ___ |
| 数据库创建 | mqtt_cloud数据库存在 | `SHOW DATABASES LIKE 'mqtt_cloud';` | 存在 | ___ |
| 表创建 | 5个表全部存在 | `USE mqtt_cloud; SHOW TABLES;` | 返回5个表名 | ___ |
| 默认管理员 | admin用户存在 | `SELECT id, username, role, status FROM sys_user WHERE username='admin';` | id=1, role=ADMIN, status=ACTIVE | ___ |
| 测试数据 | 3个测试设备存在 | `SELECT device_key, device_type FROM device WHERE owner_id=1;` | 返回3条记录 | ___ |
| 密码可验证 | admin密码为admin123 | 使用BCrypt验证器验证 | 验证通过 | ___ |
| 幂等性 | 重复执行脚本无报错 | 再次执行 `mysql -u root -p < scripts/init-mysql.sql` | 执行无报错、数据不重复 | ___ |

---

## 5. 验收标准

| 序号 | 验收项 | 通过标准 | 验证方法 | 结果 |
|------|--------|----------|----------|------|
| ACC-01 | 5个表全部创建成功 | 表结构完整、字段类型正确、注释完整 | `SHOW TABLES; SHOW FULL COLUMNS FROM <表名>;` | ___ |
| ACC-02 | 外键约束正确 | 2个外键存在、ON DELETE CASCADE行为正确 | 外键查询 + 级联删除测试 | ___ |
| ACC-03 | 索引合理覆盖查询 | 5个表的索引覆盖主要查询场景 | 执行计划验证（EXPLAIN） | ___ |
| ACC-04 | 初始化脚本可用 | 干净MySQL实例上执行无报错、数据插入正确 | 完整执行init-mysql.sql | ___ |
| ACC-05 | 默认管理员可登录 | admin/admin123可登录、密码BCrypt验证通过 | 使用后端登录接口验证 | ___ |
| ACC-06 | 数据保留策略文档化 | 每张表的保留期限和归档策略已在文档中记录 | 阅读本文档第2.6节 | ___ |

---

## 6. 验收后更新总督促文档

| 更新项 | 内容 |
|--------|------|
| 总督促进度表 | 更新T-01状态为"完成"、填入实际完成日期和工时 |
| 总督促验收表 | 填入ACC-01到ACC-06的通过/未通过结果 |
| 总督促更新日志 | 追加一行：日期、版本、T-01数据库设计完成 |

---

## 7. 文件清单

| 文件路径 | 说明 | 状态 |
|----------|------|------|
| `scripts/init-mysql.sql` | 完整MySQL初始化脚本 | **已完成** |
| `docs/T-01_数据库设计与Schema初始化_开发文档.md` | 本文档 | 已创建 |

---

## 8. 表关系图（文字版）

```
sys_user (1) ────< (N) device
                        │
                        │ (1)
                        │
                        │ (N)
                        │
                        ▼
              device_status_history

device (1) ────< (N) message (device_id可为NULL)
device (1) ────< (N) history_record
```

**关系说明：**
- 1个用户可拥有多个设备（1:N）
- 1个设备有多条状态历史记录（1:N）
- 1个设备有多条消息记录（1:N），外部系统消息可无关联设备
- 1个设备有多条历史记录（1:N）
- 删除用户时级联删除其所有设备及相关数据
- 删除设备时消息保留但device_id置NULL，状态历史和业务历史级联删除

---

## 9. 常见问题

**Q: 为什么message和history_record是两张表？**
A: message表记录原始MQTT消息（含QoS、方向、发送/接收时间等元数据），用于消息审计和监控；history_record表存储经过处理的业务数据（JSON payload），用于历史趋势查询和数据分析。两者职责分离，避免单表过大。

**Q: device.metadata的JSON字段如何查询？**
A: MySQL 8.0支持JSON函数，如 `JSON_EXTRACT(metadata, '$.unit')`、`JSON_CONTAINS(metadata, '{"unit":"°C"}')`。但在MyBatis中建议在Service层解析JSON字符串为Java对象，避免在SQL中过度使用JSON函数。

**Q: message表数据量很大时如何优化？**
A: 可考虑：(1) 按月做分区表；(2) 超过90天的数据归档到message_archive表；(3) 对高频设备单独建表（按device_id分表）。

**Q: 外键会影响写入性能吗？**
A: 外键检查有微小开销，但在当前数据规模下（百万级以内）影响可忽略。若未来数据量极大，可考虑在应用层维护数据一致性，去掉外键。
