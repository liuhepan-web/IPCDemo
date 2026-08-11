package com.ipc.demo.set;

import com.ipc.demo.set.R;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.thingclips.smart.android.user.api.IRegisterCallback;
import com.thingclips.smart.android.user.bean.User;
import com.thingclips.smart.home.sdk.ThingHomeSdk;
import com.thingclips.smart.sdk.api.IResultCallback;

import java.util.regex.Pattern;

/**
 * Register — mirrors homesdk-sample UserRegisterActivity.
 */
public class RegisterActivity extends AppCompatActivity implements View.OnClickListener {

    private static final int REGISTER_TYPE = 1;
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^([a-z0-9A-Z]+[-|.]?)+[a-z0-9A-Z]@([a-z0-9A-Z]+(-[a-z0-9A-Z]+)?\\.)+[a-zA-Z]{2,}$");

    private EditText etCountryCode;
    private EditText etAccount;
    private EditText etPassword;
    private EditText etCode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        etCountryCode = findViewById(R.id.etCountryCode);
        etAccount = findViewById(R.id.etAccount);
        etPassword = findViewById(R.id.etPassword);
        etCode = findViewById(R.id.etCode);
        Button btnCode = findViewById(R.id.btnCode);
        Button btnRegister = findViewById(R.id.btnRegister);
        btnCode.setOnClickListener(this);
        btnRegister.setOnClickListener(this);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    @Override
    public void onClick(View v) {
        String countryCode = etCountryCode.getText().toString().trim();
        String account = etAccount.getText().toString().trim();
        String password = etPassword.getText().toString();
        String code = etCode.getText().toString().trim();
        boolean isEmail = EMAIL_PATTERN.matcher(account).matches();

        int id = v.getId();
        if (id == R.id.btnCode) {
            if (TextUtils.isEmpty(countryCode) || TextUtils.isEmpty(account)) {
                Toast.makeText(this, R.string.tip_input_required, Toast.LENGTH_SHORT).show();
                return;
            }
            ThingHomeSdk.getUserInstance().sendVerifyCodeWithUserName(
                    account,
                    "",
                    countryCode,
                    REGISTER_TYPE,
                    new IResultCallback() {
                        @Override
                        public void onError(String errorCode, String error) {
                            Toast.makeText(RegisterActivity.this,
                                    getString(R.string.error_with_code, errorCode, error),
                                    Toast.LENGTH_LONG).show();
                        }

                        @Override
                        public void onSuccess() {
                            Toast.makeText(RegisterActivity.this, R.string.code_sent, Toast.LENGTH_SHORT).show();
                        }
                    });
            return;
        }

        if (id != R.id.btnRegister) {
            return;
        }
        if (TextUtils.isEmpty(countryCode) || TextUtils.isEmpty(account)
                || TextUtils.isEmpty(password) || TextUtils.isEmpty(code)) {
            Toast.makeText(this, R.string.tip_input_required, Toast.LENGTH_SHORT).show();
            return;
        }

        IRegisterCallback callback = new IRegisterCallback() {
            @Override
            public void onSuccess(User user) {
                Toast.makeText(RegisterActivity.this, R.string.register_success, Toast.LENGTH_LONG).show();
                finish();
            }

            @Override
            public void onError(String errorCode, String error) {
                Toast.makeText(RegisterActivity.this,
                        getString(R.string.error_with_code, errorCode, error),
                        Toast.LENGTH_LONG).show();
            }
        };

        if (isEmail) {
            ThingHomeSdk.getUserInstance().registerAccountWithEmail(
                    countryCode, account, password, code, callback);
        } else {
            ThingHomeSdk.getUserInstance().registerAccountWithPhone(
                    countryCode, account, password, code, callback);
        }
    }
}
