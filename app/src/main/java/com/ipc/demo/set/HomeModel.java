package com.ipc.demo.set;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Cache current homeId for device pairing.
 */
public final class HomeModel {

    private static final String SP_NAME = "HomeModel";
    private static final String KEY_CURRENT_HOME_ID = "currentHomeId";

    private HomeModel() {
    }

    public static void setCurrentHome(Context context, long homeId) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().putLong(KEY_CURRENT_HOME_ID, homeId).apply();
    }

    public static long getCurrentHome(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        return sp.getLong(KEY_CURRENT_HOME_ID, 0L);
    }

    public static void clear(Context context) {
        SharedPreferences sp = context.getSharedPreferences(SP_NAME, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_CURRENT_HOME_ID).apply();
    }
}
