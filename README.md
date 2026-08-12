# Komari Android（WebView 客户端）

基于 [komari-monitor/komari](https://github.com/komari-monitor/komari) 的安卓客户端：一个轻量的 WebView 包装 APP，把自托管的 Komari 服务器监控界面装进安卓应用。

## 功能

- 首次启动输入 Komari 服务器地址，之后直接以全屏 WebView 打开监控界面
- 支持 HTTP/HTTPS、局域网/内网明文地址
- 保留登录 Cookie，支持返回键页内回退、下拉刷新、进度条
- 站内链接在 App 内打开，外部链接交给系统浏览器
- 右上角菜单：刷新 / 更换服务器

## 使用

1. 在 [Releases](../../releases) 页面下载最新 APK 并安装（需允许"安装未知来源应用"）。
2. 打开 App，输入你的 Komari 服务器地址（如 `https://monitor.example.com` 或 `http://192.168.1.10:8443`）。
3. 登录后即可在手机上实时监控服务器。

> 当前版本为**调试签名**（可直接安装），如需上架应用商店请配置正式签名。

## 构建与发布

本项目**不在本地构建**，全部由 GitHub Actions 在云端完成：

- 推送 `v*` 标签（如 `v1.0.0`）→ 自动构建 APK 并创建 GitHub Release
- 也可以在 Actions 页面手动触发 `workflow_dispatch` 构建（仅产出构建产物，不创建 Release）

## 本地开发（可选）

需要 JDK 17 和 Gradle 8.9：

```bash
gradle assembleDebug
# 或先生成 wrapper 再构建
gradle wrapper --gradle-version 8.9
./gradlew assembleRelease
```

## 技术栈

- Kotlin
- Android WebView（`androidx.webkit` 兼容层以下原生 API）
- Material 3 主题
- GitHub Actions（JDK 17 + Gradle 8.9）云端构建

## 许可证

MIT，见 [LICENSE](LICENSE)。本 APP 为独立编写的包装客户端，不包含 Komari 服务端代码；Komari 本体版权归其作者所有，请遵守其开源协议。