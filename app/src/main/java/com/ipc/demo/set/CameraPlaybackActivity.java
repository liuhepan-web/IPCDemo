package com.ipc.demo.set;

import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
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

import com.alibaba.fastjson.JSONObject;
import com.google.android.material.button.MaterialButton;
import com.thingclips.smart.android.camera.sdk.ThingIPCSdk;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCCore;
import com.thingclips.smart.android.camera.timeline.OnBarMoveListener;
import com.thingclips.smart.android.camera.timeline.OnBarScaledListener;
import com.thingclips.smart.android.camera.timeline.ThingTimelineView;
import com.thingclips.smart.android.camera.timeline.TimeBean;
import com.thingclips.smart.android.camera.timeline.TimelineUnitMode;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.AbsP2pCameraListener;
import com.thingclips.smart.camera.camerasdk.thingplayer.callback.OperationDelegateCallBack;
import com.thingclips.smart.camera.ipccamerasdk.bean.MonthDays;
import com.thingclips.smart.camera.ipccamerasdk.p2p.ICameraP2P;
import com.thingclips.smart.camera.middleware.cloud.bean.TimePieceBean;
import com.thingclips.smart.camera.middleware.p2p.IThingSmartCameraP2P;
import com.thingclips.smart.camera.middleware.widget.AbsVideoViewCallback;
import com.thingclips.smart.camera.middleware.widget.ThingCameraView;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * SD card playback: connect P2P, query days/slices, play with timeline.
 */
public class CameraPlaybackActivity extends AppCompatActivity {

    private static final String TAG = "CameraPlayback";

    private String devId;
    private IThingSmartCameraP2P cameraP2P;
    private ThingCameraView cameraView;
    private ThingTimelineView timelineView;
    private TextView tvStatus;
    private MaterialButton btnMute;
    private RecyclerView rvDays;
    private RecyclerView rvTimePieces;

    private final List<String> dateList = new ArrayList<>();
    private final List<TimePieceBean> timeList = new ArrayList<>();
    private DayAdapter dayAdapter;
    private TimeAdapter timeAdapter;

    private boolean isPlayback;
    private int muteState = ICameraP2P.MUTE;
    private String monthPrefix;
    /** Mode_60=1 放大 24h；Mode_600=2；Mode_3600=3 缩小 12h */
    private TimelineUnitMode timelineUnitMode = TimelineUnitMode.Mode_600;

