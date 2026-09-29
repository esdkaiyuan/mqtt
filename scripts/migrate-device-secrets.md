# 存量设备凭据迁移手册（T-11）

> 适用版本：T-11 设备接入与身份体系上线后
> 目标：把存量设备的固定账号（admin/public）切换为一机一密凭据，并在确认全部设备迁移完成后关闭双轨放行。

---

## 1. 前置条件

- 数据库已完成 V2 迁移：`flyway_schema_history` 中存在 `version=2` 且 `success=1`
- `.env` 中 `ACCESS_CONTROL_ENFORCE_AUTH=false`（双轨期，认证与授权回调一律放行）
- 已用 admin 账号登录并取得 JWT
- 已确认 EMQX 侧认证/授权回调已下发成功：`docker logs mqtt-emqx-init` 输出"EMQX 认证与授权配置完成"

---

## 2. 迁移步骤

### Step 1：导出全部设备凭据

> 导出会**重置每台设备的密钥**（旧密钥立即失效），且明文仅此一次返回，请立刻保存到安全位置。

```bash
curl -X POST http://<host>:80/api/devices/export-credentials \
  -H "Authorization: Bearer <JWT>" \
  -o credentials.json
```

返回体为 `Result<List<String[]>>`，每行格式：

```
[productKey, deviceKey, username, deviceSecret]
```

其中 `username` 即 `{productKey}.{deviceKey}`，`deviceSecret` 为设备 MQTT 密码。

### Step 2：逐台设备刷机

把对应行的 `username` / `deviceSecret` 写入设备固件（见《ESP32 接入手册》第 4 节），逐台烧录。

### Step 3：逐台验证

用新凭据验证连接与上报：

```bash
mosquitto_pub -h <host> -p 1883 \
  -u "<productKey>.<deviceKey>" -P "<deviceSecret>" \
  -t "device/<deviceKey>/data" -m '{"t":25}'
```

预期：发布成功，且后端出现该消息的处理记录。

### Step 4：关闭双轨

确认全部设备迁移完成后，修改 `.env`：

```dotenv
ACCESS_CONTROL_ENFORCE_AUTH=true
DIRECT_FRONTEND_ENABLED=false
```

### Step 5：重启使配置生效

```bash
docker compose --env-file .env -f docker/docker-compose.yml up -d --force-recreate backend emqx-init
```

> `emqx-init` 会重新下发认证/授权源；认证开关由后端读取，故必须同时重建 backend。

---

## 3. 回滚

把 `ACCESS_CONTROL_ENFORCE_AUTH` 置回 `false` 并重启 backend：

```bash
docker compose --env-file .env -f docker/docker-compose.yml up -d --force-recreate backend
```

此时认证与授权回调重新放行，老凭据立即恢复可用。

---

## 4. 注意事项

- `export-credentials` 与 `reset-secret` 都会重置密钥，**不要在设备在线运行时随意执行**，否则在线设备会被踢下线且无法重连。
- 设备删除/禁用：`device.enabled=0` 会直接拒绝该设备认证（业务码 6005），与运行态 `status` 语义分离。
- 新增设备必须归属某个产品：`POST /api/devices` 的 `productId` 为必填，`deviceKey` 唯一性收敛到产品维度。
- 存量设备已统一归入默认产品 `legacy`（V2 迁移自动创建），如需拆分到独立产品，可在控制台新建产品后调整设备归属。