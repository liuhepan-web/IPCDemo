package com.ipc.demo.set;

import android.app.Application;
import android.util.Log;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.soloader.SoLoader;
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
        // MiniApp SDK 要求初始化 SoLoader
        try {
            SoLoader.init(this, false);
        } catch (Throwable t) {
            Log.e(TAG, "SoLoader init failed", t);
        }
        ThingHomeSdk.init(this);
        ThingHomeSdk.setDebugMode(true);
        DoorbellCallManager.getInstance().init(this);
        VideoCallModuleHelper.ensureRegistered(this);
    }
}
