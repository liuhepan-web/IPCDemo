package com.ipc.demo.set;

import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Debug-mode NDJSON logger → host ingest + Logcat.
 * Session: 819097
 */
public final class AgentDebugLog {

    private static final String TAG = "AGENT_DEBUG";
    private static final String SESSION = "819097";
    private static final String RUN_ID = "doorbell-call-1";
    private static final String[] URLS = new String[]{
            "http://10.0.2.2:7863/ingest/6875d60d-3c12-4f6a-b6bb-95ba9daad55c",
            "http://127.0.0.1:7863/ingest/6875d60d-3c12-4f6a-b6bb-95ba9daad55c"
    };

    private AgentDebugLog() {
    }

    public static void log(String hypothesisId, String location, String message, JSONObject data) {
        // #region agent log
        try {
            final JSONObject payload = new JSONObject();
            payload.put("sessionId", SESSION);
            payload.put("runId", RUN_ID);
            payload.put("hypothesisId", hypothesisId);
            payload.put("location", location);
            payload.put("message", message);
            payload.put("data", data != null ? data : new JSONObject());
            payload.put("timestamp", System.currentTimeMillis());
            final String body = payload.toString();
            Log.i(TAG, body);
            new Thread(() -> {
                for (String u : URLS) {
                    HttpURLConnection conn = null;
                    try {
                        conn = (HttpURLConnection) new URL(u).openConnection();
                        conn.setConnectTimeout(1000);
                        conn.setReadTimeout(1000);
                        conn.setRequestMethod("POST");
                        conn.setRequestProperty("Content-Type", "application/json");
                        conn.setRequestProperty("X-Debug-Session-Id", SESSION);
                        conn.setDoOutput(true);
                        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                        try (OutputStream os = conn.getOutputStream()) {
                            os.write(bytes);
                        }
                        conn.getResponseCode();
                    } catch (Throwable ignored) {
                    } finally {
                        if (conn != null) {
                            conn.disconnect();
                        }
                    }
                }
            }, "agent-debug-log").start();
        } catch (Throwable t) {
            Log.e(TAG, "log failed", t);
        }
        // #endregion
    }
}
