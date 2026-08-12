package com.ipc.demo.set;

/**
 * IPC DP ids —
 * SD: https://developer.tuya.com/cn/docs/app-development/sdcard?id=Ka6nxw2eufia3
 * Motion: https://developer.tuya.com/cn/docs/app-development/datapointid?id=Kceuhc115prg7
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

    /** Default motion alert switch dpId; resolve from DeviceBean schema when possible. */
    public static final String MOTION_SWITCH = "134";
    /** Schema code for motion alert switch (移动报警开关). */
    public static final String MOTION_SWITCH_CODE = "motion_switch";
    /** Default motion sensitivity dpId; resolve from DeviceBean schema when possible. */
    public static final String MOTION_SENSITIVITY = "106";
    /** Schema code for motion sensitivity. */
    public static final String MOTION_SENSITIVITY_CODE = "motion_sensitivity";

    /** PTZ: 0 up, 2 right, 4 down, 6 left */
    public static final String PTZ_UP = "0";
    public static final String PTZ_RIGHT = "2";
    public static final String PTZ_DOWN = "4";
    public static final String PTZ_LEFT = "6";

    private DPConstants() {
    }
}
