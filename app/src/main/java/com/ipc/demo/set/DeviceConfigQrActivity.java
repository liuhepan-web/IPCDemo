package com.ipc.demo.set;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.home.sdk.builder.ThingCameraActivatorBuilder;
import com.thingclips.smart.sdk.api.IThingActivatorGetToken;
import com.thingclips.smart.sdk.api.IThingCameraDevActivator;
import com.thingclips.smart.sdk.api.IThingSmartCameraActivatorListener;
import com.thingclips.smart.sdk.bean.DeviceBean;

import java.util.Hashtable;

/**
 * Camera QR pairing — device scans App QR to receive Wi-Fi credentials.
 * Docs: https://developer.tuya.com/cn/docs/app-development/camera-scan-code-network-configuration?id=Kaixkcv3adu8y
 */
public class DeviceConfigQrActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "DeviceConfigQR";
    private static final int QR_TIMEOUT_SEC = 100;
    private static final int QR_SIZE_PX = 720;
    private static final long SUCCESS_NAV_DELAY_MS = 800L;

    private enum PairState {
        IDLE,
        FETCHING_TOKEN,
        WAITING_SCAN,
        SUCCESS,
        FAILED
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EditText etSsid;
    private EditText etPassword;
    private ProgressBar progressBar;
    private Button btnSearch;
    private ImageView ivQrCode;
    private TextView tvQrHint;
    private TextView tvPairStatus;
    private IThingCameraDevActivator cameraActivator;
    private PairState pairState = PairState.IDLE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_config_qr);
        etSsid = findViewById(R.id.etSsid);
        etPassword = findViewById(R.id.etPassword);
        progressBar = findViewById(R.id.progressBar);
        btnSearch = findViewById(R.id.btnSearch);
        ivQrCode = findViewById(R.id.ivQrCode);
        tvQrHint = findViewById(R.id.tvQrHint);
        tvPairStatus = findViewById(R.id.tvPairStatus);
        btnSearch.setOnClickListener(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        applyPairState(PairState.IDLE, null);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() != R.id.btnSearch) {
            return;
        }
        if (pairState == PairState.SUCCESS) {
            goDeviceList();
            return;
        }
        if (pairState == PairState.FETCHING_TOKEN || pairState == PairState.WAITING_SCAN) {
            return;
        }

        String ssid = etSsid.getText().toString().trim();
        String password = etPassword.getText().toString();
        if (TextUtils.isEmpty(ssid)) {
            Toast.makeText(this, R.string.tip_input_wifi, Toast.LENGTH_SHORT).show();
            return;
        }
        long homeId = HomeModel.getCurrentHome(this);
        if (homeId == 0L) {
            Toast.makeText(this, R.string.tip_select_home_first, Toast.LENGTH_SHORT).show();
            return;
        }

        stopActivator();
        mainHandler.removeCallbacksAndMessages(null);
        ivQrCode.setImageDrawable(null);
        ivQrCode.setVisibility(View.GONE);
        tvQrHint.setVisibility(View.GONE);
        applyPairState(PairState.FETCHING_TOKEN, getString(R.string.qr_status_fetching_token));

        ThingHomeSdk.getActivatorInstance().getActivatorToken(homeId, new IThingActivatorGetToken() {
            @Override
            public void onSuccess(String token) {
                if (isFinishing()) {
                    return;
                }
                ThingCameraActivatorBuilder builder = new ThingCameraActivatorBuilder()
                        .setContext(DeviceConfigQrActivity.this)
                        .setSsid(ssid)
                        .setPassword(password)
                        .setToken(token)
                        .setTimeOut(QR_TIMEOUT_SEC)
                        .setListener(new IThingSmartCameraActivatorListener() {
                            @Override
                            public void onQRCodeSuccess(String qrcodeUrl) {
                                runOnUiThread(() -> handleQrCodeSuccess(qrcodeUrl));
                            }

                            @Override
                            public void onError(String errorCode, String errorMsg) {
                                runOnUiThread(() -> handlePairError(errorCode, errorMsg));
                            }

                            @Override
                            public void onActiveSuccess(DeviceBean devResp) {
                                runOnUiThread(() -> handlePairSuccess(devResp));
                            }
                        });

                cameraActivator = ThingHomeSdk.getActivatorInstance().newCameraDevActivator(builder);
                // Generate QR first; start listening after QR is shown (avoids race with success UI).
                cameraActivator.createQRCode();
            }

            @Override
            public void onFailure(String errorCode, String errorMsg) {
                runOnUiThread(() -> handlePairError(errorCode, errorMsg));
            }
        });
    }

    private void handleQrCodeSuccess(String qrcodeUrl) {
        if (isFinishing() || pairState == PairState.SUCCESS) {
            return;
        }
        try {
            Bitmap bitmap = createQRCode(qrcodeUrl, QR_SIZE_PX);
            ivQrCode.setImageBitmap(bitmap);
            ivQrCode.setVisibility(View.VISIBLE);
            tvQrHint.setVisibility(View.VISIBLE);
            tvQrHint.setText(R.string.qr_scan_hint);
            applyPairState(PairState.WAITING_SCAN, getString(R.string.qr_status_waiting_scan));
            if (cameraActivator != null) {
                cameraActivator.start();
            }
        } catch (Exception e) {
            Log.e(TAG, "QR encode failed", e);
            handlePairError("QR", e.getMessage() != null ? e.getMessage() : "encode failed");
        }
    }

    private void handlePairSuccess(DeviceBean devResp) {
        if (isFinishing()) {
            return;
        }
        // Terminal state: never fall back to "waiting".
        String name = null;
        if (devResp != null) {
            name = !TextUtils.isEmpty(devResp.getName()) ? devResp.getName() : devResp.getDevId();
        }
        String status = TextUtils.isEmpty(name)
                ? getString(R.string.qr_status_success)
                : getString(R.string.qr_status_success_named, name);
        applyPairState(PairState.SUCCESS, status);
        tvQrHint.setVisibility(View.VISIBLE);
        tvQrHint.setText(R.string.qr_success_hint);
        stopActivator();
        Toast.makeText(this, R.string.activate_success, Toast.LENGTH_SHORT).show();
        mainHandler.postDelayed(this::goDeviceList, SUCCESS_NAV_DELAY_MS);
    }

    private void handlePairError(String errorCode, String errorMsg) {
        if (isFinishing() || pairState == PairState.SUCCESS) {
            return;
        }
        String msg = getString(R.string.error_with_code,
                errorCode != null ? errorCode : "-",
                errorMsg != null ? errorMsg : "");
        applyPairState(PairState.FAILED, msg);
        stopActivator();
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }

    /**
     * Drive button / progress / status text from pairing callbacks only.
     */
    private void applyPairState(PairState state, String statusText) {
        pairState = state;
        switch (state) {
            case IDLE:
                progressBar.setVisibility(View.GONE);
                btnSearch.setEnabled(true);
                btnSearch.setText(R.string.start_qr);
                tvPairStatus.setVisibility(View.GONE);
                break;
            case FETCHING_TOKEN:
                progressBar.setVisibility(View.VISIBLE);
                btnSearch.setEnabled(false);
                btnSearch.setText(R.string.qr_status_fetching_token);
                tvPairStatus.setVisibility(View.VISIBLE);
                tvPairStatus.setTextColor(ContextCompat.getColor(this, R.color.ipc_ink_secondary));
                tvPairStatus.setText(statusText);
                break;
            case WAITING_SCAN:
                progressBar.setVisibility(View.VISIBLE);
                btnSearch.setEnabled(false);
                btnSearch.setText(R.string.qr_waiting_device);
                tvPairStatus.setVisibility(View.VISIBLE);
                tvPairStatus.setTextColor(ContextCompat.getColor(this, R.color.ipc_primary_dark));
                tvPairStatus.setText(statusText);
                break;
            case SUCCESS:
                progressBar.setVisibility(View.GONE);
                btnSearch.setEnabled(true);
                btnSearch.setText(R.string.qr_go_device_list);
                tvPairStatus.setVisibility(View.VISIBLE);
                tvPairStatus.setTextColor(ContextCompat.getColor(this, R.color.ipc_status_ok));
                tvPairStatus.setText(statusText);
                break;
            case FAILED:
                progressBar.setVisibility(View.GONE);
                btnSearch.setEnabled(true);
                btnSearch.setText(R.string.start_qr);
                tvPairStatus.setVisibility(View.VISIBLE);
                tvPairStatus.setTextColor(ContextCompat.getColor(this, R.color.ipc_danger));
                tvPairStatus.setText(statusText);
                break;
            default:
                break;
        }
    }

    private void goDeviceList() {
        if (isFinishing()) {
            return;
        }
        startActivity(new Intent(this, DeviceListActivity.class));
        finish();
    }

    /**
     * Encode URL to QR bitmap (ZXing), as shown in Tuya docs.
     */
    private static Bitmap createQRCode(String url, int widthAndHeight) throws WriterException {
        Hashtable<EncodeHintType, Object> hints = new Hashtable<>();
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
        hints.put(EncodeHintType.MARGIN, 1);
        BitMatrix matrix = new QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, widthAndHeight, widthAndHeight, hints);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        int[] pixels = new int[width * height];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[y * width + x] = matrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height);
        return bitmap;
    }

    private void stopActivator() {
        if (cameraActivator != null) {
            try {
                cameraActivator.stop();
                cameraActivator.onDestroy();
            } catch (Throwable t) {
                Log.w(TAG, "stopActivator", t);
            }
            cameraActivator = null;
        }
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        stopActivator();
        super.onDestroy();
    }
}
