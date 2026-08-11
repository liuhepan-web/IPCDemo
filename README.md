# IPC Demo

涂鸦 **Smart Life App SDK + IPC SDK** 演示工程（包名 `com.ipc.demo.set`）。

面向开发者的统一入口：克隆本仓库 → 按下方步骤跑通 Demo → 对照文档集成到自有 App。

## 文档入口

| 文档 | 说明 |
|------|------|
| [操作文档](docs/IPC_Demo_操作文档.md) | 登录、配网、预览手势、横屏、云存储显隐、长按移除设备等 |
| [集成文档](docs/IPC_SDK_集成文档.md) | 依赖版本、初始化、预览/云存储 API 与代码映射 |

## 环境要求

- Android Studio (AGP 8.7+)
- JDK 11
- 真机（ARM：`armeabi-v7a` / `arm64-v8a`）
- [涂鸦 IoT 平台](https://iot.tuya.com/) 账号与已创建 App

## 快速开始

1. **克隆仓库**
   ```bash
   git clone https://github.com/liuhepan-web/IPCDemo.git
   cd IPCDemo
   ```
2. **配置密钥（勿提交）**
   ```bash
   cp local.properties.example local.properties
   ```
   编辑 `local.properties`：填写 `sdk.dir`、`appKey`、`appSecret`；如需绑定证书 SHA256，再填写 `KEYSTORE_*`。
3. **安全算法包**  
   将平台下发的安全算法 AAR 放入 `app/libs/`。
4. **包名与证书**  
   默认 `applicationId` 为 `com.ipc.demo.set`，须与 IoT 平台 App 包名、证书 SHA256 一致。
5. **运行**  
   Android Studio 打开工程 → 真机 Debug 运行 → 按 [操作文档](docs/IPC_Demo_操作文档.md) 体验。

## 功能一览

- 账号登录 / 注册、家庭管理  
- EZ / AP 配网  
- IPC 实时预览、缩放、对讲、截图、录像、清晰度  
- 横屏全屏预览（返回 + 底部操作）  
- 云台、存储卡、卡录像时间轴、消息中心  
- 云存储 / 增值服务（仅设备支持时展示）  
- 设备列表长按移除  

## 版本

| 组件 | 版本 |
|------|------|
| thingsmart | 7.8.0 |
| thingsmart-ipcsdk | 7.8.1 |
| BizBundles BOM | 7.8.18 |

详见 `gradle/libs.versions.toml`。

## 安全说明

- 不要把 AppKey、AppSecret、签名密码写入仓库或截图。  
- 仅使用 `local.properties`（已在 `.gitignore`）。  
- 公开分享前请确认 Manifest / Gradle 中无硬编码密钥。

## License

按你们组织要求补充开源协议；示例代码仅供学习与集成参考。
