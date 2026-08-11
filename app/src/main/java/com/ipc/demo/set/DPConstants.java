package com.ipc.demo.set;

/**
 * IPC DP ids — https://developer.tuya.com/cn/docs/app-development/sdcard?id=Ka6nxw2eufia3
 */
public final class DPConstants {

    public static final String PTZ_CONTROL = "119";
    public static final String PTZ_STOP = "116";
    public static final String WATERMARK = "104";
    public static final String SD_CARD_RECORD_SWITCH = "150";
    public static final String SD_CARD_RECORD_MODE = "151";
    public static final String SD_STATUS = "110";
    public static final String SD_STORAGE = "109";
    public static final String SD_FORMAT = "111";
    public static final String SD_FORMAT_STATUS = "117";

    /** PTZ: 0 up, 2 right, 4 down, 6 left */
    public static final String PTZ_UP = "0";
    public static final String PTZ_RIGHT = "2";
    public static final String PTZ_DOWN = "4";
    public static final String PTZ_LEFT = "6";

    private DPConstants() {
    }
}
