package com.magizhchi.share;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.magizhchi.share.model.StorageUsageResponse;
import com.magizhchi.share.utils.FormatUtils;

import java.util.List;

/**
 * Storage Usage popup mirroring the web StorageModal:
 *   - Hero card with used / limit / percent / free
 *   - Three tabs: Overview · Groups · Top Files
 *   - Overview shows Personal · Direct Chats · Groups bytes with bar fills
 *   - Groups tab lists per-group usage rows
 *   - Top Files tab lists the largest uploads with sizes
 */
public class StorageUsageDialog extends Dialog {

    private final StorageUsageResponse data;

    public StorageUsageDialog(@NonNull Context ctx, @NonNull StorageUsageResponse data) {
        super(ctx);
        this.data = data;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(LayoutInflater.from(getContext()).inflate(R.layout.dialog_storage_usage, null));

        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        bind();
    }

    private void bind() {
        TextView tvUsed       = findViewById(R.id.tvUsed);
        TextView tvOfTotal    = findViewById(R.id.tvOfTotal);
        TextView tvPercent    = findViewById(R.id.tvPercent);
        TextView tvFree       = findViewById(R.id.tvFree);
        ProgressBar pbHero    = findViewById(R.id.pbHero);
        ImageButton btnClose  = findViewById(R.id.btnClose);
        TextView tabOverview  = findViewById(R.id.tabOverview);
        TextView tabGroups    = findViewById(R.id.tabGroups);
        TextView tabTopFiles  = findViewById(R.id.tabTopFiles);
        LinearLayout tabBody  = findViewById(R.id.tabBody);

        long used  = data.getUsedBytes();
        long limit = data.getLimitBytes() > 0 ? data.getLimitBytes() : 5_368_709_120L;
        double pct = data.getUsedPercent();

        tvUsed.setText(FormatUtils.formatBytes(used));
        tvOfTotal.setText("of " + FormatUtils.formatBytes(limit));
        tvPercent.setText(String.format("%.1f%% used", pct));
        tvFree.setText(FormatUtils.formatBytes(Math.max(0, limit - used)) + " free");
        pbHero.setProgress((int) Math.min(100, Math.round(pct)));

        // Color the percent label by threshold (matches web)
        int color = pct >= 90 ? 0xFFFCA5A5 : pct >= 70 ? 0xFFFCD34D : 0xFF86EFAC;
        tvPercent.setTextColor(color);

        btnClose.setOnClickListener(v -> dismiss());

        // Tab switching
        View.OnClickListener tabListener = v -> {
            int active = v.getId();
            applyTabStyle(tabOverview, active == R.id.tabOverview);
            applyTabStyle(tabGroups,   active == R.id.tabGroups);
            applyTabStyle(tabTopFiles, active == R.id.tabTopFiles);
            tabBody.removeAllViews();
            if (active == R.id.tabOverview)      renderOverview(tabBody);
            else if (active == R.id.tabGroups)   renderGroups(tabBody);
            else                                  renderTopFiles(tabBody);
        };
        tabOverview.setOnClickListener(tabListener);
        tabGroups.setOnClickListener(tabListener);
        tabTopFiles.setOnClickListener(tabListener);

        // Default tab
        renderOverview(tabBody);
    }

    private void applyTabStyle(TextView tab, boolean active) {
        if (active) {
            tab.setBackgroundResource(R.drawable.bg_chip_active);
            tab.setTextColor(getContext().getResources().getColor(R.color.primaryMid, null));
        } else {
            tab.setBackgroundColor(Color.TRANSPARENT);
            tab.setTextColor(Color.WHITE);
        }
    }

    // ── Overview tab ──────────────────────────────────────────────────────────

    private void renderOverview(LinearLayout host) {
        long limit = Math.max(1, data.getLimitBytes());
        addBreakdownRow(host, "📁 Personal",     data.getPersonalBytes(), limit);
        addBreakdownRow(host, "👤 Direct Chats", data.getDirectBytes(),   limit);
        addBreakdownRow(host, "👥 Groups",       data.getGroupBytes(),    limit);
    }

