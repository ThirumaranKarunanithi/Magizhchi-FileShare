package com.magizhchi.share;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.network.ApiService;
import com.magizhchi.share.utils.DottedGradientDrawable;
import com.magizhchi.share.utils.LinedGradientDrawable;

import java.util.HashMap;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class RegisterActivity extends AppCompatActivity {

    private EditText    etDisplayName;
    private EditText    etEmail;
    private EditText    etMobile;
    private Button      btnEmailChannel;
    private Button      btnSmsChannel;
    private Button      btnSendOtp;
    private ProgressBar progressBar;
    private TextView    tvError;

    /** Currently selected OTP delivery channel: "EMAIL" (default) or "SMS" */
    private String otpChannel = "EMAIL";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Outer screen — light sky-50 grid (same as the home screen).
        View root = findViewById(R.id.registerRoot);
        if (root != null) root.setBackground(new LinedGradientDrawable(getResources()));
        // Form card — vivid sky gradient with dot pattern, 28dp curved corners.
        View card = findViewById(R.id.registerCard);
        if (card != null) card.setBackground(new DottedGradientDrawable(getResources(), 28f));

        etDisplayName   = findViewById(R.id.etDisplayName);
        etEmail         = findViewById(R.id.etEmail);
        etMobile        = findViewById(R.id.etMobile);
        btnEmailChannel = findViewById(R.id.btnEmailChannel);
        btnSmsChannel   = findViewById(R.id.btnSmsChannel);
        btnSendOtp      = findViewById(R.id.btnSendOtp);
        progressBar     = findViewById(R.id.progressBar);
        tvError         = findViewById(R.id.tvError);

        // OTP channel selector — Email selected by default
        updateChannelButtons();
        // Title + subtitle of the card sit on a blue dotted gradient now —
        // make sure they read in white. (XML still references textPrimary
        // which IS white, so nothing to change there. Hint texts inside
        // helpers are handled per-field.)
        btnEmailChannel.setOnClickListener(v -> { otpChannel = "EMAIL"; updateChannelButtons(); });
        btnSmsChannel.setOnClickListener(v   -> { otpChannel = "SMS";   updateChannelButtons(); });

        btnSendOtp.setOnClickListener(v -> sendOtp());

        // Back arrow
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null) btnBack.setOnClickListener(v -> finish());

        // Already have an account link
        TextView tvSignIn = findViewById(R.id.tvSignIn);
        if (tvSignIn != null) tvSignIn.setOnClickListener(v -> finish()); // goes back to LoginActivity
    }

    /** Highlights the active channel button and dims the inactive one */
    private void updateChannelButtons() {
        boolean emailActive = "EMAIL".equals(otpChannel);

        btnEmailChannel.setBackgroundTintList(
                ContextCompat.getColorStateList(this,
                        emailActive ? android.R.color.white : R.color.colorGlassWhite));
        btnEmailChannel.setTextColor(emailActive
                ? ContextCompat.getColor(this, R.color.primaryMid)
                : ContextCompat.getColor(this, R.color.textSecondary));

        btnSmsChannel.setBackgroundTintList(
                ContextCompat.getColorStateList(this,
                        emailActive ? R.color.colorGlassWhite : android.R.color.white));
        btnSmsChannel.setTextColor(emailActive
                ? ContextCompat.getColor(this, R.color.textSecondary)
                : ContextCompat.getColor(this, R.color.primaryMid));
    }

    private void sendOtp() {
        String name   = etDisplayName.getText().toString().trim();
        String email  = etEmail.getText().toString().trim();
        String mobile = etMobile.getText().toString().trim();

        // ── Client-side validation ──────────────────────────────────────────
        if (name.isEmpty()) {
            showError("Full name is required.");
            etDisplayName.requestFocus();
            return;
        }
        if (email.isEmpty()) {
            showError("Email address is required.");
            etEmail.requestFocus();
            return;
        }
        if (!email.contains("@") || !email.contains(".")) {
            showError("Enter a valid email address.");
            etEmail.requestFocus();
            return;
        }
        if (mobile.isEmpty()) {
            showError("Mobile number is required.");
            etMobile.requestFocus();
            return;
        }
        hideError();
        setLoading(true);

        Map<String, String> body = new HashMap<>();
        body.put("displayName",  name);
        body.put("email",        email);
        body.put("mobileNumber", mobile);
        body.put("otpChannel",   otpChannel);

        ApiService api = ApiClient.getInstance(this).getApiService();
        api.sendRegisterOtp(body).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                setLoading(false);
                if (response.isSuccessful()) {
                    // Determine which identifier was used for the OTP
                    String otpIdentifier = "SMS".equals(otpChannel) ? mobile : email;
                    boolean identifierIsEmail = "EMAIL".equals(otpChannel);

                    Intent intent = new Intent(RegisterActivity.this, OtpActivity.class);
                    intent.putExtra(OtpActivity.EXTRA_IDENTIFIER,      otpIdentifier);
                    intent.putExtra(OtpActivity.EXTRA_IS_EMAIL,        identifierIsEmail);
                    intent.putExtra(OtpActivity.EXTRA_IS_REGISTRATION, true);
                    startActivity(intent);
                } else {
                    // Parse error message from backend if available
                    try {
                        String errorBody = response.errorBody() != null
                                ? response.errorBody().string() : "";
                        if (errorBody.contains("mobile number already exists")) {
                            showError("This mobile number is already registered.");
                        } else if (errorBody.contains("email already exists")) {
                            showError("This email address is already registered.");
                        } else {
                            showError("Could not create account. Please check your details.");
                        }
                    } catch (Exception e) {
                        showError("Something went wrong. Please try again.");
                    }
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                setLoading(false);
                showError("Network error: " + t.getMessage());
            }
        });
    }

    private void showError(String msg) {
        if (tvError != null) {
            tvError.setText(msg);
            tvError.setVisibility(View.VISIBLE);
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
        }
    }

    private void hideError() {
        if (tvError != null) tvError.setVisibility(View.GONE);
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnSendOtp.setEnabled(!loading);
    }
}
