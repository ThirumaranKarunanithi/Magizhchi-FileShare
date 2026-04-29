package com.magizhchi.share;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.magizhchi.share.model.StorageUsageResponse;
import com.magizhchi.share.model.UserSearchResponse;
import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.network.ApiService;
import com.magizhchi.share.network.TokenManager;
import com.magizhchi.share.utils.DottedGradientDrawable;
import com.magizhchi.share.utils.FormatUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Profile screen — Android port of the web ProfileModal. Shows the signed-in
 * user's avatar, display name, status message ("About me"), email, mobile
 * number, storage usage progress, and a logout button. Replaces the simpler
 * AlertDialog that MainActivity previously opened on avatar click.
 *
 * Data flow:
 *  1. Render whatever TokenManager has cached (instant) — name, email,
 *     possibly photo + initials.
 *  2. Fire /api/users/{me} to refresh status message, mobile number and
 *     latest profile photo URL (presigned, may differ from cached value).
 *  3. Fire /api/storage/usage to populate the storage card.
 */
public class ProfileActivity extends AppCompatActivity {

    private ImageView ivAvatar;
    private TextView tvInitials;
    private FrameLayout avatarFrame;
    private TextView tvName;
    private TextView btnEditName;
    private LinearLayout statusRow;
    private TextView tvStatus;
    private TextView tvEmail;
    private TextView tvMobile;
    private LinearLayout storageCard;
    private TextView tvStorageUsed;
    private ProgressBar storageBar;
    private Button btnLogout;

    /** Cached so the breakdown popup can reopen instantly. */
    private StorageUsageResponse storageUsageCache;

    /** Tracks the latest non-empty status so the placeholder doesn't overwrite real data. */
    private String currentStatusMessage;

