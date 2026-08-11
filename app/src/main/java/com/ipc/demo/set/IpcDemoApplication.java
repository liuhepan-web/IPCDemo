package com.ipc.demo.set;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.thingclips.smart.home.sdk.ThingHomeSdk;

/**
 * Application entry.
 * Initializes Fresco, MiniApp SoLoader, Home SDK and doorbell listener.
 * MiniApp: https://developer.tuya.com/cn/docs/app-development/mini-app-sdk-integration?id=Kcwzmgsmy3zg4
 */
public class IpcDemoApplication extends Application {

    private static final String TAG = "IpcDemoApp";

    @Override
    public void onCreate() {
        super.onCreate();
        // ThingCameraView / SimpleDraweeView 依赖 Fresco，必须在创建预览页前初始化
        Fresco.initialize(this);
        // MiniApp SDK 要求初始化 SoLoader（反射调用，避免 IDE/classpath 硬依赖编译失败）
        initSoLoader(this);
        ThingHomeSdk.init(this);
        ThingHomeSdk.setDebugMode(true);
        DoorbellCallManager.getInstance().init(this);
        VideoCallModuleHelper.ensureRegistered(this);
    }

    /**
     * Reflectively call {@code SoLoader.init(Context, boolean)}.
     */
    private static void initSoLoader(Context context) {
        try {
            Class<?> clz = Class.forName("com.facebook.soloader.SoLoader");
            clz.getMethod("init", Context.class, boolean.class).invoke(null, context, false);
        } catch (Throwable t) {
            Log.e(TAG, "SoLoader init failed", t);
        }
    }
}
