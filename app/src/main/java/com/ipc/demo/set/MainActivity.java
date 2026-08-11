package com.ipc.demo.set;

import com.ipc.demo.set.R;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.thingclips.smart.android.user.api.ILogoutCallback;
import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.home.sdk.bean.HomeBean;
import com.thingclips.smart.home.sdk.callback.IThingGetHomeListCallback;
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback;

import java.util.List;

/**
 * Main hub: home list, devices, pairing, logout.
 */
public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private TextView tvCurrentHome;
    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ThingHomeSdk.getUserInstance().isLogin()) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }
        setContentView(R.layout.activity_main);
        DoorbellCallManager.getInstance().ensureInit();
        // #region agent log
        try {
            AgentDebugLog.log("A", "MainActivity.onCreate", "main_ensure_doorbell_init",
                    new org.json.JSONObject()
                            .put("isLogin", true));
        } catch (Throwable ignored) {
        }
        // #endregion
        tvCurrentHome = findViewById(R.id.tvCurrentHome);
        tvStatus = findViewById(R.id.tvStatus);
        findViewById(R.id.btnCreateHome).setOnClickListener(this);
        findViewById(R.id.btnRefreshHomes).setOnClickListener(this);
        findViewById(R.id.btnHomeList).setOnClickListener(this);
        findViewById(R.id.btnDeviceList).setOnClickListener(this);
        findViewById(R.id.btnEzConfig).setOnClickListener(this);
        findViewById(R.id.btnApConfig).setOnClickListener(this);
        findViewById(R.id.btnQrConfig).setOnClickListener(this);
        findViewById(R.id.btnLogout).setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshCurrentHome();
        queryHomeList();
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnCreateHome) {
            startActivity(new Intent(this, CreateHomeActivity.class));
        } else if (id == R.id.btnRefreshHomes) {
            queryHomeList();
        } else if (id == R.id.btnHomeList) {
            startActivity(new Intent(this, HomeListActivity.class));
        } else if (id == R.id.btnDeviceList) {
            long homeId = HomeModel.getCurrentHome(this);
            if (homeId == 0L) {
                Toast.makeText(this, R.string.tip_select_home_first, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, DeviceListActivity.class));
        } else if (id == R.id.btnEzConfig) {
            long homeId = HomeModel.getCurrentHome(this);
            if (homeId == 0L) {
                Toast.makeText(this, R.string.tip_select_home_first, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, DeviceConfigEzActivity.class));
        } else if (id == R.id.btnApConfig) {
            long homeId = HomeModel.getCurrentHome(this);
            if (homeId == 0L) {
                Toast.makeText(this, R.string.tip_select_home_first, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, DeviceConfigApActivity.class));
        } else if (id == R.id.btnQrConfig) {
            long homeId = HomeModel.getCurrentHome(this);
            if (homeId == 0L) {
                Toast.makeText(this, R.string.tip_select_home_first, Toast.LENGTH_SHORT).show();
                return;
            }
            startActivity(new Intent(this, DeviceConfigQrActivity.class));
        } else if (id == R.id.btnLogout) {
            ThingHomeSdk.getUserInstance().logout(new ILogoutCallback() {
                @Override
                public void onSuccess() {
                    HomeModel.clear(MainActivity.this);
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                }

                @Override
                public void onError(String code, String error) {
                    HomeModel.clear(MainActivity.this);
                    startActivity(new Intent(MainActivity.this, LoginActivity.class));
                    finish();
                }
            });
        }
    }

    private void refreshCurrentHome() {
        long homeId = HomeModel.getCurrentHome(this);
        if (homeId == 0L) {
            tvCurrentHome.setText(R.string.no_current_home);
            return;
        }
        ThingHomeSdk.newHomeInstance(homeId).getHomeDetail(new IThingHomeResultCallback() {
            @Override
            public void onSuccess(HomeBean bean) {
                tvCurrentHome.setText(getString(R.string.current_home_format, bean.getName(), bean.getHomeId()));
            }

            @Override
            public void onError(String errorCode, String errorMsg) {
                tvCurrentHome.setText(getString(R.string.current_home_id_only, homeId));
            }
        });
    }

    private void queryHomeList() {
        ThingHomeSdk.getHomeManagerInstance().queryHomeList(new IThingGetHomeListCallback() {
            @Override
            public void onSuccess(List<HomeBean> homeBeans) {
                if (homeBeans == null || homeBeans.isEmpty()) {
                    tvStatus.setText(R.string.home_list_empty);
                    return;
                }
                StringBuilder sb = new StringBuilder();
                for (HomeBean bean : homeBeans) {
                    sb.append(bean.getName())
                            .append(" (")
                            .append(bean.getHomeId())
                            .append(")\n");
                }
                tvStatus.setText(sb.toString().trim());
                if (HomeModel.getCurrentHome(MainActivity.this) == 0L) {
                    HomeModel.setCurrentHome(MainActivity.this, homeBeans.get(0).getHomeId());
                    refreshCurrentHome();
                }
            }

            @Override
            public void onError(String errorCode, String error) {
                tvStatus.setText(getString(R.string.error_with_code, errorCode, error));
            }
        });
    }
}
