package com.magizhchi.share;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.magizhchi.share.model.FileMessageResponse;
import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.network.ApiService;
import com.magizhchi.share.utils.DottedGradientDrawable;
import com.magizhchi.share.utils.FormatUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Full-screen Upload composer — port of the web upload modal.
 *
 *   Inputs (Intent extras):
 *     EXTRA_CONVERSATION_ID   — String, target conversation
 *     EXTRA_CONVERSATION_TYPE — String, "GROUP" / "DIRECT" / "PERSONAL"
 *                               drives whether the Admins permission is enabled
 *     EXTRA_FOLDER_PATH       — String? optional folder to upload INTO
 *     EXTRA_URIS              — List<Uri> of files to upload
 *
 *   Result:
 *     RESULT_OK with EXTRA_UPLOAD_COUNT — number of files actually uploaded
 *     RESULT_CANCELED if the user backed out
 */
public class UploadActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION_ID   = "conversationId";
    public static final String EXTRA_CONVERSATION_TYPE = "conversationType";
    public static final String EXTRA_FOLDER_PATH       = "folderPath";
    public static final String EXTRA_URIS              = "uris";
    public static final String EXTRA_UPLOAD_COUNT      = "uploaded";

    /** Permission picker values — same enum as the web. */
    private static final String PERM_CAN_DOWNLOAD       = "CAN_DOWNLOAD";
    private static final String PERM_VIEW_ONLY          = "VIEW_ONLY";
    private static final String PERM_ADMIN_ONLY         = "ADMIN_ONLY_DOWNLOAD";

    private String conversationId;
    private String conversationType;
    private String folderPath;
    private List<Uri> uris = new ArrayList<>();

    private LinearLayout permAnyone, permViewOnly, permAdmins;
    private TextView tvPermHint;
    private EditText etCaption;
    private Button btnUpload, btnCancel;
    private ProgressBar uploadProgress;
    private String selectedPerm = PERM_CAN_DOWNLOAD;
    private int uploadedCount = 0;
    private int totalUploaded = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_upload);

        // Same dotted-gradient as the chat screen.
        View root = findViewById(R.id.uploadRoot);
        if (root != null) root.setBackground(new DottedGradientDrawable(getResources()));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        // Parse extras
        conversationId   = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        conversationType = getIntent().getStringExtra(EXTRA_CONVERSATION_TYPE);
        folderPath       = getIntent().getStringExtra(EXTRA_FOLDER_PATH);
        ArrayList<Uri> incoming = getIntent().getParcelableArrayListExtra(EXTRA_URIS);
        uris = incoming != null ? new ArrayList<>(incoming) : new ArrayList<>();

        if (conversationId == null || uris.isEmpty()) {
            Toast.makeText(this, "Nothing to upload.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        // Header summary
        TextView tvFileCount   = findViewById(R.id.tvFileCount);
        TextView tvDestination = findViewById(R.id.tvDestination);
        tvFileCount.setText(uris.size() + " file" + (uris.size() != 1 ? "s" : "") + " selected");
        tvDestination.setText(folderPath != null && !folderPath.isEmpty()
                ? "Uploading into 📁 " + folderPath
                : "Uploading at the top level");

        // File-name list (compact)
        LinearLayout fileList = findViewById(R.id.fileList);
        for (Uri u : uris) fileList.addView(makeFileChip(u));

        // Permission pills
        permAnyone   = findViewById(R.id.permAnyone);
        permViewOnly = findViewById(R.id.permViewOnly);
        permAdmins   = findViewById(R.id.permAdmins);
        tvPermHint   = findViewById(R.id.tvPermHint);
        etCaption    = findViewById(R.id.etCaption);
        btnUpload    = findViewById(R.id.btnUpload);
        btnCancel    = findViewById(R.id.btnCancel);
        uploadProgress = findViewById(R.id.uploadProgress);

        permAnyone.setOnClickListener(v -> setPermission(PERM_CAN_DOWNLOAD));
        permViewOnly.setOnClickListener(v -> setPermission(PERM_VIEW_ONLY));
        permAdmins.setOnClickListener(v -> {
            if ("GROUP".equalsIgnoreCase(conversationType)) {
                setPermission(PERM_ADMIN_ONLY);
            } else {
                Toast.makeText(this,
                        "Available only when uploading to a group.",
                        Toast.LENGTH_LONG).show();
            }
        });
        // Visually grey out Admins outside group conversations (matches web).
        if (!"GROUP".equalsIgnoreCase(conversationType)) {
            permAdmins.setAlpha(0.45f);
        }

        btnCancel.setOnClickListener(v -> { setResult(RESULT_CANCELED); finish(); });
        btnUpload.setOnClickListener(v -> startUpload());

        // Default state
        setPermission(PERM_CAN_DOWNLOAD);
    }

    @Override public boolean onSupportNavigateUp() { onBackPressed(); return true; }

    private TextView makeFileChip(Uri uri) {
        TextView tv = new TextView(this);
        tv.setText("📎 " + getDisplayName(uri));
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(12);
        tv.setMaxLines(1);
        tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int padH = (int) (10 * getResources().getDisplayMetrics().density);
        int padV = (int) (4  * getResources().getDisplayMetrics().density);
        tv.setPadding(padH, padV, padH, padV);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = (int) (4 * getResources().getDisplayMetrics().density);
        tv.setLayoutParams(lp);
        return tv;
    }

    private void setPermission(String value) {
        selectedPerm = value;
        applyPillStyle(permAnyone,   PERM_CAN_DOWNLOAD.equals(value), "#0284C7");
        applyPillStyle(permViewOnly, PERM_VIEW_ONLY.equals(value),    "#0284C7");
        applyPillStyle(permAdmins,   PERM_ADMIN_ONLY.equals(value),   "#0284C7");

        // Hint copy
        switch (value) {
            case PERM_VIEW_ONLY:
                tvPermHint.setText("Recipients can preview only — downloads disabled.");
                break;
            case PERM_ADMIN_ONLY:
                tvPermHint.setText("Only group admins can download. Other members get view-only access.");
                break;
            case PERM_CAN_DOWNLOAD:
            default:
                tvPermHint.setText("Anyone with access can preview AND download.");
                break;
        }
    }

    private void applyPillStyle(LinearLayout pill, boolean active, String activeColor) {
        pill.setBackgroundResource(active
                ? R.drawable.bg_perm_pill_active
                : R.drawable.bg_perm_pill_inactive);
        // Re-color child text views
        int color = active ? android.graphics.Color.parseColor(activeColor) : 0xFFFFFFFF;
        int subColor = active ? android.graphics.Color.parseColor(activeColor) : 0xCCFFFFFF;
        for (int i = 0; i < pill.getChildCount(); i++) {
            View c = pill.getChildAt(i);
            if (c instanceof TextView) {
                ((TextView) c).setTextColor(i <= 1 ? color : subColor);
            }
        }
    }

    // ── Upload pipeline ──────────────────────────────────────────────────────

    private void startUpload() {
        btnUpload.setEnabled(false);
        btnCancel.setEnabled(false);
        uploadProgress.setVisibility(View.VISIBLE);
        uploadProgress.setProgress(0);
        uploadedCount = 0;
        totalUploaded = uris.size();

        String caption = etCaption.getText().toString().trim();
        for (Uri u : uris) uploadOne(u, caption);
    }

    private void uploadOne(Uri uri, String caption) {
        try {
            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) mimeType = "application/octet-stream";

            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) {
                Toast.makeText(this, "Cannot read file: " + getDisplayName(uri),
                        Toast.LENGTH_SHORT).show();
                onOneDone(false);
                return;
            }
            byte[] bytes = readAll(is);
            is.close();

            String name = getDisplayName(uri);
            RequestBody fileBody     = RequestBody.create(bytes, MediaType.parse(mimeType));
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", name, fileBody);
            RequestBody captionPart  = RequestBody.create(caption,             MediaType.parse("text/plain"));
            RequestBody mentionsPart = RequestBody.create("",                  MediaType.parse("text/plain"));
            RequestBody permPart     = RequestBody.create(selectedPerm,        MediaType.parse("text/plain"));
            RequestBody folderPart   = RequestBody.create(folderPath != null ? folderPath : "",
                                                          MediaType.parse("text/plain"));

            ApiService api = ApiClient.getInstance(this).getApiService();
            api.sendFileWithOptions(conversationId, filePart, captionPart, mentionsPart,
                                    permPart, folderPart)
                    .enqueue(new Callback<FileMessageResponse>() {
                @Override public void onResponse(Call<FileMessageResponse> call, Response<FileMessageResponse> response) {
                    onOneDone(response.isSuccessful() && response.body() != null);
                }
                @Override public void onFailure(Call<FileMessageResponse> call, Throwable t) {
                    Toast.makeText(UploadActivity.this,
                            "Failed: " + name + " — " + t.getMessage(),
                            Toast.LENGTH_LONG).show();
                    onOneDone(false);
                }
            });
        } catch (IOException e) {
            Toast.makeText(this, "Read error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            onOneDone(false);
        }
    }

    private void onOneDone(boolean ok) {
        uploadedCount++;
        int pct = (int) Math.min(100, Math.round(uploadedCount * 100.0 / totalUploaded));
        uploadProgress.setProgress(pct);
        if (uploadedCount < totalUploaded) return;

        // All done
        Toast.makeText(this,
                uploadedCount + " of " + totalUploaded + " file" + (totalUploaded != 1 ? "s" : "")
                        + " uploaded.",
                Toast.LENGTH_SHORT).show();
        Intent data = new Intent();
        data.putExtra(EXTRA_UPLOAD_COUNT, uploadedCount);
        setResult(RESULT_OK, data);
        finish();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String getDisplayName(Uri uri) {
        String name = "file";
        try (android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null) {
                int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
                if (nameIndex >= 0 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex);
                }
            }
        } catch (Exception ignored) {}
        return name;
    }

    private static byte[] readAll(InputStream is) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int n;
        while ((n = is.read(chunk)) != -1) b.write(chunk, 0, n);
        return b.toByteArray();
    }
}
