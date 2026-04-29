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
import android.graphics.Color;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.magizhchi.share.adapter.ConversationAdapter;
import com.magizhchi.share.adapter.FriendSearchAdapter;
import com.magizhchi.share.model.AuthResponse;
import com.magizhchi.share.model.ConnectionRequestResponse;
import com.magizhchi.share.model.ConversationResponse;
import com.magizhchi.share.model.StorageUsageResponse;
import com.magizhchi.share.model.FileMessageResponse;
import com.magizhchi.share.model.SharedResourceResponse;
import com.magizhchi.share.model.UserSearchResponse;
import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.network.ApiService;
import com.magizhchi.share.network.TokenManager;
import com.magizhchi.share.utils.FormatUtils;
import com.magizhchi.share.utils.LinedGradientDrawable;

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
    private FriendSearchAdapter friendSearchAdapter;
    private LinearLayout friendSearchSection;
    private RecyclerView recyclerFriendSearch;
    private ProgressBar friendSearchProgress;
    private TextView tvFriendSearchHeader;
    private TextView tvFriendSearchEmpty;

    /** Token used to abandon stale friend-search responses when the user keeps typing. */
    private long friendSearchSeq = 0;
    /** Pending debounced search Runnable so we can cancel it when the user keeps typing. */
    private Runnable pendingFriendSearch;
    private final android.os.Handler friendSearchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private ProgressBar progressBar;
    private TextView tvWelcome;
    private ImageView ivAvatar;
    private TextView tvAvatarInitials;
    private ImageButton btnBell;
    private TextView tvStorageUsed;
    private TextView tvSharedSubtitle;
    private TextView tvBellBadge;
    private LinearLayout cardStorage;
    private LinearLayout cardShared;
    private View storageProgressContainer;
    // Cached so the breakdown popup can reopen instantly after first fetch.
    private StorageUsageResponse storageUsageCache;
    private ConversationResponse personalConversationCache;
    private EditText etSearch;
    private TextView chipPeople;
    private TextView chipGroups;
    private ProgressBar storageProgressBar;
    private RecyclerView recyclerConversations;
    private boolean showingPeople = true;
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

        // Replace the static gradient with a programmatic gradient + grid
        // overlay so the home screen mirrors the web sidebar's lined texture.
        View root = findViewById(android.R.id.content);
        if (root != null) {
            root.setBackground(new LinedGradientDrawable(getResources()));
        }

        initViews();
        setupConversationList();
        setupSearchBar();
        setupStorageCards();
        setupFab();
        loadUserData();
        loadSharedFilesStats();

        // Pre-create the system notification channel + ask the user for
        // POST_NOTIFICATIONS on Android 13+. We only need to ask once;
        // refusal is sticky.
        com.magizhchi.share.utils.NotificationHelper.ensureChannel(this);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (androidx.core.app.ActivityCompat.checkSelfPermission(this,
                    android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this,
                        new String[] { android.Manifest.permission.POST_NOTIFICATIONS },
                        1001);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadConversations();
        loadStorageUsage();
        loadSharedFilesStats();
        loadUserData();         // refresh profile pic + display name from /users/me
        refreshBellBadge();
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
        tvSharedSubtitle   = findViewById(R.id.tvSharedSubtitle);
        cardStorage        = findViewById(R.id.cardStorage);
        cardShared         = findViewById(R.id.cardShared);
        etSearch              = findViewById(R.id.etSearch);
        chipPeople            = findViewById(R.id.chipPeople);
        chipGroups            = findViewById(R.id.chipGroups);
        storageProgressBar       = findViewById(R.id.storageProgressBar);
        storageProgressContainer = findViewById(R.id.storageProgressContainer);
        if (storageProgressContainer != null) {
            storageProgressContainer.setOnClickListener(v -> openStorageUsagePopup());
        }
        recyclerConversations = findViewById(R.id.recyclerConversations);

        // Friend-search section — hidden until the user types ≥ 2 chars.
        friendSearchSection   = findViewById(R.id.friendSearchSection);
        recyclerFriendSearch  = findViewById(R.id.recyclerFriendSearch);
        friendSearchProgress  = findViewById(R.id.friendSearchProgress);
        tvFriendSearchHeader  = findViewById(R.id.tvFriendSearchHeader);
        tvFriendSearchEmpty   = findViewById(R.id.tvFriendSearchEmpty);
        friendSearchAdapter   = new FriendSearchAdapter(this);
        recyclerFriendSearch.setLayoutManager(new LinearLayoutManager(this));
        recyclerFriendSearch.setAdapter(friendSearchAdapter);
        friendSearchAdapter.setListener(new FriendSearchAdapter.OnActionListener() {
            @Override public void onOpenChat(UserSearchResponse u)     { openDirectChatWith(u); }
            @Override public void onSendRequest(UserSearchResponse u)  { sendConnectionRequestTo(u); }
            @Override public void onAcceptRequest(UserSearchResponse u) { acceptConnectionRequestFrom(u); }
        });

        // Bell — open the full Notifications activity (connection requests +
        // recent shares). The badge clears immediately so the user doesn't
        // see a stale count after returning.
        btnBell.setOnClickListener(v -> {
            pendingNotifications = 0;
            refreshBellBadge();
            startActivity(new Intent(this, NotificationsActivity.class));
        });

        // Pill tab toggle
        chipPeople.setOnClickListener(v -> setActiveTab(true));
        chipGroups.setOnClickListener(v  -> setActiveTab(false));
    }

    /** Update the red dot/number on the bell to match `pendingNotifications`. */
    private void refreshBellBadge() {
        if (tvBellBadge == null) return;
        if (pendingNotifications <= 0) {
            tvBellBadge.setVisibility(View.GONE);
        } else {
            tvBellBadge.setText(pendingNotifications > 9 ? "9+" : String.valueOf(pendingNotifications));
            tvBellBadge.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Pop a simple Notifications dialog. The full notification UI will land
     * later — for now this surfaces the live unread count, lets the user
     * acknowledge and clears the badge.
     */
    private void showNotificationsDialog() {
        String body = pendingNotifications > 0
                ? pendingNotifications + " new "
                  + (pendingNotifications == 1 ? "notification" : "notifications")
                  + " since you last opened this screen.\n\nOpen any conversation to view the latest activity."
                : "You're all caught up — no new notifications right now.";
        new AlertDialog.Builder(this)
                .setTitle("🔔 Notifications")
                .setMessage(body)
                .setPositiveButton("OK", (d, w) -> {
                    pendingNotifications = 0;
                    refreshBellBadge();
                })
                .show();
    }

    private void setupConversationList() {
        conversationAdapter = new ConversationAdapter(this);
        recyclerConversations.setLayoutManager(new LinearLayoutManager(this));
        recyclerConversations.setAdapter(conversationAdapter);

        conversationAdapter.setListener(new ConversationAdapter.OnConversationClickListener() {
            @Override
            public void onConversationClick(ConversationResponse conversation) {
                // Opening a conversation clears its unread badge.
                conversationAdapter.clearUnread(conversation.getId());
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

    private void setActiveTab(boolean people) {
        showingPeople = people;
        // Inactive chip color: slate-600 — readable on the light grid bg.
        // Active chip stays sky-700 over the white pill bg.
        final int activeColor   = getResources().getColor(R.color.primaryMid, null);
        final int inactiveColor = 0xFF475569; // slate-600
        if (people) {
            chipPeople.setBackgroundResource(R.drawable.bg_chip_active);
            chipPeople.setTextColor(activeColor);
            chipGroups.setBackgroundColor(Color.TRANSPARENT);
            chipGroups.setTextColor(inactiveColor);
        } else {
            chipGroups.setBackgroundResource(R.drawable.bg_chip_active);
            chipGroups.setTextColor(activeColor);
            chipPeople.setBackgroundColor(Color.TRANSPARENT);
            chipPeople.setTextColor(inactiveColor);
        }
        filterConversations(etSearch.getText().toString());
    }

    private void setupSearchBar() {
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String q = s.toString().trim();
                filterConversations(q);

                // Cancel any in-flight debounce so we don't fire stale searches.
                if (pendingFriendSearch != null) {
                    friendSearchHandler.removeCallbacks(pendingFriendSearch);
                    pendingFriendSearch = null;
                }

                if (q.length() >= 2) {
                    showFriendSearchSection();
                    friendSearchProgress.setVisibility(View.VISIBLE);
                    tvFriendSearchEmpty.setVisibility(View.GONE);
                    pendingFriendSearch = () -> performFriendSearch(q);
                    // 300 ms debounce — matches the web's 350 ms feel.
                    friendSearchHandler.postDelayed(pendingFriendSearch, 300);
                } else {
                    hideFriendSearchSection();
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void filterConversations(String query) {
        // First filter by the active People/Groups tab. People = DIRECT chats,
        // Groups = GROUP conversations. PERSONAL is left out here (it has its
        // own tile up top via the My Storage card).
        String wantedType = showingPeople ? "DIRECT" : "GROUP";
        List<ConversationResponse> typeFiltered = new ArrayList<>();
        for (ConversationResponse conv : allConversations) {
            if (wantedType.equalsIgnoreCase(conv.getType())) {
                typeFiltered.add(conv);
            }
        }
        // Then narrow by the search query if present.
        if (query == null || query.trim().isEmpty()) {
            conversationAdapter.setConversations(typeFiltered);
            return;
        }
        String lower = query.toLowerCase();
        List<ConversationResponse> filtered = new ArrayList<>();
        for (ConversationResponse conv : typeFiltered) {
            if (conv.getName() != null && conv.getName().toLowerCase().contains(lower)) {
                filtered.add(conv);
            }
        }
        conversationAdapter.setConversations(filtered);
    }

    // ── Friend search ────────────────────────────────────────────────────────

    /** Reveal the search-results section and hide the conversation list. */
    private void showFriendSearchSection() {
        if (friendSearchSection != null) friendSearchSection.setVisibility(View.VISIBLE);
        if (recyclerConversations != null) recyclerConversations.setVisibility(View.GONE);
    }

    private void hideFriendSearchSection() {
        if (friendSearchSection != null) friendSearchSection.setVisibility(View.GONE);
        if (recyclerConversations != null) recyclerConversations.setVisibility(View.VISIBLE);
        friendSearchAdapter.setUsers(new ArrayList<>());
    }

    /**
     * Hit /api/users/search. The {@code seq} token is bumped on every call;
     * the response handler ignores results from a stale call so a slow
     * response can't paint over a fresher query.
     */
    private void performFriendSearch(String query) {
        final long mySeq = ++friendSearchSeq;
        ApiClient.getInstance(this).getApiService().searchUsers(query)
                .enqueue(new Callback<List<UserSearchResponse>>() {
            @Override
            public void onResponse(Call<List<UserSearchResponse>> call,
                                   Response<List<UserSearchResponse>> response) {
                if (mySeq != friendSearchSeq) return;     // stale — newer query in flight
                friendSearchProgress.setVisibility(View.GONE);

                if (!response.isSuccessful() || response.body() == null) {
                    friendSearchAdapter.setUsers(new ArrayList<>());
                    tvFriendSearchEmpty.setVisibility(View.VISIBLE);
                    tvFriendSearchEmpty.setText("Search failed (" + response.code() + ").");
                    tvFriendSearchHeader.setText("👤 People");
                    return;
                }

                // Filter out SELF and BLOCKED_BY_ME — they shouldn't appear
                // in friend-search results (matches the backend's web semantics).
                List<UserSearchResponse> visible = new ArrayList<>();
                for (UserSearchResponse u : response.body()) {
                    String s = u.getConnectionStatus();
                    if ("SELF".equalsIgnoreCase(s) || "BLOCKED_BY_ME".equalsIgnoreCase(s)) continue;
                    visible.add(u);
                }

                friendSearchAdapter.setUsers(visible);
                tvFriendSearchHeader.setText("👤 People · " + visible.size());
                tvFriendSearchEmpty.setVisibility(visible.isEmpty() ? View.VISIBLE : View.GONE);
                if (visible.isEmpty()) {
                    tvFriendSearchEmpty.setText("No people match \"" + query + "\".");
                }
            }

            @Override
            public void onFailure(Call<List<UserSearchResponse>> call, Throwable t) {
                if (mySeq != friendSearchSeq) return;
                friendSearchProgress.setVisibility(View.GONE);
                friendSearchAdapter.setUsers(new ArrayList<>());
                tvFriendSearchEmpty.setVisibility(View.VISIBLE);
                // Map common transient failures to actionable copy. The raw
                // exception text ("Unable to resolve host …") is correct but
                // unhelpful — most users just need to know "your phone lost
                // signal for a moment, try again."
                tvFriendSearchEmpty.setText(friendlyNetworkError(t));
            }
        });
    }

    /**
     * Translate a Throwable from a network call into a one-line message a
     * non-engineer can act on. Falls back to {@code t.getMessage()} for
     * exception types we don't recognise.
     */
    private static String friendlyNetworkError(Throwable t) {
        if (t instanceof java.net.UnknownHostException) {
            return "Couldn't reach the server. Check your internet connection and try again.";
        }
        if (t instanceof java.net.SocketTimeoutException) {
            return "The server took too long to respond. Try again in a moment.";
        }
        if (t instanceof java.net.ConnectException) {
            return "Couldn't connect to the server. Check your network and try again.";
        }
        if (t instanceof javax.net.ssl.SSLException) {
            return "Secure connection failed. Check your network and try again.";
        }
        return "Network error: " + (t.getMessage() != null ? t.getMessage() : "please try again.");
    }

    /** CONNECTED row tapped — open / create the direct chat with that user. */
    private void openDirectChatWith(UserSearchResponse u) {
        if (u == null || u.getId() == null) return;
        progressBar.setVisibility(View.VISIBLE);
        ApiClient.getInstance(this).getApiService()
                .openDirectConversation(u.getId())
                .enqueue(new Callback<ConversationResponse>() {
            @Override
            public void onResponse(Call<ConversationResponse> call,
                                   Response<ConversationResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    // Clear search so the conversation list is showing when
                    // the user comes back from the chat screen.
                    etSearch.setText("");
                    Intent intent = new Intent(MainActivity.this, ChatActivity.class);
                    intent.putExtra(ChatActivity.EXTRA_CONVERSATION,
                            new Gson().toJson(response.body()));
                    startActivity(intent);
                } else {
                    Toast.makeText(MainActivity.this,
                            "Could not open chat: " + readErrorBody(response),
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(Call<ConversationResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this,
                        friendlyNetworkError(t), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** NONE row's "🤝 Connect" tapped — fire a connection request. */
    private void sendConnectionRequestTo(UserSearchResponse u) {
        if (u == null || u.getId() == null) return;
        ApiClient.getInstance(this).getApiService()
                .sendConnectionRequest(u.getId())
                .enqueue(new Callback<ConnectionRequestResponse>() {
            @Override
            public void onResponse(Call<ConnectionRequestResponse> call,
                                   Response<ConnectionRequestResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MainActivity.this,
                            "Request sent to " + u.getDisplayName(), Toast.LENGTH_SHORT).show();
                    // Re-run the search so the row's status / button updates.
                    String q = etSearch.getText().toString().trim();
                    if (q.length() >= 2) performFriendSearch(q);
                } else {
                    Toast.makeText(MainActivity.this,
                            "Could not send request: " + readErrorBody(response),
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(Call<ConnectionRequestResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this,
                        friendlyNetworkError(t), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** PENDING_RECEIVED row's "✓ Accept" tapped — accept the request. */
    private void acceptConnectionRequestFrom(UserSearchResponse u) {
        if (u == null || u.getConnectionRequestId() == null) {
            Toast.makeText(this, "Could not find the connection request.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
        ApiClient.getInstance(this).getApiService()
                .acceptRequest(u.getConnectionRequestId())
                .enqueue(new Callback<ConnectionRequestResponse>() {
            @Override
            public void onResponse(Call<ConnectionRequestResponse> call,
                                   Response<ConnectionRequestResponse> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(MainActivity.this,
                            "Connected with " + u.getDisplayName(),
                            Toast.LENGTH_SHORT).show();
                    String q = etSearch.getText().toString().trim();
                    if (q.length() >= 2) performFriendSearch(q);
                    // Reload conversations so the new direct chat appears.
                    loadConversations();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Could not accept: " + readErrorBody(response),
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(Call<ConnectionRequestResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this,
                        friendlyNetworkError(t), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setupStorageCards() {
        // My Storage → open the user's personal-storage conversation. We
        // resolve it lazily on first tap (cached afterwards) so the home
        // screen doesn't pay an extra round-trip on every launch.
        cardStorage.setOnClickListener(v -> openMyStorage());

        // Shared Files → open the dedicated SharedFilesActivity (full screen
        // with stats banner + scrollable list, mirrors the web view).
        cardShared.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SharedFilesActivity.class)));
    }

    /** Resolve and open the personal-storage conversation. */
    private void openMyStorage() {
        if (personalConversationCache != null) {
            launchChat(personalConversationCache);
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        ApiClient.getInstance(this).getApiService().getPersonalConversation()
                .enqueue(new Callback<ConversationResponse>() {
            @Override
            public void onResponse(Call<ConversationResponse> call,
                                   Response<ConversationResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    personalConversationCache = response.body();
                    launchChat(personalConversationCache);
                } else {
                    Toast.makeText(MainActivity.this,
                            "Could not open My Storage (" + response.code() + ").",
                            Toast.LENGTH_SHORT).show();
                }
            }
            @Override
            public void onFailure(Call<ConversationResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void launchChat(ConversationResponse conv) {
        Intent intent = new Intent(MainActivity.this, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA_CONVERSATION, new Gson().toJson(conv));
        startActivity(intent);
    }

    /**
     * Show a dialog listing every file shared WITH the user. Tapping a row
     * opens the conversation that file lives in (so the user can preview /
     * download it from the chat view). Surfaces the count + total size in
     * the title so the dialog reads like a mini Storage card.
     */
    /** Refresh the Shared Files card subtitle (count + total size). */
    private void loadSharedFilesStats() {
        ApiClient.getInstance(this).getApiService().getSharedWithMe()
                .enqueue(new Callback<List<SharedResourceResponse>>() {
            @Override
            public void onResponse(Call<List<SharedResourceResponse>> call,
                                   Response<List<SharedResourceResponse>> response) {
                if (tvSharedSubtitle == null) return;
                if (response.isSuccessful() && response.body() != null) {
                    List<SharedResourceResponse> list = response.body();
                    if (list.isEmpty()) {
                        tvSharedSubtitle.setText("No shared files yet");
                    } else {
                        long total = 0;
                        for (SharedResourceResponse s : list) total += s.getSizeBytes();
                        tvSharedSubtitle.setText(list.size() + " file"
                                + (list.size() != 1 ? "s" : "")
                                + " · " + FormatUtils.formatBytes(total));
                    }
                } else {
                    tvSharedSubtitle.setText("Browse shared");
                }
            }
            @Override
            public void onFailure(Call<List<SharedResourceResponse>> call, Throwable t) {
                if (tvSharedSubtitle != null) tvSharedSubtitle.setText("Browse shared");
            }
        });
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
        // Build the JSON `data` part the backend expects:
        //   { "name": "<group name>", "memberIds": [] }
        // Sending a flat "name" form-field made the @RequestPart binder fail —
        // that's the source of the persistent "Failed to create group" error.
        com.google.gson.JsonObject data = new com.google.gson.JsonObject();
        data.addProperty("name", name);
        data.add("memberIds", new com.google.gson.JsonArray());

        okhttp3.RequestBody dataPart = okhttp3.RequestBody.create(
                data.toString(), okhttp3.MediaType.parse("application/json; charset=utf-8"));

        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.createGroupWithData(dataPart, null).enqueue(new Callback<ConversationResponse>() {
            @Override
            public void onResponse(Call<ConversationResponse> call, Response<ConversationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(MainActivity.this, "Group \"" + name + "\" created!", Toast.LENGTH_SHORT).show();
                    loadConversations();
                } else {
                    // Surface the ACTUAL server error so the user knows what
                    // went wrong (e.g. plan limit, validation error, etc.)
                    String reason = readErrorBody(response);
                    Toast.makeText(MainActivity.this,
                            "Failed to create group: " + reason,
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ConversationResponse> call, Throwable t) {
                Toast.makeText(MainActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Read the server's error body and return a friendly summary. */
    private static String readErrorBody(Response<?> response) {
        if (response == null) return "no response";
        try {
            String raw = response.errorBody() != null ? response.errorBody().string() : "";
            if (raw.isEmpty()) {
                return "HTTP " + response.code();
            }
            // Try parse a "message" field from JSON (Spring's standard shape)
            try {
                com.google.gson.JsonObject obj = new com.google.gson.Gson().fromJson(raw, com.google.gson.JsonObject.class);
                if (obj != null && obj.has("message")) return obj.get("message").getAsString();
                if (obj != null && obj.has("error"))   return obj.get("error").getAsString();
            } catch (Exception ignored) {}
            return raw.length() > 160 ? raw.substring(0, 160) + "…" : raw;
        } catch (Exception e) {
            return "HTTP " + response.code();
        }
    }

    private void loadUserData() {
        // Render whatever we have cached locally first so the UI is never blank.
        TokenManager tm = TokenManager.getInstance(this);
        renderUser(tm.getDisplayName(), tm.getProfilePhotoUrl());

        // Avatar click → full ProfileActivity (mirrors web ProfileModal).
        // The old AlertDialog (showProfileDialog) only showed name + email and
        // a logout button; the new screen surfaces avatar, status message,
        // mobile, storage usage and a styled sign-out flow.
        View.OnClickListener openProfile = v ->
                startActivity(new Intent(this, ProfileActivity.class));
        ivAvatar.setOnClickListener(openProfile);
        tvAvatarInitials.setOnClickListener(openProfile);

        // Then hit users/me to pull a FRESH profile (URL may be a presigned
        // S3 link that's expired since last login). Update cached TokenManager
        // values on success and re-render — that's why the profile pic
        // sometimes vanished after a session-lost / relogin cycle.
        ApiClient.getInstance(this).getApiService().getMe()
                .enqueue(new Callback<AuthResponse>() {
            @Override public void onResponse(Call<AuthResponse> call, Response<AuthResponse> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                AuthResponse me = response.body();
                String freshPhoto = me.getProfilePhotoUrl();
                String freshName  = me.getDisplayName();
                tm.saveUserInfo(
                        me.getUserId() != null ? me.getUserId() : tm.getUserId(),
                        freshName != null ? freshName : tm.getDisplayName(),
                        me.getEmail() != null ? me.getEmail() : tm.getEmail(),
                        freshPhoto);
                renderUser(freshName != null ? freshName : tm.getDisplayName(), freshPhoto);
            }
            @Override public void onFailure(Call<AuthResponse> call, Throwable t) {
                // Network blip — keep showing the cached values
            }
        });
    }

    /**
     * Paint the welcome name + profile pic. INITIALS render first (always
     * something on screen), then Glide attempts the photo; on success it
     * overlays + hides the initials, on failure the initials stay. This
     * fixes the "blank circle" bug we hit when presigned photo URLs expired.
     */
    private void renderUser(String displayName, String photoUrl) {
        if (displayName != null && !displayName.isEmpty()) {
            tvWelcome.setText(displayName.split(" ")[0]);
        }

        // Step 1 — always show initials so the header is never blank.
        tvAvatarInitials.setVisibility(View.VISIBLE);
        tvAvatarInitials.setText(displayName != null
                ? FormatUtils.initials(displayName) : "?");
        ivAvatar.setVisibility(View.GONE);

        if (photoUrl == null || photoUrl.isEmpty()) return;

        // Step 2 — attempt the photo; overlay only on success.
        // Disable Glide caches: presigned URLs go stale, and a cached failure
        // would otherwise prevent any future attempt from succeeding.
        Glide.with(MainActivity.this)
                .load(photoUrl)
                .apply(RequestOptions.circleCropTransform()
                        .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                        .skipMemoryCache(true))
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override
                    public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e,
                                                Object model,
                                                com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                boolean isFirstResource) {
                        // Keep initials visible — return true so Glide doesn't
                        // try its own placeholder/error drawable.
                        return true;
                    }
                    @Override
                    public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                                   Object model,
                                                   com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                   com.bumptech.glide.load.DataSource dataSource,
                                                   boolean isFirstResource) {
                        // Photo really loaded — overlay + hide initials.
                        ivAvatar.setVisibility(View.VISIBLE);
                        tvAvatarInitials.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(ivAvatar);
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
                    // Re-apply the active People/Groups filter + current search
                    // query, so the just-loaded list respects whichever tab is
                    // currently selected.
                    filterConversations(etSearch.getText().toString());
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
                    storageUsageCache = usage;  // breakdown popup uses this
                    // Show 1 decimal of precision — matches the web. Using
                    // "%.0f%%" here was rounding 0.18 → "0%", which looked
                    // broken for users with tiny uploads.
                    String text = FormatUtils.formatBytes(usage.getUsedBytes())
                            + " / " + FormatUtils.formatBytes(usage.getLimitBytes())
                            + " (" + String.format(java.util.Locale.getDefault(),
                                    "%.1f%%", usage.getUsedPercent()) + ")";
                    tvStorageUsed.setText(text);
                    if (storageProgressBar != null) {
                        // For sub-1% usage, still nudge the bar to 1 px so
                        // the user can SEE there's some content stored.
                        double pct = usage.getUsedPercent();
                        int progress = pct > 0 && pct < 1 ? 1
                                : (int) Math.min(Math.round(pct), 100);
                        storageProgressBar.setProgress(progress);
                    }
                }
            }

            @Override
            public void onFailure(Call<StorageUsageResponse> call, Throwable t) {
                // Storage load failure is non-critical — fail silently
            }
        });
    }

    /**
     * Open the StorageUsageDialog popup (Overview / Groups / Top Files).
     * Uses the cached response if we already have it; otherwise fetches.
     */
    private void openStorageUsagePopup() {
        if (storageUsageCache != null) {
            new StorageUsageDialog(this, storageUsageCache).show();
            return;
        }
        progressBar.setVisibility(View.VISIBLE);
        ApiClient.getInstance(this).getApiService().getStorageUsage()
                .enqueue(new Callback<StorageUsageResponse>() {
            @Override public void onResponse(Call<StorageUsageResponse> call,
                                              Response<StorageUsageResponse> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    storageUsageCache = response.body();
                    new StorageUsageDialog(MainActivity.this, storageUsageCache).show();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Could not load storage usage (" + response.code() + ").",
                            Toast.LENGTH_SHORT).show();
                }
            }
            @Override public void onFailure(Call<StorageUsageResponse> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(MainActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
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
                    refreshBellBadge();
                    // Pull fresh shared-stats too — a new share may have arrived
                    loadSharedFilesStats();
                    // Parse the event so we can bump the per-conversation
                    // unread count. The server sends NotificationEvent shaped
                    //   { type, payload: { conversationId, fileName, ... } }
                    // The "fileName" can include a "(N files)" summary suffix
                    // when a folder upload happens — surface that as N.
                    try {
                        com.google.gson.JsonObject root = new com.google.gson.Gson()
                                .fromJson(text, com.google.gson.JsonObject.class);
                        if (root == null || !root.has("type")) return;
                        String type = root.get("type").getAsString();
                        com.google.gson.JsonObject p = root.has("payload")
                                ? root.getAsJsonObject("payload") : null;
                        if (p == null) return;
                        if ("NEW_FILE".equals(type)) {
                            String convId = p.has("conversationId") ? p.get("conversationId").getAsString() : null;
                            String fileName = p.has("fileName") ? p.get("fileName").getAsString() : "";
                            String senderName = p.has("senderName") ? p.get("senderName").getAsString()
                                    : (p.has("uploaderName") ? p.get("uploaderName").getAsString() : null);
                            int incoming = 1;
                            // Folder uploads send "📁 Foo (5 files)" — extract N.
                            java.util.regex.Matcher m = java.util.regex.Pattern
                                    .compile("\\((\\d+)\\s+files?\\)", java.util.regex.Pattern.CASE_INSENSITIVE)
                                    .matcher(fileName);
                            if (m.find()) {
                                try { incoming = Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
                            }
                            if (convId != null) conversationAdapter.bumpUnread(convId, incoming);
                            // System notification — drops a heads-up on the
                            // notification shade so the user is alerted even
                            // when the app is backgrounded.
                            com.magizhchi.share.utils.NotificationHelper.showIncomingFile(
                                    MainActivity.this, senderName, fileName, incoming, convId);
                            // Also refresh the conversation list so the lastFile
                            // preview shows the new arrival.
                            loadConversations();
                        }
                    } catch (Exception ignored) {
                        // Malformed message — best-effort only
                    }
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
