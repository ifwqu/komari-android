# Komari Android（原生客户端）

基于 [komari-monitor/komari](https://github.com/komari-monitor/komari) 的自托管服务器监控平台的**原生安卓客户端**（Kotlin + Jetpack Compose），不会再有 WebView 的"手机看桌面版网页"体验问题。

## 功能

- **多服务器管理**：添加/编辑/删除多个 Komari 服务器，登录后自动保存会话
- **实时节点列表**：接入 komari 的 WebSocket（`/api/clients`），秒级刷新 CPU / 内存 / 磁盘 / 网络 / 负载，在线状态一目了然
- **节点详情**：CPU 环形仪表、内存/磁盘/Swap 进度条、网络上下行速率、负载、运行时长、连接数
- **历史趋势图**：CPU / 内存 / 网络 / 负载 / 磁盘 5 类指标折线图，支持 1/3/6/24 小时时间范围
- **底部导航分组**：节点 / 通知 / 主题 / 插件 / 设置 五个分组一键切换，设置内含节点管理与站点设置
- **自托管友好**：支持内网 HTTP 明文地址、HTTP/HTTPS 自动补全、双因素认证（2FA）

## 安装

1. 在 [Releases](../../releases) 页面下载最新 APK（`.apk`）并安装（需允许"安装未知来源应用"）。
2. 打开 App → 右下角「+」→ 输入服务器地址（如 `https://monitor.example.com` 或 `http://192.168.1.10:8443`）、用户名、密码，如有 2FA 一并填写。
3. 保存登录后即可查看节点列表，点击节点进入详情。

> 当前版本为**调试签名** APK（可直接安装），上架应用商店前请配置正式签名（GitHub Secrets + keystore）。

## 构建与发布

全部由 **GitHub Actions 在云端完成**，本地无需安装任何工具链：

- 推送 `v*` 标签（如 `v2.0.0`）→ 自动构建 APK 并创建 GitHub Release
- 或手动触发 Actions 页面 `workflow_dispatch`（仅产出构建产物，不创建 Release）

## API 对接要点（供二次开发参考）

| 能力 | 接口 |
|---|---|
| 登录 | `POST /api/login` → 返回/写 Cookie `session_token` |
| 会话校验 | `GET /api/me` |
| 节点列表 | `GET /api/nodes` |
| 实时数据 | WebSocket `GET /api/clients`，发送 `get`（全部）或 `get <uuid>`，返回 `{online, data}` |
| 历史记录 | `GET /api/records/load?uuid=&load_type=&hours=` |

> WebSocket 注意：komari 校验 Origin 必须与服务器 Host 一致，客户端需显式携带 `Origin` 头；如你的服务器版本较老/已配置，也可通过服务端环境变量 `KOMARI_WS_DISABLE_ORIGIN=true` 关闭该校验。

## 本地开发（可选）

需要 JDK 17 与 Gradle 8.9：

```bash
gradle assembleDebug
```

## 技术栈

- Kotlin + Jetpack Compose（Material 3）
- OkHttp（REST + WebSocket）
- kotlinx.serialization
- GitHub Actions（JDK 17 + Gradle 8.9）云端构建

## 许可证

MIT，见 [LICENSE](LICENSE)。本客户端为独立编写的原生应用，不含 Komari 服务端代码；Komari 本体版权归其作者所有，请遵守其开源协议。