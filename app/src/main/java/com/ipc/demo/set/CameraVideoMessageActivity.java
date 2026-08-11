package com.ipc.demo.set;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.alibaba.fastjson.JSONObject;
import com.google.android.material.button.MaterialButton;
import com.thingclips.smart.android.camera.sdk.ThingIPCSdk;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCMsg;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.AbsP2pCameraListener;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.IRegistorIOTCListener;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.OperationCallBack;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.OperationDelegateCallBack;
import com.thingclips.smart.camera.ipccamerasdk.msgvideo.IThingCloudVideo;
import com.thingclips.smart.camera.ipccamerasdk.p2p.ICameraP2P;
import com.thingclips.smart.camera.middleware.widget.ThingCameraView;

/**
 * Play encrypted video attached to a camera alarm message.
 */
public class CameraVideoMessageActivity extends AppCompatActivity {

    private ThingCameraView cameraView;
    private ProgressBar progressBar;
    private MaterialButton btnMute;
    private IThingCloudVideo cloudVideo;
    private String playUrl;
    private String encryptKey;
    private String devId;
    private int muteState = ICameraP2P.MUTE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_video_message);

        playUrl = getIntent().getStringExtra("playUrl");
        encryptKey = getIntent().getStringExtra("encryptKey");
        devId = getIntent().getStringExtra(IpcConstants.EXTRA_DEV_ID);
        if (TextUtils.isEmpty(playUrl) || TextUtils.isEmpty(devId)) {
            Toast.makeText(this, R.string.ipc_invalid_device, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        if (encryptKey == null) {
            encryptKey = "";
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        cameraView = findViewById(R.id.camera_video_view);
        progressBar = findViewById(R.id.progressBar);
        btnMute = findViewById(R.id.btnMute);
        btnMute.setText(R.string.ipc_unmute);

        findViewById(R.id.btnPause).setOnClickListener(v -> {
            if (cloudVideo != null) {
                cloudVideo.pauseVideo(null);
            }
        });
        findViewById(R.id.btnResume).setOnClickListener(v -> {
            if (cloudVideo != null) {
                cloudVideo.resumeVideo(null);
            }
        });
        btnMute.setOnClickListener(v -> toggleMute());

        cameraView.createVideoView(devId);
        initPlayer();
    }

    private void initPlayer() {
        IThingIPCMsg message = ThingIPCSdk.getMessage();
        if (message == null) {
            Toast.makeText(this, R.string.ipc_operate_fail_generic, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        cloudVideo = message.createVideoMessagePlayer();
        if (cloudVideo == null) {
            Toast.makeText(this, R.string.ipc_operate_fail_generic, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        cloudVideo.registerP2PCameraListener(new AbsP2pCameraListener() {
        });
        Object created = cameraView.createdView();
        if (created instanceof IRegistorIOTCListener) {
            cloudVideo.generateCloudCameraView((IRegistorIOTCListener) created);
        }
        String cachePath = getApplication().getCacheDir().getPath();
        cloudVideo.createCloudDevice(cachePath, devId, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                runOnUiThread(() -> startPlay());
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(CameraVideoMessageActivity.this,
                            getString(R.string.ipc_operate_fail, errCode), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void startPlay() {
        if (cloudVideo == null) {
            return;
        }
        Object created = cameraView.createdView();
        if (created instanceof IRegistorIOTCListener) {
            cloudVideo.generateCloudCameraView((IRegistorIOTCListener) created);
        }
        cloudVideo.playVideo(playUrl, 0, encryptKey, new OperationCallBack() {
            @Override
            public void onSuccess(int i, int i1, String s, Object o) {
                runOnUiThread(() -> progressBar.setVisibility(View.GONE));
            }

            @Override
            public void onFailure(int i, int i1, int i2, Object o) {
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(CameraVideoMessageActivity.this,
                            getString(R.string.ipc_operate_fail, i2), Toast.LENGTH_SHORT).show();
                });
            }
        }, new OperationCallBack() {
            @Override
            public void onSuccess(int i, int i1, String s, Object o) {
            }

            @Override
            public void onFailure(int i, int i1, int i2, Object o) {
            }
        });
    }

    private void toggleMute() {
        if (cloudVideo == null) {
            return;
        }
        int next = muteState == ICameraP2P.MUTE ? ICameraP2P.UNMUTE : ICameraP2P.MUTE;
        cloudVideo.setCloudVideoMute(next, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                try {
                    JSONObject json = JSONObject.parseObject(data);
                    Object value = json.get("mute");
                    muteState = Integer.parseInt(String.valueOf(value));
                } catch (Exception e) {
                    muteState = next;
                }
                runOnUiThread(() -> btnMute.setText(muteState == ICameraP2P.MUTE
                        ? R.string.ipc_unmute : R.string.ipc_mute));
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                runOnUiThread(() -> Toast.makeText(CameraVideoMessageActivity.this,
                        getString(R.string.ipc_operate_fail, errCode), Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cloudVideo != null) {
            cloudVideo.stopVideo(null);
            cloudVideo.removeOnDelegateP2PCameraListener();
            cloudVideo.deinitCloudVideo();
            cloudVideo = null;
        }
    }
}
