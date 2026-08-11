package com.ipc.demo.set;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSONObject;
import com.google.android.material.button.MaterialButton;
import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.sdk.api.IDevListener;
import com.thingclips.smart.sdk.api.IResultCallback;
import com.thingclips.smart.sdk.api.IThingDevice;
import com.thingclips.smart.sdk.bean.DeviceBean;

import java.util.Map;

/**
 * SD card DP management: status, storage, format, record switch, watermark.
 * Docs: https://developer.tuya.com/cn/docs/app-development/sdcard?id=Ka6nxw2eufia3
 */
public class CameraSdManageActivity extends AppCompatActivity {

    private static final String TAG = "CameraSdManage";

    private String devId;
    private IThingDevice device;
    private TextView tvSdStatus;
    private TextView tvSdStorage;
    private TextView tvFormatProgress;
    private TextView tvRecordValue;
    private TextView tvWatermarkValue;
    private MaterialButton btnFormat;
    private MaterialButton btnRecordToggle;
    private MaterialButton btnWatermarkToggle;

    private final IDevListener devListener = new IDevListener() {
        @Override
        public void onDpUpdate(String deviceId, String dpStr) {
            if (TextUtils.isEmpty(dpStr)) {
                return;
            }
            Map<String, Object> dps = JSONObject.parseObject(dpStr, Map.class);
            if (dps == null) {
                return;
            }
            runOnUiThread(() -> {
                if (dps.containsKey(DPConstants.SD_STATUS)) {
                    updateStatus(dps.get(DPConstants.SD_STATUS));
                }
                if (dps.containsKey(DPConstants.SD_STORAGE)) {
                    updateStorage(dps.get(DPConstants.SD_STORAGE));
                }
                if (dps.containsKey(DPConstants.SD_FORMAT_STATUS)) {
                    updateFormatProgress(dps.get(DPConstants.SD_FORMAT_STATUS));
                }
                if (dps.containsKey(DPConstants.SD_CARD_RECORD_SWITCH)) {
                    updateRecord(dps.get(DPConstants.SD_CARD_RECORD_SWITCH));
                }
                if (dps.containsKey(DPConstants.WATERMARK)) {
                    updateWatermark(dps.get(DPConstants.WATERMARK));
                }
            });
        }

        @Override
        public void onRemoved(String deviceId) {
        }

        @Override
        public void onStatusChanged(String deviceId, boolean online) {
        }

        @Override
        public void onNetworkStatusChanged(String deviceId, boolean status) {
        }

        @Override
        public void onDevInfoUpdate(String deviceId) {
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_sd_manage);
        devId = getIntent().getStringExtra(IpcConstants.EXTRA_DEV_ID);
        if (TextUtils.isEmpty(devId)) {
            Toast.makeText(this, R.string.ipc_invalid_device, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        tvSdStatus = findViewById(R.id.tvSdStatus);
        tvSdStorage = findViewById(R.id.tvSdStorage);
        tvFormatProgress = findViewById(R.id.tvFormatProgress);
        tvRecordValue = findViewById(R.id.tvRecordValue);
        tvWatermarkValue = findViewById(R.id.tvWatermarkValue);
        btnFormat = findViewById(R.id.btnFormat);
        btnRecordToggle = findViewById(R.id.btnRecordToggle);
        btnWatermarkToggle = findViewById(R.id.btnWatermarkToggle);

        device = ThingHomeSdk.newDeviceInstance(devId);
        device.registerDevListener(devListener);

        bindStatus();
        bindStorageAndFormat();
        bindRecord();
        bindWatermark();
    }

    private void bindStatus() {
        Object value = queryDp(DPConstants.SD_STATUS);
        if (value == null) {
            tvSdStatus.setText(R.string.ipc_not_support_dp);
        } else {
            updateStatus(value);
        }
    }

    private void bindStorageAndFormat() {
        Object storage = queryDp(DPConstants.SD_STORAGE);
        if (storage == null) {
            tvSdStorage.setText(R.string.ipc_not_support_dp);
            return;
        }
        updateStorage(storage);
        if (queryDp(DPConstants.SD_FORMAT) != null) {
            btnFormat.setVisibility(View.VISIBLE);
            btnFormat.setOnClickListener(v -> {
                publishDp(DPConstants.SD_FORMAT, true);
                tvFormatProgress.setVisibility(View.VISIBLE);
                tvFormatProgress.setText(getString(R.string.ipc_format_progress, "0"));
            });
        }
    }

    private void bindRecord() {
        Object value = queryDp(DPConstants.SD_CARD_RECORD_SWITCH);
        if (value == null) {
            tvRecordValue.setText(R.string.ipc_not_support_dp);
            return;
        }
        updateRecord(value);
        btnRecordToggle.setVisibility(View.VISIBLE);
        btnRecordToggle.setOnClickListener(v -> {
            boolean current = Boolean.parseBoolean(String.valueOf(queryDp(DPConstants.SD_CARD_RECORD_SWITCH)));
            publishDp(DPConstants.SD_CARD_RECORD_SWITCH, !current);
        });
    }

    private void bindWatermark() {
        Object value = queryDp(DPConstants.WATERMARK);
        if (value == null) {
            tvWatermarkValue.setText(R.string.ipc_not_support_dp);
            return;
        }
        updateWatermark(value);
        btnWatermarkToggle.setVisibility(View.VISIBLE);
        btnWatermarkToggle.setOnClickListener(v -> {
            boolean current = Boolean.parseBoolean(String.valueOf(queryDp(DPConstants.WATERMARK)));
            publishDp(DPConstants.WATERMARK, !current);
        });
    }

    private void updateStatus(Object value) {
        int code;
        try {
            code = Integer.parseInt(String.valueOf(value));
        } catch (Exception e) {
            tvSdStatus.setText(String.valueOf(value));
            return;
        }
        int resId;
        switch (code) {
            case 1:
                resId = R.string.ipc_sd_status_1;
                break;
            case 2:
                resId = R.string.ipc_sd_status_2;
                break;
            case 3:
                resId = R.string.ipc_sd_status_3;
                break;
            case 4:
                resId = R.string.ipc_sd_status_4;
                break;
            case 5:
                resId = R.string.ipc_sd_status_5;
                break;
            case 7:
                resId = R.string.ipc_sd_status_7;
                break;
            default:
                tvSdStatus.setText(getString(R.string.ipc_sd_status_unknown, code));
                return;
        }
        tvSdStatus.setText(resId);
    }

    private void updateStorage(Object value) {
        tvSdStorage.setText(String.valueOf(value));
    }

    private void updateFormatProgress(Object value) {
        tvFormatProgress.setVisibility(View.VISIBLE);
        String progress = String.valueOf(value);
        tvFormatProgress.setText(getString(R.string.ipc_format_progress, progress));
        if ("100".equals(progress)) {
            updateStorage(queryDp(DPConstants.SD_STORAGE));
            tvFormatProgress.setVisibility(View.GONE);
            Toast.makeText(this, R.string.ipc_format_done, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateRecord(Object value) {
        tvRecordValue.setText(String.valueOf(value));
    }

    private void updateWatermark(Object value) {
        tvWatermarkValue.setText(String.valueOf(value));
    }

    private Object queryDp(String dpId) {
        DeviceBean bean = ThingHomeSdk.getDataInstance().getDeviceBean(devId);
        if (bean == null || bean.getDps() == null) {
            return null;
        }
        return bean.getDps().get(dpId);
    }

    private void publishDp(String dpId, Object value) {
        if (device == null) {
            device = ThingHomeSdk.newDeviceInstance(devId);
        }
        JSONObject json = new JSONObject();
        json.put(dpId, value);
        String dps = json.toString();
        device.publishDps(dps, new IResultCallback() {
            @Override
            public void onError(String code, String error) {
                Log.e(TAG, "publishDps err " + dps + " " + code + " " + error);
                runOnUiThread(() -> Toast.makeText(CameraSdManageActivity.this,
                        getString(R.string.error_with_code, code, error), Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onSuccess() {
                Log.i(TAG, "publishDps suc " + dps);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (device != null) {
            device.unRegisterDevListener();
            device.onDestroy();
            device = null;
        }
    }
}
