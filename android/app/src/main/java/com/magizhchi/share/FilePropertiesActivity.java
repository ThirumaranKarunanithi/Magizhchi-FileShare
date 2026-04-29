package com.magizhchi.share;

import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.gson.Gson;
import com.magizhchi.share.model.FileMessageResponse;
import com.magizhchi.share.utils.DottedGradientDrawable;
import com.magizhchi.share.utils.FormatUtils;

/**
 * Properties screen — replaces the old AlertDialog. Handles both files
 * (FILE mode) and folders (FOLDER mode) so a single activity covers both
 * entry points from {@link com.magizhchi.share.adapter.FileMessageAdapter}.
 *
 * <p>Launch via:
 * <pre>
 *   Intent i = new Intent(ctx, FilePropertiesActivity.class);
 *   i.putExtra(EXTRA_MODE, MODE_FILE);
 *   i.putExtra(EXTRA_FILE_JSON, new Gson().toJson(fileMessageResponse));
 *   startActivity(i);
 * </pre>
 *
 * <p>Folder data has no DTO, so the host activity passes the individual
 * folder fields as extras instead of serialising a wrapper object.
 */
public class FilePropertiesActivity extends AppCompatActivity {

    public static final String EXTRA_MODE      = "mode";
    public static final String MODE_FILE       = "FILE";
    public static final String MODE_FOLDER     = "FOLDER";

    /** FILE mode — Gson-serialised FileMessageResponse. */
    public static final String EXTRA_FILE_JSON = "file_json";

    /** FOLDER mode — flat folder fields. */
    public static final String EXTRA_FOLDER_NAME       = "folder_name";
    public static final String EXTRA_FOLDER_PATH       = "folder_path";
    public static final String EXTRA_FOLDER_ITEM_COUNT = "folder_item_count";
    public static final String EXTRA_FOLDER_TOTAL_BYTES = "folder_total_bytes";
    public static final String EXTRA_FOLDER_LATEST_AT  = "folder_latest_at";

    private LinearLayout propertiesContainer;
    private TextView tvIcon, tvName, tvSubtitle;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file_properties);

        // Same dotted-blue gradient as the rest of the chat / profile chrome.
        View root = findViewById(R.id.propertiesRoot);
        if (root != null) root.setBackground(new DottedGradientDrawable(getResources()));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        tvIcon              = findViewById(R.id.tvIcon);
        tvName              = findViewById(R.id.tvName);
        tvSubtitle          = findViewById(R.id.tvSubtitle);
        propertiesContainer = findViewById(R.id.propertiesContainer);
        Button btnClose     = findViewById(R.id.btnClose);
        btnClose.setOnClickListener(v -> finish());

        String mode = getIntent().getStringExtra(EXTRA_MODE);
        if (MODE_FOLDER.equals(mode)) {
            renderFolder();
        } else {
            renderFile();
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) { finish(); return true; }
        return super.onOptionsItemSelected(item);
    }

    // ── FILE mode ────────────────────────────────────────────────────────────

    private void renderFile() {
        String json = getIntent().getStringExtra(EXTRA_FILE_JSON);
        FileMessageResponse f;
        try { f = new Gson().fromJson(json, FileMessageResponse.class); }
        catch (Exception e) { f = null; }

        if (f == null) {
            renderError("Couldn't load file properties.");
            return;
        }

        tvIcon.setText(FormatUtils.fileIcon(f.getCategory(), f.getContentType()));
        tvName.setText(f.getOriginalFileName() != null ? f.getOriginalFileName() : "Untitled");
        tvSubtitle.setText(FormatUtils.formatBytes(f.getFileSizeBytes())
                + (f.getCategory() != null ? "  ·  " + f.getCategory() : ""));

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("📄 File properties");

        addRow("Name",        f.getOriginalFileName());
        addRow("Extension",   extensionOf(f.getOriginalFileName()));
        addRow("Type",        f.getCategory());
        addRow("Size",        FormatUtils.formatBytes(f.getFileSizeBytes()));
        addRow("Description", f.getCaption() != null && !f.getCaption().isEmpty() ? f.getCaption() : null);
        addRow("Permission",  permissionLabel(safe(f.getDownloadPermission())));
        addRow("Uploaded by", f.getSenderName());
        addRow("Uploaded on", FormatUtils.formatDateTime(f.getSentAt()));
        if (f.getFolderPath() != null && !f.getFolderPath().isEmpty()) {
            addRow("Folder", f.getFolderPath());
        }
        addRow("Status", "⬆ Uploaded");
    }

    // ── FOLDER mode ──────────────────────────────────────────────────────────

    private void renderFolder() {
        String name      = getIntent().getStringExtra(EXTRA_FOLDER_NAME);
        String path      = getIntent().getStringExtra(EXTRA_FOLDER_PATH);
        int    itemCount = getIntent().getIntExtra(EXTRA_FOLDER_ITEM_COUNT, 0);
        long   totalBytes = getIntent().getLongExtra(EXTRA_FOLDER_TOTAL_BYTES, 0L);
        String latestAt  = getIntent().getStringExtra(EXTRA_FOLDER_LATEST_AT);

        tvIcon.setText("📁");
        tvName.setText(name != null ? name : "Folder");
        tvSubtitle.setText(itemCount + " item" + (itemCount != 1 ? "s" : "")
                + "  ·  " + FormatUtils.formatBytes(totalBytes));

        if (getSupportActionBar() != null) getSupportActionBar().setTitle("📁 Folder properties");

        addRow("Name",       name);
        addRow("Path",       path);
        addRow("Items",      itemCount + " file" + (itemCount != 1 ? "s" : ""));
        addRow("Total size", FormatUtils.formatBytes(totalBytes));
        if (latestAt != null && !latestAt.isEmpty()) {
            addRow("Updated", FormatUtils.formatDateTime(latestAt));
        }
        addRow("Status", "📁 Folder");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void renderError(String message) {
        tvIcon.setText("⚠");
        tvName.setText(message);
        tvSubtitle.setText("");
    }

    /**
     * Append a label/value row to the properties container. Empty / null
     * values render as "—" to make the row visually consistent with the
     * other rows. A 1-px divider sits between consecutive rows.
     */
    private void addRow(String label, String value) {
        if (propertiesContainer.getChildCount() > 0) addDivider();

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView lbl = new TextView(this);
        lbl.setText(label.toUpperCase());
        lbl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        lbl.setTextColor(0xCCFFFFFF);
        lbl.setLetterSpacing(0.05f);
        lbl.setTypeface(lbl.getTypeface(), android.graphics.Typeface.BOLD);

        TextView val = new TextView(this);
        val.setText(value == null || value.isEmpty() ? "—" : value);
        val.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        val.setTextColor(Color.WHITE);
        val.setGravity(Gravity.START);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(2);
        val.setLayoutParams(lp);

        row.addView(lbl);
        row.addView(val);
        propertiesContainer.addView(row);
    }

    private void addDivider() {
        View divider = new View(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(lp);
        divider.setBackgroundColor(0x33FFFFFF);
        propertiesContainer.addView(divider);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }

    private static String extensionOf(String name) {
        if (name == null) return null;
        int i = name.lastIndexOf('.');
        return (i <= 0 || i == name.length() - 1) ? null : name.substring(i + 1).toUpperCase();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String permissionLabel(String p) {
        if ("VIEW_ONLY".equals(p))           return "👁 View only · downloads disabled";
        if ("ADMIN_ONLY_DOWNLOAD".equals(p)) return "🛡 Admin only · only group admins can download";
        return "⬇ Anyone with access can download";
    }
}
