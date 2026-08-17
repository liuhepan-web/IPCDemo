package com.ipc.demo.set;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.thingclips.loguploader.TLogSDK;
import com.thingclips.loguploader.api.LogFileCallback;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Offline logs live in app-private storage on the phone.
 * Primary export copies them to public Downloads/IPCDemo_logs for file manager access.
 * Docs: https://developer.tuya.com/cn/docs/app-development/ipcsdklog?id=Kbvezkn5bkaam
 */
public class OfflineLogActivity extends AppCompatActivity {

    private static final String TAG = "OfflineLog";
    private static final String EXPORT_DIR_NAME = "IPCDemo_logs";

    private TextView tvLogPaths;
    private MaterialButton btnExport;
    private MaterialButton btnShare;
    private MaterialButton btnFetchLogs;
    private MaterialButton btnCopyPaths;
    private String lastPathsText = "";
    private final ArrayList<String> lastLogPaths = new ArrayList<>();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_offline_log);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvLogPaths = findViewById(R.id.tvLogPaths);
        btnExport = findViewById(R.id.btnExport);
        btnShare = findViewById(R.id.btnShare);
        btnFetchLogs = findViewById(R.id.btnFetchLogs);
        btnCopyPaths = findViewById(R.id.btnCopyPaths);

        btnExport.setOnClickListener(v -> fetchLogPaths(ExportMode.DOWNLOADS));
        btnShare.setOnClickListener(v -> fetchLogPaths(ExportMode.SHARE));
        btnFetchLogs.setOnClickListener(v -> fetchLogPaths(ExportMode.NONE));
        btnCopyPaths.setOnClickListener(v -> copyPaths());
    }

    private enum ExportMode {
        NONE,
        DOWNLOADS,
        SHARE
    }

    private void fetchLogPaths(ExportMode mode) {
        setBusy(true);
        tvLogPaths.setText(R.string.ipc_offline_log_fetching);
        TLogSDK.fetchLogs(getApplicationContext(), new LogFileCallback() {
            @Override
            public void onSuccess(ArrayList<String> logPaths) {
                runOnUiThread(() -> {
                    applyPaths(logPaths);
                    if (mode == ExportMode.DOWNLOADS) {
                        exportToDownloads();
                    } else if (mode == ExportMode.SHARE) {
                        setBusy(false);
                        shareLogFiles();
                    } else {
                        setBusy(false);
                    }
                });
            }

            @Override
            public void onFail(String errorMsg) {
                runOnUiThread(() -> {
                    setBusy(false);
                    lastLogPaths.clear();
                    lastPathsText = "";
                    btnCopyPaths.setEnabled(false);
                    String msg = TextUtils.isEmpty(errorMsg)
                            ? getString(R.string.ipc_operate_fail_generic)
                            : errorMsg;
                    tvLogPaths.setText(getString(R.string.ipc_offline_log_fail, msg));
                    Toast.makeText(OfflineLogActivity.this, msg, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void applyPaths(ArrayList<String> logPaths) {
        lastLogPaths.clear();
        if (logPaths == null || logPaths.isEmpty()) {
            lastPathsText = "";
            btnCopyPaths.setEnabled(false);
            tvLogPaths.setText(R.string.ipc_offline_log_none);
            Toast.makeText(this, R.string.ipc_offline_log_none, Toast.LENGTH_SHORT).show();
            return;
        }
        lastLogPaths.addAll(logPaths);
        StringBuilder sb = new StringBuilder();
        sb.append(getString(R.string.ipc_offline_log_on_phone));
        sb.append('\n');
        sb.append(getString(R.string.ipc_offline_log_count, logPaths.size()));
        sb.append('\n');
        for (int i = 0; i < logPaths.size(); i++) {
            String path = logPaths.get(i);
            sb.append(i + 1).append(". ").append(path == null ? "" : path).append('\n');
        }
        lastPathsText = sb.toString().trim();
        tvLogPaths.setText(lastPathsText);
        btnCopyPaths.setEnabled(true);
    }

    private void exportToDownloads() {
        if (lastLogPaths.isEmpty()) {
            setBusy(false);
            Toast.makeText(this, R.string.ipc_offline_log_none, Toast.LENGTH_SHORT).show();
            return;
        }
        executor.execute(() -> {
            int ok = 0;
            ArrayList<String> saved = new ArrayList<>();
            for (String path : lastLogPaths) {
                if (TextUtils.isEmpty(path)) {
                    continue;
                }
                File src = new File(path);
                if (!src.isFile()) {
                    continue;
                }
                String destHint = copyOneToDownloads(src);
                if (destHint != null) {
                    ok++;
                    saved.add(destHint);
                }
            }
            int count = ok;
            runOnUiThread(() -> {
                setBusy(false);
                if (count <= 0) {
                    Toast.makeText(this, R.string.ipc_offline_log_export_fail, Toast.LENGTH_LONG).show();
                    return;
                }
                StringBuilder sb = new StringBuilder();
                sb.append(getString(R.string.ipc_offline_log_saved_title, count));
                sb.append('\n');
                for (String item : saved) {
                    sb.append(item).append('\n');
                }
                sb.append('\n').append(lastPathsText);
                tvLogPaths.setText(sb.toString().trim());
                Toast.makeText(this,
                        getString(R.string.ipc_offline_log_saved_toast, count),
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    /**
     * @return user-visible location hint, or null on failure
     */
    private String copyOneToDownloads(File src) {
        String displayName = src.getName();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Downloads.DISPLAY_NAME, displayName);
                values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
                values.put(MediaStore.Downloads.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS + "/" + EXPORT_DIR_NAME);
                values.put(MediaStore.Downloads.IS_PENDING, 1);
                Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return null;
                }
                try (InputStream in = new FileInputStream(src);
                     OutputStream out = getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        getContentResolver().delete(uri, null, null);
                        return null;
                    }
                    copyStream(in, out);
                }
                values.clear();
                values.put(MediaStore.Downloads.IS_PENDING, 0);
                getContentResolver().update(uri, values, null, null);
                return "Download/" + EXPORT_DIR_NAME + "/" + displayName;
            }

            File dir = new File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    EXPORT_DIR_NAME);
            if (!dir.exists() && !dir.mkdirs()) {
                return null;
            }
            File dest = new File(dir, displayName);
            try (InputStream in = new FileInputStream(src);
                 OutputStream out = new FileOutputStream(dest)) {
                copyStream(in, out);
            }
            return dest.getAbsolutePath();
        } catch (Exception e) {
            Log.e(TAG, "copy to downloads fail: " + displayName, e);
            return null;
        }
    }

    private static void copyStream(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[8192];
        int len;
        while ((len = in.read(buf)) != -1) {
            out.write(buf, 0, len);
        }
        out.flush();
    }

    private void shareLogFiles() {
        if (lastLogPaths.isEmpty()) {
            Toast.makeText(this, R.string.ipc_offline_log_none, Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<Uri> uris = new ArrayList<>();
        for (String path : lastLogPaths) {
            if (TextUtils.isEmpty(path)) {
                continue;
            }
            File file = new File(path);
            if (!file.isFile()) {
                continue;
            }
            try {
                Uri uri = FileProvider.getUriForFile(
                        this, getPackageName() + ".provider", file);
                uris.add(uri);
            } catch (Exception e) {
                Log.e(TAG, "FileProvider fail: " + path, e);
            }
        }
        if (uris.isEmpty()) {
            Toast.makeText(this, R.string.ipc_offline_log_export_fail, Toast.LENGTH_LONG).show();
            return;
        }

        Intent share;
        if (uris.size() == 1) {
            share = new Intent(Intent.ACTION_SEND);
            share.setType("*/*");
            share.putExtra(Intent.EXTRA_STREAM, uris.get(0));
        } else {
            share = new Intent(Intent.ACTION_SEND_MULTIPLE);
            share.setType("*/*");
            share.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
        }
        share.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.ipc_offline_log));
        share.putExtra(Intent.EXTRA_TEXT, getString(R.string.ipc_offline_log_share_text));
        try {
            startActivity(Intent.createChooser(share, getString(R.string.ipc_offline_log_share)));
        } catch (Exception e) {
            Toast.makeText(this, R.string.ipc_offline_log_export_fail, Toast.LENGTH_SHORT).show();
        }
    }

    private void copyPaths() {
        if (TextUtils.isEmpty(lastPathsText)) {
            Toast.makeText(this, R.string.ipc_offline_log_path_empty, Toast.LENGTH_SHORT).show();
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, R.string.ipc_operate_fail_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("offline_log_paths", lastPathsText));
        Toast.makeText(this, R.string.ipc_offline_log_copied, Toast.LENGTH_SHORT).show();
    }

    private void setBusy(boolean busy) {
        btnExport.setEnabled(!busy);
        btnShare.setEnabled(!busy);
        btnFetchLogs.setEnabled(!busy);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
