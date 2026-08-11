package com.ipc.demo.set;

import com.ipc.demo.set.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.home.sdk.bean.HomeBean;
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback;
import com.thingclips.smart.sdk.api.IResultCallback;
import com.thingclips.smart.sdk.bean.DeviceBean;

import java.util.ArrayList;
import java.util.List;

/**
 * Query devices under current home; open IPC panel for camera devices.
 * Long-press a device to remove (unbind) it from the home.
 */
public class DeviceListActivity extends AppCompatActivity {

    private DeviceAdapter adapter;
    private TextView tvEmpty;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_list);
        TextView tvTitle = findViewById(R.id.tvTitle);
        tvTitle.setText(R.string.device_list_title);
        tvEmpty = findViewById(R.id.tvEmpty);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new DeviceAdapter();
        recyclerView.setAdapter(adapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadDevices();
    }

    private void loadDevices() {
        long homeId = HomeModel.getCurrentHome(this);
        if (homeId == 0L) {
            tvEmpty.setVisibility(View.VISIBLE);
            tvEmpty.setText(R.string.tip_select_home_first);
            adapter.setData(new ArrayList<>());
            return;
        }
        ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(new IThingHomeResultCallback() {
            @Override
            public void onSuccess(HomeBean bean) {
                List<DeviceBean> devices = bean.getDeviceList();
                if (devices == null) {
                    devices = new ArrayList<>();
                }
                adapter.setData(devices);
                tvEmpty.setVisibility(devices.isEmpty() ? View.VISIBLE : View.GONE);
                tvEmpty.setText(R.string.device_list_empty);
            }

            @Override
            public void onError(String errorCode, String errorMsg) {
                Toast.makeText(DeviceListActivity.this,
                        getString(R.string.error_with_code, errorCode, errorMsg),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Confirm and remove device from current home (unbind).
     *
     * @param bean device to remove
     */
    private void confirmRemoveDevice(DeviceBean bean) {
        if (bean == null || bean.getDevId() == null) {
            return;
        }
        final String devId = bean.getDevId();
        String name = bean.getName() != null ? bean.getName() : devId;
        new AlertDialog.Builder(this)
                .setTitle(R.string.device_remove_title)
                .setMessage(getString(R.string.device_remove_message, name))
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.confirm, (dialog, which) -> removeDevice(devId))
                .show();
    }

    /**
     * Call SDK to unbind/remove device, then refresh list.
     *
     * @param devId device id
     */
    private void removeDevice(String devId) {
        ThingHomeSdk.newDeviceInstance(devId).removeDevice(new IResultCallback() {
            @Override
            public void onError(String code, String error) {
                runOnUiThread(() -> Toast.makeText(DeviceListActivity.this,
                        getString(R.string.error_with_code, code, error),
                        Toast.LENGTH_LONG).show());
            }

            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    Toast.makeText(DeviceListActivity.this, R.string.device_remove_success,
                            Toast.LENGTH_SHORT).show();
                    adapter.removeByDevId(devId);
                });
            }
        });
    }

    private class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.Holder> {
        private final List<DeviceBean> data = new ArrayList<>();

        void setData(List<DeviceBean> list) {
            data.clear();
            data.addAll(list);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_simple_row, parent, false);
            return new Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder holder, int position) {
            DeviceBean bean = data.get(position);
            String devId = bean.getDevId();
            int type = IpcDeviceHelper.resolveDeviceType(devId);
            String typeTag;
            if (type == 2) {
                typeTag = getString(R.string.device_type_doorbell);
            } else if (type == 1) {
                typeTag = getString(R.string.device_type_ipc);
            } else {
                typeTag = getString(R.string.device_type_other);
            }
            holder.tvTitle.setText(bean.getName());
            String online = Boolean.TRUE.equals(bean.getIsOnline())
                    ? getString(R.string.device_online) : getString(R.string.device_offline);
            holder.tvSubtitle.setText(typeTag + " · " + online + " · " + devId);

            holder.itemView.setOnClickListener(v -> {
                if (type == 0 && !IpcDeviceHelper.canOpenLivePreview(devId)) {
                    Toast.makeText(DeviceListActivity.this, R.string.not_ipc_device, Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent intent = new Intent(DeviceListActivity.this, CameraPanelActivity.class);
                intent.putExtra(IpcConstants.EXTRA_DEV_ID, devId);
                startActivity(intent);
            });
            holder.itemView.setOnLongClickListener(v -> {
                confirmRemoveDevice(bean);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return data.size();
        }

        void removeByDevId(String removeDevId) {
            for (int i = 0; i < data.size(); i++) {
                if (removeDevId.equals(data.get(i).getDevId())) {
                    data.remove(i);
                    notifyItemRemoved(i);
                    break;
                }
            }
            tvEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
            tvEmpty.setText(R.string.device_list_empty);
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView tvTitle;
            final TextView tvSubtitle;

            Holder(@NonNull View itemView) {
                super(itemView);
                tvTitle = itemView.findViewById(R.id.tvTitle);
                tvSubtitle = itemView.findViewById(R.id.tvSubtitle);
            }
        }
    }
}
