package com.magizhchi.share;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.magizhchi.share.model.SharedResourceResponse;
import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.network.ApiService;
import com.magizhchi.share.utils.FormatUtils;
import com.magizhchi.share.utils.VioletGradientDrawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * "Shared with me" screen — full-page list of every file shared with the
 * current user (mirrors the web's Shared Files view). Replaces the previous
 * AlertDialog-based shared-files preview.
 */
public class SharedFilesActivity extends AppCompatActivity {

    private RecyclerView recycler;
    private ProgressBar progressBar;
    private TextView tvEmpty, tvCount, tvTotal;
    private final List<SharedResourceResponse> items = new ArrayList<>();
    private SharedAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shared_files);

        // Violet dotted gradient — visually distinct from the blue chat
        // gradient so the "Shared with me" identity matches the web's
        // purple-accent "Shared in this space" section.
        View root = findViewById(R.id.sharedRoot);
        if (root != null) root.setBackground(new VioletGradientDrawable(getResources()));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        recycler    = findViewById(R.id.recyclerShared);
        progressBar = findViewById(R.id.progressBar);
        tvEmpty     = findViewById(R.id.tvEmpty);
        tvCount     = findViewById(R.id.tvSharedCount);
        tvTotal     = findViewById(R.id.tvSharedTotal);

        adapter = new SharedAdapter();
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);

        loadShared();
    }

    @Override
    public boolean onSupportNavigateUp() { finish(); return true; }

    private void loadShared() {
        progressBar.setVisibility(View.VISIBLE);
        tvEmpty.setVisibility(View.GONE);
        ApiClient.getInstance(this).getApiService().getSharedWithMe()
                .enqueue(new Callback<List<SharedResourceResponse>>() {
            @Override public void onResponse(Call<List<SharedResourceResponse>> call,
                                              Response<List<SharedResourceResponse>> response) {
                progressBar.setVisibility(View.GONE);
                if (!response.isSuccessful()) {
                    String msg = response.code() == 401
                            ? "Session expired. Please log in again."
                            : response.code() >= 500
                                    ? "Server unavailable. Please try later."
                                    : "Couldn't load shared files (" + response.code() + ").";
                    Toast.makeText(SharedFilesActivity.this, msg, Toast.LENGTH_LONG).show();
                    return;
                }
                List<SharedResourceResponse> body = response.body() != null
                        ? response.body() : new ArrayList<>();
                items.clear();
                items.addAll(body);

                long total = 0;
                for (SharedResourceResponse s : items) total += s.getSizeBytes();
                tvCount.setText(items.size() + " file" + (items.size() != 1 ? "s" : ""));
                tvTotal.setText(items.isEmpty()
                        ? "No shared files yet"
                        : FormatUtils.formatBytes(total) + " total");

                if (items.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                }
                adapter.notifyDataSetChanged();
            }
            @Override public void onFailure(Call<List<SharedResourceResponse>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(SharedFilesActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Download a shared file. Uses the SharedResource's fileMessageId to
     *  hit the existing {@code /api/files/{id}/download-url} endpoint. */
    private void downloadFile(SharedResourceResponse s) {
        if (s.getFileMessageId() == null) {
            Toast.makeText(this, "This share has no underlying file.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.getDownloadUrl(s.getFileMessageId()).enqueue(new Callback<Map<String, String>>() {
            @Override public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String url = response.body().get("url");
                    if (url == null) url = response.body().get("downloadUrl");
                    if (url != null) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        return;
                    }
                }
                Toast.makeText(SharedFilesActivity.this, "Couldn't get download URL", Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<Map<String, String>> call, Throwable t) {
                Toast.makeText(SharedFilesActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── Adapter ──────────────────────────────────────────────────────────────

    private class SharedAdapter extends RecyclerView.Adapter<SharedAdapter.VH> {

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_shared_file, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            SharedResourceResponse s = items.get(position);
            h.tvIcon.setText(FormatUtils.fileIcon(s.getCategory(), s.getContentType()));
            h.tvFileName.setText(s.getFileName() != null ? s.getFileName() : "Untitled");
            // Prefer the share's sentAt — when the file was originally
            // uploaded — falling back to sharedAt (when the share was
            // created) since either is informative for the user.
            String when = s.getFileSentAt() != null ? s.getFileSentAt() : s.getSharedAt();
            h.tvMeta.setText(FormatUtils.formatBytes(s.getSizeBytes())
                    + "  ·  " + FormatUtils.formatDateTime(when));
            // Use ownerName (the person who shared it with me), NOT
            // senderName — SharedResourceResponse has no senderName field.
            h.tvFrom.setText("📥 From " + (s.getOwnerName() != null ? s.getOwnerName() : "Someone"));
            h.btnDownload.setOnClickListener(v -> downloadFile(s));
            h.itemView.setOnClickListener(v -> downloadFile(s));
        }

        @Override public int getItemCount() { return items.size(); }

        class VH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvFileName, tvMeta, tvFrom;
            ImageButton btnDownload;
            VH(View v) {
                super(v);
                tvIcon      = v.findViewById(R.id.tvIcon);
                tvFileName  = v.findViewById(R.id.tvFileName);
                tvMeta      = v.findViewById(R.id.tvMeta);
                tvFrom      = v.findViewById(R.id.tvFrom);
                btnDownload = v.findViewById(R.id.btnDownload);
            }
        }
    }
}
