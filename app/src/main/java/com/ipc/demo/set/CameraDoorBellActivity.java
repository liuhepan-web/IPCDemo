package com.ipc.demo.set;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.thingclips.smart.android.camera.sdk.ThingIPCSdk;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCCore;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCDoorBellManager;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCDoorbell;
import com.thingclips.smart.android.camera.sdk.bean.ThingDoorBellCallModel;
import com.thingclips.smart.android.camera.sdk.callback.ThingSmartDoorBellObserver;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.AbsP2pCameraListener;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.OperationDelegateCallBack;
import com.thingclips.smart.camera.ipccamerasdk.p2p.ICameraP2P;
import com.thingclips.smart.camera.middleware.p2p.IThingSmartCameraP2P;
import com.thingclips.smart.camera.middleware.widget.AbsVideoViewCallback;
import com.thingclips.smart.camera.middleware.widget.ThingCameraView;
import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.sdk.bean.DeviceBean;

/**
 * Incoming doorbell / video-lock answer page with live preview after accept.
 * Docs: https://developer.tuya.com/cn/docs/app-development/android-doorbell?id=Kalemt2dq2tjw
 */
public class CameraDoorBellActivity extends AppCompatActivity {

    private static final String TAG = "CameraDoorBell";

    private String messageId;
    private String devId;
    private IThingIPCDoorBellManager doorBellManager;
    private TextView tvState;
    private MaterialButton btnAccept;
    private MaterialButton btnRefuse;
    private ThingCameraView videoView;
    private IThingSmartCameraP2P cameraP2P;
    private boolean answered;
    private boolean playing;
    private boolean destroyed;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final ThingSmartDoorBellObserver observer = new ThingSmartDoorBellObserver() {
        @Override
        public void doorBellCallDidCanceled(ThingDoorBellCallModel callModel, boolean isTimeOut) {
            Toast.makeText(CameraDoorBellActivity.this,
                    isTimeOut ? R.string.ipc_call_timeout : R.string.ipc_call_canceled,
                    Toast.LENGTH_LONG).show();
            finish();
        }

        @Override
        public void doorBellCallDidHangUp(ThingDoorBellCallModel callModel) {
            Toast.makeText(CameraDoorBellActivity.this, R.string.ipc_call_hangup, Toast.LENGTH_LONG).show();
            finish();
        }

        @Override
        public void doorBellCallDidAnsweredByOther(ThingDoorBellCallModel callModel) {
            Toast.makeText(CameraDoorBellActivity.this, R.string.ipc_call_answered_other, Toast.LENGTH_LONG).show();
            if (doorBellManager != null) {
                doorBellManager.refuseDoorBellCall(messageId);
            }
            finish();
        }
    };

    private final AbsP2pCameraListener p2pListener = new AbsP2pCameraListener() {
    };

