package com.magizhchi.share;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.magizhchi.share.model.AuthResponse;
import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.network.ApiService;
import com.magizhchi.share.network.TokenManager;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OtpActivity extends AppCompatActivity {

    public static final String EXTRA_IDENTIFIER      = "identifier";
    public static final String EXTRA_IS_EMAIL        = "is_email";
    /** true = account creation flow; false = login flow */
    public static final String EXTRA_IS_REGISTRATION = "is_registration";

    private EditText    etOtp;
    private Button      btnVerify;
    private ProgressBar progressBar;

    private String  identifier;
    private boolean isEmail;
    private boolean isRegistration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_otp);

        identifier     = getIntent().getStringExtra(EXTRA_IDENTIFIER);
        isEmail        = getIntent().getBooleanExtra(EXTRA_IS_EMAIL,        true);
        isRegistration = getIntent().getBooleanExtra(EXTRA_IS_REGISTRATION, false);

        // Subtitle: "code sent to your email / mobile"
        TextView tvSubtitle = findViewById(R.id.tvOtpSubtitle);
        if (tvSubtitle != null) {
            String channel = isEmail ? "email" : "mobile number";
            tvSubtitle.setText("Enter the 6-digit code sent to your " + channel + "\n" + identifier);
        }

        // Title
        TextView tvTitle = findViewById(R.id.tvOtpTitle);
        if (tvTitle != null) {
            tvTitle.setText(isRegistration ? "Verify your account" : "Sign in");
        }

        etOtp       = findViewById(R.id.etOtp);
        btnVerify   = findViewById(R.id.btnVerify);
        progressBar = findViewById(R.id.progressBar);

        btnVerify.setOnClickListener(v -> verifyOtp());

        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> finish());
        }
    }

    private void verifyOtp() {
        String otp = etOtp.getText().toString().trim();
        if (otp.length() != 6) {
            etOtp.setError("Enter the 6-digit OTP");
            etOtp.requestFocus();
            return;
        }

        setLoading(true);

        Map<String, String> body = new HashMap<>();
        body.put("identifier", identifier);
        body.put("code",       otp);

        ApiService api = ApiClient.getInstance(this).getApiService();

        Call<AuthResponse> call = isRegistration
                ? api.verifyRegisterOtp(body)
                : api.verifyLoginOtp(body);

        call.enqueue(new Callback<AuthResponse>() {
            @Override
            public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                setLoading(false);
                if (response.isSuccessful() && response.body() != null) {
                    AuthResponse auth = response.body();
                    TokenManager tm   = TokenManager.getInstance(OtpActivity.this);
                    tm.saveTokens(auth.getAccessToken(), auth.getRefreshToken());
                    tm.saveUserInfo(
                            auth.getUserId(),
                            auth.getDisplayName(),
                            auth.getEmail(),
                            auth.getProfilePhotoUrl()
                    );
                    Intent intent = new Intent(OtpActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(OtpActivity.this,
                            "Incorrect or expired OTP. Please try again.", Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<AuthResponse> call, Throwable t) {
                setLoading(false);
                Toast.makeText(OtpActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnVerify.setEnabled(!loading);
    }
}
