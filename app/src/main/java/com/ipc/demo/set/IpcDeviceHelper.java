package com.ipc.demo.set;

import android.text.TextUtils;

import com.thingclips.smart.android.camera.sdk.ThingIPCSdk;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCCore;
import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.sdk.bean.DeviceBean;

/**
 * Shared helpers for IPC / doorbell / video-lock preview eligibility.
 * Video locks and doorbells may not pass {@code isIPCDevice()} but still support P2P live view.
 */
public final class IpcDeviceHelper {

    private IpcDeviceHelper() {
    }

    /**
     * Whether the device can attempt live preview (P2P).
     *
     * @param devId device id
     * @return true if preview should be allowed
     */
    public static boolean canOpenLivePreview(String devId) {
        if (TextUtils.isEmpty(devId)) {
            return false;
        }
        IThingIPCCore camera = ThingIPCSdk.getCameraInstance();
        if (camera == null) {
            return false;
        }
        if (camera.isIPCDevice(devId) || camera.isLowPowerDevice(devId)) {
            return true;
        }
        if (isDoorbellCategory(devId)) {
            return true;
        }
        // Config available usually means camera stack knows the device
        try {
            return camera.getCameraConfig(devId) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /**
     * Label type for device list.
     *
     * @param devId device id
     * @return 0 other, 1 ipc, 2 doorbell/low-power
     */
    public static int resolveDeviceType(String devId) {
        IThingIPCCore camera = ThingIPCSdk.getCameraInstance();
        if (camera == null) {
            return isDoorbellCategory(devId) ? 2 : 0;
        }
        boolean ipc = camera.isIPCDevice(devId);
        boolean lowPower = camera.isLowPowerDevice(devId);
        if (lowPower || isDoorbellCategory(devId)) {
            return 2;
        }
        if (ipc) {
            return 1;
        }
        return canOpenLivePreview(devId) ? 1 : 0;
    }

    /**
     * Doorbell / video-lock category heuristic.
     *
     * @param devId device id
     * @return true if category suggests doorbell
     */
    public static boolean isDoorbellCategory(String devId) {
        if (TextUtils.isEmpty(devId)) {
            return false;
        }
        try {
            DeviceBean bean = ThingHomeSdk.getDataInstance().getDeviceBean(devId);
            if (bean == null) {
                return false;
            }
            String[] candidates = new String[]{
                    invokeString(bean, "getCategory"),
                    invokeString(bean, "getCategoryCode"),
                    invokeString(bean, "getProductId"),
                    invokeString(bean, "getProductBean") == null
                            ? null
                            : String.valueOf(bean.getClass()),
                    bean.getName()
            };
            // Also check productBean category if present
            try {
                Object product = bean.getClass().getMethod("getProductBean").invoke(bean);
                if (product != null) {
                    candidates = new String[]{
                            invokeString(bean, "getCategory"),
                            invokeString(bean, "getCategoryCode"),
                            invokeString(bean, "getProductId"),
                            invokeString(product, "getCategory"),
                            invokeString(product, "getCategoryCode"),
                            bean.getName()
                    };
                }
            } catch (Throwable ignored) {
            }
            for (String value : candidates) {
                if (containsDoorbellToken(safeLower(value))) {
                    return true;
                }
            }
            // Call model type from door bell manager is handled elsewhere;
            // DeviceBean.getDeviceType / capability
            String raw = String.valueOf(bean);
            return containsDoorbellToken(safeLower(raw));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static String invokeString(Object target, String method) {
        if (target == null) {
            return null;
        }
        try {
            Object value = target.getClass().getMethod(method).invoke(target);
            return value == null ? null : String.valueOf(value);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean containsDoorbellToken(String value) {
        if (TextUtils.isEmpty(value)) {
            return false;
        }
        return value.contains("doorbell")
                || value.contains("ac_doorbell")
                || value.contains("videolock")
                || value.contains("video_lock")
                || (value.contains("lock") && value.contains("video"));
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase();
    }
}
