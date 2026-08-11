package com.ipc.demo.set;

import com.ipc.demo.set.R;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.home.sdk.bean.HomeBean;
import com.thingclips.smart.home.sdk.callback.IThingHomeResultCallback;

import java.util.ArrayList;

/**
 * Create home — mirrors homesdk-sample NewHomeActivity.
 */
public class CreateHomeActivity extends AppCompatActivity implements View.OnClickListener {

    private EditText etHomeName;
    private EditText etCity;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_home);
        etHomeName = findViewById(R.id.etHomeName);
        etCity = findViewById(R.id.etCity);
        Button btnDone = findViewById(R.id.btnDone);
        btnDone.setOnClickListener(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    public void onClick(View v) {
        if (v.getId() != R.id.btnDone) {
            return;
        }
        String homeName = etHomeName.getText().toString().trim();
        String city = etCity.getText().toString().trim();
        if (TextUtils.isEmpty(homeName) || TextUtils.isEmpty(city)) {
            Toast.makeText(this, R.string.tip_input_required, Toast.LENGTH_SHORT).show();
            return;
        }

        // Lon/Lat: sample values (Shanghai area), replace with real location if needed
        ThingHomeSdk.getHomeManagerInstance().createHome(
                homeName,
                120.52,
                30.40,
                city,
                new ArrayList<>(),
                new IThingHomeResultCallback() {
                    @Override
                    public void onSuccess(HomeBean bean) {
                        HomeModel.setCurrentHome(CreateHomeActivity.this, bean.getHomeId());
                        Toast.makeText(CreateHomeActivity.this, R.string.create_home_success, Toast.LENGTH_LONG).show();
                        finish();
                    }

                    @Override
                    public void onError(String errorCode, String errorMsg) {
                        Toast.makeText(CreateHomeActivity.this,
                                getString(R.string.error_with_code, errorCode, errorMsg),
                                Toast.LENGTH_LONG).show();
                    }
                }
        );
    }
}