    private final AbsP2pCameraListener p2pCameraListener = new AbsP2pCameraListener() {
        @Override
        public void onReceiveFrameYUVData(int i, ByteBuffer byteBuffer, ByteBuffer byteBuffer1,
                                          ByteBuffer byteBuffer2, int i1, int i2, int i3, int i4,
                                          long l, long l1, long l2, Object o) {
            super.onReceiveFrameYUVData(i, byteBuffer, byteBuffer1, byteBuffer2, i1, i2, i3, i4, l, l1, l2, o);
            runOnUiThread(() -> {
                if (timelineView != null) {
                    timelineView.setCurrentTimeInMillisecond(l * 1000L);
                }
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_playback);
        devId = getIntent().getStringExtra(IpcConstants.EXTRA_DEV_ID);
        if (TextUtils.isEmpty(devId)) {
            Toast.makeText(this, R.string.ipc_invalid_device, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        cameraView = findViewById(R.id.camera_video_view);
        timelineView = findViewById(R.id.timelineView);
        tvStatus = findViewById(R.id.tvStatus);
        btnMute = findViewById(R.id.btnMute);
        rvDays = findViewById(R.id.rvDays);
        rvTimePieces = findViewById(R.id.rvTimePieces);

        applyVideoAspect();
        initLists();
        initTimeline();
        initButtons();
        initCamera();
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

    private void initTimeline() {
        timelineView.setBackgroundColor(Color.BLACK);
        timelineView.setLinesColor(Color.parseColor("#B3FFFFFF"));
        timelineView.setShowTimeText(true);
        timelineView.setShowShortMark(true);
        timelineView.setShowBubbleWhenDrag(true);
        timelineView.setTimeAmPmString("上午", "下午");
        timelineView.setTimeZone(TimeZone.getDefault());
        timelineView.setContentShader(new LinearGradient(
                0, 0, 0, 64,
                new int[]{Color.parseColor("#4D1F6F8B"), Color.parseColor("#991F6F8B")},
                null,
                Shader.TileMode.CLAMP));
        applyTimelineUnitMode(TimelineUnitMode.Mode_600);
        bindTimelineScaleListener();

        findViewById(R.id.btnTimelineZoomOut).setOnClickListener(v -> zoomTimeline(false));
        findViewById(R.id.btnTimelineZoomIn).setOnClickListener(v -> zoomTimeline(true));

        timelineView.setOnBarMoveListener(new OnBarMoveListener() {
            @Override
            public void onBarMove(long l, long l1, long l2) {
            }

            @Override
            public void onBarMoveFinish(long startTime, long endTime, long currentTime) {
                timelineView.setCanQueryData();
                timelineView.setQueryNewVideoData(false);
                if (startTime != -1 && endTime != -1) {
                    // Timeline returns seconds for clip bounds + playhead.
                    playback((int) startTime, (int) endTime, (int) currentTime);
                } else {
                    Toast.makeText(CameraPlaybackActivity.this,
                            R.string.ipc_playback_no_clip, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onBarActionDown() {
            }
        });
    }

    /**
     * 缩小 → Mode_3600 + 12 小时制；放大 → Mode_60 + 24 小时制。
     */
    private void zoomTimeline(boolean zoomIn) {
        if (zoomIn) {
            if (timelineUnitMode == TimelineUnitMode.Mode_3600) {
                applyTimelineUnitMode(TimelineUnitMode.Mode_600);
            } else {
                applyTimelineUnitMode(TimelineUnitMode.Mode_60);
            }
        } else {
            if (timelineUnitMode == TimelineUnitMode.Mode_60) {
                applyTimelineUnitMode(TimelineUnitMode.Mode_600);
            } else {
                applyTimelineUnitMode(TimelineUnitMode.Mode_3600);
            }
        }
    }

    private void applyTimelineUnitMode(TimelineUnitMode mode) {
        timelineUnitMode = mode;
        // Mode_60(放大)=24h，其余缩小档=12h
        boolean use24Hour = mode == TimelineUnitMode.Mode_60;
        setTimeline24Hour(use24Hour);
        timelineView.setUnitMode(mode);
        // 放大时单位更宽，缩小更密
        if (mode == TimelineUnitMode.Mode_60) {
            timelineView.setSpacePerUnit(dp(48));
        } else if (mode == TimelineUnitMode.Mode_600) {
            timelineView.setSpacePerUnit(dp(36));
        } else {
            timelineView.setSpacePerUnit(dp(28));
        }
    }

    private void bindTimelineScaleListener() {
        Object impl = timelineImpl();
        if (impl == null) {
            return;
        }
        try {
            Method setter = impl.getClass().getMethod("setOnBarScaledListener", OnBarScaledListener.class);
            setter.invoke(impl, new OnBarScaledListener() {
                @Override
                public void onOnBarScaledMode(int modeValue) {
                    TimelineUnitMode mode = modeFromValue(modeValue);
                    if (mode != null) {
                        applyTimelineUnitMode(mode);
                    }
                }

                @Override
                public void onBarScaled(long screenLeftTime, long screenRightTime, long currentTime) {
                }

                @Override
                public void onBarScaleFinish(long startTime, long endTime, long currentTime) {
                    timelineView.setCanQueryData();
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "bind timeline scale listener failed", t);
        }
    }

    private TimelineUnitMode modeFromValue(int value) {
        if (value == TimelineUnitMode.Mode_60.getValue()) {
            return TimelineUnitMode.Mode_60;
        }
        if (value == TimelineUnitMode.Mode_600.getValue()) {
            return TimelineUnitMode.Mode_600;
        }
        if (value == TimelineUnitMode.Mode_3600.getValue()) {
            return TimelineUnitMode.Mode_3600;
        }
        return null;
    }

    private void setTimeline24Hour(boolean use24Hour) {
        Object impl = timelineImpl();
        if (impl == null) {
            return;
        }
        try {
            Field field = impl.getClass().getDeclaredField("is24hour");
            field.setAccessible(true);
            field.setBoolean(impl, use24Hour);
        } catch (Throwable t) {
            Log.w(TAG, "set is24hour failed", t);
        }
    }

    private Object timelineImpl() {
        if (timelineView.getChildCount() > 0) {
            return timelineView.getChildAt(0);
        }
        return null;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void initButtons() {
        btnMute.setText(R.string.ipc_unmute);
        btnMute.setOnClickListener(v -> toggleMute());
        findViewById(R.id.btnPause).setOnClickListener(v -> {
            if (cameraP2P == null) {
                return;
            }
            cameraP2P.pausePlayBack(new OperationDelegateCallBack() {
                @Override
                public void onSuccess(int sessionId, int requestId, String data) {
                    isPlayback = false;
                }

                @Override
                public void onFailure(int sessionId, int requestId, int errCode) {
                }
            });
        });
        findViewById(R.id.btnResume).setOnClickListener(v -> {
            if (cameraP2P == null) {
                return;
            }
            cameraP2P.resumePlayBack(new OperationDelegateCallBack() {
                @Override
                public void onSuccess(int sessionId, int requestId, String data) {
                    isPlayback = true;
                }

                @Override
                public void onFailure(int sessionId, int requestId, int errCode) {
                }
            });
        });
        findViewById(R.id.btnStop).setOnClickListener(v -> {
            if (cameraP2P == null) {
                return;
            }
            cameraP2P.stopPlayBack(new OperationDelegateCallBack() {
                @Override
                public void onSuccess(int sessionId, int requestId, String data) {
                }

                @Override
                public void onFailure(int sessionId, int requestId, int errCode) {
                }
            });
            isPlayback = false;
        });
    }

    private void initCamera() {
        IThingIPCCore cameraInstance = ThingIPCSdk.getCameraInstance();
        if (cameraInstance != null) {
            cameraP2P = cameraInstance.createCameraP2P(devId);
        }
        if (cameraP2P == null) {
            Toast.makeText(this, R.string.ipc_operate_fail_generic, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        cameraView.setViewCallback(new AbsVideoViewCallback() {
            @Override
            public void onCreated(Object o) {
                super.onCreated(o);
                if (cameraP2P != null) {
                    cameraP2P.generateCameraView(cameraView.createdView());
                }
            }
        });
        cameraView.createVideoView(devId);
        tvStatus.setText(R.string.ipc_connecting);
        if (!cameraP2P.isConnecting()) {
            cameraP2P.connect(devId, new OperationDelegateCallBack() {
                @Override
                public void onSuccess(int i, int i1, String s) {
                    runOnUiThread(() -> {
                        tvStatus.setText(R.string.ipc_connected);
                        queryCurrentMonth();
                    });
                }

                @Override
                public void onFailure(int i, int i1, int i2) {
                    runOnUiThread(() -> tvStatus.setText(getString(R.string.ipc_connect_fail, i2)));
                }
            });
        } else {
            tvStatus.setText(R.string.ipc_connected);
            queryCurrentMonth();
        }
    }

    private void queryCurrentMonth() {
        if (cameraP2P == null || !cameraP2P.isConnecting()) {
            Toast.makeText(this, R.string.ipc_connect_first, Toast.LENGTH_SHORT).show();
            return;
        }
        Calendar calendar = Calendar.getInstance();
        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH) + 1;
        monthPrefix = year + "/" + month;
        cameraP2P.queryRecordDaysByMonth(year, month, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                MonthDays monthDays = JSONObject.parseObject(data, MonthDays.class);
                List<String> dataDays = monthDays != null ? monthDays.getDataDays() : null;
                dateList.clear();
                timeList.clear();
                if (dataDays != null) {
                    for (String day : dataDays) {
                        dateList.add(monthPrefix + "/" + day);
                    }
                }
                runOnUiThread(() -> {
                    dayAdapter.notifyDataSetChanged();
                    timeAdapter.notifyDataSetChanged();
                    if (dateList.isEmpty()) {
                        Toast.makeText(CameraPlaybackActivity.this, R.string.ipc_no_data, Toast.LENGTH_SHORT).show();
                    } else {
                        rvDays.scrollToPosition(dateList.size() - 1);
                    }
                });
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                runOnUiThread(() -> Toast.makeText(CameraPlaybackActivity.this,
                        getString(R.string.ipc_operate_fail, errCode), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void queryDay(String dateStr) {
        if (cameraP2P == null || TextUtils.isEmpty(dateStr) || !dateStr.contains("/")) {
            return;
        }
        String[] parts = dateStr.split("/");
        if (parts.length != 3) {
            return;
        }
        int year = Integer.parseInt(parts[0]);
        int month = Integer.parseInt(parts[1]);
        int day = Integer.parseInt(parts[2]);
        cameraP2P.queryRecordTimeSliceByDay(year, month, day, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                parsePlaybackData(data);
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                runOnUiThread(() -> Toast.makeText(CameraPlaybackActivity.this,
                        getString(R.string.ipc_operate_fail, errCode), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void parsePlaybackData(String data) {
        RecordInfoBean recordInfoBean = JSONObject.parseObject(data, RecordInfoBean.class);
        timeList.clear();
        if (recordInfoBean != null && recordInfoBean.getCount() != 0
                && recordInfoBean.getItems() != null) {
            timeList.addAll(recordInfoBean.getItems());
        }
        runOnUiThread(() -> {
            timeAdapter.notifyDataSetChanged();
            if (timeList.isEmpty()) {
                Toast.makeText(this, R.string.ipc_no_data, Toast.LENGTH_SHORT).show();
                return;
            }
            List<TimeBean> timelineData = new ArrayList<>();
            for (TimePieceBean bean : timeList) {
                TimeBean b = new TimeBean();
                b.setStartTime(bean.getStartTime());
                b.setEndTime(bean.getEndTime());
                timelineData.add(b);
            }
            timelineView.setCurrentTimeConfig(timeList.get(0).getEndTime() * 1000L);
            timelineView.setRecordDataExistTimeClipsList(timelineData);
        });
    }

    private void playback(int startTime, int endTime, int playTime) {
        if (cameraP2P == null) {
            Toast.makeText(this, R.string.ipc_operate_fail_generic, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!cameraP2P.isConnecting()) {
            Toast.makeText(this, R.string.ipc_connect_first, Toast.LENGTH_SHORT).show();
            return;
        }
        // Docs: playTime ∈ [startTime, stopTime). end==start or playTime==end → -40201/-40205.
        if (endTime <= startTime) {
            Toast.makeText(this, R.string.ipc_playback_invalid_range, Toast.LENGTH_SHORT).show();
            return;
        }
        if (playTime < startTime) {
            playTime = startTime;
        }
        if (playTime >= endTime) {
            playTime = endTime - 1;
        }
        final int safeStart = startTime;
        final int safeEnd = endTime;
        final int safePlay = playTime;
        Log.i(TAG, "startPlayBack start=" + safeStart + " end=" + safeEnd + " play=" + safePlay);

        Runnable start = () -> cameraP2P.startPlayBack(safeStart, safeEnd, safePlay,
                new OperationDelegateCallBack() {
                    @Override
                    public void onSuccess(int sessionId, int requestId, String data) {
                        isPlayback = true;
                        runOnUiThread(() -> tvStatus.setText(R.string.ipc_playback_playing));
                    }

                    @Override
                    public void onFailure(int sessionId, int requestId, int errCode) {
                        isPlayback = false;
                        runOnUiThread(() -> {
                            tvStatus.setText(playbackErrorText(errCode));
                            Toast.makeText(CameraPlaybackActivity.this,
                                    playbackErrorText(errCode), Toast.LENGTH_LONG).show();
                        });
                    }
                }, new OperationDelegateCallBack() {
                    @Override
                    public void onSuccess(int sessionId, int requestId, String data) {
                        isPlayback = false;
                        runOnUiThread(() -> tvStatus.setText(R.string.ipc_playback_finished));
                    }

                    @Override
                    public void onFailure(int sessionId, int requestId, int errCode) {
                        isPlayback = false;
                    }
                });

        // Stop previous playback first to avoid device busy / invalid state (-40201).
        if (isPlayback) {
            cameraP2P.stopPlayBack(new OperationDelegateCallBack() {
                @Override
                public void onSuccess(int sessionId, int requestId, String data) {
                    isPlayback = false;
                    start.run();
                }

                @Override
                public void onFailure(int sessionId, int requestId, int errCode) {
                    isPlayback = false;
                    start.run();
                }
            });
        } else {
            start.run();
        }
    }

    /**
     * Map common SD playback failures (often device-side) to actionable copy.
     * -40201: invalid playback params / session state
     * -40205: no recording at selected time / clip unavailable
     */
    private String playbackErrorText(int errCode) {
        if (errCode == -40201) {
            return getString(R.string.ipc_playback_err_40201);
        }
        if (errCode == -40205) {
            return getString(R.string.ipc_playback_err_40205);
        }
        return getString(R.string.ipc_operate_fail, errCode);
    }

    private void toggleMute() {
        if (cameraP2P == null) {
            return;
        }
        int next = muteState == ICameraP2P.MUTE ? ICameraP2P.UNMUTE : ICameraP2P.MUTE;
        cameraP2P.setMute(next, new OperationDelegateCallBack() {
            @Override
            public void onSuccess(int sessionId, int requestId, String data) {
                muteState = Integer.parseInt(data);
                runOnUiThread(() -> btnMute.setText(muteState == ICameraP2P.MUTE
                        ? R.string.ipc_unmute : R.string.ipc_mute));
            }

            @Override
            public void onFailure(int sessionId, int requestId, int errCode) {
                runOnUiThread(() -> Toast.makeText(CameraPlaybackActivity.this,
                        getString(R.string.ipc_operate_fail, errCode), Toast.LENGTH_SHORT).show());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        cameraView.onResume();
        if (cameraP2P != null) {
            cameraP2P.registerP2PCameraListener(p2pCameraListener);
            cameraP2P.generateCameraView(cameraView.createdView());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        cameraView.onPause();
        if (isPlayback && cameraP2P != null) {
            cameraP2P.stopPlayBack(null);
            isPlayback = false;
        }
        if (cameraP2P != null) {
            cameraP2P.removeOnP2PCameraListener();
            if (isFinishing()) {
                cameraP2P.disconnect(new OperationDelegateCallBack() {
                    @Override
                    public void onSuccess(int i, int i1, String s) {
                    }

                    @Override
                    public void onFailure(int i, int i1, int i2) {
                    }
                });
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraP2P != null) {
            cameraP2P.destroyP2P();
            cameraP2P = null;
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
            String date = dateList.get(position);
            holder.tvDate.setText(date);
            holder.itemView.setOnClickListener(v -> queryDay(date));
        }

        @Override
        public int getItemCount() {
            return dateList.size();
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
            TimePieceBean bean = timeList.get(position);
            holder.tvTitle.setText(sdf.format(new Date(bean.getStartTime() * 1000L)));
            int duration = bean.getEndTime() - bean.getStartTime();
            holder.tvSubtitle.setText(getString(R.string.ipc_duration, formatDuration(duration)));
            holder.itemView.setOnClickListener(v ->
                    playback(bean.getStartTime(), bean.getEndTime(), bean.getStartTime()));
        }

        @Override
        public int getItemCount() {
            return timeList.size();
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
