# T-58 · 安全基座：TOTP 多因素认证（MFA）

> 版本：v1.0　创建日期：2026-10-26　状态：**已交付**
> 定位：补齐企业客户安全基座 —— 账号登录支持 TOTP 动态口令二步验证。SSO / 多租户不在本任务范围。
> 关联：[T-53 平台治理与规模化补强](T-53_平台治理与规模化补强_实施计划.md)、[T-57 开放 API 写操作](T-57_开放API写操作与SDK示例_实施计划.md)。

---

## 1. 背景与目标

平台此前只有「用户名 + 密码」单因子认证，缺少主流平台标配的多因素认证。密码泄露即账号失守，尤其是 ADMIN / OPERATOR 账号。

本任务目标：

1. 用户可自助绑定 / 停用 TOTP 动态口令；
2. 启用 MFA 的账号登录需「密码 + 动态口令」两步；
3. 密钥加密存储，不明文落库、不二次展示；
4. 提供全局开关，关闭时登录与端点行为可回退；
5. 不引入额外认证依赖（JDK 实现 TOTP）。

---

## 2. 设计决策

### 2.1 登录两步流程

```
POST /auth/login  ──► 密码通过且已启用 MFA ──► 返回 { mfaRequired=true, mfaToken }
POST /auth/mfa/verify { mfaToken, code } ──► 口令通过 ──► 返回完整登录响应（token + 用户）
```

- `mfaToken` 是带 `mfa=true` 声明的短期令牌（固定 5 分钟），防止把普通访问令牌当二步令牌；
- 最后登录时间在动态口令通过后才更新；
- 未启用 MFA 的账号流程与原来完全一致。

### 2.2 密钥存储

- `sys_user.mfa_secret` 存 AES-256-GCM 密文（Base64：IV || 密文 || Tag）；
- 加密密钥优先取 `MFA_ENCRYPTION_KEY`，否则由 `JWT_SECRET` 派生（`SHA-256(JWT_SECRET + ":mfa")`）；
- 任意长度配置统一收敛为 32 字节派生值，避免 InvalidKeyException；
- 绑定未确认时 `mfa_enabled=0`，确认后才置 1，避免「已启用却无法登录」；
- 停用需再次输入动态口令，成功后清空密钥。

### 2.3 TOTP 实现

- `TotpUtil` 用 JDK 实现 RFC 6238 / RFC 4226：HMAC-SHA1 + Base32 + 动态截断；
- 默认 30 秒步长、6 位口令、容忍 ±1 步（±30 秒时钟偏差）；
- 密钥默认 20 字节（Base32 32 字符），authenticator 可直接录入；
- 口令比较使用常量时间算法。

### 2.4 开关与回滚

- `app.mfa.enabled=false` 时 `MfaController` 不注册（端点 404），登录不要求二步验证；
- 已绑定用户的密钥保留，重新开启后自动恢复；
- 轮换 `JWT_SECRET` 且未配置 `MFA_ENCRYPTION_KEY` 会导致历史密文无法解密，需引导用户重新绑定（部署文档已说明）。

---

## 3. 数据模型

`V34__user_mfa.sql`：

```sql
ALTER TABLE sys_user
    ADD COLUMN mfa_enabled TINYINT NOT NULL DEFAULT 0 COMMENT '是否启用 TOTP MFA（T-58）' AFTER status,
    ADD COLUMN mfa_secret TEXT NULL COMMENT 'TOTP 密钥密文（AES-GCM，Base64）；NULL=未绑定' AFTER mfa_enabled,
    ADD COLUMN mfa_confirmed_at DATETIME NULL COMMENT 'MFA 启用时间' AFTER mfa_secret;
```

---

## 4. 后端变更

