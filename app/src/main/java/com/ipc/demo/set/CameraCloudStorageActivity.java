package com.ipc.demo.set;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.thingclips.smart.android.camera.sdk.ThingIPCSdk;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCCloud;
import com.thingclips.smart.android.camera.sdk.bean.CloudStatusBean;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.AbsP2pCameraListener;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.IRegistorIOTCListener;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.OperationCallBack;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.OperationDelegateCallBack;
import com.thingclips.smart.camera.ipccamerasdk.cloud.IThingCloudCamera;
import com.thingclips.smart.camera.middleware.cloud.bean.CloudDayBean;
import com.thingclips.smart.camera.middleware.cloud.bean.TimePieceBean;
import com.thingclips.smart.camera.middleware.widget.AbsVideoViewCallback;
import com.thingclips.smart.camera.middleware.widget.ThingCameraView;
import com.thingclips.smart.home.sdk.callback.IThingResultCallback;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Cloud storage playback. Opened from panel when device supports cloud.
 */
public class CameraCloudStorageActivity extends AppCompatActivity {

    public static final int NO_SERVICE = 10001;
    public static final int SERVES_RUNNING = 10010;
    public static final int SERVES_EXPIRED = 10011;

    private String devId;
    private IThingCloudCamera cloudCamera;
    private ThingCameraView cameraView;
    private TextView tvStatus;
    private View actionBar;
    private MaterialButton btnMute;
    private RecyclerView rvDays;
    private RecyclerView rvTimePieces;

