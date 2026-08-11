# IPC Demo · SDK 集成文档

本文说明如何在本工程（`IPCDemo`）中集成涂鸦 Smart Life App SDK、IPC SDK 及预览相关能力，并对应到 Demo 中的实现位置。  
官方文档入口：[IPC 业务概述](https://developer.tuya.com/cn/docs/app-development/overview?id=Ka6km92o4do96) · [App SDK 集成](https://developer.tuya.com/cn/docs/app-development/integrated?id=Ka69nt96cw0uj)

---

## 1. 工程概况

| 项 | 说明 |
|----|------|
| 模块 | 单模块 `:app` |
| 包名 / applicationId | `com.ipc.demo.set` |
| 语言 | Java |
| minSdk / targetSdk / compileSdk | 24 / 35 / 35 |
| ABI | `armeabi-v7a`、`arm64-v8a` |

主要业务代码目录：

```text
app/src/main/java/com/ipc/demo/set/
├── IpcDemoApplication.java      # SDK / Fresco / SoLoader 初始化
├── LoginActivity.java           # 登录
├── MainActivity.java            # 控制台（家庭 / 配网入口）
├── DeviceConfigEzActivity.java  # EZ 配网
├── DeviceConfigApActivity.java  # AP 配网
├── DeviceListActivity.java      # 设备列表
├── CameraPanelActivity.java     # 实时预览面板（核心）
├── CameraPlaybackActivity.java  # 卡录像回放 + 时间轴
├── CameraSdManageActivity.java  # 存储卡管理
├── CameraMessageActivity.java   # 消息中心
├── CameraCloudStorageActivity.java  # 云存储播放
└── ...
```

参考样例（非本 App 模块）：`tuya-ipc-ref/`。

---

## 2. 平台侧准备

1. 在 [涂鸦 IoT 平台](https://iot.tuya.com/) 创建 App，获取 **AppKey / AppSecret**。
2. 将包名绑定为 **`com.ipc.demo.set`**（与本工程 `applicationId` 一致）。
3. 按平台要求上传 **调试证书 SHA256**（Debug / Release 分别绑定）。
4. 将 **安全算法包** AAR 放入 `app/libs/`（本工程通过 `fileTree("libs")` 引入）。

---

## 3. Maven 仓库

`settings.gradle.kts` 中需包含涂鸦仓库（`pluginManagement` 与 `dependencyResolutionManagement` 均需配置）：

```kotlin
maven { url = uri("https://maven-other.tuya.com/repository/maven-releases/") }
maven { url = uri("https://maven-other.tuya.com/repository/maven-commercial-releases/") }
```

并配合 Google、Maven Central、JitPack 等常规仓库。

---

## 4. 依赖版本（本工程）

定义见 `gradle/libs.versions.toml`，引用见 `app/build.gradle.kts`。

| 组件 | 坐标 | 版本 |
|------|------|------|
| App / Home SDK | `com.thingclips.smart:thingsmart` | **7.8.0** |
| IPC SDK | `com.thingclips.smart:thingsmart-ipcsdk` | **7.8.1** |
| 时间轴组件 | `thingsmart-ipc-camera-timeline` | **1.1.0** |
| BizBundles BOM | `thingsmart-BizBundlesBom` | **7.8.18** |
| MiniApp | `thingsmart-bizbundle-miniapp` | BOM |
| BaseKit | `thingsmart-bizbundle-basekit` | BOM |
| BizKit | `thingsmart-bizbundle-bizkit` | BOM |
| Fresco | `com.facebook.fresco:fresco` | **2.10.0** |
| SoLoader | `com.facebook.soloader:soloader` | **0.10.5** |

其他：Fastjson、OkHttp urlconnection、AndroidX AppCompat / Material / RecyclerView。

**说明：**

- 全局排除 `thingsmart-modularCampAnno`，避免注解冲突。
- `packaging` 中对 `libc++_shared`、`libyuv`、`libv8*` 等做了 `pickFirst`，避免 so 冲突。
- 版本目录中有 `thingsmart-bizbundle-ipckit`，**当前未引入**（资源冲突风险）；增值服务通过 MiniApp + bizkit 打开。

核心依赖片段：

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
```

---

## 5. AppKey / AppSecret 配置

### 5.1 推荐方式（占位符）

`app/build.gradle.kts` 从 `local.properties` 读取并写入 Manifest 占位符；`AndroidManifest.xml` 使用：

```xml
<meta-data
    android:name="THING_SMART_APPKEY"
    android:value="${TUYA_SMART_APPKEY}" />
<meta-data
    android:name="THING_SMART_SECRET"
    android:value="${TUYA_SMART_SECRET}" />
```

复制模板并填写（**不要提交** `local.properties`）：

```bash
cp local.properties.example local.properties
```

```properties
sdk.dir=/path/to/Android/sdk
appKey=你的_AppKey
appSecret=你的_AppSecret
```

可选签名字段：`KEYSTORE_FILE` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`。

> 切勿在 Manifest 或 Gradle 中硬编码密钥、证书密码。

---

## 6. Application 初始化（必须）

文件：`IpcDemoApplication.java`

初始化顺序：

1. **`Fresco.initialize(this)`** — `ThingCameraView` / `SimpleDraweeView` 依赖，未初始化会崩溃。
2. **`SoLoader.init(this, false)`** — MiniApp SDK 要求。
3. **`ThingHomeSdk.init(this)`** — Home / App SDK。
4. （可选）`ThingHomeSdk.setDebugMode(true)` — 调试日志。
5. （Demo）`DoorbellCallManager` — 门铃呼叫监听。

```java
Fresco.initialize(this);
SoLoader.init(this, false);
ThingHomeSdk.init(this);
```

Manifest：

```xml
<application android:name="com.ipc.demo.set.IpcDemoApplication" ...>
```

---

## 7. 权限

`AndroidManifest.xml` 已声明（按需裁剪）：

- 网络：`INTERNET`、`ACCESS_NETWORK_STATE`、`ACCESS_WIFI_STATE`、`CHANGE_WIFI_STATE`
- 定位（配网常用）：`ACCESS_FINE_LOCATION`、`ACCESS_COARSE_LOCATION`
- 音视频：`RECORD_AUDIO`、`MODIFY_AUDIO_SETTINGS`、`CAMERA`
- 其他：`WAKE_LOCK`、前台服务相关权限

运行时需动态申请：麦克风（对讲）、定位（部分配网场景）等。Demo 中对讲前会检查 `RECORD_AUDIO`。

---

## 8. 核心能力与代码映射

### 8.1 账号与家庭

| 能力 | 说明 | Demo 位置 |
|------|------|-----------|
| 登录 / 注册 | `ThingHomeSdk` 账号体系 | `LoginActivity` / `RegisterActivity` |
| 家庭创建 / 列表 | 设备归属家庭 | `CreateHomeActivity` / `HomeListActivity` / `HomeModel` |
| 当前家庭 | 本地缓存 homeId | `HomeModel` |

### 8.2 配网

| 方式 | Demo | 要点 |
|------|------|------|
| EZ（智能配网） | `DeviceConfigEzActivity` | 获取 token → Activator 多设备监听 |
| AP（热点配网） | `DeviceConfigApActivity` | 获取 token → 引导连接 `SmartLife-XXXX` → 回 App 激活 |

### 8.3 实时预览（IPC 核心）

文件：`CameraPanelActivity.java`  
布局：`activity_camera_panel.xml`

集成步骤概要：

```text
ThingIPCSdk.getCameraInstance()
  → isIPCDevice(devId)
  → createCameraP2P(devId)
  → ThingCameraView 创建成功后 bind
  → connect()
  → startPreview(clarity)
```

低功耗设备可调用门铃唤醒：`ThingIPCSdk.getDoorbell().wirelessWake(devId)`。  
会话异常码（如 `-3` / `-105`）Demo 中做了有限次重连。

### 8.4 预览缩放与横屏

| 交互 | 实现 | 位置 |
|------|------|------|
| 双指捏合 | `ScaleGestureDetector`，倍率 1.0～4.0 | `CameraPanelActivity` 约 215 行 |
| 单击 | 1× ↔ 2× | `onSingleTapConfirmed` |
| 双击 | 1× ↔ 3× | `onDoubleTap`（约 240 行） |
| 触摸分发 | `videoContainer.setOnTouchListener` | 约 247 行 |
| 进入横屏 | `enterFullscreen()`：横屏、沉浸式、显示返回与操作条 | 约 344 行 |
| 退出横屏 | `exitFullscreen()` / 系统返回 / 黑色返回按钮 | 约 362 行 |
| 尺寸 | `applyVideoSize()`：竖屏 16:9，横屏铺满 | 约 315 行 |

横屏 UI（布局）：

- `btnFsBack`：黑色返回
- `fsActionBar`：静音 / 清晰度 / 对讲 / 截图 / 录像

### 8.5 预览控制 API（Demo 封装）

| 功能 | 典型 API | Demo 方法 |
|------|----------|-----------|
| 静音 | `setMute(MUTE/UNMUTE)` | `toggleMute()` |
| 清晰度 | `setVideoClarity(HD/STANDEND)` | `toggleClarity()` |
| 语音对讲 | `startAudioTalk` / `stopAudioTalk` | `toggleAudioTalk()` |
| 视频通话 | 能力位 `isSupportChangeTalkBackMode` | `toggleVideoTalk()` |
| 截图 | `snapshot(...)` | 截图按钮 |
| 本地录像 | `startRecordLocalMp4` / `stopRecordLocalMp4` | `toggleRecord()` |

能力判断示例：

- 对讲：`CameraConfig.isSupportSpeaker()` / `isSupportPickup()`
- 云台：`ThingIPCSdk.getPTZInstance(devId).querySupportByDPCode(DP_PTZ_CONTROL)`

### 8.6 云存储能力判断（重要）

**仅设备支持云存储时展示「云存储」「增值服务」入口**，不支持则不显示。

```java
IThingIPCCloud cloud = ThingIPCSdk.getCloud();
boolean supportCloud = cloud != null && cloud.isSupportCloudStorage(devId);
btnCloud.setVisibility(supportCloud ? View.VISIBLE : View.GONE);
btnVas.setVisibility(supportCloud ? View.VISIBLE : View.GONE);
```

位置：`CameraPanelActivity` 初始化能力处。  
云回放页：`CameraCloudStorageActivity`（查询开通状态、按日时间轴播放）。  
增值服务：反射 / MiniApp（`UrlRouter` / `MiniAppClient`）打开 VAS 页面。

### 8.7 存储卡 / 回放 / 消息

| 功能 | Activity | 依赖 |
|------|----------|------|
| SD 状态 / 格式化 / 卡录像开关 | `CameraSdManageActivity` | DP（见 `DPConstants`） |
| 卡录像回放 + 时间轴 | `CameraPlaybackActivity` | `thingsmart-ipc-camera-timeline` |
| 告警消息 / 加密图 | `CameraMessageActivity` | 消息 SDK |
| 视频消息播放 | `CameraVideoMessageActivity` | `IThingCloudVideo` |
| 门铃呼叫 | `CameraDoorBellActivity` + `DoorbellCallManager` | 门铃 SDK |

---

## 9. 集成到自有 App 的最小清单

1. 配置涂鸦 Maven + AppKey/Secret + 安全算法包 + 包名/SHA256。
2. 引入 `thingsmart` + `thingsmart-ipcsdk`（按需 timeline / MiniApp）。
3. Application 中初始化 Fresco →（可选 SoLoader）→ `ThingHomeSdk.init`。
4. 登录并选定家庭，再对 `devId` 打开预览页。
5. 使用 `createCameraP2P` + `ThingCameraView` 完成连接与 `startPreview`。
6. 按能力位控制 UI（对讲、云台、云存储等），云存储务必用 `isSupportCloudStorage` 门控。
7. 横屏请处理 `configChanges` 或自行切换方向，并在退出时恢复系统栏与竖屏布局。

---

## 10. 常见问题

| 现象 | 可能原因 |
|------|----------|
| `SimpleDraweeView was not initialized` | 未调用 `Fresco.initialize` |
| 预览黑屏 / 连不上 | 非 IPC、设备离线、P2P 失败、低功耗未唤醒 |
| 对讲无声 | 未授权 `RECORD_AUDIO`、设备不支持扬声器/拾音 |
| 云存储按钮不出现 | 设备 `isSupportCloudStorage == false`（预期行为） |
| MiniApp / VAS 打不开 | 未引 bizkit/miniapp，或 SoLoader 未初始化 |
| so 冲突 | 检查 `packaging.jniLibs.pickFirsts` |

---

## 11. 相关官方链接

- [App SDK 集成](https://developer.tuya.com/cn/docs/app-development/integrated?id=Ka69nt96cw0uj)
- [IPC 概述](https://developer.tuya.com/cn/docs/app-development/overview?id=Ka6km92o4do96)
- [时间轴组件](https://developer.tuya.com/cn/docs/app-development/timeline?id=Ka6nxw2j09f0r)
- [MiniApp SDK](https://developer.tuya.com/cn/docs/app-development/mini-app-sdk-integration?id=Kcwzmgsmy3zg4)

配套操作说明见：[IPC_Demo_操作文档.md](./IPC_Demo_操作文档.md)
