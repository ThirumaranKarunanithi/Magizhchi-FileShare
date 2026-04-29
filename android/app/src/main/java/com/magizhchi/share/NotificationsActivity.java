package com.magizhchi.share;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.google.gson.Gson;
import com.magizhchi.share.model.ConnectionRequestResponse;
import com.magizhchi.share.model.ConversationResponse;
import com.magizhchi.share.model.FileMessageResponse;
import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.network.ApiService;
import com.magizhchi.share.utils.DottedGradientDrawable;
import com.magizhchi.share.utils.FormatUtils;

import java.util.List;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Notifications screen — Android port of the web notification surface.
 *
 * The web doesn't have a single dedicated panel; instead it shows toasts
 * for live WS events and a separate ConnectionRequestsModal. We collapse
 * both into a single screen with two stacked sections:
 *
 *   1. Pending connection requests (Accept / Reject inline)
 *      → GET    /api/connections/requests/received
 *      → POST   /api/connections/request/{id}/accept
 *      → POST   /api/connections/request/{id}/reject
 *
 *   2. Recent files / folders shared with me — tap a row to jump to the
 *      hosting conversation.
 *      → GET    /api/share/shared-with-me
 *      → GET    /api/conversations  (used to build the list view target)
 *
 * Pull-to-refresh re-runs both fetches.
 */
public class NotificationsActivity extends AppCompatActivity {

    private LinearLayout requestsContainer;
    private LinearLayout sharesContainer;
    private TextView emptyRequests;
    private TextView emptyShares;
    private SwipeRefreshLayout swipeRefresh;
    private ProgressBar progressBar;