    private final List<CloudDayBean> dayBeanList = new ArrayList<>();
    private final List<TimePieceBean> timePieceBeans = new ArrayList<>();
    private DayAdapter dayAdapter;
    private TimeAdapter timeAdapter;
    private int soundState = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_cloud_storage);
        devId = getIntent().getStringExtra(IpcConstants.EXTRA_DEV_ID);
        if (TextUtils.isEmpty(devId)) {
            Toast.makeText(this, R.string.ipc_invalid_device, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        cameraView = findViewById(R.id.camera_video_view);
        tvStatus = findViewById(R.id.tvStatus);
        actionBar = findViewById(R.id.actionBar);
        btnMute = findViewById(R.id.btnMute);
        rvDays = findViewById(R.id.rvDays);
        rvTimePieces = findViewById(R.id.rvTimePieces);

        applyVideoAspect();
        initLists();
        initButtons();
        initCloud();
    }

    private void applyVideoAspect() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        int width = wm.getDefaultDisplay().getWidth();
        int height = width * 9 / 16;
        FrameLayout container = findViewById(R.id.videoContainer);
        ViewGroup.LayoutParams lp = container.getLayoutParams();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = height;
        container.setLayoutParams(lp);
    }

    private void initLists() {
        dayAdapter = new DayAdapter();
        rvDays.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvDays.setAdapter(dayAdapter);

        timeAdapter = new TimeAdapter();
        rvTimePieces.setLayoutManager(new LinearLayoutManager(this));
        rvTimePieces.setAdapter(timeAdapter);
    }

    private void initButtons() {
        btnMute.setText(R.string.ipc_unmute);
        findViewById(R.id.btnQuery).setOnClickListener(v -> queryCloudDays());
        findViewById(R.id.btnPause).setOnClickListener(v -> {
            if (cloudCamera != null) {
                cloudCamera.pausePlayCloudVideo(emptyDelegate());
            }
        });
        findViewById(R.id.btnResume).setOnClickListener(v -> {
            if (cloudCamera != null) {
                cloudCamera.resumePlayCloudVideo(emptyDelegate());
            }
        });
        findViewById(R.id.btnStop).setOnClickListener(v -> {
            if (cloudCamera != null) {
                cloudCamera.stopPlayCloudVideo(emptyDelegate());
            }
        });
        btnMute.setOnClickListener(v -> setMute(soundState == 0 ? 1 : 0));
    }

    private void initCloud() {
        IThingIPCCloud cloud = ThingIPCSdk.getCloud();
        if (cloud != null) {
            cloudCamera = cloud.createCloudCamera();
        }
        if (cloudCamera == null) {
            Toast.makeText(this, R.string.ipc_operate_fail_generic, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        cameraView.setViewCallback(new AbsVideoViewCallback() {
            @Override
            public void onCreated(Object o) {
                super.onCreated(o);
                if (o instanceof IRegistorIOTCListener && cloudCamera != null) {
                    cloudCamera.generateCloudCameraView((IRegistorIOTCListener) o);
                }
            }
        });
        cameraView.createVideoView(devId);

        String cachePath = getApplication().getCacheDir().getPath();
        cloudCamera.createCloudDevice(cachePath, devId);

        cloudCamera.queryCloudServiceStatus(devId, new IThingResultCallback<CloudStatusBean>() {
            @Override
            public void onSuccess(CloudStatusBean result) {
                String statusText = getServiceStatus(result.getStatus());
                tvStatus.setText(getString(R.string.ipc_cloud_status, statusText));
                if (result.getStatus() == SERVES_EXPIRED || result.getStatus() == SERVES_RUNNING) {
                    actionBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                Toast.makeText(CameraCloudStorageActivity.this,
                        getString(R.string.error_with_code, errorCode, errorMessage),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void queryCloudDays() {
        if (cloudCamera == null) {
            return;
        }
        TimeZone timeZone = TimeZone.getDefault();
        cloudCamera.getCloudDays(devId, timeZone.getID(), new IThingResultCallback<List<CloudDayBean>>() {
            @Override
            public void onSuccess(List<CloudDayBean> result) {
                dayBeanList.clear();
                if (result != null) {
                    dayBeanList.addAll(result);
                }
                dayAdapter.notifyDataSetChanged();
                if (dayBeanList.isEmpty()) {
                    Toast.makeText(CameraCloudStorageActivity.this, R.string.ipc_no_data, Toast.LENGTH_SHORT).show();
                } else {
                    rvDays.scrollToPosition(dayBeanList.size() - 1);
                }
            }

            @Override
            public void onError(String errorCode, String errorMessage) {
                Toast.makeText(CameraCloudStorageActivity.this,
                        getString(R.string.error_with_code, errorCode, errorMessage),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void getTimeLineInfo(CloudDayBean dayBean) {
        if (cloudCamera == null || dayBean == null) {
            return;
        }
        cloudCamera.getTimeLineInfo(devId, dayBean.getCurrentStartDayTime(),
                dayBean.getCurrentDayEndTime(),
                new IThingResultCallback<List<TimePieceBean>>() {
                    @Override
                    public void onSuccess(List<TimePieceBean> result) {
                        timePieceBeans.clear();
                        if (result != null) {
                            timePieceBeans.addAll(result);
                        }
                        timeAdapter.notifyDataSetChanged();
                        if (timePieceBeans.isEmpty()) {
                            Toast.makeText(CameraCloudStorageActivity.this,
                                    R.string.ipc_no_data, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onError(String errorCode, String errorMessage) {
                        Toast.makeText(CameraCloudStorageActivity.this,
                                getString(R.string.error_with_code, errorCode, errorMessage),
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void playCloud(TimePieceBean bean) {
        if (cloudCamera == null || bean == null) {
            return;
        }
        cloudCamera.playCloudDataWithStartTime(bean.getStartTime(), bean.getEndTime(), bean.isEvent(),
                new OperationCallBack() {
                    @Override
                    public void onSuccess(int sessionId, int requestId, String data, Object camera) {
                    }

                    @Override
                    public void onFailure(int sessionId, int requestId, int errCode, Object camera) {
                        runOnUiThread(() -> Toast.makeText(CameraCloudStorageActivity.this,
                                getString(R.string.ipc_operate_fail, errCode), Toast.LENGTH_SHORT).show());
                    }
                },
                new OperationCallBack() {
                    @Override
                    public void onSuccess(int sessionId, int requestId, String data, Object camera) {
                    }

                    @Override
                    public void onFailure(int sessionId, int requestId, int errCode, Object camera) {
                    }
                });
    }

    private void setMute(int mute) {
        if (cloudCamera == null) {
            return;
        }
        cloudCamera.setCloudMute(mute, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                soundState = Integer.parseInt(data);
                runOnUiThread(() -> btnMute.setText(soundState == 0
                        ? R.string.ipc_unmute : R.string.ipc_mute));
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                runOnUiThread(() -> Toast.makeText(CameraCloudStorageActivity.this,
                        getString(R.string.ipc_operate_fail, errCode), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private String getServiceStatus(int code) {
        if (code == SERVES_EXPIRED) {
            return getString(R.string.ipc_cloud_expired);
        }
        if (code == SERVES_RUNNING) {
            return getString(R.string.ipc_cloud_running);
        }
        if (code == NO_SERVICE) {
            return getString(R.string.ipc_cloud_none);
        }
        return String.valueOf(code);
    }

    private OperationDelegateCallBack emptyDelegate() {
        return new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                runOnUiThread(() -> Toast.makeText(CameraCloudStorageActivity.this,
                        getString(R.string.ipc_operate_fail, errCode), Toast.LENGTH_SHORT).show());
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.onResume();
        if (cloudCamera != null) {
            Object created = cameraView.createdView();
            if (created instanceof IRegistorIOTCListener) {
                cloudCamera.generateCloudCameraView((IRegistorIOTCListener) created);
            }
            cloudCamera.registerP2PCameraListener(new AbsP2pCameraListener() {
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.onPause();
        if (cloudCamera != null) {
            cloudCamera.removeOnP2PCameraListener();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cloudCamera != null) {
            cloudCamera.destroy();
            cloudCamera.deinitCloudCamera();
            cloudCamera = null;
        }
    }

    private class DayAdapter extends RecyclerView.Adapter<DayAdapter.Holder> {
        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_date_chip, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            CloudDayBean bean = dayBeanList.get(position);
            holder.tvDate.setText(bean.getUploadDay());
            holder.itemView.setOnClickListener(v -> getTimeLineInfo(bean));
        }

        @Override
        public int getItemCount() {
            return dayBeanList.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView tvDate;

            Holder(View itemView) {
                super(itemView);
                tvDate = itemView.findViewById(R.id.tvDate);
            }
        }
    }

    private class TimeAdapter extends RecyclerView.Adapter<TimeAdapter.Holder> {
        private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_simple_row, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            TimePieceBean bean = timePieceBeans.get(position);
            holder.tvTitle.setText(sdf.format(new Date(bean.getStartTime() * 1000L)));
            int duration = bean.getEndTime() - bean.getStartTime();
            holder.tvSubtitle.setText(getString(R.string.ipc_duration, formatDuration(duration)));
            holder.itemView.setOnClickListener(v -> playCloud(bean));
        }

        @Override
        public int getItemCount() {
            return timePieceBeans.size();
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView tvTitle;
            final TextView tvSubtitle;

            Holder(View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            }
        }
    }

    private static String formatDuration(int seconds) {
        int h = seconds / 3600;
        int m = seconds % 3600 / 60;
        int s = seconds % 60;
        return String.format(Locale.getDefault(), "%02d:%02d:%02d", h, m, s);
    }
}
