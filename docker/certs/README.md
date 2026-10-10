# 证书目录（T-25 认证方式扩展）

本目录是 TLS 服务端证书与设备证书签发 CA 的投放点，被 `docker/docker-compose.yml` 以只读方式
挂载进两个容器：

| 容器 | 容器内路径 | 用途 |
| --- | --- | --- |
| `emqx` | `/opt/emqx/etc/mqtt-certs` | 8883 TLS 监听器的服务端证书、私钥与受信 CA 链 |
| `backend` | `/etc/mqtt/certs` | 设备证书签发所依赖的 CA 证书与 CA 私钥 |

目录可以为空：此时 EMQX 仍使用镜像自带的演示证书，后端保持 `AUTH_X509_ENABLED=false`，
整套服务照常启动，SECRET 与 JWT 两种认证方式不受影响。

## 需要的文件

| 文件 | 说明 |
| --- | --- |
| `ca.crt` | 设备证书签发 CA 的证书（PEM）。后端签发时读取，EMQX 用它校验设备客户端证书 |
| `ca.key` | 上述 CA 的私钥（PEM，PKCS#8 或传统 RSA/EC 封装均可，不得加密） |
| `server.crt` | EMQX 8883 监听器的服务端证书（PEM）。若由中间 CA 签发，须把中间证书按顺序追加在其后 |
| `server.key` | 服务端证书私钥（PEM，不得加密） |

`server.crt` 的 CN / SAN 必须包含设备实际连接使用的主机名或 IP，否则客户端校验会失败。

## 权限

后端容器以非 root 用户 `app` 运行，EMQX 同样以非 root 身份读取证书，故四个文件都必须对
容器内进程可读：

```sh
chmod 644 ca.crt ca.key server.crt server.key
```

CA 私钥落在宿主机磁盘上，务必限制本机其他用户访问（例如把本目录设为 `chmod 750` 并只允许部署账号进入）。

## 启用步骤

1. 生成 CA 与服务端证书，放入本目录（命名与上表一致）。
2. 在仓库根目录 `.env` 中开启 X.509：

   ```dotenv
   AUTH_X509_ENABLED=true
   MQTT_TLS_CERTFILE=etc/mqtt-certs/server.crt
   MQTT_TLS_KEYFILE=etc/mqtt-certs/server.key
   MQTT_TLS_CACERTFILE=etc/mqtt-certs/ca.crt
   ```

3. 重建相关容器：`docker compose -f docker/docker-compose.yml up -d --force-recreate emqx backend`。
4. 在产品上把认证方式改为 X.509，再在设备详情页签发证书；设备改连 8883 并携带客户端证书。

完整说明见 `docs/DEPLOYMENT.md` 的 MQTT TLS 与 X.509 设备认证一节。

## 注意事项

- 轮换 CA 会让所有已签发设备证书的链路校验失败，须重新签发；与服务端证书轮换是两件事。
- 服务端证书续期只需替换 `server.crt` / `server.key` 后重建 emqx 容器，设备侧无需改动。
- 不要把 CA 私钥提交到版本库，`.gitignore` 已忽略本目录下的 `*.key` 与 `*.crt`（仅保留本说明文件）。
