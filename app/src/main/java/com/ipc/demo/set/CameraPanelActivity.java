package com.ipc.demo.set;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;
import com.thingclips.smart.android.camera.sdk.ThingIPCSdk;
import com.thingclips.smart.android.camera.sdk.api.ICameraConfigInfo;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCCloud;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCCore;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCDoorbell;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCPTZ;
import com.thingclips.smart.android.camera.sdk.constant.PTZDPModel;
import com.thingclips.smart.call.module.api.IThingCallModule;
import com.thingclips.smart.call.module.api.bean.ThingCallError;
import com.thingclips.smart.camera.camerasdk.bean.ThingVideoSplitInfo;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.AbsP2pCameraListener;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.OperationDelegateCallBack;
import com.thingclips.smart.camera.ipccamerasdk.p2p.ICameraP2P;
import com.thingclips.smart.camera.middleware.p2p.IThingSmartCameraP2P;
import com.thingclips.smart.camera.middleware.widget.AbsVideoViewCallback;
import com.thingclips.smart.camera.middleware.widget.ThingCameraView;
import com.thingclips.smart.camera.middleware.widget.ThingMultiCameraView;
import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.home.sdk.callback.IThingResultCallback;
import com.thingclips.smart.sdk.api.IResultCallback;
import com.thingclips.smart.sdk.api.IThingDataCallback;
import com.thingclips.smart.sdk.bean.DeviceBean;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function1;

/**
 * Live preview with shrinked 16:9 view, fullscreen, pinch-zoom, PTZ and feature entries.
 */
