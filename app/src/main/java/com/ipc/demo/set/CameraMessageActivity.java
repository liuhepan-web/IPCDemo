package com.ipc.demo.set;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.alibaba.fastjson.JSONObject;
import com.google.android.material.button.MaterialButton;
import com.thingclips.smart.android.camera.sdk.ThingIPCSdk;
import com.thingclips.smart.android.camera.sdk.api.IThingCameraMessage;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCMsg;
import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.home.sdk.callback.IThingResultCallback;
import com.thingclips.smart.ipc.messagecenter.bean.CameraMessageBean;
import com.thingclips.smart.ipc.messagecenter.bean.CameraMessageClassifyBean;
import com.thingclips.smart.android.device.bean.SchemaBean;
import com.thingclips.smart.sdk.api.IDevListener;
import com.thingclips.smart.sdk.api.IResultCallback;
import com.thingclips.smart.sdk.api.IThingDevice;
import com.thingclips.smart.sdk.bean.DeviceBean;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Alarm message center: query today's pics/videos.
 * Motion switch UI is shown only when DeviceBean schema contains motion_switch.
 */
public class CameraMessageActivity extends AppCompatActivity {

    private static final String TAG = "CameraMessage";
    private static final String MSG_MOTION = "ipc_motion";

    private String devId;
    private IThingDevice device;
    private IThingCameraMessage cameraMessage;
    private CameraMessageClassifyBean selectClassify;
    private CameraMessageAdapter adapter;
    private View layoutMotionPanel;
    private TextView tvEmpty;
    private TextView tvMotionLabel;
    private TextView tvMotionValue;
    private TextView tvSensitivityLabel;
    private TextView tvSensitivityValue;
    private TextView tvClassifyHint;
    private LinearLayout layoutSensitivity;
    private MaterialButton btnMotionToggle;
    /** Resolved from schema; null if product has no motion_switch. */
    private String motionSwitchDpId;
    /** Resolved from schema; null if product has no motion_sensitivity. */
    private String motionSensitivityDpId;
    private final List<CameraMessageBean> messageList = new ArrayList<>();

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
                if (motionSwitchDpId != null && dps.containsKey(motionSwitchDpId)) {
                    updateMotionUi(dps.get(motionSwitchDpId));
                }
                if (motionSensitivityDpId != null && dps.containsKey(motionSensitivityDpId)) {
                    updateSensitivityUi(dps.get(motionSensitivityDpId));
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
        setContentView(R.layout.activity_camera_message);
        devId = getIntent().getStringExtra(IpcConstants.EXTRA_DEV_ID);
        if (TextUtils.isEmpty(devId)) {
            Toast.makeText(this, R.string.ipc_invalid_device, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnRefresh).setOnClickListener(v -> loadTodayMessages());
        layoutMotionPanel = findViewById(R.id.layoutMotionPanel);
        tvEmpty = findViewById(R.id.tvEmpty);
        tvMotionLabel = findViewById(R.id.tvMotionLabel);
        tvMotionValue = findViewById(R.id.tvMotionValue);
        tvSensitivityLabel = findViewById(R.id.tvSensitivityLabel);
        tvSensitivityValue = findViewById(R.id.tvSensitivityValue);
        tvClassifyHint = findViewById(R.id.tvClassifyHint);
        layoutSensitivity = findViewById(R.id.layoutSensitivity);
        btnMotionToggle = findViewById(R.id.btnMotionToggle);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CameraMessageAdapter(this);
        adapter.setListener(new CameraMessageAdapter.OnItemListener() {
            @Override
            public void onItemClick(CameraMessageBean bean) {
                openVideoIfNeeded(bean);
            }

            @Override
            public void onImageClick(CameraMessageBean bean) {
                openImage(bean);
            }

            @Override
            public void onLongClick(CameraMessageBean bean) {
                deleteMessage(bean);
            }
        });
        recyclerView.setAdapter(adapter);

        bindMotionFromSchema();

        IThingIPCMsg message = ThingIPCSdk.getMessage();
        if (message != null) {
            cameraMessage = message.createCameraMessage();
        }
        if (cameraMessage == null) {
            Toast.makeText(this, R.string.ipc_operate_fail_generic, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        tvClassifyHint.setText(R.string.ipc_msg_enable_motion_hint);
        queryClassify();
    }

    /**
     * Show motion controls only when DeviceBean schema declares motion_switch.
     */
    private void bindMotionFromSchema() {
        DeviceBean deviceBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId);
        SchemaBean motionSchema = findSchema(deviceBean,
                DPConstants.MOTION_SWITCH, DPConstants.MOTION_SWITCH_CODE);
        if (motionSchema == null) {
            layoutMotionPanel.setVisibility(View.GONE);
            motionSwitchDpId = null;
            motionSensitivityDpId = null;
            return;
        }

        motionSwitchDpId = resolveDpId(motionSchema, DPConstants.MOTION_SWITCH);
        layoutMotionPanel.setVisibility(View.VISIBLE);
        if (!TextUtils.isEmpty(motionSchema.getName())) {
            tvMotionLabel.setText(motionSchema.getName());
        } else {
            tvMotionLabel.setText(R.string.ipc_motion_switch);
        }
        updateMotionUi(queryDp(motionSwitchDpId));
        btnMotionToggle.setVisibility(View.VISIBLE);
        btnMotionToggle.setOnClickListener(v -> {
            boolean current = Boolean.parseBoolean(String.valueOf(queryDp(motionSwitchDpId)));
            publishDp(motionSwitchDpId, !current);
        });

        SchemaBean sensSchema = findSchema(deviceBean,
                DPConstants.MOTION_SENSITIVITY, DPConstants.MOTION_SENSITIVITY_CODE);
        if (sensSchema == null) {
            motionSensitivityDpId = null;
            tvSensitivityLabel.setVisibility(View.GONE);
            tvSensitivityValue.setVisibility(View.GONE);
            layoutSensitivity.setVisibility(View.GONE);
        } else {
            motionSensitivityDpId = resolveDpId(sensSchema, DPConstants.MOTION_SENSITIVITY);
            tvSensitivityLabel.setVisibility(View.VISIBLE);
            tvSensitivityValue.setVisibility(View.VISIBLE);
            layoutSensitivity.setVisibility(View.VISIBLE);
            if (!TextUtils.isEmpty(sensSchema.getName())) {
                tvSensitivityLabel.setText(sensSchema.getName());
            } else {
                tvSensitivityLabel.setText(R.string.ipc_motion_sensitivity);
            }
            updateSensitivityUi(queryDp(motionSensitivityDpId));
            findViewById(R.id.btnSensLow).setOnClickListener(v ->
                    publishDp(motionSensitivityDpId, "0"));
            findViewById(R.id.btnSensMid).setOnClickListener(v ->
                    publishDp(motionSensitivityDpId, "1"));
            findViewById(R.id.btnSensHigh).setOnClickListener(v ->
                    publishDp(motionSensitivityDpId, "2"));
        }

        device = ThingHomeSdk.newDeviceInstance(devId);
        device.registerDevListener(devListener);
    }

    /**
     * Look up DP schema by preferred dpId, otherwise by schema code (e.g. motion_switch).
     */
    private SchemaBean findSchema(DeviceBean deviceBean, String preferredDpId, String code) {
        if (deviceBean == null) {
            return null;
        }
        Map<String, SchemaBean> schemaMap = deviceBean.getSchemaMap();
        if (schemaMap == null || schemaMap.isEmpty()) {
            return null;
        }
        SchemaBean byId = schemaMap.get(preferredDpId);
        if (byId != null) {
            return byId;
        }
        if (TextUtils.isEmpty(code)) {
            return null;
        }
        for (SchemaBean schema : schemaMap.values()) {
            if (schema != null && code.equals(schema.getCode())) {
                return schema;
            }
        }
        return null;
    }

    private String resolveDpId(SchemaBean schema, String fallback) {
        if (schema != null && !TextUtils.isEmpty(schema.getId())) {
            return schema.getId();
        }
        return fallback;
    }

    private void updateMotionUi(Object value) {
        if (value == null) {
            tvMotionValue.setText(R.string.ipc_motion_off);
            return;
        }
        boolean on = Boolean.parseBoolean(String.valueOf(value));
        tvMotionValue.setText(on ? R.string.ipc_motion_on : R.string.ipc_motion_off);
    }

    private void updateSensitivityUi(Object value) {
        String code = value == null ? "0" : String.valueOf(value);
        int labelRes;
        if ("1".equals(code)) {
            labelRes = R.string.ipc_motion_sens_mid;
        } else if ("2".equals(code)) {
            labelRes = R.string.ipc_motion_sens_high;
        } else {
            labelRes = R.string.ipc_motion_sens_low;
        }
        tvSensitivityValue.setText(getString(R.string.ipc_motion_sens_value, getString(labelRes)));
    }

    private void queryClassify() {
        cameraMessage.queryAlarmDetectionClassify(devId, new IThingResultCallback<List<CameraMessageClassifyBean>>() {
            @Override
            public void onSuccess(List<CameraMessageClassifyBean> result) {
                selectClassify = pickMotionClassify(result);
                if (selectClassify != null) {
                    String name = classifyName(selectClassify);
                    if (TextUtils.isEmpty(name)) {
                        name = MSG_MOTION;
                    }
                    tvClassifyHint.setText(getString(R.string.ipc_msg_classify, name));
                } else {
                    tvClassifyHint.setText(R.string.ipc_msg_enable_motion_hint);
                }
                loadTodayMessages();
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                Toast.makeText(CameraMessageActivity.this,
                        getString(R.string.error_with_code, errorCode, errorMessage),
                        Toast.LENGTH_SHORT).show();
                selectClassify = null;
                loadTodayMessages();
            }
        });
    }

    private String classifyName(CameraMessageClassifyBean bean) {
        try {
            Object value = bean.getClass().getMethod("getDescribe").invoke(bean);
            if (value != null && !TextUtils.isEmpty(String.valueOf(value))) {
                return String.valueOf(value);
            }
        } catch (Throwable ignored) {
        }
        try {
            Object value = bean.getClass().getMethod("getName").invoke(bean);
            if (value != null) {
                return String.valueOf(value);
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Prefer a classify that includes ipc_motion; otherwise first item.
     */
    private CameraMessageClassifyBean pickMotionClassify(List<CameraMessageClassifyBean> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (CameraMessageClassifyBean bean : list) {
            if (bean == null) {
                continue;
            }
            String[] codes = bean.getMsgCode();
            if (codes == null) {
                continue;
            }
            for (String code : codes) {
                if (MSG_MOTION.equals(code)) {
                    return bean;
                }
            }
        }
        return list.get(0);
    }

    private void loadTodayMessages() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        int startTime = (int) (calendar.getTimeInMillis() / 1000);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        int endTime = (int) (calendar.getTimeInMillis() / 1000) - 1;

        String[] msgCodes = selectClassify != null ? selectClassify.getMsgCode() : null;
        cameraMessage.getAlarmDetectionMessageList(devId, startTime, endTime,
                msgCodes, 0, 30,
                new IThingResultCallback<List<CameraMessageBean>>() {
                    @Override
                    public void onSuccess(List<CameraMessageBean> result) {
                        messageList.clear();
                        if (result != null) {
                            messageList.addAll(result);
                        }
                        adapter.submitList(messageList);
                        showEmpty(messageList.isEmpty());
                    }

                    @Override
                    public void onError(String errorCode, String errorMessage) {
                        Toast.makeText(CameraMessageActivity.this,
                                getString(R.string.error_with_code, errorCode, errorMessage),
                                Toast.LENGTH_SHORT).show();
                        showEmpty(true);
                    }
                });
    }

    private void openImage(CameraMessageBean bean) {
        String attachPics = bean.getAttachPics();
        if (TextUtils.isEmpty(attachPics)) {
            Toast.makeText(this, R.string.ipc_msg_image_missing, Toast.LENGTH_SHORT).show();
            return;
        }
        String imageUrl = attachPics;
        String encryptKey = "";
        if (attachPics.contains("@")) {
            int index = attachPics.lastIndexOf('@');
            imageUrl = attachPics.substring(0, index);
            encryptKey = attachPics.substring(index + 1);
        }
        Intent intent = new Intent(this, CameraMessageImageActivity.class);
        intent.putExtra(IpcConstants.EXTRA_DEV_ID, devId);
        intent.putExtra(IpcConstants.EXTRA_IMAGE_URL, imageUrl);
        intent.putExtra(IpcConstants.EXTRA_IMAGE_KEY, encryptKey);
        startActivity(intent);
    }

    private void openVideoIfNeeded(CameraMessageBean bean) {
        String[] videos = bean.getAttachVideos();
        if (videos == null || videos.length == 0 || TextUtils.isEmpty(videos[0])) {
            // No video: open image preview when available.
            if (!TextUtils.isEmpty(bean.getAttachPics())) {
                openImage(bean);
            } else {
                Toast.makeText(this, R.string.ipc_msg_pic_only, Toast.LENGTH_SHORT).show();
            }
            return;
        }
        String attachVideo = videos[0];
        String playUrl = attachVideo;
        String encryptKey = "";
        if (attachVideo.contains("@")) {
            int index = attachVideo.lastIndexOf('@');
            playUrl = attachVideo.substring(0, index);
            encryptKey = attachVideo.substring(index + 1);
        }
        Intent intent = new Intent(this, CameraVideoMessageActivity.class);
        intent.putExtra("playUrl", playUrl);
        intent.putExtra("encryptKey", encryptKey);
        intent.putExtra(IpcConstants.EXTRA_DEV_ID, devId);
        startActivity(intent);
    }

    private void deleteMessage(CameraMessageBean bean) {
        List<String> ids = Collections.singletonList(bean.getId());
        cameraMessage.deleteMotionMessageList(ids, new IThingResultCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                adapter.remove(bean);
                messageList.remove(bean);
                showEmpty(messageList.isEmpty());
                Toast.makeText(CameraMessageActivity.this, R.string.ipc_msg_deleted, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                Toast.makeText(CameraMessageActivity.this,
                        getString(R.string.error_with_code, errorCode, errorMessage),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showEmpty(boolean empty) {
        tvEmpty.setVisibility(empty ? View.VISIBLE : View.GONE);
    }

    private Object queryDp(String dpId) {
        if (TextUtils.isEmpty(dpId)) {
            return null;
        }
        DeviceBean bean = ThingHomeSdk.getDataInstance().getDeviceBean(devId);
        if (bean == null || bean.getDps() == null) {
            return null;
        }
        return bean.getDps().get(dpId);
    }

    private void publishDp(String dpId, Object value) {
        if (TextUtils.isEmpty(dpId)) {
            return;
        }
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
                runOnUiThread(() -> Toast.makeText(CameraMessageActivity.this,
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
        if (cameraMessage != null) {
            cameraMessage.destroy();
            cameraMessage = null;
        }
    }
}
