package com.ipc.demo.set;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.google.android.material.button.MaterialButton;
import com.thingclips.drawee.view.DecryptImageView;
import com.thingclips.smart.android.camera.sdk.ThingIPCSdk;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCTool;
import com.thingclips.smart.home.sdk.callback.IThingResultCallback;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Preview alarm attach image; encrypted images use DecryptImageView / downloadEncryptedImg.
 */
public class CameraMessageImageActivity extends AppCompatActivity {

    private static final String TAG = "CameraMsgImage";

    private String devId;
    private String imageUrl;
    private String encryptKey;
    private boolean encrypted;
    private ProgressBar progressBar;
    private MaterialButton btnDownload;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_message_image);

        devId = getIntent().getStringExtra(IpcConstants.EXTRA_DEV_ID);
        imageUrl = getIntent().getStringExtra(IpcConstants.EXTRA_IMAGE_URL);
        encryptKey = getIntent().getStringExtra(IpcConstants.EXTRA_IMAGE_KEY);
        if (TextUtils.isEmpty(imageUrl)) {
            Toast.makeText(this, R.string.ipc_msg_image_missing, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (encryptKey == null) {
            encryptKey = "";
        }
        encrypted = !TextUtils.isEmpty(encryptKey);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        progressBar = findViewById(R.id.progressBar);
        btnDownload = findViewById(R.id.btnDownload);
        DecryptImageView ivPreview = findViewById(R.id.ivPreview);
        bindPreview(ivPreview);
        btnDownload.setOnClickListener(v -> downloadImage());
    }

    private void bindPreview(DecryptImageView imageView) {
        if (encrypted) {
            imageView.setImageURI(imageUrl, encryptKey.getBytes());
        } else {
            try {
                DraweeController controller = Fresco.newDraweeControllerBuilder()
                        .setUri(Uri.parse(imageUrl))
                        .build();
                imageView.setController(controller);
            } catch (Exception e) {
                Log.e(TAG, "bind plain image fail", e);
                Toast.makeText(this, R.string.ipc_msg_image_load_fail, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void downloadImage() {
        setDownloading(true);
        if (encrypted) {
            downloadEncrypted();
        } else {
            downloadPlain();
        }
    }

    private void downloadEncrypted() {
        IThingIPCTool tool = ThingIPCSdk.getTool();
        if (tool == null) {
            setDownloading(false);
            Toast.makeText(this, R.string.ipc_operate_fail_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        tool.downloadEncryptedImg(imageUrl, encryptKey, new IThingResultCallback<Bitmap>() {
            @Override
            public void onSuccess(Bitmap result) {
                if (result == null || result.isRecycled()) {
                    runOnUiThread(() -> {
                        setDownloading(false);
                        Toast.makeText(CameraMessageImageActivity.this,
                                R.string.ipc_msg_image_download_fail, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }
                executor.execute(() -> {
                    File file = saveBitmap(result);
                    runOnUiThread(() -> {
                        setDownloading(false);
                        if (file != null) {
                            Toast.makeText(CameraMessageImageActivity.this,
                                    R.string.ipc_msg_image_saved, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(CameraMessageImageActivity.this,
                                    R.string.ipc_msg_image_download_fail, Toast.LENGTH_SHORT).show();
                        }
                    });
                });
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                runOnUiThread(() -> {
                    setDownloading(false);
                    Toast.makeText(CameraMessageImageActivity.this,
                            getString(R.string.error_with_code, errorCode, errorMessage),
                            Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void downloadPlain() {
        executor.execute(() -> {
            File file = null;
            HttpURLConnection connection = null;
            try {
                File dir = IpcLocalMediaHelper.ensureDeviceMediaDir(this, resolveSaveDevId());
                if (dir == null) {
                    throw new IllegalStateException("no media dir");
                }
                file = new File(dir, "alarm_" + System.currentTimeMillis() + ".jpg");
                connection = (HttpURLConnection) new URL(imageUrl).openConnection();
                connection.setConnectTimeout(15000);
                connection.setReadTimeout(30000);
                connection.connect();
                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new IllegalStateException("http " + connection.getResponseCode());
                }
                try (InputStream in = new BufferedInputStream(connection.getInputStream());
                     FileOutputStream out = new FileOutputStream(file)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        out.write(buf, 0, len);
                    }
                    out.flush();
                }
                // Validate as image when possible.
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
                if (opts.outWidth <= 0 || opts.outHeight <= 0) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                    file = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "download plain fail", e);
                if (file != null && file.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    file.delete();
                }
                file = null;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
            File saved = file;
            runOnUiThread(() -> {
                setDownloading(false);
                if (saved != null) {
                    Toast.makeText(CameraMessageImageActivity.this,
                            R.string.ipc_msg_image_saved, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(CameraMessageImageActivity.this,
                            R.string.ipc_msg_image_download_fail, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private File saveBitmap(Bitmap bitmap) {
        FileOutputStream out = null;
        try {
            File dir = IpcLocalMediaHelper.ensureDeviceMediaDir(this, resolveSaveDevId());
            if (dir == null) {
                return null;
            }
            File file = new File(dir, "alarm_" + System.currentTimeMillis() + ".jpg");
            out = new FileOutputStream(file);
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)) {
                //noinspection ResultOfMethodCallIgnored
                file.delete();
                return null;
            }
            out.flush();
            return file;
        } catch (Exception e) {
            Log.e(TAG, "saveBitmap fail", e);
            return null;
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private String resolveSaveDevId() {
        return TextUtils.isEmpty(devId) ? "alarm" : devId;
    }

    private void setDownloading(boolean downloading) {
        progressBar.setVisibility(downloading ? View.VISIBLE : View.GONE);
        btnDownload.setEnabled(!downloading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }
}