    /** Cached on first fetch so tapping a share row can resolve its conversation. */
    private List<ConversationResponse> conversationCache;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notifications);

        View root = findViewById(R.id.notifRoot);
        if (root != null) root.setBackground(new DottedGradientDrawable(getResources()));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        requestsContainer = findViewById(R.id.requestsContainer);
        sharesContainer   = findViewById(R.id.sharesContainer);
        emptyRequests     = findViewById(R.id.emptyRequests);
        emptyShares       = findViewById(R.id.emptyShares);
        swipeRefresh      = findViewById(R.id.swipeRefresh);
        progressBar       = findViewById(R.id.progressBar);

        swipeRefresh.setOnRefreshListener(this::loadAll);

        loadAll();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void loadAll() {
        progressBar.setVisibility(View.VISIBLE);
        loadConnectionRequests();
        loadSharedFiles();
    }

    private void stopRefreshIfDone() {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        progressBar.setVisibility(View.GONE);
    }

    // ── Section 1: connection requests ────────────────────────────────────────

    private void loadConnectionRequests() {
        ApiService api = ApiClient.getInstance(this).getApiService();
        api.getReceivedRequests().enqueue(new Callback<List<ConnectionRequestResponse>>() {
            @Override
            public void onResponse(Call<List<ConnectionRequestResponse>> call,
                                   Response<List<ConnectionRequestResponse>> response) {
                requestsContainer.removeAllViews();
                if (response.isSuccessful() && response.body() != null
                        && !response.body().isEmpty()) {
                    emptyRequests.setVisibility(View.GONE);
                    for (ConnectionRequestResponse req : response.body()) {
                        requestsContainer.addView(buildRequestCard(req));
                    }
                } else {
                    emptyRequests.setVisibility(View.VISIBLE);
                }
                stopRefreshIfDone();
            }

            @Override
            public void onFailure(Call<List<ConnectionRequestResponse>> call, Throwable t) {
                emptyRequests.setVisibility(View.VISIBLE);
                emptyRequests.setText("Could not load requests: " + t.getMessage());
                stopRefreshIfDone();
            }
        });
    }

    private View buildRequestCard(ConnectionRequestResponse req) {
        View card = LayoutInflater.from(this).inflate(
                R.layout.item_notification_request, requestsContainer, false);

        ImageView ivAvatar  = card.findViewById(R.id.ivAvatar);
        TextView  tvInits   = card.findViewById(R.id.tvInitials);
        TextView  tvName    = card.findViewById(R.id.tvName);
        TextView  tvTime    = card.findViewById(R.id.tvTime);
        Button    btnAccept = card.findViewById(R.id.btnAccept);
        Button    btnReject = card.findViewById(R.id.btnReject);

        String name = req.getSenderName() != null ? req.getSenderName() : "Unknown";
        tvName.setText(name);
        tvInits.setText(FormatUtils.initials(name));
        tvTime.setText(req.getCreatedAt() != null
                ? FormatUtils.formatDateTime(req.getCreatedAt()) : "");

        // Initials always visible first; photo overlays on Glide success.
        tvInits.setVisibility(View.VISIBLE);
        ivAvatar.setVisibility(View.GONE);
        loadAvatar(req.getSenderPhotoUrl(), ivAvatar, tvInits);

        btnAccept.setOnClickListener(v -> respondToRequest(req, true, card));
        btnReject.setOnClickListener(v -> respondToRequest(req, false, card));
        return card;
    }

    private void respondToRequest(ConnectionRequestResponse req, boolean accept, View card) {
        if (req.getId() == null) return;
        ApiService api = ApiClient.getInstance(this).getApiService();
        Call<ConnectionRequestResponse> call = accept
                ? api.acceptRequest(req.getId())
                : api.rejectRequest(req.getId());
        call.enqueue(new Callback<ConnectionRequestResponse>() {
            @Override
            public void onResponse(Call<ConnectionRequestResponse> call,
                                   Response<ConnectionRequestResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(NotificationsActivity.this,
                            accept ? "Connection accepted." : "Request rejected.",
                            Toast.LENGTH_SHORT).show();
                    requestsContainer.removeView(card);
                    if (requestsContainer.getChildCount() == 0) {
                        emptyRequests.setVisibility(View.VISIBLE);
                    }
                } else {
                    Toast.makeText(NotificationsActivity.this,
                            "Failed: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<ConnectionRequestResponse> call, Throwable t) {
                Toast.makeText(NotificationsActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── Section 2: recent shared files ────────────────────────────────────────

    private void loadSharedFiles() {
        ApiService api = ApiClient.getInstance(this).getApiService();

        // Cache the conversation list first — taps need to launch ChatActivity
        // with the full ConversationResponse JSON, and shared-with-me only
        // returns conversationId. Best-effort: if conversation fetch fails we
        // still show the rows but tapping shows a toast.
        api.getConversations().enqueue(new Callback<List<ConversationResponse>>() {
            @Override
            public void onResponse(Call<List<ConversationResponse>> call,
                                   Response<List<ConversationResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    conversationCache = response.body();
                }
                fetchSharedFiles();
            }
            @Override
            public void onFailure(Call<List<ConversationResponse>> call, Throwable t) {
                fetchSharedFiles();   // still try shares — taps will degrade
            }
        });
    }

    private void fetchSharedFiles() {
        ApiService api = ApiClient.getInstance(this).getApiService();
        api.getSharedWithMe().enqueue(new Callback<List<FileMessageResponse>>() {
            @Override
            public void onResponse(Call<List<FileMessageResponse>> call,
                                   Response<List<FileMessageResponse>> response) {
                sharesContainer.removeAllViews();
                if (response.isSuccessful() && response.body() != null
                        && !response.body().isEmpty()) {
                    emptyShares.setVisibility(View.GONE);
                    // Newest first (matches the web sidebar)
                    List<FileMessageResponse> sorted = new java.util.ArrayList<>(response.body());
                    sorted.sort((a, b) -> {
                        String ad = a.getSentAt() == null ? "" : a.getSentAt();
                        String bd = b.getSentAt() == null ? "" : b.getSentAt();
                        return bd.compareTo(ad);
                    });
                    int max = Math.min(sorted.size(), 50);
                    for (int i = 0; i < max; i++) {
                        sharesContainer.addView(buildShareCard(sorted.get(i)));
                    }
                } else {
                    emptyShares.setVisibility(View.VISIBLE);
                }
                stopRefreshIfDone();
            }

            @Override
            public void onFailure(Call<List<FileMessageResponse>> call, Throwable t) {
                emptyShares.setVisibility(View.VISIBLE);
                emptyShares.setText("Could not load recent shares: " + t.getMessage());
                stopRefreshIfDone();
            }
        });
    }

    private View buildShareCard(FileMessageResponse f) {
        View card = LayoutInflater.from(this).inflate(
                R.layout.item_notification_share, sharesContainer, false);

        TextView tvIcon = card.findViewById(R.id.tvIcon);
        TextView tvName = card.findViewById(R.id.tvFileName);
        TextView tvSub  = card.findViewById(R.id.tvSubtitle);

        tvIcon.setText(FormatUtils.fileIcon(f.getCategory(), f.getContentType()));
        tvName.setText(f.getOriginalFileName() != null ? f.getOriginalFileName() : "(unnamed)");

        StringBuilder sub = new StringBuilder();
        if (f.getSenderName() != null && !f.getSenderName().isEmpty()) {
            sub.append(f.getSenderName());
        }
        if (sub.length() > 0) sub.append(" · ");
        sub.append(FormatUtils.formatBytes(f.getFileSizeBytes()));
        if (f.getSentAt() != null) {
            sub.append(" · ").append(FormatUtils.formatDate(f.getSentAt()));
        }
        tvSub.setText(sub.toString());

        card.setOnClickListener(v -> openConversationFor(f));
        return card;
    }

    /** Resolve the ConversationResponse for a shared file and launch chat. */
    private void openConversationFor(FileMessageResponse f) {
        String convId = f.getConversationId();
        if (convId == null) {
            Toast.makeText(this, "This file isn't linked to a conversation.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        ConversationResponse target = null;
        if (conversationCache != null) {
            for (ConversationResponse c : conversationCache) {
                if (convId.equals(c.getId())) { target = c; break; }
            }
        }
        if (target == null) {
            Toast.makeText(this, "Could not open conversation.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION, new Gson().toJson(target));
        startActivity(intent);
    }

    // ── Avatar helper (initials first, photo overlays on success) ─────────────

    private void loadAvatar(String url, ImageView img, TextView initials) {
        if (url == null || url.isEmpty()) {
            img.setVisibility(View.GONE);
            initials.setVisibility(View.VISIBLE);
            return;
        }
        Glide.with(this)
                .load(url)
                .apply(RequestOptions.circleCropTransform()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true))
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                 Target<Drawable> target, boolean isFirstResource) {
                        img.setVisibility(View.GONE);
                        initials.setVisibility(View.VISIBLE);
                        return true;
                    }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                                                    Target<Drawable> target, DataSource dataSource,
                                                    boolean isFirstResource) {
                        img.setVisibility(View.VISIBLE);
                        initials.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(img);
    }
}