| 组件 | 变更 |
|---|---|
| `User` | 新增 `mfaEnabled` / `mfaSecret` / `mfaConfirmedAt` |
| `UserServiceImpl.login` | 启用 MFA 时只返回 `mfaRequired` 与 `mfaToken` |
| `JwtUtil` | 新增 `generateMfaToken` / `isMfaToken`（`mfa=true`，5 分钟） |
| `LoginResponseDTO` | 新增 `mfaRequired` / `mfaToken` 与 `mfaRequired(...)` 工厂 |
| `TotpUtil` | 新增 TOTP / Base32 / otpauth URI |
| `MfaCrypto` | 新增 AES-256-GCM 加解密 |
| `MfaProperties` | 新增 `app.mfa.*` 配置 |
| `MfaService` / `Impl` | 绑定 / 启用 / 停用 / 二步登录 |
| `MfaController` | `/auth/mfa/**` 五端点（verify 免登录） |
| `SecurityConfig` | `/auth/mfa/verify` 加入公开端点 |
| `AuditActionResolver` | `/auth/mfa/verify` 记录为 `LOGIN` |
| `ResultCode` | 新增 `6318`~`6322` |

---

## 5. 前端变更

- 登录页支持二步验证：密码通过后展示动态口令输入；
- 「开发接入 → 安全设置」新增 MFA 管理页：状态展示、绑定（密钥 / otpauth URI + 口令确认）、停用；
- 路由 `/workbench/settings/security`，左导航「安全设置」；
- 文档站「权限与开放接入」新增 MFA 说明。

---

## 6. API

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/auth/mfa/status` | 当前用户是否启用 |
| POST | `/auth/mfa/setup` | 发起绑定，返回 secret / otpauthUri |
| POST | `/auth/mfa/enable` | `{code}` 确认启用 |
| POST | `/auth/mfa/disable` | `{code}` 停用 |
| POST | `/auth/mfa/verify` | 二步登录 `{mfaToken, code}`（免登录） |

错误码：`6318` 已启用、`6319` 未绑定、`6320` 口令错误、`6321` 二步会话无效、`6322` MFA 已关闭。

---

## 7. 配置

| 变量 | 默认 | 说明 |
|---|---|---|
| `MFA_ENABLED` | `true` | false 时端点 404、登录不要求二步 |
| `MFA_TIME_STEP_SECONDS` | `30` | TOTP 步长 |
| `MFA_ALLOWED_WINDOW` | `1` | 容忍前后步数 |
| `MFA_DIGITS` | `6` | 口令位数 |
| `MFA_SECRET_LENGTH` | `20` | 密钥字节数 |
| `MFA_ISSUER` | `MQTT云平台` | authenticator 发行方 |
| `MFA_ENCRYPTION_KEY` | 空 | 留空由 `JWT_SECRET` 派生 |

---

## 8. 测试

- `TotpUtilTest`：Base32 往返、RFC 6238 向量、窗口容忍、非法输入、otpauth URI；
- `MfaCryptoTest`：加解密往返、篡改检测、显式密钥与换钥失效；
- `MfaServiceImplTest`：绑定 → 启用 → 停用生命周期、错误口令、二步登录成功 / 失败 / 无效令牌；
- 门禁命令：

```bash
sh scripts/mvnw.sh -B verify
cd frontend && npx --no-install eslint src && npx --no-install vitest run && npx --no-install vite build
```

门禁结果（2026-10-26）：

- T-58 定向测试 **12 项全绿**；
- 后端全量 `mvn -B verify`：**1642 项测试 0 失败 0 错误，BUILD SUCCESS**（含 ArchUnit）；
- 前端 `eslint` **0 error / 0 warning**；`vitest` **683 项 / 87 个文件全绿**；`vite build` 通过；
- 待现场实测：真实 authenticator 绑定与二步登录。

---

## 9. 未做 / 后续

| 项 | 说明 |
|---|---|
| SSO / OIDC / LDAP | 另立任务；MFA 已为账号安全提供基线 |
| 多租户 / 组织 | 另立任务 |
| 恢复码 / 备用因子 | 当前停用需动态口令；恢复码与管理员重置后续补 |
| 管理员强制 MFA | 当前为用户自助；强制策略后续补 |
| 现场实测 | 真实 authenticator 绑定与登录待现场验收 |

---

## 10. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| v1.0 | 2026-10-26 | 首次成文：TOTP MFA 全链路（绑定 / 启停 / 二步登录 / 加密存储 / 开关）交付。 |