    /** System image picker — lets the user swap their profile photo. */
    private final ActivityResultLauncher<String> photoPicker =
            registerForActivityResult(new ActivityResultContracts.GetContent(),
                    this::handlePickedPhoto);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Same dotted-blue programmatic background as the chat / login screens.
        View root = findViewById(R.id.profileRoot);
        if (root != null) root.setBackground(new DottedGradientDrawable(getResources()));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("My Profile");
        }

        ivAvatar       = findViewById(R.id.profileAvatar);
        tvInitials     = findViewById(R.id.profileInitials);
        avatarFrame    = findViewById(R.id.avatarFrame);
        tvName         = findViewById(R.id.profileName);
        btnEditName    = findViewById(R.id.btnEditName);
        statusRow      = findViewById(R.id.statusRow);
        tvStatus       = findViewById(R.id.profileStatus);
        tvEmail        = findViewById(R.id.profileEmail);
        tvMobile       = findViewById(R.id.profileMobile);
        storageCard    = findViewById(R.id.profileStorageCard);
        tvStorageUsed  = findViewById(R.id.profileStorageUsed);
        storageBar     = findViewById(R.id.profileStorageBar);
        btnLogout      = findViewById(R.id.btnLogout);

        bindCachedUser();
        loadFreshUser();
        loadStorageUsage();

        storageCard.setOnClickListener(v -> openStorageUsagePopup());
        btnLogout.setOnClickListener(v -> confirmLogout());

        // Tapping the avatar (or the camera badge inside it) opens the image
        // picker so the user can swap their profile photo. The picker handles
        // its own runtime permissions on Android 13+.
        avatarFrame.setOnClickListener(v -> photoPicker.launch("image/*"));
        btnEditName.setOnClickListener(v -> editTextField(
                "Display name",
                tvName.getText().toString(),
                "displayName",
                false));
        statusRow.setOnClickListener(v -> editTextField(
                "Status message",
                currentStatusMessage != null ? currentStatusMessage : "",
                "statusMessage",
                true));
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** Paint whatever TokenManager already has so the screen is never empty. */
    private void bindCachedUser() {
        TokenManager tm = TokenManager.getInstance(this);
        String name = tm.getDisplayName();
        tvName.setText(name != null && !name.isEmpty() ? name : "—");
        tvInitials.setText(FormatUtils.initials(name));
        tvInitials.setVisibility(View.VISIBLE);
        ivAvatar.setVisibility(View.GONE);

        String email = tm.getEmail();
        tvEmail.setText(email != null && !email.isEmpty() ? email : "—");

        // Mobile + status come from the network call — show placeholder for now
        tvMobile.setText("—");

        loadAvatar(tm.getProfilePhotoUrl());
    }

    /** Pull fresh user details (mobile, statusMessage, photo URL). */
    private void loadFreshUser() {
        TokenManager tm = TokenManager.getInstance(this);
        String userId = tm.getUserId();
        if (userId == null) return;

        ApiService api = ApiClient.getInstance(this).getApiService();
        api.getUserById(userId).enqueue(new Callback<UserSearchResponse>() {
            @Override
            public void onResponse(Call<UserSearchResponse> call, Response<UserSearchResponse> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                UserSearchResponse u = response.body();

                if (u.getDisplayName() != null && !u.getDisplayName().isEmpty()) {
                    tvName.setText(u.getDisplayName());
                    tvInitials.setText(FormatUtils.initials(u.getDisplayName()));
                }

                String status = u.getStatusMessage();
                if (status != null && !status.trim().isEmpty()) {
                    currentStatusMessage = status.trim();
                    tvStatus.setText("“" + currentStatusMessage + "”");
                } else {
                    currentStatusMessage = "";
                    tvStatus.setText("Tap to add a status…");
                }

                String email = u.getEmail();
                if (email != null && !email.isEmpty()) tvEmail.setText(email);

                String mobile = u.getMobileNumber();
                tvMobile.setText(mobile != null && !mobile.isEmpty() ? mobile : "—");

                // Fresh presigned photo URL — re-load.
                if (u.getProfilePhotoUrl() != null && !u.getProfilePhotoUrl().isEmpty()) {
                    loadAvatar(u.getProfilePhotoUrl());
                }
            }

            @Override
            public void onFailure(Call<UserSearchResponse> call, Throwable t) {
                // Keep cached values — non-fatal.
            }
        });
    }

    /** Initials show first; photo overlays only on Glide success. */
    private void loadAvatar(String url) {
        if (url == null || url.isEmpty()) {
            ivAvatar.setVisibility(View.GONE);
            tvInitials.setVisibility(View.VISIBLE);
            return;
        }
        Glide.with(this)
                .load(url)
                // Presigned S3 URLs go stale — never cache.
                .apply(RequestOptions.circleCropTransform()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true))
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                 Target<Drawable> target, boolean isFirstResource) {
                        ivAvatar.setVisibility(View.GONE);
                        tvInitials.setVisibility(View.VISIBLE);
                        return true;
                    }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                                                    Target<Drawable> target, DataSource dataSource,
                                                    boolean isFirstResource) {
                        ivAvatar.setVisibility(View.VISIBLE);
                        tvInitials.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(ivAvatar);
    }

    private void loadStorageUsage() {
        ApiService api = ApiClient.getInstance(this).getApiService();
        api.getStorageUsage().enqueue(new Callback<StorageUsageResponse>() {
            @Override
            public void onResponse(Call<StorageUsageResponse> call, Response<StorageUsageResponse> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                StorageUsageResponse usage = response.body();
                storageUsageCache = usage;
                String text = FormatUtils.formatBytes(usage.getUsedBytes())
                        + " / " + FormatUtils.formatBytes(usage.getLimitBytes())
                        + " (" + String.format(java.util.Locale.getDefault(),
                                                "%.1f%%", usage.getUsedPercent()) + ")";
                tvStorageUsed.setText(text);
                double pct = usage.getUsedPercent();
                int progress = pct > 0 && pct < 1 ? 1
                        : (int) Math.min(Math.round(pct), 100);
                storageBar.setProgress(progress);
            }

            @Override
            public void onFailure(Call<StorageUsageResponse> call, Throwable t) {
                // Non-critical
            }
        });
    }

    private void openStorageUsagePopup() {
        if (storageUsageCache != null) {
            new StorageUsageDialog(this, storageUsageCache).show();
            return;
        }
        ApiClient.getInstance(this).getApiService().getStorageUsage()
                .enqueue(new Callback<StorageUsageResponse>() {
            @Override
            public void onResponse(Call<StorageUsageResponse> call, Response<StorageUsageResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    storageUsageCache = response.body();
                    new StorageUsageDialog(ProfileActivity.this, storageUsageCache).show();
                } else {
                    Toast.makeText(ProfileActivity.this,
                            "Could not load storage usage (" + response.code() + ").",
                            Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<StorageUsageResponse> call, Throwable t) {
                Toast.makeText(ProfileActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Sign out")
                .setMessage("Are you sure you want to sign out of Magizhchi Share?")
                .setPositiveButton("Sign out", (dialog, which) -> performLogout())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void performLogout() {
        TokenManager.getInstance(this).clear();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    // ── Edit profile ──────────────────────────────────────────────────────────

    /**
     * Pop up a single-line text editor for one PATCH /api/users/me field.
     * Empty-allowed only for statusMessage (the user can clear their status).
     */
    private void editTextField(String title, String currentValue,
                                String fieldKey, boolean allowEmpty) {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setText(currentValue);
        input.setSelection(currentValue != null ? currentValue.length() : 0);

        // Pad the EditText so it doesn't hug the dialog edges.
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String value = input.getText().toString().trim();
                    if (!allowEmpty && value.isEmpty()) {
                        Toast.makeText(this, title + " cannot be empty.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    patchProfile(fieldKey, value);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void patchProfile(String key, String value) {
        java.util.Map<String, String> body = new java.util.HashMap<>();
        body.put(key, value);
        ApiClient.getInstance(this).getApiService().updateMe(body)
                .enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call,
                                   Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful()) {
                    // Apply locally for instant feedback; then re-fetch.
                    if ("displayName".equals(key)) {
                        tvName.setText(value);
                        tvInitials.setText(FormatUtils.initials(value));
                        TokenManager tm = TokenManager.getInstance(ProfileActivity.this);
                        tm.saveUserInfo(tm.getUserId(), value, tm.getEmail(),
                                tm.getProfilePhotoUrl());
                    } else if ("statusMessage".equals(key)) {
                        currentStatusMessage = value;
                        tvStatus.setText(value.isEmpty()
                                ? "Tap to add a status…" : "“" + value + "”");
                    }
                    Toast.makeText(ProfileActivity.this, "Profile updated.",
                            Toast.LENGTH_SHORT).show();
                    loadFreshUser();
                } else {
                    Toast.makeText(ProfileActivity.this,
                            "Update failed: " + readErrorBody(response),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                Toast.makeText(ProfileActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Image-picker callback — uploads the selected photo to /api/users/me/photo. */
    private void handlePickedPhoto(@Nullable Uri uri) {
        if (uri == null) return;     // user cancelled
        try {
            // Read the picked image into memory. Photos are usually small (< 5 MB)
            // so we can safely buffer rather than streaming through a temp file.
            java.io.InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) {
                Toast.makeText(this, "Could not read the selected image.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) baos.write(buf, 0, n);
            in.close();
            byte[] bytes = baos.toByteArray();

            String mime = getContentResolver().getType(uri);
            if (mime == null) mime = "image/jpeg";
            String filename = "profile-" + System.currentTimeMillis()
                    + (mime.contains("png") ? ".png" : ".jpg");

            okhttp3.RequestBody filePart = okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse(mime), bytes);
            okhttp3.MultipartBody.Part part = okhttp3.MultipartBody.Part
                    .createFormData("file", filename, filePart);

            Toast.makeText(this, "Uploading photo…", Toast.LENGTH_SHORT).show();
            ApiClient.getInstance(this).getApiService().uploadProfilePhoto(part)
                    .enqueue(new Callback<java.util.Map<String, String>>() {
                @Override
                public void onResponse(Call<java.util.Map<String, String>> call,
                                       Response<java.util.Map<String, String>> response) {
                    if (response.isSuccessful() && response.body() != null) {
                        String url = response.body().get("photoUrl");
                        if (url != null && !url.isEmpty()) {
                            loadAvatar(url);
                            TokenManager tm = TokenManager.getInstance(ProfileActivity.this);
                            tm.saveUserInfo(tm.getUserId(), tm.getDisplayName(),
                                    tm.getEmail(), url);
                        }
                        Toast.makeText(ProfileActivity.this, "Photo updated.",
                                Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(ProfileActivity.this,
                                "Upload failed: " + readErrorBody(response),
                                Toast.LENGTH_LONG).show();
                    }
                }
                @Override
                public void onFailure(Call<java.util.Map<String, String>> call, Throwable t) {
                    Toast.makeText(ProfileActivity.this,
                            "Network error: " + t.getMessage(),
                            Toast.LENGTH_LONG).show();
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "Could not upload photo: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Best-effort extraction of the server's error message for a Toast. */
    private static String readErrorBody(Response<?> response) {
        try {
            if (response.errorBody() == null) return "HTTP " + response.code();
            String raw = response.errorBody().string();
            try {
                com.google.gson.JsonObject obj = new com.google.gson.Gson()
                        .fromJson(raw, com.google.gson.JsonObject.class);
                if (obj != null) {
                    if (obj.has("message")) return obj.get("message").getAsString();
                    if (obj.has("error"))   return obj.get("error").getAsString();
                }
            } catch (Exception ignored) { /* not JSON */ }
            return raw.isEmpty() ? ("HTTP " + response.code()) : raw;
        } catch (Exception e) {
            return "HTTP " + response.code();
        }
    }
}
