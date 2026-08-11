package com.ipc.demo.set;

import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.home.sdk.builder.ActivatorBuilder;
import com.thingclips.smart.sdk.api.IThingActivator;
import com.thingclips.smart.sdk.api.IThingActivatorGetToken;
import com.thingclips.smart.sdk.api.IThingSmartActivatorListener;
import com.thingclips.smart.sdk.bean.DeviceBean;
import com.thingclips.smart.sdk.enums.ActivatorModelEnum;

/**
 * AP (hotspot) pairing — mirrors homesdk-sample DeviceConfigAPActivity.
 * Flow: get token → open Wi-Fi settings to join device AP → start activator on return.
 */
public class DeviceConfigApActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "DeviceConfigAP";

    private EditText etSsid;
    private EditText etPassword;
    private ProgressBar progressBar;
    private Button btnSearch;

    private String ssid;
    private String password;
    private String token;
    private boolean waitingWifiReturn;
    private boolean activating;
    private IThingActivator activator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_config_ap);
        etSsid = findViewById(R.id.etSsid);
        etPassword = findViewById(R.id.etPassword);
        progressBar = findViewById(R.id.progressBar);
        btnSearch = findViewById(R.id.btnSearch);
        btnSearch.setOnClickListener(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    public void onClick(View v) {
        if (v.getId() != R.id.btnSearch) {
            return;
        }
        ssid = etSsid.getText().toString().trim();
        password = etPassword.getText().toString();
        if (TextUtils.isEmpty(ssid)) {
            Toast.makeText(this, R.string.tip_input_wifi, Toast.LENGTH_SHORT).show();
            return;
        }
        long homeId = HomeModel.getCurrentHome(this);
        if (homeId == 0L) {
            Toast.makeText(this, R.string.tip_select_home_first, Toast.LENGTH_SHORT).show();
            return;
        }

        setLoading(true);
        ThingHomeSdk.getActivatorInstance().getActivatorToken(homeId, new IThingActivatorGetToken() {
            @Override
            public void onSuccess(String activatorToken) {
                token = activatorToken;
                waitingWifiReturn = true;
                setLoading(false);
                Toast.makeText(DeviceConfigApActivity.this,
                        R.string.ap_connect_hotspot_tip, Toast.LENGTH_LONG).show();
                openWifiSettings();
            }

            @Override
            public void onFailure(String errorCode, String errorMsg) {
                setLoading(false);
                Toast.makeText(DeviceConfigApActivity.this,
                        getString(R.string.error_with_code, errorCode, errorMsg),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        if (waitingWifiReturn && !TextUtils.isEmpty(token) && !activating) {
            waitingWifiReturn = false;
            startApActivator();
        }
    }

    private void startApActivator() {
        activating = true;
        setLoading(true);
        ActivatorBuilder builder = new ActivatorBuilder()
                .setSsid(ssid)
                .setContext(this)
                .setPassword(password)
                .setActivatorModel(ActivatorModelEnum.THING_AP)
                .setTimeOut(100)
                .setToken(token)
                .setListener(new IThingSmartActivatorListener() {
                    @Override
                    public void onError(String errorCode, String errorMsg) {
                        activating = false;
                        setLoading(false);
                        Toast.makeText(DeviceConfigApActivity.this,
                                getString(R.string.error_with_code, errorCode, errorMsg),
                                Toast.LENGTH_LONG).show();
                    }

                    @Override
                    public void onActiveSuccess(DeviceBean devResp) {
                        activating = false;
                        setLoading(false);
                        Log.i(TAG, "Activate success");
                        Toast.makeText(DeviceConfigApActivity.this,
                                R.string.activate_success, Toast.LENGTH_LONG).show();
                        startActivity(new Intent(DeviceConfigApActivity.this, DeviceListActivity.class));
                        finish();
                    }

                    @Override
                    public void onStep(String step, Object data) {
                        Log.i(TAG, step + " --> " + data);
                    }
                });

        if (activator != null) {
            activator.stop();
            activator.onDestroy();
        }
        activator = ThingHomeSdk.getActivatorInstance().newActivator(builder);
        activator.start();
    }

    private void openWifiSettings() {
        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
        } else {
            Toast.makeText(this, R.string.ap_open_wifi_fail, Toast.LENGTH_LONG).show();
            waitingWifiReturn = false;
        }
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSearch.setEnabled(!loading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        waitingWifiReturn = false;
        activating = false;
        if (activator != null) {
            activator.stop();
            activator.onDestroy();
            activator = null;
        }
    }
}