public class CameraPanelActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CameraPanel";
    private static final int REQ_AUDIO = 1001;
    private static final int ASPECT_W = 16;
    private static final int ASPECT_H = 9;

    private String devId;
    private ThingCameraView videoView;
    private FrameLayout videoContainer;
    private View headerBar;
    private View actionBar;
    private View featureScroll;
    private View ptzBoard;
    private View fsActionBar;
    private View btnFsBack;
    private TextView tvZoomHint;
    private IThingSmartCameraP2P cameraP2P;
    private IThingIPCPTZ ptz;
    private TextView tvStatus;
    private TextView tvDeviceCaps;
    private MaterialButton btnMute;
    private MaterialButton btnClarity;
    private MaterialButton btnTalk;
    private MaterialButton btnVideoTalk;
    private MaterialButton btnSnapshot;
    private MaterialButton btnRecord;
    private MaterialButton btnCloud;
    private MaterialButton btnVas;
    private MaterialButton btnFsMute;
    private MaterialButton btnFsClarity;
    private MaterialButton btnFsTalk;
    private MaterialButton btnFsSnapshot;
    private MaterialButton btnFsRecord;

    private boolean speaking;
    private boolean recording;
    private boolean playing;
    private boolean reconnectTried;
    private boolean fullscreen;
    private boolean supportCloud;
    private boolean supportAudioTalk = true;
    private boolean supportVideoTalk;
    private boolean supportMultiCam;
    private boolean supportSd;
    private boolean supportPtz;
    private boolean lowPowerDevice;
    private boolean supportDoorbell;
    private int previewMute = ICameraP2P.MUTE;
    private int videoClarity = ICameraP2P.HD;
    private float scaleFactor = 1f;
    private ScaleGestureDetector scaleDetector;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private final AbsP2pCameraListener p2pListener = new AbsP2pCameraListener() {
        @Override
        public void onReceiveSpeakerEchoData(ByteBuffer pcm, int sampleRate) {
            if (cameraP2P == null || pcm == null) {
                return;
            }
            int length = pcm.capacity();
            byte[] pcmData = new byte[length];
            pcm.get(pcmData, 0, length);
            cameraP2P.sendAudioTalkData(pcmData, length);
        }

        @Override
        public void onSessionStatusChanged(Object camera, int sessionId, int sessionStatus) {
            super.onSessionStatusChanged(camera, sessionId, sessionStatus);
            if ((sessionStatus == -3 || sessionStatus == -105) && !reconnectTried && cameraP2P != null) {
                reconnectTried = true;
                cameraP2P.connect(devId, connectCallback);
            }
        }
    };

    private final OperationDelegateCallBack connectCallback = new OperationDelegateCallBack() {
        @Override
        public void onSuccess(int sessionId, int requestId, String data) {
            mainHandler.post(() -> {
                setStatus(getString(R.string.ipc_connected));
                startPreview();
            });
        }

        @Override
        public void onFailure(int sessionId, int requestId, int errCode) {
            mainHandler.post(() -> setStatus(getString(R.string.ipc_connect_fail, errCode)));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_panel);
        devId = getIntent().getStringExtra(IpcConstants.EXTRA_DEV_ID);
        if (TextUtils.isEmpty(devId)) {
            Toast.makeText(this, R.string.ipc_invalid_device, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        bindViews();
        setupZoom();
        initCamera();
        applyCapabilityUi();
        applyVideoSize();
    }

    private void bindViews() {
        findViewById(R.id.btnBack).setOnClickListener(v -> {
            if (fullscreen) {
                exitFullscreen();
            } else {
                finish();
            }
        });
        findViewById(R.id.btnFullscreen).setOnClickListener(v -> toggleFullscreen());
        headerBar = findViewById(R.id.headerBar);
        actionBar = findViewById(R.id.actionBar);
        featureScroll = findViewById(R.id.featureScroll);
        ptzBoard = findViewById(R.id.ptzBoard);
        videoContainer = findViewById(R.id.videoContainer);
        tvStatus = findViewById(R.id.tvStatus);
        tvDeviceCaps = findViewById(R.id.tvDeviceCaps);
        tvZoomHint = findViewById(R.id.tvZoomHint);
        videoView = findViewById(R.id.camera_video_view);
        fsActionBar = findViewById(R.id.fsActionBar);
        btnFsBack = findViewById(R.id.btnFsBack);
        btnMute = findViewById(R.id.btnMute);
        btnClarity = findViewById(R.id.btnClarity);
        btnTalk = findViewById(R.id.btnTalk);
        btnVideoTalk = findViewById(R.id.btnVideoTalk);
        btnSnapshot = findViewById(R.id.btnSnapshot);
        btnRecord = findViewById(R.id.btnRecord);
        btnCloud = findViewById(R.id.btnCloud);
        btnVas = findViewById(R.id.btnVas);
        btnFsMute = findViewById(R.id.btnFsMute);
        btnFsClarity = findViewById(R.id.btnFsClarity);
        btnFsTalk = findViewById(R.id.btnFsTalk);
        btnFsSnapshot = findViewById(R.id.btnFsSnapshot);
        btnFsRecord = findViewById(R.id.btnFsRecord);

        btnMute.setOnClickListener(this);
        btnClarity.setOnClickListener(this);
        btnTalk.setOnClickListener(this);
        btnVideoTalk.setOnClickListener(this);
        btnSnapshot.setOnClickListener(this);
        btnRecord.setOnClickListener(this);
        btnFsMute.setOnClickListener(this);
        btnFsClarity.setOnClickListener(this);
        btnFsTalk.setOnClickListener(this);
        btnFsSnapshot.setOnClickListener(this);
        btnFsRecord.setOnClickListener(this);
        btnFsBack.setOnClickListener(v -> exitFullscreen());
        findViewById(R.id.btnPtz).setOnClickListener(this);
        findViewById(R.id.btnSdManage).setOnClickListener(this);
        findViewById(R.id.btnPlayback).setOnClickListener(this);
        findViewById(R.id.btnMessage).setOnClickListener(this);
        findViewById(R.id.btnAlbum).setOnClickListener(this);
        btnCloud.setOnClickListener(this);
        btnVas.setOnClickListener(this);
        // 默认隐藏：仅设备能力确认后再展示
        btnCloud.setVisibility(View.GONE);
        btnVas.setVisibility(View.GONE);
        btnVideoTalk.setVisibility(View.GONE);
        bindPtzButtons();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void setupZoom() {
        scaleDetector = new ScaleGestureDetector(this, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                scaleFactor *= detector.getScaleFactor();
                scaleFactor = Math.max(1f, Math.min(scaleFactor, 4f));
                videoView.setScaleX(scaleFactor);
                videoView.setScaleY(scaleFactor);
                return true;
            }
        });
        final GestureDetector tapDetector = new GestureDetector(this,
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        scaleFactor = scaleFactor > 1.5f ? 1f : 3f;
                        videoView.setScaleX(scaleFactor);
                        videoView.setScaleY(scaleFactor);
                        return true;
                    }
                });
        videoContainer.setOnTouchListener((v, event) -> {
            scaleDetector.onTouchEvent(event);
            tapDetector.onTouchEvent(event);
            return true;
        });
    }

    private void bindPtzButtons() {
        View.OnTouchListener touch = (v, event) -> {
            if (ptz == null) {
                return false;
            }
            String dir = null;
            int id = v.getId();
            if (id == R.id.btnPtzUp) {
                dir = DPConstants.PTZ_UP;
            } else if (id == R.id.btnPtzDown) {
                dir = DPConstants.PTZ_DOWN;
            } else if (id == R.id.btnPtzLeft) {
                dir = DPConstants.PTZ_LEFT;
            } else if (id == R.id.btnPtzRight) {
                dir = DPConstants.PTZ_RIGHT;
            }
            if (dir == null) {
                return false;
            }
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                publishPtz(dir);
            } else if (event.getAction() == MotionEvent.ACTION_UP
                    || event.getAction() == MotionEvent.ACTION_CANCEL) {
                stopPtz();
            }
            return true;
        };
        findViewById(R.id.btnPtzUp).setOnTouchListener(touch);
        findViewById(R.id.btnPtzDown).setOnTouchListener(touch);
        findViewById(R.id.btnPtzLeft).setOnTouchListener(touch);
        findViewById(R.id.btnPtzRight).setOnTouchListener(touch);
        findViewById(R.id.btnPtzStop).setOnClickListener(v -> stopPtz());
    }

    private void publishPtz(String direction) {
        if (ptz == null) {
            return;
        }
        ptz.publishDps(PTZDPModel.DP_PTZ_CONTROL, direction, emptyResult());
    }

    private void stopPtz() {
        if (ptz == null) {
            return;
        }
        ptz.publishDps(PTZDPModel.DP_PTZ_STOP, true, emptyResult());
    }

    private IResultCallback emptyResult() {
        return new IResultCallback() {
            @Override
            public void onError(String code, String error) {
                toastFailMsg(error);
            }

            @Override
            public void onSuccess() {
            }
        };
    }

    private void applyVideoSize() {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        ViewGroup.LayoutParams lp = videoContainer.getLayoutParams();
        if (fullscreen || getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE) {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).weight = 1f;
            }
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
            lp.height = width * ASPECT_H / ASPECT_W;
            if (lp instanceof LinearLayout.LayoutParams) {
                ((LinearLayout.LayoutParams) lp).weight = 0f;
            }
        }
        videoContainer.setLayoutParams(lp);
    }

    private void toggleFullscreen() {
        if (fullscreen) {
            exitFullscreen();
        } else {
            enterFullscreen();
        }
    }

    private void enterFullscreen() {
        fullscreen = true;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        headerBar.setVisibility(View.GONE);
        tvStatus.setVisibility(View.GONE);
        if (tvDeviceCaps != null) {
            tvDeviceCaps.setVisibility(View.GONE);
        }
        actionBar.setVisibility(View.GONE);
        featureScroll.setVisibility(View.GONE);
        if (tvZoomHint != null) {
            tvZoomHint.setVisibility(View.GONE);
        }
        btnFsBack.setVisibility(View.VISIBLE);
        fsActionBar.setVisibility(View.VISIBLE);
        syncFsActionTexts();
        applyCapabilityUi();
        hideSystemUi();
        applyVideoSize();
    }

    private void exitFullscreen() {
        fullscreen = false;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        headerBar.setVisibility(View.VISIBLE);
        tvStatus.setVisibility(View.VISIBLE);
        updateCapabilityBadge();
        actionBar.setVisibility(View.VISIBLE);
        featureScroll.setVisibility(View.VISIBLE);
        if (tvZoomHint != null) {
            tvZoomHint.setVisibility(View.VISIBLE);
        }
        btnFsBack.setVisibility(View.GONE);
        fsActionBar.setVisibility(View.GONE);
        showSystemUi();
        applyVideoSize();
    }

    private void hideSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    private void showSystemUi() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyVideoSize();
    }

    @Override
    public void onBackPressed() {
        if (fullscreen) {
            exitFullscreen();
            return;
        }
        super.onBackPressed();
    }

    private void initCamera() {
        IThingIPCCore cameraInstance = ThingIPCSdk.getCameraInstance();
        if (cameraInstance == null) {
            Toast.makeText(this, R.string.not_ipc_device, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        // Video lock / doorbell may fail isIPCDevice(); allow P2P-capable devices.
        if (!IpcDeviceHelper.canOpenLivePreview(devId)) {
            Log.w(TAG, "canOpenLivePreview=false, try createCameraP2P anyway, devId=" + devId);
        }
        try {
            cameraP2P = cameraInstance.createCameraP2P(devId);
        } catch (Throwable t) {
            Log.e(TAG, "createCameraP2P failed", t);
            Toast.makeText(this, R.string.not_ipc_device, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (cameraP2P == null) {
            Toast.makeText(this, R.string.not_ipc_device, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        lowPowerDevice = cameraInstance.isLowPowerDevice(devId);
        ICameraConfigInfo config = null;
        try {
            config = cameraInstance.getCameraConfig(devId);
            if (config != null) {
                supportAudioTalk = config.isSupportSpeaker() || config.isSupportPickup();
                // 多目：https://developer.tuya.com/cn/docs/app-development/multiCam?id=Kdfpjpvv423vw
                supportMultiCam = config.isSupportVideoSegmentation();
            }
        } catch (Throwable t) {
            Log.w(TAG, "query camera config failed", t);
        }

        // 视频通话能力走带屏通话 API，勿用 isSupportChangeTalkBackMode
        // https://developer.tuya.com/cn/docs/app-development/videocall?id=Kdfiptfe3joyp
        supportVideoTalk = false;
        queryVideoCallSupport();
        queryDoorbellAbility();
        querySdSupport();

        setupPreviewSurface(config);

        ptz = ThingIPCSdk.getPTZInstance(devId);
        supportPtz = ptz != null && ptz.querySupportByDPCode(PTZDPModel.DP_PTZ_CONTROL);

        IThingIPCCloud cloud = ThingIPCSdk.getCloud();
        supportCloud = cloud != null && cloud.isSupportCloudStorage(devId);

        applyCapabilityUi();
        updateCapabilityBadge();
    }

    /**
     * Bind single or multi camera view, then create renderer.
     */
    private void setupPreviewSurface(ICameraConfigInfo config) {
        if (supportMultiCam && config != null) {
            ThingVideoSplitInfo splitInfo = config.getCameraVideoSegmentationModel();
            if (splitInfo != null) {
                ThingCameraView old = videoView;
                ThingMultiCameraView multi = new ThingMultiCameraView(this);
                multi.setLayoutParams(new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
                multi.setThingVideoSplitInfo(splitInfo);
                multi.setCameraViewWidth(getResources().getDisplayMetrics().widthPixels);
                videoContainer.addView(multi, 0);
                if (old != null) {
                    old.setVisibility(View.GONE);
                }
                videoView = multi;
                Log.i(TAG, "use ThingMultiCameraView for video segmentation");
            }
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
    }

    private void queryVideoCallSupport() {
        IThingCallModule videoCall = ThingIPCSdk.getVideoCall();
        if (videoCall == null) {
            supportVideoTalk = false;
            applyCapabilityUi();
            return;
        }
        VideoCallModuleHelper.ensureRegistered(getApplication());
        videoCall.fetchSupportVideoCall(devId, new IThingResultCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                supportVideoTalk = Boolean.TRUE.equals(result);
                mainHandler.post(() -> {
                    applyCapabilityUi();
                    updateCapabilityBadge();
                });
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                supportVideoTalk = false;
                Log.w(TAG, "fetchSupportVideoCall fail " + errorCode + " " + errorMessage);
                mainHandler.post(() -> applyCapabilityUi());
            }
        });
    }

    private void queryDoorbellAbility() {
        IThingIPCDoorbell doorbell = ThingIPCSdk.getDoorbell();
        if (doorbell == null) {
            supportDoorbell = false;
            return;
        }
        // https://developer.tuya.com/cn/docs/app-development/android-doorbell?id=Kalemt2dq2tjw
        doorbell.hasDoorbellAbility(devId, new IThingDataCallback<Boolean>() {
            @Override
            public void onSuccess(Boolean result) {
                supportDoorbell = Boolean.TRUE.equals(result);
                mainHandler.post(CameraPanelActivity.this::updateCapabilityBadge);
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                supportDoorbell = false;
            }
        });
    }

    private void querySdSupport() {
        supportSd = false;
        try {
            DeviceBean bean = ThingHomeSdk.getDataInstance().getDeviceBean(devId);
            if (bean != null && bean.getDps() != null
                    && bean.getDps().containsKey(DPConstants.SD_STATUS)) {
                supportSd = true;
            }
        } catch (Throwable t) {
            Log.w(TAG, "query sd support failed", t);
        }
    }

    private void applyCapabilityUi() {
        int talkVisibility = supportAudioTalk ? View.VISIBLE : View.GONE;
        btnTalk.setVisibility(talkVisibility);
        if (btnFsTalk != null) {
            btnFsTalk.setVisibility(talkVisibility);
        }
        // 仅支持带屏视频通话时展示
        int videoTalkVisibility = supportVideoTalk ? View.VISIBLE : View.GONE;
        btnVideoTalk.setVisibility(videoTalkVisibility);

        int cloudVisibility = supportCloud ? View.VISIBLE : View.GONE;
        btnCloud.setVisibility(cloudVisibility);
        btnVas.setVisibility(cloudVisibility);

        findViewById(R.id.btnPtz).setVisibility(supportPtz ? View.VISIBLE : View.GONE);
        int sdVisibility = supportSd ? View.VISIBLE : View.GONE;
        findViewById(R.id.btnSdManage).setVisibility(sdVisibility);
        findViewById(R.id.btnPlayback).setVisibility(sdVisibility);

        syncFsActionTexts();
    }

    private void updateCapabilityBadge() {
        if (tvDeviceCaps == null) {
            return;
        }
        List<String> tags = new ArrayList<>();
        if (supportMultiCam) {
            tags.add(getString(R.string.ipc_cap_multi));
        }
        if (lowPowerDevice) {
            tags.add(getString(R.string.ipc_cap_low_power));
        }
        if (supportDoorbell) {
            tags.add(getString(R.string.ipc_cap_doorbell));
        }
        if (supportVideoTalk) {
            tags.add(getString(R.string.ipc_cap_video_call));
        }
        if (tags.isEmpty()) {
            tvDeviceCaps.setVisibility(View.GONE);
        } else {
            tvDeviceCaps.setVisibility(View.VISIBLE);
            tvDeviceCaps.setText(TextUtils.join(" · ", tags));
        }
    }

    private void syncFsActionTexts() {
        int muteRes = previewMute == ICameraP2P.MUTE ? R.string.ipc_unmute : R.string.ipc_mute;
        int clarityRes = videoClarity == ICameraP2P.HD ? R.string.ipc_hd : R.string.ipc_sd;
        int talkRes = speaking ? R.string.ipc_talk_stop : R.string.ipc_talk;
        int recordRes = recording ? R.string.ipc_record_stop : R.string.ipc_record;
        btnMute.setText(muteRes);
        btnClarity.setText(clarityRes);
        btnTalk.setText(talkRes);
        btnRecord.setText(recordRes);
        if (btnFsMute != null) {
            btnFsMute.setText(muteRes);
            btnFsClarity.setText(clarityRes);
            btnFsTalk.setText(talkRes);
            btnFsRecord.setText(recordRes);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Recreate P2P if handoff to playback/cloud destroyed it.
        if (cameraP2P == null && !TextUtils.isEmpty(devId)) {
            IThingIPCCore cameraInstance = ThingIPCSdk.getCameraInstance();
            if (cameraInstance != null) {
                cameraP2P = cameraInstance.createCameraP2P(devId);
            }
        }
        if (videoView != null) {
            videoView.onResume();
        }
        if (cameraP2P == null) {
            return;
        }
        cameraP2P.registerP2PCameraListener(p2pListener);
        if (videoView.createdView() != null) {
            cameraP2P.generateCameraView(videoView.createdView());
        }
        if (cameraP2P.isConnecting()) {
            startPreview();
            return;
        }
        wakeIfLowPower();
        setStatus(getString(R.string.ipc_connecting));
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
        if (cameraP2P == null) {
            return;
        }
        cameraP2P.startPreview(videoClarity, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                playing = true;
                mainHandler.post(() -> setStatus(getString(R.string.ipc_previewing)));
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                playing = false;
                mainHandler.post(() -> setStatus(getString(R.string.ipc_preview_fail, errCode)));
            }
        });
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnMute || id == R.id.btnFsMute) {
            toggleMute();
        } else if (id == R.id.btnClarity || id == R.id.btnFsClarity) {
            toggleClarity();
        } else if (id == R.id.btnTalk || id == R.id.btnFsTalk) {
            toggleAudioTalk();
        } else if (id == R.id.btnVideoTalk) {
            toggleVideoTalk();
        } else if (id == R.id.btnSnapshot || id == R.id.btnFsSnapshot) {
            snapshot();
        } else if (id == R.id.btnRecord || id == R.id.btnFsRecord) {
            toggleRecord();
        } else if (id == R.id.btnPtz) {
            boolean show = ptzBoard.getVisibility() != View.VISIBLE;
            ptzBoard.setVisibility(show ? View.VISIBLE : View.GONE);
            MaterialButton ptzBtn = findViewById(R.id.btnPtz);
            ptzBtn.setText(show ? R.string.ipc_ptz_collapse : R.string.ipc_ptz_expand);
            if (show) {
                featureScroll.post(() -> featureScroll.scrollTo(0, ptzBoard.getTop()));
            }
        } else if (id == R.id.btnSdManage) {
            startFeature(CameraSdManageActivity.class);
        } else if (id == R.id.btnPlayback) {
            startFeature(CameraPlaybackActivity.class);
        } else if (id == R.id.btnMessage) {
            startFeature(CameraMessageActivity.class);
        } else if (id == R.id.btnAlbum) {
            startFeature(CameraAlbumActivity.class);
        } else if (id == R.id.btnCloud) {
            openCloud();
        } else if (id == R.id.btnVas) {
            openVas();
        }
    }

    private void startFeature(Class<?> cls) {
        // Live + playback cannot share one device P2P session. Release before SD/cloud pages.
        if (cls == CameraPlaybackActivity.class || cls == CameraCloudStorageActivity.class) {
            releaseCameraThenStart(cls);
            return;
        }
        Intent intent = new Intent(this, cls);
        intent.putExtra(IpcConstants.EXTRA_DEV_ID, devId);
        startActivity(intent);
    }

    /**
     * Stop live session then open feature. Avoid sync destroyP2P on UI (can ANR).
     */
    private void releaseCameraThenStart(Class<?> cls) {
        Intent intent = new Intent(this, cls);
        intent.putExtra(IpcConstants.EXTRA_DEV_ID, devId);
        if (cameraP2P == null) {
            startActivity(intent);
            return;
        }
        final IThingSmartCameraP2P p2p = cameraP2P;
        cameraP2P = null;
        speaking = false;
        playing = false;
        final java.util.concurrent.atomic.AtomicBoolean started =
                new java.util.concurrent.atomic.AtomicBoolean(false);
        final Runnable go = () -> {
            if (!started.compareAndSet(false, true)) {
                return;
            }
            try {
                p2p.destroyP2P();
            } catch (Throwable t) {
                Log.w(TAG, "destroyP2P after handoff", t);
            }
            startActivity(intent);
        };
        try {
            p2p.stopAudioTalk(null);
            p2p.stopPreview(null);
            p2p.removeOnP2PCameraListener();
            p2p.disconnect(new OperationDelegateCallBack() {
                @Override
                public void onSuccess(int sessionId, int requestId, String data) {
                    mainHandler.post(go);
                }

                @Override
                public void onFailure(int sessionId, int requestId, int errCode) {
                    mainHandler.post(go);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "releaseCameraThenStart", t);
            mainHandler.post(go);
        }
        // Fallback if disconnect callback never arrives.
        mainHandler.postDelayed(go, 1200);
    }

    private void openCloud() {
        if (!supportCloud) {
            Toast.makeText(this, R.string.ipc_cloud_unsupported, Toast.LENGTH_SHORT).show();
            btnCloud.setVisibility(View.GONE);
            btnVas.setVisibility(View.GONE);
            return;
        }
        startFeature(CameraCloudStorageActivity.class);
    }

    /**
     * 增值服务：获取 VAS URL 后走 MiniApp 容器打开。
     * https://developer.tuya.com/cn/docs/app-development/ipc-value-added-service-2?id=Ke2iaqr2xoyz5
     */
    private void openVas() {
        if (!supportCloud) {
            Toast.makeText(this, R.string.ipc_cloud_unsupported, Toast.LENGTH_SHORT).show();
            btnVas.setVisibility(View.GONE);
            return;
        }
        try {
            Object vas = ThingIPCSdk.class.getMethod("getIPCVAS").invoke(null);
            if (vas == null) {
                Toast.makeText(this, R.string.ipc_vas_fallback, Toast.LENGTH_SHORT).show();
                openCloud();
                return;
            }
            Class<?> paramsClz = Class.forName("com.thingclips.smart.android.camera.sdk.bean.CameraVASParams");
            Object params = paramsClz.getDeclaredConstructor().newInstance();
            paramsClz.getField("devId").set(params, devId);
            paramsClz.getField("spaceId").set(params, String.valueOf(HomeModel.getCurrentHome(this)));
            paramsClz.getField("languageCode").set(params, "zh");
            try {
                Class<?> constants = Class.forName("com.thingclips.smart.android.camera.sdk.constant.ThingIPCConstant");
                paramsClz.getField("categoryCode").set(params,
                        constants.getField("CATEGORY_CODE_SECURITY_CLOUD_SERVICE").get(null));
                paramsClz.getField("hybridType").set(params,
                        constants.getField("HYBRID_TYPE_MINI_APP").get(null));
            } catch (Throwable ignored) {
            }
            Class<?> callbackClz = Class.forName(
                    "com.thingclips.smart.camera.camerasdk.thingplayer.callback.OperationCallBack");
            Object callback = java.lang.reflect.Proxy.newProxyInstance(
                    callbackClz.getClassLoader(),
                    new Class[]{callbackClz},
                    (proxy, method, args) -> {
                        if ("onSuccess".equals(method.getName()) && args != null && args.length >= 3) {
                            String url = String.valueOf(args[2]);
                            mainHandler.post(() -> openMiniAppUrl(url));
                        } else if ("onFailure".equals(method.getName())) {
                            mainHandler.post(() -> {
                                Toast.makeText(this, R.string.ipc_vas_fallback, Toast.LENGTH_SHORT).show();
                                openCloud();
                            });
                        }
                        return null;
                    });
            vas.getClass().getMethod("fetchValueAddedServiceUrl", paramsClz, callbackClz)
                    .invoke(vas, params, callback);
        } catch (Throwable t) {
            Log.w(TAG, "open VAS failed", t);
            Toast.makeText(this, R.string.ipc_vas_fallback, Toast.LENGTH_SHORT).show();
            openCloud();
        }
    }

    private void openMiniAppUrl(String url) {
        if (TextUtils.isEmpty(url) || "null".equals(url)) {
            openCloud();
            return;
        }
        // 优先 UrlRouter / MiniAppClient，失败再尝试 openMiniAppByUrl
        try {
            Class<?> routerClz = Class.forName("com.thingclips.smart.api.router.UrlRouter");
            Class<?> builderClz = Class.forName("com.thingclips.smart.api.router.UrlBuilder");
            Object builder = builderClz.getConstructor(android.content.Context.class, String.class)
                    .newInstance(this, "miniApp");
            android.os.Bundle extras = new android.os.Bundle();
            extras.putString("url", url);
            builder = builderClz.getMethod("putExtras", android.os.Bundle.class).invoke(builder, extras);
            routerClz.getMethod("execute", builderClz).invoke(null, builder);
            return;
        } catch (Throwable ignored) {
        }
        try {
            Class<?> clientClz = Class.forName("com.thingclips.smart.miniapp.MiniAppClient");
            Object core = clientClz.getMethod("coreClient").invoke(null);
            core.getClass().getMethod("openMiniAppByUrl",
                            android.content.Context.class, String.class, android.os.Bundle.class)
                    .invoke(core, this, url, null);
            return;
        } catch (Throwable t) {
            Log.w(TAG, "openMiniAppByUrl failed", t);
        }
        openUrlOrCloud(url);
    }

    private void openUrlOrCloud(String url) {
        if (TextUtils.isEmpty(url)) {
            openCloud();
            return;
        }
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            openCloud();
        }
    }

    private void toggleMute() {
        if (cameraP2P == null) {
            return;
        }
        int mute = previewMute == ICameraP2P.MUTE ? ICameraP2P.UNMUTE : ICameraP2P.MUTE;
        cameraP2P.setMute(mute, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                previewMute = Integer.parseInt(data);
                mainHandler.post(CameraPanelActivity.this::syncFsActionTexts);
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                toastFail(errCode);
            }
        });
    }

    private void toggleClarity() {
        if (cameraP2P == null) {
            return;
        }
        int target = videoClarity == ICameraP2P.HD ? ICameraP2P.STANDEND : ICameraP2P.HD;
        cameraP2P.setVideoClarity(target, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                videoClarity = Integer.parseInt(data);
                mainHandler.post(CameraPanelActivity.this::syncFsActionTexts);
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                toastFail(errCode);
            }
        });
    }

    private void toggleAudioTalk() {
        if (cameraP2P == null) {
            return;
        }
        if (speaking) {
            cameraP2P.stopAudioTalk(new OperationDelegateCallBack() {
                @Override
                public void onSuccess(int sessionId, int requestId, String data) {
                    speaking = false;
                    mainHandler.post(CameraPanelActivity.this::syncFsActionTexts);
                }

                @Override
                public void onFailure(int sessionId, int requestId, int errCode) {
                    speaking = false;
                    mainHandler.post(CameraPanelActivity.this::syncFsActionTexts);
                }
            });
            return;
        }
        if (!ensureAudioPermission()) {
            return;
        }
        cameraP2P.startAudioTalk(new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                speaking = true;
                mainHandler.post(() -> {
                    syncFsActionTexts();
                    Toast.makeText(CameraPanelActivity.this, R.string.ipc_talk_on, Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                speaking = false;
                toastFail(errCode);
            }
        });
    }

    private void toggleVideoTalk() {
        if (!supportVideoTalk) {
            Toast.makeText(this, R.string.ipc_video_talk_unsupported, Toast.LENGTH_SHORT).show();
            btnVideoTalk.setVisibility(View.GONE);
            return;
        }
        IThingCallModule videoCall = ThingIPCSdk.getVideoCall();
        if (videoCall == null) {
            Toast.makeText(this, R.string.ipc_video_talk_unsupported, Toast.LENGTH_SHORT).show();
            return;
        }
        if (videoCall.isCalling()) {
            Toast.makeText(this, getString(R.string.ipc_video_call_fail, "BUSY"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!ensureAudioPermission()) {
            return;
        }
        VideoCallModuleHelper.ensureRegistered(getApplication());
        Map<String, Object> extra = new HashMap<>();
        extra.put("bizType", "screen_ipc");
        extra.put("channelType", 2);
        extra.put("category", "sp_dpsxj");
        extra.put("keepConnect", false);
        Toast.makeText(this, R.string.ipc_video_call_start, Toast.LENGTH_SHORT).show();
        videoCall.launchCall(devId, 30L, extra,
                new Function0<Unit>() {
                    @Override
                    public Unit invoke() {
                        return Unit.INSTANCE;
                    }
                },
                new Function1<ThingCallError, Unit>() {
                    @Override
                    public Unit invoke(ThingCallError error) {
                        final String msg = error != null ? error.name() : "unknown";
                        mainHandler.post(() -> Toast.makeText(CameraPanelActivity.this,
                                getString(R.string.ipc_video_call_fail, msg),
                                Toast.LENGTH_SHORT).show());
                        return Unit.INSTANCE;
                    }
                });
    }

    private void snapshot() {
        if (cameraP2P == null) {
            return;
        }
        File dir = IpcLocalMediaHelper.ensureDeviceMediaDir(this, devId);
        if (dir == null) {
            toastFail(-1);
            return;
        }
        String fileName = System.currentTimeMillis() + ".jpg";
        cameraP2P.snapshot(dir.getAbsolutePath(), fileName, this, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                mainHandler.post(() -> Toast.makeText(CameraPanelActivity.this,
                        R.string.ipc_snapshot_ok, Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                toastFail(errCode);
            }
        });
    }

    private void toggleRecord() {
        if (cameraP2P == null) {
            return;
        }
        File dir = IpcLocalMediaHelper.ensureDeviceMediaDir(this, devId);
        if (dir == null) {
            toastFail(-1);
            return;
        }
        if (recording) {
            cameraP2P.stopRecordLocalMp4(new OperationDelegateCallBack() {
                @Override
                public void onSuccess(int sessionId, int requestId, String data) {
                    recording = false;
                    mainHandler.post(() -> {
                        syncFsActionTexts();
                        Toast.makeText(CameraPanelActivity.this,
                                R.string.ipc_record_ok, Toast.LENGTH_SHORT).show();
                    });
                }

                @Override
                public void onFailure(int sessionId, int requestId, int errCode) {
                    recording = false;
                    mainHandler.post(CameraPanelActivity.this::syncFsActionTexts);
                }
            });
            return;
        }
        String fileName = System.currentTimeMillis() + ".mp4";
        cameraP2P.startRecordLocalMp4(dir.getAbsolutePath(), fileName, this, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                recording = true;
                mainHandler.post(CameraPanelActivity.this::syncFsActionTexts);
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                toastFail(errCode);
            }
        });
    }

    private boolean ensureAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, REQ_AUDIO);
        return false;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_AUDIO && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toggleAudioTalk();
        }
    }

    private void setStatus(String text) {
        tvStatus.setText(text);
    }

    private void toastFail(int errCode) {
        mainHandler.post(() -> Toast.makeText(this,
                getString(R.string.ipc_operate_fail, errCode), Toast.LENGTH_SHORT).show());
    }

    private void toastFailMsg(String msg) {
        mainHandler.post(() -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (videoView != null) {
            videoView.onPause();
        }
        if (cameraP2P == null) {
            return;
        }
        if (speaking) {
            cameraP2P.stopAudioTalk(null);
            speaking = false;
        }
        if (playing) {
            cameraP2P.stopPreview(null);
            playing = false;
        }
        cameraP2P.removeOnP2PCameraListener();
        cameraP2P.disconnect(null);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraP2P != null) {
            cameraP2P.destroyP2P();
            cameraP2P = null;
        }
    }
}
