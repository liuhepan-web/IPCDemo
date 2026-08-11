package com.ipc.demo.set;

import com.ipc.demo.set.R;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.thingclips.smart.android.common.utils.ValidatorUtil;
import com.thingclips.smart.android.user.api.ILoginCallback;
import com.thingclips.smart.android.user.bean.User;
import com.thingclips.smart.home.sdk.ThingHomeSdk;

/**
 * Login — mirrors homesdk-sample UserLoginActivity.
 */
public class LoginActivity extends AppCompatActivity implements View.OnClickListener {

    private EditText etCountryCode;
    private EditText etAccount;
    private EditText etPassword;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ThingHomeSdk.getUserInstance().isLogin()) {
            goMain();
            return;
        }
        setContentView(R.layout.activity_login);
        etCountryCode = findViewById(R.id.etCountryCode);
        etAccount = findViewById(R.id.etAccount);
        etPassword = findViewById(R.id.etPassword);
        Button btnLogin = findViewById(R.id.btnLogin);
        Button btnGoRegister = findViewById(R.id.btnGoRegister);
        btnLogin.setOnClickListener(this);
        btnGoRegister.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.btnGoRegister) {
            startActivity(new Intent(this, RegisterActivity.class));
            return;
        }
        if (id != R.id.btnLogin) {
            return;
        }

        String countryCode = etCountryCode.getText().toString().trim();
        String account = etAccount.getText().toString().trim();
        String password = etPassword.getText().toString();

        if (TextUtils.isEmpty(countryCode) || TextUtils.isEmpty(account) || TextUtils.isEmpty(password)) {
            Toast.makeText(this, R.string.tip_input_required, Toast.LENGTH_SHORT).show();
            return;
        }

        ILoginCallback callback = new ILoginCallback() {
            @Override
            public void onSuccess(User user) {
                Toast.makeText(LoginActivity.this, R.string.login_success, Toast.LENGTH_SHORT).show();
                DoorbellCallManager.getInstance().ensureInit();
                // #region agent log
                try {
                    AgentDebugLog.log("A", "LoginActivity.onSuccess", "login_ensure_doorbell_init",
                            new org.json.JSONObject());
                } catch (Throwable ignored) {
                }
                // #endregion
                goMain();
            }

            @Override
            public void onError(String code, String error) {
                Toast.makeText(LoginActivity.this,
                        getString(R.string.error_with_code, code, error),
                        Toast.LENGTH_LONG).show();
            }
        };

        if (ValidatorUtil.isEmail(account)) {
            ThingHomeSdk.getUserInstance().loginWithEmail(countryCode, account, password, callback);
        } else {
            ThingHomeSdk.getUserInstance().loginWithPhonePassword(countryCode, account, password, callback);
        }
    }

    private void goMain() {
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
