package com.ipc.demo.set;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.thingclips.loguploader.TLogSDK;
import com.thingclips.smart.android.common.utils.L;
import com.thingclips.smart.android.common.utils.log.ILogInterception;
import com.thingclips.smart.home.sdk.ThingHomeSdk;

/**
 * Application entry.
 * Initializes Fresco, MiniApp SoLoader, Home SDK, offline log and doorbell listener.
 * Offline log: https://developer.tuya.com/cn/docs/app-development/ipcsdklog?id=Kbvezkn5bkaam
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
        initOfflineLog(this);
        DoorbellCallManager.getInstance().init(this);
        VideoCallModuleHelper.ensureRegistered(this);
    }

    /**
     * Init TLogSDK and IPC log interception for offline encrypted log files.
     */
    private static void initOfflineLog(Application application) {
        try {
            // Single file up to 10MB, keep up to 5 files of the same type.
            TLogSDK.init(application, 10, 5);
            L.setLogInterception(2, new ILogInterception() {
                @Override
                public void log(int level, String tag, String msg) {
                    // Bridge IPC SDK logs into Android logcat for debug; files still go to offline store.
                    Log.println(Math.max(Log.VERBOSE, Math.min(Log.ASSERT, level)),
                            tag == null ? "IPC" : tag,
                            msg == null ? "" : msg);
                }
            });
            Log.i(TAG, "TLogSDK init ok");
        } catch (Throwable t) {
            Log.e(TAG, "TLogSDK init failed", t);
        }
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
