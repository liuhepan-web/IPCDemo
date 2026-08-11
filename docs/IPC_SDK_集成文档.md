# IPC Demo · SDK 集成文档

> 与当前工程（`com.ipc.demo.set`）同步。操作与 UI 示意见：[IPC_Demo_操作文档.md](./IPC_Demo_操作文档.md)  
> 官方入口：[IPC 业务概述](https://developer.tuya.com/cn/docs/app-development/overview?id=Ka6km92o4do96) · [App SDK 集成](https://developer.tuya.com/cn/docs/app-development/integrated?id=Ka69nt96cw0uj)

---

## 1. 工程概况

| 项 | 说明 |
|----|------|
| 模块 | 单模块 `:app` |
| 包名 / applicationId | `com.ipc.demo.set` |
| 语言 | Java |
| minSdk / targetSdk / compileSdk | 24 / 35 / 35 |
| ABI | `armeabi-v7a`、`arm64-v8a` |
| AGP | 8.7.3 |

### 1.1 源码结构

```text
app/src/main/java/com/ipc/demo/set/
├── IpcDemoApplication.java
├── LoginActivity.java / RegisterActivity.java
├── MainActivity.java
├── CreateHomeActivity.java / HomeListActivity.java / HomeModel.java
├── DeviceConfigEzActivity.java / DeviceConfigApActivity.java
├── DeviceConfigQrActivity.java          # 摄像头二维码配网（ZXing）
├── DeviceListActivity.java              # 长按移除设备
├── CameraPanelActivity.java             # 实时预览（核心）
├── CameraAlbumActivity.java / CameraAlbumPreviewActivity.java
├── IpcLocalMediaHelper.java
├── CameraPlaybackActivity.java
├── CameraSdManageActivity.java
├── CameraMessageActivity.java / CameraVideoMessageActivity.java
├── CameraCloudStorageActivity.java
├── CameraDoorBellActivity.java / DoorbellCallManager.java
├── VideoCallModuleHelper.java
├── IpcDeviceHelper.java / IpcConstants.java / DPConstants.java
└── ...
```

---

## 2. 平台侧准备

1. [涂鸦 IoT 平台](https://iot.tuya.com/) 创建 App，获取 AppKey / AppSecret。  
2. 包名绑定 `com.ipc.demo.set`，上传证书 SHA256。  
3. 安全算法包 AAR 放入 `app/libs/`。

---

## 3. Maven 仓库

`settings.gradle.kts` 的 `pluginManagement` 与 `dependencyResolutionManagement` 均需：

```kotlin
maven { url = uri("https://maven-other.tuya.com/repository/maven-releases/") }
maven { url = uri("https://maven-other.tuya.com/repository/maven-commercial-releases/") }
```

---

## 4. 依赖版本

见 `gradle/libs.versions.toml`、`app/build.gradle.kts`。

| 组件 | 版本 |
|------|------|
| thingsmart | **7.8.0** |
| thingsmart-ipcsdk | **7.8.1** |
| thingsmart-ipc-camera-timeline | **1.1.0** |
| BizBundles BOM | **7.8.18** |
| Fresco | **2.10.0** |
| SoLoader | **0.10.5** |
| ZXing core | **3.5.3** |

```kotlin
implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
implementation(libs.thingsmart)
implementation(libs.thingsmart.ipcsdk)
implementation(libs.thingsmart.ipc.timeline)
implementation(libs.facebook.fresco)
implementation(enforcedPlatform(libs.thingsmart.bizbundles.bom))
implementation(libs.thingsmart.bizbundle.miniapp)
implementation(libs.thingsmart.bizbundle.basekit)
implementation(libs.thingsmart.bizbundle.bizkit)
implementation(libs.facebook.soloader)
implementation(libs.zxing.core)
```

- 排除 `thingsmart-modularCampAnno`。  
- `packaging.jniLibs.pickFirsts` 处理 so 冲突。  
- `thingsmart-bizbundle-ipckit` **未引入**。

---

## 5. AppKey / AppSecret

`local.properties` → Manifest 占位符（勿提交密钥）：

```xml
<meta-data android:name="THING_SMART_APPKEY" android:value="${TUYA_SMART_APPKEY}" />
<meta-data android:name="THING_SMART_SECRET" android:value="${TUYA_SMART_SECRET}" />
```

---

## 6. Application 初始化

`IpcDemoApplication`：

```text
Fresco.initialize → SoLoader.init → ThingHomeSdk.init
  → DoorbellCallManager / VideoCallModuleHelper
```

未初始化 Fresco 会导致预览页崩溃。

---

## 7. 权限

网络、Wi-Fi、定位、`RECORD_AUDIO`、`CAMERA`、`WAKE_LOCK`、前台服务等。对讲前动态申请麦克风。

---

## 8. 核心能力与代码映射

### 8.1 账号 / 家庭 / 配网

| 能力 | Demo |
|------|------|
| 登录 / 注册 | `LoginActivity` / `RegisterActivity` |
| 家庭 | `MainActivity` / `CreateHomeActivity` / `HomeListActivity` / `HomeModel` |
| EZ / AP | `DeviceConfigEzActivity` / `DeviceConfigApActivity` |
| 二维码配网 | `DeviceConfigQrActivity` + ZXing；[官方文档](https://developer.tuya.com/cn/docs/app-development/camera-scan-code-network-configuration?id=Kaixkcv3adu8y) |
| 设备列表 / 移除 | `DeviceListActivity` → `removeDevice` |

### 8.2 实时预览

```text
createCameraP2P(devId) → ThingCameraView.bind → connect → startPreview
```

低功耗可 `wirelessWake`。进卡录像/云存储前 `releaseCameraThenStart()`（异步 disconnect + destroy，避免主线程 ANR）。

### 8.3 缩放与横屏

| 交互 | 实现 |
|------|------|
| 双指捏合 | `ScaleGestureDetector`，约 1.0～4.0，`setScaleX/Y` |
| 双击放大 | `ThingCameraView` **默认开启**（可用 `setCameraViewDoubleClickEnable` 开关）；Demo `setupZoom()` 中亦有 `onDoubleTap` |
| 竖屏 | `applyVideoSize()` 16:9 |
| 横屏 | `enterFullscreen` / `exitFullscreen`；`btnFsBack` + `fsActionBar` |

### 8.4 预览控制

| 功能 | Demo |
|------|------|
| 静音 / 清晰度 / 对讲 | `toggleMute` / `toggleClarity` / `toggleAudioTalk` |
| 视频通话 | `toggleVideoTalk` + `VideoCallModuleHelper` |
| 截图 / 本地录像 | `snapshot` / `startRecordLocalMp4` → `IpcLocalMediaHelper` |
| 本地相册 | `CameraAlbumActivity` |

云存储门控：

```java
boolean support = cloud != null && cloud.isSupportCloudStorage(devId);
```

### 8.5 其他

| 功能 | Activity |
|------|----------|
| SD 管理 | `CameraSdManageActivity` |
| 卡录像 + 时间轴 | `CameraPlaybackActivity` |
| 消息 | `CameraMessageActivity` |
| 云存储 | `CameraCloudStorageActivity` |
| 门铃 | `DoorbellCallManager` + `CameraDoorBellActivity` |

---

## 9. 集成到自有 App 的最小清单

1. Maven + AppKey/Secret + 安全算法包 + 包名/SHA256。  
2. `thingsmart` + `thingsmart-ipcsdk`（按需 timeline / MiniApp / ZXing）。  
3. Application：Fresco → SoLoader → `ThingHomeSdk.init`。  
4. 登录并选定家庭后对 `devId` 预览。  
5. `createCameraP2P` + `ThingCameraView`。  
6. 能力位门控 UI；云存储用 `isSupportCloudStorage`。  
7. 预览与回放切换时释放 P2P。  
8. 横屏处理方向与系统栏。

---

## 10. 常见问题

| 现象 | 可能原因 |
|------|----------|
| Fresco 未初始化崩溃 | 未 `Fresco.initialize` |
| 预览黑屏 | 非 IPC、离线、P2P 失败、低功耗未唤醒 |
| 对讲无声 | 未授麦克风 / 设备不支持 |
| 云存储按钮不出现 | `isSupportCloudStorage == false`（预期） |
| 卡回放 `-40201` / `-40205` | 未释放预览 P2P；或时间不在片段内 |
| MiniApp / VAS 失败 | 未引 bizkit/miniapp 或 SoLoader |
| so 冲突 | 检查 `pickFirsts` |

---

## 11. 官方链接

- [App SDK 集成](https://developer.tuya.com/cn/docs/app-development/integrated?id=Ka69nt96cw0uj)
- [IPC 概述](https://developer.tuya.com/cn/docs/app-development/overview?id=Ka6km92o4do96)
- [音视频功能（含双击缩放开关）](https://developer.tuya.com/cn/docs/app-development/avfunction?id=Ka6nuvucjujar)
- [摄像头扫码配网](https://developer.tuya.com/cn/docs/app-development/camera-scan-code-network-configuration?id=Kaixkcv3adu8y)
- [时间轴](https://developer.tuya.com/cn/docs/app-development/timeline?id=Ka6nxw2j09f0r)
- [MiniApp](https://developer.tuya.com/cn/docs/app-development/mini-app-sdk-integration?id=Kcwzmgsmy3zg4)

操作文档：[IPC_Demo_操作文档.md](./IPC_Demo_操作文档.md)
