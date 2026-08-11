package com.ipc.demo.set;

import com.ipc.demo.set.R;

import android.content.Intent;
import android.os.Bundle;
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
 * EZ (smart config) pairing — mirrors homesdk-sample DeviceConfigEZActivity.
 */
public class DeviceConfigEzActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "DeviceConfigEZ";

    private EditText etSsid;
    private EditText etPassword;
    private ProgressBar progressBar;
    private Button btnSearch;
    private IThingActivator activator;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_config_ez);
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
        String ssid = etSsid.getText().toString().trim();
        String password = etPassword.getText().toString();
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
            public void onSuccess(String token) {
                ActivatorBuilder builder = new ActivatorBuilder()
                        .setSsid(ssid)
                        .setContext(DeviceConfigEzActivity.this)
                        .setPassword(password)
                        .setActivatorModel(ActivatorModelEnum.THING_EZ)
                        .setTimeOut(100)
                        .setToken(token)
                        .setListener(new IThingSmartActivatorListener() {
                            @Override
                            public void onError(String errorCode, String errorMsg) {
                                setLoading(false);
                                Toast.makeText(DeviceConfigEzActivity.this,
                                        getString(R.string.error_with_code, errorCode, errorMsg),
                                        Toast.LENGTH_LONG).show();
                            }

                            @Override
                            public void onActiveSuccess(DeviceBean devResp) {
                                setLoading(false);
                                Log.i(TAG, "Activate success");
                                Toast.makeText(DeviceConfigEzActivity.this,
                                        R.string.activate_success, Toast.LENGTH_LONG).show();
                                startActivity(new Intent(DeviceConfigEzActivity.this, DeviceListActivity.class));
                                finish();
                            }

                            @Override
                            public void onStep(String step, Object data) {
                                Log.i(TAG, step + " --> " + data);
                            }
                        });

                activator = ThingHomeSdk.getActivatorInstance().newMultiActivator(builder);
                activator.start();
            }

            @Override
            public void onFailure(String errorCode, String errorMsg) {
                setLoading(false);
                Toast.makeText(DeviceConfigEzActivity.this,
                        getString(R.string.error_with_code, errorCode, errorMsg),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSearch.setEnabled(!loading);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (activator != null) {
            activator.stop();
            activator.onDestroy();
            activator = null;
        }
    }
}
