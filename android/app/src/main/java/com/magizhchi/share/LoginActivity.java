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

import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.network.ApiService;
import com.magizhchi.share.network.TokenManager;

import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginActivity extends AppCompatActivity {

    private EditText etIdentifier;
    private Button   btnSendOtp;
    private ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Already logged in → skip to home
        if (TokenManager.getInstance(this).getAccessToken() != null) {
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_login);

        etIdentifier = findViewById(R.id.etIdentifier);
        btnSendOtp   = findViewById(R.id.btnSendOtp);
        progressBar  = findViewById(R.id.progressBar);

        btnSendOtp.setOnClickListener(v -> sendOtp());

        // "Create one free" link → RegisterActivity
        TextView tvSignUp = findViewById(R.id.tvSignUp);
        if (tvSignUp != null) {
            tvSignUp.setOnClickListener(v ->
                    startActivity(new Intent(this, RegisterActivity.class)));
        }
    }

    private void sendOtp() {
        String identifier = etIdentifier.getText().toString().trim();
        if (identifier.isEmpty()) {
            etIdentifier.setError("Enter your phone number or email");
            etIdentifier.requestFocus();
            return;
        }

        setLoading(true);

        Map<String, String> body = new HashMap<>();
        body.put("identifier", identifier);

        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.sendLoginOtp(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                setLoading(false);
                if (response.isSuccessful()) {
                    Intent intent = new Intent(LoginActivity.this, OtpActivity.class);
                    intent.putExtra(OtpActivity.EXTRA_IDENTIFIER,      identifier);
                    intent.putExtra(OtpActivity.EXTRA_IS_EMAIL,        identifier.contains("@"));
                    intent.putExtra(OtpActivity.EXTRA_IS_REGISTRATION, false);
                    startActivity(intent);
                } else {
                    String msg = "No account found. Please check your details or create an account.";
                    Toast.makeText(LoginActivity.this, msg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                setLoading(false);
                Toast.makeText(LoginActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSendOtp.setEnabled(!loading);
    }
}
