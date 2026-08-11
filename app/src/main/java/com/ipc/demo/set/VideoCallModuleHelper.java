package com.ipc.demo.set;

import android.app.Application;
import android.util.Log;

import com.thingclips.smart.android.camera.sdk.ThingIPCSdk;
import com.thingclips.smart.call.module.api.IThingCallModule;
import com.thingclips.smart.call.module.api.bean.ThingCall;
import com.thingclips.smart.call.module.api.ui.ICallInterfaceProvider;

/**
 * Register screen-IPC video call module (带屏摄像机).
 * Docs: https://developer.tuya.com/cn/docs/app-development/videocall?id=Kdfiptfe3joyp
 */
public final class VideoCallModuleHelper {

    private static final String TAG = "VideoCallModule";
    private static final String CATEGORY_SCREEN_IPC = "screen_ipc";
    private static boolean registered;

    private VideoCallModuleHelper() {
    }

    /**
     * Register after Home SDK init. Safe to call multiple times.
     */
    public static void ensureRegistered(Application application) {
        if (registered) {
            return;
        }
        try {
            IThingCallModule module = ThingIPCSdk.getVideoCall();
            if (module == null) {
                Log.w(TAG, "getVideoCall() is null");
                return;
            }
            module.registerCallModuleProvider(CATEGORY_SCREEN_IPC, new ICallInterfaceProvider() {
                @Override
                public void launchUI(ThingCall call) {
                    if (call == null) {
                        // #region agent log
                        try {
                            AgentDebugLog.log("C", "VideoCallModuleHelper.launchUI", "call_null",
                                    new org.json.JSONObject());
                        } catch (Throwable ignored) {
                        }
                        // #endregion
                        return;
                    }
                    Log.i(TAG, "launchUI outgoing=" + call.getOutgoing()
                            + " target=" + call.getTargetId()
                            + " session=" + call.getSessionId());
                    // #region agent log
                    try {
                        AgentDebugLog.log("C", "VideoCallModuleHelper.launchUI", "videocall_launchUI",
                                new org.json.JSONObject()
                                        .put("outgoing", call.getOutgoing())
                                        .put("targetId", call.getTargetId() != null ? call.getTargetId() : "")
                                        .put("sessionId", call.getSessionId() != null ? call.getSessionId() : "")
                                        .put("bizType", call.getBizType() != null ? call.getBizType() : "")
                                        .put("opensUi", false));
                    } catch (Throwable ignored) {
                    }
                    // #endregion
                    // Demo：呼出成功后仅打日志；完整通话 UI 可按文档接 ICallInterface
                    // https://developer.tuya.com/cn/docs/app-development/videocall?id=Kdfiptfe3joyp
                }
            });
            module.registerMessageHandler();
            registered = true;
            Log.i(TAG, "screen_ipc video call module registered");
        } catch (Throwable t) {
            Log.e(TAG, "register video call module failed", t);
        }
    }
}
