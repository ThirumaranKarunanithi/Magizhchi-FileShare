package com.magizhchi.share;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.magizhchi.share.adapter.ConversationAdapter;
import com.magizhchi.share.model.AuthResponse;
import com.magizhchi.share.model.ConversationResponse;
import com.magizhchi.share.model.StorageUsageResponse;
import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.network.ApiService;
import com.magizhchi.share.network.TokenManager;
import com.magizhchi.share.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private ConversationAdapter conversationAdapter;
    private List<ConversationResponse> allConversations = new ArrayList<>();
    private ProgressBar progressBar;
    private TextView tvWelcome;
    private ImageView ivAvatar;
    private TextView tvAvatarInitials;
    private ImageButton btnBell;
    private TextView tvBellBadge;
    private TextView tvStorageUsed;
    private LinearLayout cardStorage;
    private LinearLayout cardShared;
    private EditText etSearch;
    private ChipGroup chipGroupToggle;
    private RecyclerView recyclerConversations;
    private WebSocket webSocket;
    private int pendingNotifications = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TokenManager tokenManager = TokenManager.getInstance(this);
        if (tokenManager.getAccessToken() == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
            return;
        }

        setContentView(R.layout.activity_main);

        initViews();
        setupConversationList();
        setupSearchBar();
        setupStorageCards();
        setupFab();
        loadUserData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConversations();
        loadStorageUsage();
        connectWebSocket();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (webSocket != null) {
            webSocket.close(1000, "Activity paused");
            webSocket = null;
        }
    }

    private void initViews() {
        progressBar        = findViewById(R.id.progressBar);
        tvWelcome          = findViewById(R.id.tvWelcome);
        ivAvatar           = findViewById(R.id.ivAvatar);
        tvAvatarInitials   = findViewById(R.id.tvAvatarInitials);
        btnBell            = findViewById(R.id.btnBell);
        tvBellBadge        = findViewById(R.id.tvBellBadge);
        tvStorageUsed      = findViewById(R.id.tvStorageUsed);
        cardStorage        = findViewById(R.id.cardStorage);
        cardShared         = findViewById(R.id.cardShared);
        etSearch           = findViewById(R.id.etSearch);
        chipGroupToggle    = findViewById(R.id.chipGroupToggle);
        recyclerConversations = findViewById(R.id.recyclerConversations);

        btnBell.setOnClickListener(v ->
                Toast.makeText(this, "Notifications coming soon", Toast.LENGTH_SHORT).show());
    }

    private void setupConversationList() {
        conversationAdapter = new ConversationAdapter(this);
        recyclerConversations.setLayoutManager(new LinearLayoutManager(this));
        recyclerConversations.setAdapter(conversationAdapter);

        conversationAdapter.setListener(new ConversationAdapter.OnConversationClickListener() {
            @Override
            public void onConversationClick(ConversationResponse conversation) {
                Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                intent.putExtra(ChatActivity.EXTRA_CONVERSATION, new Gson().toJson(conversation));
                startActivity(intent);
            }

            @Override
            public void onConversationLongPress(ConversationResponse conversation, View anchorView) {
                PopupMenu popup = new PopupMenu(MainActivity.this, anchorView);
                popup.getMenu().add("Open");
                if ("GROUP".equalsIgnoreCase(conversation.getType())) {
                    popup.getMenu().add("Group Info");
                }
                popup.setOnMenuItemClickListener(item -> {
                    if ("Open".equals(item.getTitle().toString())) {
                        Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                        intent.putExtra(ChatActivity.EXTRA_CONVERSATION, new Gson().toJson(conversation));
                        startActivity(intent);
                    } else if ("Group Info".equals(item.getTitle().toString())) {
                        Intent intent = new Intent(MainActivity.this, GroupInfoActivity.class);
                        intent.putExtra(GroupInfoActivity.EXTRA_CONVERSATION_ID, conversation.getId());
                        intent.putExtra(GroupInfoActivity.EXTRA_CONVERSATION_NAME, conversation.getName());
                        startActivity(intent);
                    }
                    return true;
                });
                popup.show();
            }
        });
    }

    private void setupSearchBar() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterConversations(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filterConversations(String query) {
        if (query == null || query.trim().isEmpty()) {
            conversationAdapter.setConversations(allConversations);
            return;
        }
        String lower = query.toLowerCase();
        List<ConversationResponse> filtered = new ArrayList<>();
        for (ConversationResponse conv : allConversations) {
            if (conv.getName() != null && conv.getName().toLowerCase().contains(lower)) {
                filtered.add(conv);
            }
        }
        conversationAdapter.setConversations(filtered);
    }

    private void setupStorageCards() {
        cardStorage.setOnClickListener(v ->
                Toast.makeText(this, "My Storage — tap a conversation to browse files", Toast.LENGTH_SHORT).show());

        cardShared.setOnClickListener(v ->
                Toast.makeText(this, "Shared Files feature coming soon", Toast.LENGTH_SHORT).show());
    }

    private void setupFab() {
        FloatingActionButton fab = findViewById(R.id.fabNewGroup);
        fab.setOnClickListener(v -> showNewGroupDialog());
    }

    private void showNewGroupDialog() {
        EditText etGroupName = new EditText(this);
        etGroupName.setHint("Group name");

        new AlertDialog.Builder(this)
                .setTitle("New Group")
                .setView(etGroupName)
                .setPositiveButton("Create", (dialog, which) -> {
                    String name = etGroupName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        createGroup(name);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void createGroup(String name) {
        okhttp3.RequestBody namePart = okhttp3.RequestBody.create(
                name, okhttp3.MediaType.parse("text/plain"));

        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.createGroup(namePart, null).enqueue(new Callback<ConversationResponse>() {
            @Override
            public void onResponse(Call<ConversationResponse> call, Response<ConversationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(MainActivity.this, "Group created!", Toast.LENGTH_SHORT).show();
                    loadConversations();
                } else {
                    Toast.makeText(MainActivity.this, "Failed to create group", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ConversationResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadUserData() {
        TokenManager tm = TokenManager.getInstance(this);
        String displayName = tm.getDisplayName();
        String photoUrl = tm.getProfilePhotoUrl();

        if (displayName != null) {
            String firstName = displayName.split(" ")[0];
            tvWelcome.setText("Welcome back 👋 " + firstName);
        }

        if (photoUrl != null && !photoUrl.isEmpty()) {
            ivAvatar.setVisibility(View.VISIBLE);
            tvAvatarInitials.setVisibility(View.GONE);
            Glide.with(this)
                    .load(photoUrl)
                    .apply(RequestOptions.circleCropTransform())
                    .into(ivAvatar);
        } else if (displayName != null) {
            ivAvatar.setVisibility(View.GONE);
            tvAvatarInitials.setVisibility(View.VISIBLE);
            tvAvatarInitials.setText(FormatUtils.initials(displayName));
        }

        ivAvatar.setOnClickListener(v -> showProfileDialog());
        tvAvatarInitials.setOnClickListener(v -> showProfileDialog());
    }

    private void showProfileDialog() {
        TokenManager tm = TokenManager.getInstance(this);
        new AlertDialog.Builder(this)
                .setTitle(tm.getDisplayName() != null ? tm.getDisplayName() : "Profile")
                .setMessage("Email: " + (tm.getEmail() != null ? tm.getEmail() : "—"))
                .setPositiveButton("Logout", (dialog, which) -> logout())
                .setNegativeButton("Close", null)
                .show();
    }

    private void logout() {
        TokenManager.getInstance(this).clear();
        Intent intent = new Intent(this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void loadConversations() {
        progressBar.setVisibility(View.VISIBLE);
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.getConversations().enqueue(new Callback<List<ConversationResponse>>() {
            @Override
            public void onResponse(Call<List<ConversationResponse>> call, Response<List<ConversationResponse>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    allConversations = response.body();
                    conversationAdapter.setConversations(allConversations);
                } else {
                    Toast.makeText(MainActivity.this, "Failed to load conversations", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<ConversationResponse>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void loadStorageUsage() {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.getStorageUsage().enqueue(new Callback<StorageUsageResponse>() {
            @Override
            public void onResponse(Call<StorageUsageResponse> call, Response<StorageUsageResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    StorageUsageResponse usage = response.body();
                    String text = FormatUtils.formatBytes(usage.getUsedBytes())
                            + " / " + FormatUtils.formatBytes(usage.getLimitBytes())
                            + " (" + String.format("%.0f%%", usage.getUsedPercent()) + ")";
                    tvStorageUsed.setText(text);
                }
            }

            @Override
            public void onFailure(Call<StorageUsageResponse> call, Throwable t) {
                // Storage load failure is non-critical — fail silently
            }
        });
    }

    private void connectWebSocket() {
        TokenManager tm = TokenManager.getInstance(this);
        String userId = tm.getUserId();
        String token = tm.getAccessToken();
        if (userId == null || token == null) return;

        // Subscribe to notification topic via raw WebSocket
        String wsUrl = ApiClient.BASE_URL.replace("https://", "wss://")
                .replace("http://", "ws://")
                + "ws/notifications?token=" + token;

        OkHttpClient wsClient = new OkHttpClient();
        Request request = new Request.Builder().url(wsUrl).build();
        webSocket = wsClient.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onMessage(@androidx.annotation.NonNull WebSocket webSocket,
                                  @androidx.annotation.NonNull String text) {
                runOnUiThread(() -> {
                    pendingNotifications++;
                    tvBellBadge.setVisibility(View.VISIBLE);
                    tvBellBadge.setText(String.valueOf(pendingNotifications));
                });
            }

            @Override
            public void onFailure(@androidx.annotation.NonNull WebSocket webSocket,
                                  @androidx.annotation.NonNull Throwable t,
                                  okhttp3.Response response) {
                // WebSocket connection failure — non-critical, notifications just won't update
            }
        });
    }
}