    private void addBreakdownRow(LinearLayout host, String label, long bytes, long limit) {
        Context ctx = getContext();
        int dp12 = dp(12);
        int dp10 = dp(10);
        int dp4  = dp(4);

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundResource(R.drawable.bg_storage_panel);
        row.setPadding(dp12, dp10, dp12, dp10);

        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowLp.bottomMargin = dp(8);
        host.addView(row, rowLp);

        // Title row
        LinearLayout title = new LinearLayout(ctx);
        title.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView tvLabel = new TextView(ctx);
        tvLabel.setText(label);
        tvLabel.setTextColor(Color.WHITE);
        tvLabel.setTextSize(13);
        tvLabel.setTypeface(tvLabel.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams lpLabel = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        title.addView(tvLabel, lpLabel);

        TextView tvBytes = new TextView(ctx);
        tvBytes.setText(FormatUtils.formatBytes(bytes));
        tvBytes.setTextColor(0xCCFFFFFF);
        tvBytes.setTextSize(12);
        title.addView(tvBytes);

        // Progress bar
        ProgressBar pb = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgress((int) Math.min(100, Math.round(bytes * 100.0 / limit)));
        pb.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));
        pb.setProgressTintList(android.content.res.ColorStateList.valueOf(Color.WHITE));
        LinearLayout.LayoutParams lpBar = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(5));
        lpBar.topMargin = dp4;
        row.addView(pb, lpBar);
    }

    // ── Groups tab ────────────────────────────────────────────────────────────

    private void renderGroups(LinearLayout host) {
        List<StorageUsageResponse.GroupItem> groups = data.getGroupBreakdown();
        if (groups == null || groups.isEmpty()) {
            host.addView(emptyText("No group uploads yet"));
            return;
        }
        long limit = Math.max(1, data.getLimitBytes());
        for (StorageUsageResponse.GroupItem g : groups) {
            addBreakdownRow(host, "👥 " + (g.getName() != null ? g.getName() : "Group"),
                    g.getUsedBytes(), limit);
        }
    }

    // ── Top Files tab ─────────────────────────────────────────────────────────

    private void renderTopFiles(LinearLayout host) {
        List<StorageUsageResponse.TopFileItem> files = data.getTopFiles();
        if (files == null || files.isEmpty()) {
            host.addView(emptyText("No uploads yet"));
            return;
        }
        Context ctx = getContext();
        int dp12 = dp(12);
        int dp10 = dp(10);
        for (StorageUsageResponse.TopFileItem f : files) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setBackgroundResource(R.drawable.bg_storage_panel);
            row.setPadding(dp12, dp10, dp12, dp10);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp(8);
            host.addView(row, lp);

            TextView tvName = new TextView(ctx);
            tvName.setText(iconFor(f.getCategory()) + "  " + (f.getFileName() != null ? f.getFileName() : "Untitled"));
            tvName.setTextColor(Color.WHITE);
            tvName.setTextSize(13);
            tvName.setMaxLines(1);
            tvName.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(tvName, new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

            TextView tvSize = new TextView(ctx);
            tvSize.setText(FormatUtils.formatBytes(f.getSizeBytes()));
            tvSize.setTextColor(0xCCFFFFFF);
            tvSize.setTextSize(12);
            row.addView(tvSize);
        }
    }

    private TextView emptyText(String msg) {
        TextView tv = new TextView(getContext());
        tv.setText(msg);
        tv.setTextColor(0xCCFFFFFF);
        tv.setTextSize(13);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setPadding(0, dp(24), 0, dp(24));
        return tv;
    }

    private static String iconFor(String category) {
        if (category == null) return "📎";
        switch (category) {
            case "IMAGE":    return "🖼";
            case "VIDEO":    return "🎬";
            case "DOCUMENT": return "📄";
            case "AUDIO":    return "🎵";
            case "ARCHIVE":  return "🗜";
            default:         return "📎";
        }
    }

    private int dp(int v) {
        return Math.round(v * getContext().getResources().getDisplayMetrics().density);
    }
}
