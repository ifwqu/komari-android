# 签名密钥说明

- `komari-release.jks`：正式签名密钥（RSA 2048 / validity 10000 天，alias `komari`）
- `keystore.properties`：口令配置（storePassword / keyAlias / keyPassword）

**用途**：每次 GitHub Actions 构建都用同一把密钥签名，APK 签名一致后可**直接覆盖安装**，无需卸载重装。

**安全说明**：该密钥用于个人测试与分发场景。若需公开发布或上架应用商店，请：
1. 将本目录从仓库中移除，并加入 `.gitignore`；
2. 把密钥与口令存到 GitHub Secrets（`KEYSTORE_B64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`）；
3. 构建脚本已支持环境变量方式（优先级高于本目录配置）。

重新生成密钥：在 Actions 页面运行 `Generate Signing Keystore` 工作流。