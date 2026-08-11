pluginManagement {
    repositories {
        maven { url = uri("https://maven-other.tuya.com/repository/maven-releases/") }
        maven { url = uri("https://maven-other.tuya.com/repository/maven-commercial-releases/") }
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
}

// 根目录仓库配置（对应文档 allprojects/repositories）
// https://developer.tuya.com/cn/docs/app-development/integrated?id=Ka69nt96cw0uj
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("https://maven-other.tuya.com/repository/maven-releases/") }
        maven { url = uri("https://maven-other.tuya.com/repository/maven-commercial-releases/") }
        maven { url = uri("https://jitpack.io") }
        google()
        mavenCentral()
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://developer.huawei.com/repo/") }
    }
}

rootProject.name = "IPC Demo"
include(":app")
