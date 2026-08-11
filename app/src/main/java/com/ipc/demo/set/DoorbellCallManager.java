package com.ipc.demo.set;

import android.app.Application;
import android.content.Intent;
import android.text.TextUtils;
import android.util.Log;

import com.thingclips.smart.android.camera.sdk.ThingIPCSdk;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCDoorBellManager;
import com.thingclips.smart.android.camera.sdk.bean.ThingDoorBellCallModel;
import com.thingclips.smart.android.camera.sdk.callback.ThingSmartDoorBellObserver;
import com.thingclips.smart.sdk.bean.DeviceBean;

import org.json.JSONObject;

/**
 * Listen doorbell / call events and open answer UI.
 * Docs: https://developer.tuya.com/cn/docs/app-development/android-doorbell?id=Kalemt2dq2tjw
 */
public final class DoorbellCallManager {

    private static final String TAG = "DoorbellCallMgr";
    private static final DoorbellCallManager INSTANCE = new DoorbellCallManager();

    private IThingIPCDoorBellManager doorBellManager;
    private ThingSmartDoorBellObserver observer;
    private boolean initialized;
    private Application appRef;

    private DoorbellCallManager() {
    }

    public static DoorbellCallManager getInstance() {
        return INSTANCE;
    }

    public void init(Application application) {
        appRef = application;
        // #region agent log
        try {
            AgentDebugLog.log("A", "DoorbellCallManager.init", "init_enter",
                    new JSONObject()
                            .put("doorbellNull", ThingIPCSdk.getDoorbell() == null)
                            .put("alreadyInit", initialized));
        } catch (Throwable ignored) {
        }
        // #endregion
        if (ThingIPCSdk.getDoorbell() == null) {
            Log.e(TAG, "ThingIPCSdk.getDoorbell() is null, observer NOT registered");
            // #region agent log
            try {
                AgentDebugLog.log("A", "DoorbellCallManager.init", "doorbell_sdk_null",
                        new JSONObject().put("registered", false));
            } catch (Throwable ignored) {
            }
            // #endregion
            return;
        }
        doorBellManager = ThingIPCSdk.getDoorbell().getIPCDoorBellManagerInstance();
        if (doorBellManager == null) {
            Log.e(TAG, "getIPCDoorBellManagerInstance() is null");
            // #region agent log
            try {
                AgentDebugLog.log("A", "DoorbellCallManager.init", "manager_null",
                        new JSONObject().put("registered", false));
            } catch (Throwable ignored) {
            }
            // #endregion
            return;
        }
        if (observer != null) {
            try {
                doorBellManager.removeObserver(observer);
            } catch (Throwable ignored) {
            }
        }
        observer = new ThingSmartDoorBellObserver() {
            @Override
            public void doorBellCallDidReceivedFromDevice(ThingDoorBellCallModel callModel, DeviceBean deviceBean) {
                String msgId = callModel != null ? callModel.getMessageId() : null;
                String type = callModel != null ? callModel.getType() : null;
                String devId = callModel != null ? callModel.getDevId() : null;
                // #region agent log
                try {
                    AgentDebugLog.log("B", "DoorbellCallManager.onReceived", "call_received",
                            new JSONObject()
                                    .put("callModelNull", callModel == null)
                                    .put("messageIdEmpty", TextUtils.isEmpty(msgId))
                                    .put("messageId", msgId != null ? msgId : "")
                                    .put("type", type != null ? type : "")
                                    .put("devId", devId != null ? devId : "")
                                    .put("deviceBeanNull", deviceBean == null));
                } catch (Throwable ignored) {
                }
                // #endregion
                if (callModel == null || TextUtils.isEmpty(msgId)) {
                    Log.w(TAG, "drop call: null model or empty messageId");
                    // #region agent log
                    try {
                        AgentDebugLog.log("B", "DoorbellCallManager.onReceived", "call_dropped_empty_msgid",
                                new JSONObject().put("type", type != null ? type : ""));
                    } catch (Throwable ignored) {
                    }
                    // #endregion
                    return;
                }
                Intent intent = new Intent(application, CameraDoorBellActivity.class);
                intent.putExtra(IpcConstants.EXTRA_MSG_ID, msgId);
                intent.putExtra(IpcConstants.EXTRA_DEV_ID, devId);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
                try {
                    application.startActivity(intent);
                    // #region agent log
                    try {
                        AgentDebugLog.log("D", "DoorbellCallManager.onReceived", "startActivity_ok",
                                new JSONObject()
                                        .put("messageId", msgId)
                                        .put("devId", devId != null ? devId : "")
                                        .put("type", type != null ? type : ""));
                    } catch (Throwable ignored) {
                    }
                    // #endregion
                    Log.i(TAG, "started CameraDoorBellActivity type=" + type + " msgId=" + msgId);
                } catch (Throwable t) {
                    Log.e(TAG, "startActivity failed", t);
                    // #region agent log
                    try {
                        AgentDebugLog.log("D", "DoorbellCallManager.onReceived", "startActivity_fail",
                                new JSONObject()
                                        .put("error", t.getClass().getSimpleName())
                                        .put("errorMsg", String.valueOf(t.getMessage())));
                    } catch (Throwable ignored) {
                    }
                    // #endregion
                }
            }
        };
        doorBellManager.addObserver(observer);
        initialized = true;
        // #region agent log
        try {
            AgentDebugLog.log("A", "DoorbellCallManager.init", "observer_registered",
                    new JSONObject().put("registered", true));
        } catch (Throwable ignored) {
        }
        // #endregion
        Log.i(TAG, "doorbell observer registered");
    }

    /**
     * Re-bind after login if earlier init failed (doorbell SDK null).
     */
    public void ensureInit() {
        if (!initialized && appRef != null) {
            init(appRef);
        }
    }

    public void deInit() {
        if (doorBellManager != null && observer != null) {
            doorBellManager.removeObserver(observer);
        }
        initialized = false;
    }
}