    private final OperationDelegateCallBack connectCallback = new OperationDelegateCallBack() {
        @Override
        public void onSuccess(int sessionId, int requestId, String data) {
            mainHandler.post(() -> {
                if (!destroyed) {
                    startPreview();
                }
            });
        }

        @Override
        public void onFailure(int sessionId, int requestId, int errCode) {
            mainHandler.post(() -> {
                if (!destroyed) {
                    tvState.setText(getString(R.string.ipc_connect_fail, errCode));
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_doorbell);
        messageId = getIntent().getStringExtra(IpcConstants.EXTRA_MSG_ID);
        devId = getIntent().getStringExtra(IpcConstants.EXTRA_DEV_ID);
        // #region agent log
        try {
            AgentDebugLog.log("E", "CameraDoorBellActivity.onCreate", "onCreate",
                    new org.json.JSONObject()
                            .put("messageIdEmpty", TextUtils.isEmpty(messageId))
                            .put("messageId", messageId != null ? messageId : "")
                            .put("devId", devId != null ? devId : "")
                            .put("doorbellNull", ThingIPCSdk.getDoorbell() == null));
        } catch (Throwable ignored) {
        }
        // #endregion
        if (TextUtils.isEmpty(messageId) || ThingIPCSdk.getDoorbell() == null) {
            // #region agent log
            try {
                AgentDebugLog.log("E", "CameraDoorBellActivity.onCreate", "finish_early",
                        new org.json.JSONObject()
                                .put("reason", TextUtils.isEmpty(messageId) ? "empty_msgid" : "doorbell_null"));
            } catch (Throwable ignored) {
            }
            // #endregion
            finish();
            return;
        }
        doorBellManager = ThingIPCSdk.getDoorbell().getIPCDoorBellManagerInstance();
        if (doorBellManager == null) {
            finish();
            return;
        }
        doorBellManager.addObserver(observer);

        tvState = findViewById(R.id.tvState);
        btnAccept = findViewById(R.id.btnAccept);
        btnRefuse = findViewById(R.id.btnRefuse);
        videoView = findViewById(R.id.camera_video_view);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        ThingDoorBellCallModel model = doorBellManager.getCallModelByMessageId(messageId);
        if (model != null && TextUtils.isEmpty(devId)) {
            devId = model.getDevId();
        }
        String name = devId;
        DeviceBean deviceBean = ThingHomeSdk.getDataInstance().getDeviceBean(devId);
        if (deviceBean != null && !TextUtils.isEmpty(deviceBean.getName())) {
            name = deviceBean.getName();
        }
        tvState.setText(getString(R.string.ipc_call_incoming, name));

        btnRefuse.setOnClickListener(v -> {
            if (isAnsweredBySelf() || answered) {
                doorBellManager.hangupDoorBellCall(messageId);
            } else {
                doorBellManager.refuseDoorBellCall(messageId);
            }
            finish();
        });
        btnAccept.setOnClickListener(v -> acceptCall());
    }

    private void acceptCall() {
        if (answered) {
            return;
        }
        answered = true;
        doorBellManager.answerDoorBellCall(messageId);
        tvState.setText(R.string.ipc_call_answered);
        btnAccept.setVisibility(View.GONE);
        btnRefuse.setText(R.string.ipc_call_hangup);
        startLivePreview();
    }

    /**
     * Start P2P live preview on this page (do not jump to CameraPanel).
     */
    private void startLivePreview() {
        if (TextUtils.isEmpty(devId)) {
            tvState.setText(R.string.ipc_invalid_device);
            return;
        }
        IThingIPCCore cameraInstance = ThingIPCSdk.getCameraInstance();
        if (cameraInstance == null) {
            tvState.setText(R.string.not_ipc_device);
            return;
        }
        // Video lock / doorbell may not be isIPCDevice; still try P2P.
        if (!IpcDeviceHelper.canOpenLivePreview(devId)) {
            Log.w(TAG, "canOpenLivePreview=false, still try createCameraP2P, devId=" + devId);
        }
        try {
            cameraP2P = cameraInstance.createCameraP2P(devId);
        } catch (Throwable t) {
            Log.e(TAG, "createCameraP2P failed", t);
            tvState.setText(R.string.not_ipc_device);
            return;
        }
        if (cameraP2P == null) {
            tvState.setText(R.string.not_ipc_device);
            return;
        }
        videoView.setViewCallback(new AbsVideoViewCallback() {
            @Override
            public void onCreated(Object view) {
                super.onCreated(view);
                if (cameraP2P != null) {
                    cameraP2P.generateCameraView(view);
                }
            }
        });
        videoView.createVideoView(devId);
        wakeIfLowPower();
        tvState.setText(R.string.ipc_connecting);
        cameraP2P.registerP2PCameraListener(p2pListener);
        if (videoView.createdView() != null) {
            cameraP2P.generateCameraView(videoView.createdView());
        }
        cameraP2P.connect(devId, connectCallback);
    }

    private void wakeIfLowPower() {
        IThingIPCCore cameraInstance = ThingIPCSdk.getCameraInstance();
        if (cameraInstance != null && cameraInstance.isLowPowerDevice(devId)) {
            IThingIPCDoorbell doorbell = ThingIPCSdk.getDoorbell();
            if (doorbell != null) {
                doorbell.wirelessWake(devId);
            }
        }
    }

    private void startPreview() {
        if (cameraP2P == null || destroyed) {
            return;
        }
        cameraP2P.startPreview(ICameraP2P.HD, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                playing = true;
                mainHandler.post(() -> {
                    if (!destroyed) {
                        tvState.setText(R.string.ipc_previewing);
                    }
                });
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                playing = false;
                mainHandler.post(() -> {
                    if (!destroyed) {
                        tvState.setText(getString(R.string.ipc_preview_fail, errCode));
                    }
                });
            }
        });
    }

    private boolean isAnsweredBySelf() {
        ThingDoorBellCallModel callModel = doorBellManager.getCallModelByMessageId(messageId);
        return callModel != null && callModel.isAnsweredBySelf();
    }

    private void releasePreview() {
        if (cameraP2P == null) {
            return;
        }
        try {
            if (playing) {
                cameraP2P.stopPreview(null);
                playing = false;
            }
            cameraP2P.removeOnP2PCameraListener();
            cameraP2P.disconnect(null);
            cameraP2P.destroyP2P();
        } catch (Throwable t) {
            Log.w(TAG, "releasePreview error", t);
        }
        cameraP2P = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (videoView != null) {
            videoView.onResume();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) {
            videoView.onPause();
        }
    }

    @Override
    protected void onDestroy() {
        destroyed = true;
        releasePreview();
        if (doorBellManager != null) {
            doorBellManager.removeObserver(observer);
        }
        super.onDestroy();
    }
}
