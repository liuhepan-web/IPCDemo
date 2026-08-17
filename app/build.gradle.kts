import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

android {
    // 签名从 local.properties 读取，勿把密码/证书路径写死进仓库
    // 示例：
    //   KEYSTORE_FILE=/absolute/path/to/debug.jks
    //   KEYSTORE_PASSWORD=***
    //   KEY_ALIAS=key0
    //   KEY_PASSWORD=***
    val localProps = Properties()
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localProps.load(localPropsFile.inputStream())
    }
    val keystorePath = localProps.getProperty("KEYSTORE_FILE", "")
    if (keystorePath.isNotBlank()) {
        signingConfigs {
            getByName("debug") {
                storeFile = file(keystorePath)
                storePassword = localProps.getProperty("KEYSTORE_PASSWORD", "")
                keyAlias = localProps.getProperty("KEY_ALIAS", "key0")
                keyPassword = localProps.getProperty("KEY_PASSWORD", "")
            }
        }
    }
    namespace = "com.ipc.demo.set"
    // 文档：minSdk 23，targetSdk 35；UI BizBundle 7.8 要求 Android 7.0+
    compileSdk = 35

    defaultConfig {
        applicationId = "com.ipc.demo.set"
        minSdk = 24
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        multiDexEnabled = true

        ndk {
            abiFilters += listOf("armeabi-v7a", "arm64-v8a")
        }

        // AppKey / AppSecret 从 local.properties 读取
        val properties = Properties()
        val localFile = rootProject.file("local.properties")
        if (localFile.exists()) {
            properties.load(localFile.inputStream())
        }
        manifestPlaceholders["TUYA_SMART_APPKEY"] = properties.getProperty("appKey", "")
        manifestPlaceholders["TUYA_SMART_SECRET"] = properties.getProperty("appSecret", "")
    }

    packaging {
        jniLibs {
            pickFirsts += listOf(
                "lib/*/libc++_shared.so",
                "lib/*/libyuv.so",
                "lib/*/libopenh264.so",
                "lib/*/liblog.so",
                // MiniApp SDK：https://developer.tuya.com/cn/docs/app-development/mini-app-sdk-integration?id=Kcwzmgsmy3zg4
                "lib/*/libv8wrapper.so",
                "lib/*/libv8android.so"
            )
        }
        resources {
            excludes += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE",
                "META-INF/LICENSE.txt",
                "META-INF/LICENSE.md",
                "META-INF/NOTICE",
                "META-INF/NOTICE.txt",
                "META-INF/NOTICE.md",
                "META-INF/ASL2.0",
                "META-INF/*.kotlin_module"
            )
            pickFirsts += setOf(
                "META-INF/INDEX.LIST",
                "META-INF/io.netty.versions.properties"
            )
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

configurations.all {
    exclude(group = "com.thingclips.smart", module = "thingsmart-modularCampAnno")
    // thingsmart-logsdk embeds loguploader api builders; thingsmart 7.8 also pulls
    // thingsmart-android-loguploader-api → Duplicate class. Keep logsdk's copy.
    exclude(group = "com.thingclips.smart", module = "thingsmart-android-loguploader-api")
}

dependencies {
    // 安全算法包等本地 AAR：放入 app/libs/
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar"))))
    implementation(libs.alibaba.fastjson)
    implementation(libs.okhttp.urlconnection)
    // App SDK 最新稳定安卓版（保持 7.8.0，不降到文档示例的 6.7.7）
    implementation(libs.thingsmart)
    // IPC SDK：https://developer.tuya.com/cn/docs/app-development/overview?id=Ka6km92o4do96
    implementation(libs.thingsmart.ipcsdk)
    // 离线日志：https://developer.tuya.com/cn/docs/app-development/ipcsdklog?id=Kbvezkn5bkaam
    // 文档写 5.0.2，但 maven-releases 上 5.0.2 404；用 5.0.0（含 TLogSDK/LogFileCallback）
    // thing-log-sdk 用文档的 6.7.0；并排除 loguploader-api 避免与 logsdk 重复类
    implementation("com.thingclips.smart:thingsmart-logsdk:5.0.0")
    implementation("com.thingclips.smart:thing-log-sdk:6.7.0")
    // 时间轴：https://developer.tuya.com/cn/docs/app-development/timeline?id=Ka6nxw2j09f0r
    implementation(libs.thingsmart.ipc.timeline)
    // IPC ThingCameraView 依赖 Fresco（SimpleDraweeView）
    implementation(libs.facebook.fresco)

    // MiniApp SDK：https://developer.tuya.com/cn/docs/app-development/mini-app-sdk-integration?id=Kcwzmgsmy3zg4
    implementation(enforcedPlatform(libs.thingsmart.bizbundles.bom))
    implementation(libs.thingsmart.bizbundle.miniapp)
    implementation(libs.thingsmart.bizbundle.basekit)
    // 业务能力（打开面板/多语言等）；增值服务页跳转依赖
    implementation(libs.thingsmart.bizbundle.bizkit)
    // Explicit coordinate — Version Catalog accessor may not sync into IDE classpath
    implementation("com.facebook.soloader:soloader:0.10.5")

    // 摄像头二维码配网：https://developer.tuya.com/cn/docs/app-development/camera-scan-code-network-configuration?id=Kaixkcv3adu8y
    implementation(libs.zxing.core)

    implementation(libs.androidx.appcompat)
    implementation(libs.google.material)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.recyclerview)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
