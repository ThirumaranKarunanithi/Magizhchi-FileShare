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

import com.magizhchi.share.model.FileMessageResponse;
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
    private final List<FileMessageResponse> items = new ArrayList<>();
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
                .enqueue(new Callback<List<FileMessageResponse>>() {
            @Override public void onResponse(Call<List<FileMessageResponse>> call,
                                              Response<List<FileMessageResponse>> response) {
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
                List<FileMessageResponse> body = response.body() != null
                        ? response.body() : new ArrayList<>();
                items.clear();
                items.addAll(body);

                long total = 0;
                for (FileMessageResponse f : items) total += f.getFileSizeBytes();
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
            @Override public void onFailure(Call<List<FileMessageResponse>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(SharedFilesActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void downloadFile(FileMessageResponse f) {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.getDownloadUrl(f.getId()).enqueue(new Callback<Map<String, String>>() {
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
            FileMessageResponse f = items.get(position);
            h.tvIcon.setText(FormatUtils.fileIcon(f.getCategory(), f.getContentType()));
            h.tvFileName.setText(f.getOriginalFileName() != null ? f.getOriginalFileName() : "Untitled");
            h.tvMeta.setText(FormatUtils.formatBytes(f.getFileSizeBytes())
                    + "  ·  " + FormatUtils.formatDateTime(f.getSentAt()));
            h.tvFrom.setText("📥 From " + (f.getSenderName() != null ? f.getSenderName() : "Someone"));
            h.btnDownload.setOnClickListener(v -> downloadFile(f));
            h.itemView.setOnClickListener(v -> downloadFile(f));
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
