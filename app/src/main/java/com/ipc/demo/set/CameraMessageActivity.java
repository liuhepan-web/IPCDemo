package com.ipc.demo.set;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.thingclips.smart.android.camera.sdk.ThingIPCSdk;
import com.thingclips.smart.android.camera.sdk.api.IThingCameraMessage;
import com.thingclips.smart.android.camera.sdk.api.IThingIPCMsg;
import com.thingclips.smart.home.sdk.callback.IThingResultCallback;
import com.thingclips.smart.ipc.messagecenter.bean.CameraMessageBean;
import com.thingclips.smart.ipc.messagecenter.bean.CameraMessageClassifyBean;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Camera alarm / detection message center for today.
 */
public class CameraMessageActivity extends AppCompatActivity {

    private String devId;
    private IThingCameraMessage cameraMessage;
    private CameraMessageClassifyBean selectClassify;
    private CameraMessageAdapter adapter;
    private TextView tvEmpty;
    private final List<CameraMessageBean> messageList = new ArrayList<>();

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
        tvEmpty = findViewById(R.id.tvEmpty);
        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CameraMessageAdapter(this);
        adapter.setListener(new CameraMessageAdapter.OnItemListener() {
            @Override
            public void onItemClick(CameraMessageBean bean) {
                openVideoIfNeeded(bean);
            }

            @Override
            public void onLongClick(CameraMessageBean bean) {
                deleteMessage(bean);
            }
        });
        recyclerView.setAdapter(adapter);

        IThingIPCMsg message = ThingIPCSdk.getMessage();
        if (message != null) {
            cameraMessage = message.createCameraMessage();
        }
        if (cameraMessage == null) {
            Toast.makeText(this, R.string.ipc_operate_fail_generic, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        queryClassify();
    }

    private void queryClassify() {
        cameraMessage.queryAlarmDetectionClassify(devId, new IThingResultCallback<List<CameraMessageClassifyBean>>() {
            @Override
            public void onSuccess(List<CameraMessageClassifyBean> result) {
                if (result == null || result.isEmpty()) {
                    showEmpty(true);
                    return;
                }
                selectClassify = result.get(0);
                loadTodayMessages();
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

    private void loadTodayMessages() {
        if (selectClassify == null) {
            return;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        int startTime = (int) (calendar.getTimeInMillis() / 1000);
        calendar.add(Calendar.DAY_OF_MONTH, 1);
        int endTime = (int) (calendar.getTimeInMillis() / 1000) - 1;

        cameraMessage.getAlarmDetectionMessageList(devId, startTime, endTime,
                selectClassify.getMsgCode(), 0, 30,
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

    private void openVideoIfNeeded(CameraMessageBean bean) {
        String[] videos = bean.getAttachVideos();
        if (videos == null || videos.length == 0) {
            return;
        }
        String attachVideo = videos[0];
        if (TextUtils.isEmpty(attachVideo)) {
            return;
        }
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraMessage != null) {
            cameraMessage.destroy();
            cameraMessage = null;
        }
    }
}
