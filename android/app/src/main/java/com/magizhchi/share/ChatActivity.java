package com.magizhchi.share;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.magizhchi.share.adapter.FileMessageAdapter;
import com.magizhchi.share.model.ConversationResponse;
import com.magizhchi.share.model.FileMessageResponse;
import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.network.ApiService;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import android.widget.ImageView;
import com.magizhchi.share.model.UserSearchResponse;
import com.magizhchi.share.network.TokenManager;
import com.magizhchi.share.utils.DottedGradientDrawable;
import com.magizhchi.share.utils.FormatUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ChatActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION = "conversation";
    private static final int REQUEST_PICK_FILES = 1001;
    private static final int REQUEST_UPLOAD     = 1002;

    private ConversationResponse conversation;
    private FileMessageAdapter fileAdapter;
    private List<FileMessageResponse> allFiles = new ArrayList<>();
    private ProgressBar progressBar;
    private RecyclerView recyclerFiles;
    private Button btnFilter;
    private Button btnViewMode;
    private FileMessageAdapter.ViewMode currentViewMode = FileMessageAdapter.ViewMode.DETAILS;
    private static final String PREF_VIEW_MODE = "chat_view_mode";
    private LinearLayout selectionToolbar;
    private TextView tvSelectionCount;
    private ImageButton btnSelectAll, btnDeleteSelected, btnExitSelection;
    private View breadcrumbScroll;
    private LinearLayout breadcrumb;
    private EditText etSearch;
    private Button btnLoadMore;
    private int currentPage = 0;
    private boolean hasMore = true;
    private String activeFilter = "ALL";
    /** Path the user has navigated INTO (null = root). Folders show only at this level. */
    private String currentFolderPath = null;
    // pendingUploadUris is populated in onActivityResult and consumed by showCaptionDialog

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // Programmatic chat-screen background — sky gradient + dot pattern,
        // matches the web ChatWindow exactly. Painted on the root so it
        // extends behind the status bar; the inner LinearLayout in the XML
        // uses fitsSystemWindows="true" so the toolbar lives below the bar.
        View root = findViewById(R.id.chatRoot);
        if (root != null) root.setBackground(new DottedGradientDrawable(getResources()));

        // Parse conversation from intent
        String json = getIntent().getStringExtra(EXTRA_CONVERSATION);
        if (json == null) { finish(); return; }
        conversation = new Gson().fromJson(json, ConversationResponse.class);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            // Hide the default title — we render avatar + name + status inside
            // a custom view embedded in the Toolbar via <include>.
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        bindCustomToolbarTitle();

        initViews();
        setupFilterChips();
        setupSearch();
        setupUploadFab();
        loadFiles(0, true);
    }

    /**
     * Paint the custom toolbar title — avatar circle + display name + status
     * line. For DIRECT chats we additionally fetch the counterparty's profile
     * so we can show their statusMessage under their name (mirrors web).
     */
    private void bindCustomToolbarTitle() {
        TextView tvTitle      = findViewById(R.id.toolbarTitle);
        TextView tvStatus     = findViewById(R.id.toolbarStatus);
        ImageView avatar      = findViewById(R.id.toolbarAvatar);
        TextView avatarLetters = findViewById(R.id.toolbarAvatarInitials);

        String name = conversation.getName() != null ? conversation.getName() : "Chat";
        if (tvTitle != null) tvTitle.setText(name);

        // Default subtitle while we wait for the user fetch — group/personal info.
        if (tvStatus != null) {
            String fallback = "GROUP".equalsIgnoreCase(conversation.getType())
                    ? conversation.getMemberCount() + " member"
                            + (conversation.getMemberCount() != 1 ? "s" : "")
                    : "PERSONAL".equalsIgnoreCase(conversation.getType())
                            ? "My personal storage"
                            : "Direct file share";
            tvStatus.setText(fallback);
            tvStatus.setVisibility(View.VISIBLE);
        }

        // Initials fallback first — avatar overlays on top once Glide loads.
        if (avatarLetters != null) {
            avatarLetters.setVisibility(View.VISIBLE);
            avatarLetters.setText(FormatUtils.initials(name));
        }
        if (avatar != null) avatar.setVisibility(View.GONE);

        // Tap on either the photo or the initials block opens the
        // full-screen avatar viewer. PERSONAL conversations are skipped
        // because there's no user-facing photo to enlarge. For DIRECT
        // chats we pass otherUserId so the viewer can refresh the photo
        // URL if the cached one has expired.
        if (!"PERSONAL".equalsIgnoreCase(conversation.getType())) {
            String userIdForRefresh = "DIRECT".equalsIgnoreCase(conversation.getType())
                    ? conversation.getOtherUserId() : null;
            View.OnClickListener openViewer = v ->
                    AvatarViewerActivity.launch(this,
                            conversation.getIconUrl(), name, userIdForRefresh);
            if (avatar != null)        avatar.setOnClickListener(openViewer);
            if (avatarLetters != null) avatarLetters.setOnClickListener(openViewer);
        }

        // Try the conversation's own iconUrl first (cached on the home screen).
        loadAvatarInto(conversation.getIconUrl(), avatar, avatarLetters);

        // For DIRECT chats only: fetch the other user's profile so we can
        // surface their statusMessage and a fresh photo URL.
        if ("DIRECT".equalsIgnoreCase(conversation.getType())
                && conversation.getOtherUserId() != null) {
            ApiClient.getInstance(this).getApiService()
                    .getUserById(conversation.getOtherUserId())
                    .enqueue(new Callback<UserSearchResponse>() {
                @Override public void onResponse(Call<UserSearchResponse> call, Response<UserSearchResponse> response) {
                    if (!response.isSuccessful() || response.body() == null) return;
                    UserSearchResponse u = response.body();
                    if (tvStatus != null) {
                        String status = u.getStatusMessage();
                        if (status != null && !status.trim().isEmpty()) {
                            tvStatus.setText("💬 " + status.trim());
                        }
                    }
                    loadAvatarInto(u.getProfilePhotoUrl(), avatar, avatarLetters);
                    // Re-bind the avatar tap with the freshly-presigned URL
                    // and the user id so the viewer can refresh on failure.
                    final String freshUrl = u.getProfilePhotoUrl();
                    final String freshUserId = u.getId();
                    View.OnClickListener openViewer = v ->
                            AvatarViewerActivity.launch(ChatActivity.this,
                                    freshUrl, name, freshUserId);
                    if (avatar != null)        avatar.setOnClickListener(openViewer);
                    if (avatarLetters != null) avatarLetters.setOnClickListener(openViewer);
                }
                @Override public void onFailure(Call<UserSearchResponse> call, Throwable t) { /* keep cached values */ }
            });
        }
    }

    /** Load a URL into the toolbar avatar; falls back to initials on any error. */
    private void loadAvatarInto(String url, ImageView avatarView, TextView lettersView) {
        if (avatarView == null || lettersView == null) return;
        if (url == null || url.isEmpty()) {
            avatarView.setVisibility(View.GONE);
            lettersView.setVisibility(View.VISIBLE);
            return;
        }
        Glide.with(this)
                .load(url)
                // Presigned URLs go stale; skip caches so we always re-fetch.
                .apply(RequestOptions.circleCropTransform()
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true))
                .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                    @Override public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e,
                                                          Object model,
                                                          com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                          boolean isFirstResource) {
                        avatarView.setVisibility(View.GONE);
                        lettersView.setVisibility(View.VISIBLE);
                        return true;
                    }
                    @Override public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                                             Object model,
                                                             com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                             com.bumptech.glide.load.DataSource dataSource,
                                                             boolean isFirstResource) {
                        avatarView.setVisibility(View.VISIBLE);
                        lettersView.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(avatarView);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_chat, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // If we're inside a folder, back-arrow pops out instead of closing.
            if (currentFolderPath != null) {
                currentFolderPath = null;
                applyFilterAndSearch();
            } else {
                finish();
            }
            return true;
        } else if (id == R.id.action_select) {
            fileAdapter.setSelectionMode(!fileAdapter.isSelectionMode());
            return true;
        } else if (id == R.id.action_new_folder) {
            showCreateFolderDialog();
            return true;
        } else if (id == R.id.action_manage_group) {
            Intent intent = new Intent(this, GroupInfoActivity.class);
            intent.putExtra(GroupInfoActivity.EXTRA_CONVERSATION_ID, conversation.getId());
            intent.putExtra(GroupInfoActivity.EXTRA_CONVERSATION_NAME, conversation.getName());
            startActivity(intent);
            return true;
        } else if (id == R.id.action_exit_group) {
            confirmExitGroup();
            return true;
        } else if (id == R.id.action_unfriend) {
            confirmUnfriend();
            return true;
        } else if (id == R.id.action_block) {
            confirmBlock();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (fileAdapter != null && fileAdapter.isSelectionMode()) {
            exitSelectionMode();
            return;
        }
        if (currentFolderPath != null) {
            currentFolderPath = null;
            applyFilterAndSearch();
            return;
        }
        super.onBackPressed();
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isGroup  = "GROUP".equalsIgnoreCase(conversation.getType());
        boolean isDirect = "DIRECT".equalsIgnoreCase(conversation.getType());

        MenuItem manageGroup = menu.findItem(R.id.action_manage_group);
        MenuItem exitGroup   = menu.findItem(R.id.action_exit_group);
        MenuItem unfriend    = menu.findItem(R.id.action_unfriend);
        MenuItem block       = menu.findItem(R.id.action_block);

        if (manageGroup != null) manageGroup.setVisible(isGroup);
        if (exitGroup   != null) exitGroup.setVisible(isGroup);
        if (unfriend    != null) unfriend.setVisible(isDirect);
        if (block       != null) block.setVisible(isDirect);

        return super.onPrepareOptionsMenu(menu);
    }

    private void initViews() {
        progressBar   = findViewById(R.id.progressBar);
        recyclerFiles = findViewById(R.id.recyclerFiles);
        etSearch      = findViewById(R.id.etSearch);
        btnLoadMore   = findViewById(R.id.btnLoadMore);
        btnFilter     = findViewById(R.id.btnFilter);
        btnViewMode   = findViewById(R.id.btnViewMode);
        breadcrumbScroll  = findViewById(R.id.breadcrumbScroll);
        breadcrumb        = findViewById(R.id.breadcrumb);
        selectionToolbar  = findViewById(R.id.selectionToolbar);
        tvSelectionCount  = findViewById(R.id.tvSelectionCount);
        btnSelectAll      = findViewById(R.id.btnSelectAll);
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected);
        btnExitSelection  = findViewById(R.id.btnExitSelection);

        String currentUserId = TokenManager.getInstance(this).getUserId();
        fileAdapter = new FileMessageAdapter(this, currentUserId);
        // Restore the persisted view mode before the first bind so reopening
        // a chat preserves what the user picked last time.
        currentViewMode = readPersistedViewMode();
        applyViewModeToRecycler();
        recyclerFiles.setAdapter(fileAdapter);

        if (btnViewMode != null) {
            btnViewMode.setOnClickListener(v -> showViewModePicker(v));
        }

        fileAdapter.setListener(new FileMessageAdapter.OnFileActionListener() {
            @Override public void onPreview(FileMessageResponse f) { previewFile(f); }
            @Override public void onDownload(FileMessageResponse f) { downloadFile(f); }
            @Override public void onPin(FileMessageResponse f) {
                Toast.makeText(ChatActivity.this, "Pinned " + f.getOriginalFileName(), Toast.LENGTH_SHORT).show();
            }
            @Override public void onShare(FileMessageResponse f) {
                Toast.makeText(ChatActivity.this, "Share — " + f.getOriginalFileName() + " (coming soon)", Toast.LENGTH_SHORT).show();
            }
            @Override public void onDelete(FileMessageResponse f, int position) { confirmDeleteFile(f, position); }
            @Override public void onProperties(FileMessageResponse f) { showFileProperties(f); }

            @Override public void onFolderOpen(FileMessageAdapter.Row r) {
                currentFolderPath = r.folderPath;
                applyFilterAndSearch();
            }
            @Override public void onFolderDownload(FileMessageAdapter.Row r) {
                Toast.makeText(ChatActivity.this,
                        "Downloading " + r.folderItemCount + " files…",
                        Toast.LENGTH_SHORT).show();
                for (FileMessageResponse f : filesUnder(r.folderPath)) downloadFile(f);
            }
            @Override public void onFolderShare(FileMessageAdapter.Row r) {
                Toast.makeText(ChatActivity.this, "Share folder " + r.folderName + " (coming soon)", Toast.LENGTH_SHORT).show();
            }
            @Override public void onFolderDelete(FileMessageAdapter.Row r, int position) { confirmDeleteFolder(r, position); }
            @Override public void onFolderProperties(FileMessageAdapter.Row r) { showFolderProperties(r); }

            @Override public void onSelectionChanged(int count) { updateSelectionToolbar(count); }
            @Override public void onLongPressStartSelection() { selectionToolbar.setVisibility(View.VISIBLE); }
        });

        if (btnLoadMore != null) {
            btnLoadMore.setOnClickListener(v -> { if (hasMore) loadFiles(currentPage, false); });
        }

        // Selection toolbar
        if (btnSelectAll      != null) btnSelectAll.setOnClickListener(v -> fileAdapter.selectAll());
        if (btnDeleteSelected != null) btnDeleteSelected.setOnClickListener(v -> confirmDeleteSelected());
        if (btnExitSelection  != null) btnExitSelection.setOnClickListener(v -> exitSelectionMode());
    }

    private void updateSelectionToolbar(int count) {
        boolean inMode = fileAdapter.isSelectionMode();
        if (selectionToolbar != null) selectionToolbar.setVisibility(inMode ? View.VISIBLE : View.GONE);
        if (tvSelectionCount != null) tvSelectionCount.setText(count + " selected");
    }

    // ── View-mode picker (Large / Medium / Details) ──────────────────────────

    /** Read the persisted view mode from SharedPreferences (default DETAILS). */
    private FileMessageAdapter.ViewMode readPersistedViewMode() {
        String stored = getSharedPreferences("magizhchi_prefs", MODE_PRIVATE)
                .getString(PREF_VIEW_MODE, FileMessageAdapter.ViewMode.DETAILS.name());
        try { return FileMessageAdapter.ViewMode.valueOf(stored); }
        catch (IllegalArgumentException e) { return FileMessageAdapter.ViewMode.DETAILS; }
    }

    private void persistViewMode(FileMessageAdapter.ViewMode mode) {
        getSharedPreferences("magizhchi_prefs", MODE_PRIVATE)
                .edit().putString(PREF_VIEW_MODE, mode.name()).apply();
    }

    /**
     * Swap the RecyclerView's LayoutManager and tell the adapter to redraw
     * with cells of the right shape:
     *   DETAILS → linear vertical list (current behaviour)
     *   LARGE   → 2-column grid with big icons
     *   MEDIUM  → 3-column grid with smaller icons
     */
    private void applyViewModeToRecycler() {
        if (recyclerFiles == null || fileAdapter == null) return;
        RecyclerView.LayoutManager lm;
        switch (currentViewMode) {
            case LARGE:  lm = new GridLayoutManager(this, 2); break;
            case MEDIUM: lm = new GridLayoutManager(this, 3); break;
            case DETAILS:
            default:     lm = new LinearLayoutManager(this);  break;
        }
        recyclerFiles.setLayoutManager(lm);
        fileAdapter.setViewMode(currentViewMode);
        if (btnViewMode != null) btnViewMode.setText(viewModeIcon(currentViewMode));
    }

    private String viewModeIcon(FileMessageAdapter.ViewMode mode) {
        switch (mode) {
            case LARGE:  return "▦ Large";
            case MEDIUM: return "▤ Medium";
            case DETAILS:
            default:     return "≡ Details";
        }
    }

    private void showViewModePicker(View anchor) {
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add(0, 1, 0, "▦ Large icons");
        popup.getMenu().add(0, 2, 1, "▤ Medium icons");
        popup.getMenu().add(0, 3, 2, "≡ Details");
        popup.setOnMenuItemClickListener(item -> {
            FileMessageAdapter.ViewMode picked;
            switch (item.getItemId()) {
                case 1: picked = FileMessageAdapter.ViewMode.LARGE;  break;
                case 2: picked = FileMessageAdapter.ViewMode.MEDIUM; break;
                default: picked = FileMessageAdapter.ViewMode.DETAILS; break;
            }
            if (picked != currentViewMode) {
                currentViewMode = picked;
                applyViewModeToRecycler();
                persistViewMode(picked);
            }
            return true;
        });
        popup.show();
    }

    private void exitSelectionMode() {
        fileAdapter.setSelectionMode(false);
        if (selectionToolbar != null) selectionToolbar.setVisibility(View.GONE);
    }

    /** Show a single 🎯 button that opens a popup with the category filter. */
    private void setupFilterChips() {
        if (btnFilter == null) return;
        applyFilterButtonLabel();
        btnFilter.setOnClickListener(v -> {
            PopupMenu pm = new PopupMenu(this, btnFilter);
            String[] filters = {"ALL", "IMAGE", "VIDEO", "DOCUMENT", "AUDIO", "ARCHIVE", "OTHER"};
            for (int i = 0; i < filters.length; i++) {
                pm.getMenu().add(0, i, i, iconFor(filters[i]) + "  " + filters[i]);
            }
            pm.setOnMenuItemClickListener(item -> {
                activeFilter = filters[item.getItemId()];
                applyFilterButtonLabel();
                applyFilterAndSearch();
                return true;
            });
            pm.show();
        });
    }

    private void applyFilterButtonLabel() {
        if (btnFilter != null) btnFilter.setText(iconFor(activeFilter) + " " + activeFilter);
    }

    private static String iconFor(String filter) {
        switch (filter) {
            case "IMAGE":    return "🖼";
            case "VIDEO":    return "🎬";
            case "DOCUMENT": return "📄";
            case "AUDIO":    return "🎵";
            case "ARCHIVE":  return "🗜";
            case "OTHER":    return "📎";
            default:         return "🎯";
        }
    }

    private void setupSearch() {
        if (etSearch == null) return;
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilterAndSearch();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /**
     * Build the visible row list: folders first, files second, both sorted by
     * the most recent activity. Inside a folder (currentFolderPath != null)
     * the folder header collapses and only direct children render.
     */
    private void applyFilterAndSearch() {
        String query = etSearch != null ? etSearch.getText().toString().toLowerCase().trim() : "";

        // Apply category + search filter first
        List<FileMessageResponse> filtered = new ArrayList<>();
        for (FileMessageResponse f : allFiles) {
            if (!"ALL".equals(activeFilter) && !activeFilter.equalsIgnoreCase(f.getCategory())) continue;
            if (!query.isEmpty()) {
                boolean nameMatch    = f.getOriginalFileName() != null && f.getOriginalFileName().toLowerCase().contains(query);
                boolean captionMatch = f.getCaption() != null && f.getCaption().toLowerCase().contains(query);
                boolean senderMatch  = f.getSenderName() != null && f.getSenderName().toLowerCase().contains(query);
                if (!nameMatch && !captionMatch && !senderMatch) continue;
            }
            // Folder navigation: show only files that start with the current path
            if (currentFolderPath != null) {
                if (f.getFolderPath() == null || !f.getFolderPath().startsWith(currentFolderPath)) continue;
            }
            filtered.add(f);
        }

        // Newest-first sort by sentAt; null timestamps go last.
        filtered.sort((a, b) -> {
            String sa = a.getSentAt(), sb = b.getSentAt();
            if (sa == null && sb == null) return 0;
            if (sa == null) return 1;
            if (sb == null) return -1;
            return sb.compareTo(sa);
        });

        // Group into folder bucket (top-level folder name relative to currentFolderPath)
        // and file bucket. Folders appear first, then standalone files at this level.
        String base = currentFolderPath == null ? "" : currentFolderPath;
        Map<String, List<FileMessageResponse>> folderBuckets = new LinkedHashMap<>();
        List<FileMessageResponse> looseFiles = new ArrayList<>();
        for (FileMessageResponse f : filtered) {
            String fp = f.getFolderPath();
            if (fp == null || fp.isEmpty()) {
                looseFiles.add(f); continue;
            }
            String relative = fp.startsWith(base) ? fp.substring(base.length()) : fp;
            if (relative.isEmpty()) {
                // file lives directly in currentFolderPath — show flat
                looseFiles.add(f);
            } else {
                int slash = relative.indexOf('/');
                String topSeg = slash >= 0 ? relative.substring(0, slash) : relative;
                String groupKey = base + topSeg + "/";
                folderBuckets.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(f);
            }
        }

        // Build BOTH folder rows AND loose-file rows into one combined list,
        // each tagged with its activity timestamp, then sort the combined list
        // newest-first. Folders use the timestamp of their newest member, so a
        // folder that just received a file outranks an older standalone file.
        List<Object[]> tagged = new ArrayList<>(); // each entry: [Row, String sortKey]

        for (Map.Entry<String, List<FileMessageResponse>> e : folderBuckets.entrySet()) {
            String path = e.getKey();
            String name = path.replaceAll("/$", "");
            int slash = name.lastIndexOf('/');
            if (slash >= 0) name = name.substring(slash + 1);
            long total = 0;
            String latest = null;
            for (FileMessageResponse f : e.getValue()) {
                total += f.getFileSizeBytes();
                if (latest == null || (f.getSentAt() != null && f.getSentAt().compareTo(latest) > 0)) {
                    latest = f.getSentAt();
                }
            }
            tagged.add(new Object[]{
                    FileMessageAdapter.Row.folder(path, name, e.getValue().size(), total, latest),
                    latest
            });
        }
        for (FileMessageResponse f : looseFiles) {
            tagged.add(new Object[]{ FileMessageAdapter.Row.file(f), f.getSentAt() });
        }

        tagged.sort((a, b) -> {
            String sa = (String) a[1], sb = (String) b[1];
            if (sa == null && sb == null) return 0;
            if (sa == null) return 1;
            if (sb == null) return -1;
            return sb.compareTo(sa);   // newest first
        });

        List<FileMessageAdapter.Row> rows = new ArrayList<>();
        for (Object[] o : tagged) rows.add((FileMessageAdapter.Row) o[0]);
        fileAdapter.setRows(rows);
        renderBreadcrumb();
    }

    /**
     * Draw the folder-navigation breadcrumb below the toolbar. Always visible
     * — at root it shows just "🏠 All Files" so the user knows where they
     * are. Each path segment becomes a clickable TextView that jumps to that
     * depth (the deepest segment is bold + non-tappable).
     */
    private void renderBreadcrumb() {
        if (breadcrumb == null || breadcrumbScroll == null) return;
        breadcrumb.removeAllViews();
        breadcrumbScroll.setVisibility(View.VISIBLE);

        boolean atRoot = (currentFolderPath == null || currentFolderPath.isEmpty());

        // Home chip — bold + non-tappable when we're already at root.
        breadcrumb.addView(makeCrumb("🏠 All Files", !atRoot, atRoot ? null : () -> {
            currentFolderPath = null;
            applyFilterAndSearch();
        }));

        if (atRoot) return;

        String[] segs = currentFolderPath.replaceAll("/$", "").split("/");
        StringBuilder built = new StringBuilder();
        for (int i = 0; i < segs.length; i++) {
            built.append(segs[i]).append("/");
            String pathToHere = built.toString();
            boolean isLast = i == segs.length - 1;
            breadcrumb.addView(makeSeparator());
            breadcrumb.addView(makeCrumb(segs[i], !isLast, isLast ? null : () -> {
                currentFolderPath = pathToHere;
                applyFilterAndSearch();
            }));
        }
    }

    private TextView makeCrumb(String label, boolean clickable, Runnable onClick) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(clickable ? 0xCCFFFFFF : 0xFFFFFFFF);
        tv.setTextSize(12);
        tv.setTypeface(null, clickable ? android.graphics.Typeface.NORMAL : android.graphics.Typeface.BOLD);
        int padH = (int) (8 * getResources().getDisplayMetrics().density);
        int padV = (int) (4 * getResources().getDisplayMetrics().density);
        tv.setPadding(padH, padV, padH, padV);
        if (clickable && onClick != null) {
            tv.setBackgroundResource(android.R.drawable.list_selector_background);
            tv.setOnClickListener(v -> onClick.run());
        }
        return tv;
    }

    private TextView makeSeparator() {
        TextView tv = new TextView(this);
        tv.setText("›");
        tv.setTextColor(0x66FFFFFF);
        tv.setTextSize(13);
        int pad = (int) (4 * getResources().getDisplayMetrics().density);
        tv.setPadding(pad, 0, pad, 0);
        return tv;
    }

    /** All files whose folderPath starts with the given prefix (used for batch download). */
    private List<FileMessageResponse> filesUnder(String folderPath) {
        List<FileMessageResponse> out = new ArrayList<>();
        for (FileMessageResponse f : allFiles) {
            if (f.getFolderPath() != null && f.getFolderPath().startsWith(folderPath)) out.add(f);
        }
        return out;
    }

    private void setupUploadFab() {
        FloatingActionButton fab = findViewById(R.id.fabUpload);
        fab.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(intent, "Select file(s)"), REQUEST_PICK_FILES);
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_PICK_FILES && resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) launchUploadComposer(uris);
            return;
        }

        if (requestCode == REQUEST_UPLOAD && resultCode == Activity.RESULT_OK) {
            // UploadActivity reports back the upload count — refresh the list.
            int count = data != null ? data.getIntExtra(UploadActivity.EXTRA_UPLOAD_COUNT, 0) : 0;
            if (count > 0) {
                Toast.makeText(this, count + " file" + (count != 1 ? "s" : "") + " uploaded.",
                        Toast.LENGTH_SHORT).show();
                loadFiles(0, true);
            }
        }
    }

    /**
     * Launch the full-screen UploadActivity (web-style upload modal) with
     * the picked URIs + the destination conversation/folder so the user can
     * pick a download permission and add a description before sending.
     */
    private void launchUploadComposer(ArrayList<Uri> uris) {
        Intent i = new Intent(this, UploadActivity.class);
        i.putExtra(UploadActivity.EXTRA_CONVERSATION_ID,   conversation.getId());
        i.putExtra(UploadActivity.EXTRA_CONVERSATION_TYPE, conversation.getType());
        i.putExtra(UploadActivity.EXTRA_FOLDER_PATH,       currentFolderPath);
        i.putParcelableArrayListExtra(UploadActivity.EXTRA_URIS, uris);
        // Forward the read permission grant so UploadActivity can openInputStream.
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(i, REQUEST_UPLOAD);
    }

    private void uploadFile(Uri uri, String caption) {
        try {
            String mimeType = getContentResolver().getType(uri);
            if (mimeType == null) mimeType = "application/octet-stream";

            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "Cannot read file", Toast.LENGTH_SHORT).show();
                return;
            }

            byte[] bytes = readAllBytes(inputStream);
            inputStream.close();

            String fileName = getFileName(uri);
            RequestBody fileBody = RequestBody.create(bytes, MediaType.parse(mimeType));
            MultipartBody.Part filePart = MultipartBody.Part.createFormData("file", fileName, fileBody);
            RequestBody captionPart = RequestBody.create(caption, MediaType.parse("text/plain"));
            RequestBody mentionsPart = RequestBody.create("[]", MediaType.parse("text/plain"));

            ApiService apiService = ApiClient.getInstance(this).getApiService();
            apiService.sendFile(conversation.getId(), filePart, captionPart, mentionsPart)
                    .enqueue(new Callback<FileMessageResponse>() {
                        @Override
                        public void onResponse(Call<FileMessageResponse> call, Response<FileMessageResponse> response) {
                            if (response.isSuccessful() && response.body() != null) {
                                Toast.makeText(ChatActivity.this, "File uploaded!", Toast.LENGTH_SHORT).show();
                                allFiles.add(0, response.body());
                                applyFilterAndSearch();
                            } else {
                                Toast.makeText(ChatActivity.this, "Upload failed", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<FileMessageResponse> call, Throwable t) {
                            Toast.makeText(ChatActivity.this, "Upload error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
        } catch (IOException e) {
            Toast.makeText(this, "Error reading file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private String getFileName(Uri uri) {
        String name = "file";
        android.database.Cursor cursor = getContentResolver().query(uri, null, null, null, null);
        if (cursor != null) {
            int nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME);
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                name = cursor.getString(nameIndex);
            }
            cursor.close();
        }
        return name;
    }

    private void loadFiles(int page, boolean replace) {
        progressBar.setVisibility(View.VISIBLE);
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.getConversationFiles(conversation.getId(), page, 20)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        progressBar.setVisibility(View.GONE);
                        if (response.isSuccessful() && response.body() != null) {
                            try {
                                String bodyStr = response.body().string();
                                Gson gson = new Gson();
                                JsonObject pageObj = gson.fromJson(bodyStr, JsonObject.class);
                                JsonArray content = pageObj.has("content")
                                        ? pageObj.getAsJsonArray("content") : new JsonArray();
                                boolean last = !pageObj.has("last") || pageObj.get("last").getAsBoolean();
                                hasMore = !last;

                                List<FileMessageResponse> newFiles = new ArrayList<>();
                                for (int i = 0; i < content.size(); i++) {
                                    newFiles.add(gson.fromJson(content.get(i), FileMessageResponse.class));
                                }

                                if (replace) {
                                    allFiles = newFiles;
                                } else {
                                    allFiles.addAll(newFiles);
                                }
                                currentPage = page + 1;

                                if (btnLoadMore != null) {
                                    btnLoadMore.setVisibility(hasMore ? View.VISIBLE : View.GONE);
                                }
                                applyFilterAndSearch();
                            } catch (Exception e) {
                                Toast.makeText(ChatActivity.this, "Parse error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        } else {
                            Toast.makeText(ChatActivity.this, "Failed to load files", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        progressBar.setVisibility(View.GONE);
                        Toast.makeText(ChatActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    /**
     * Open the New Folder composer — port of the web dialog. Lets the user
     * pick a name + description + default download permission. Posts to
     * /api/folders and refreshes the list on success.
     */
    private void showCreateFolderDialog() {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_new_folder, null);

        EditText etName = dialogView.findViewById(R.id.etFolderName);
        EditText etDesc = dialogView.findViewById(R.id.etFolderDescription);
        TextView tvDest = dialogView.findViewById(R.id.tvFolderDestination);
        LinearLayout pAnyone = dialogView.findViewById(R.id.folderPermAnyone);
        LinearLayout pView   = dialogView.findViewById(R.id.folderPermViewOnly);
        LinearLayout pAdmins = dialogView.findViewById(R.id.folderPermAdmins);
        Button btnCreate = dialogView.findViewById(R.id.btnFolderCreate);
        Button btnCancel = dialogView.findViewById(R.id.btnFolderCancel);

        // Show where the folder will be created
        tvDest.setText(currentFolderPath != null && !currentFolderPath.isEmpty()
                ? "Inside " + currentFolderPath
                : "At the top level");

        final String[] permRef = { "CAN_DOWNLOAD" };
        final boolean groupChat = "GROUP".equalsIgnoreCase(conversation.getType());

        Runnable applyPills = () -> {
            applyPermPill(pAnyone,  "CAN_DOWNLOAD".equals(permRef[0]));
            applyPermPill(pView,    "VIEW_ONLY".equals(permRef[0]));
            applyPermPill(pAdmins,  "ADMIN_ONLY_DOWNLOAD".equals(permRef[0]));
        };
        applyPills.run();
        if (!groupChat) pAdmins.setAlpha(0.45f);

        pAnyone.setOnClickListener(v -> { permRef[0] = "CAN_DOWNLOAD";        applyPills.run(); });
        pView.setOnClickListener(v   -> { permRef[0] = "VIEW_ONLY";           applyPills.run(); });
        pAdmins.setOnClickListener(v -> {
            if (!groupChat) {
                Toast.makeText(this, "Available only inside group conversations.", Toast.LENGTH_LONG).show();
                return;
            }
            permRef[0] = "ADMIN_ONLY_DOWNLOAD"; applyPills.run();
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create();
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(
                    new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        }

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        btnCreate.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            if (name.isEmpty()) { etName.setError("Folder name is required"); return; }
            if (name.contains("/") || name.contains("\\")) {
                etName.setError("Folder name cannot contain '/' or '\\'");
                return;
            }
            createFolderOnServer(name, etDesc.getText().toString().trim(), permRef[0], dialog);
        });

        dialog.show();
    }

    /** Restyle a permission pill based on selection state. */
    private void applyPermPill(LinearLayout pill, boolean active) {
        pill.setBackgroundResource(active
                ? R.drawable.bg_perm_pill_active
                : R.drawable.bg_perm_pill_inactive);
        int color    = active ? android.graphics.Color.parseColor("#0284C7") : 0xFFFFFFFF;
        int subColor = active ? android.graphics.Color.parseColor("#0284C7") : 0xCCFFFFFF;
        for (int i = 0; i < pill.getChildCount(); i++) {
            View c = pill.getChildAt(i);
            if (c instanceof TextView) ((TextView) c).setTextColor(i <= 1 ? color : subColor);
        }
    }

    private void createFolderOnServer(String name, String description, String permission,
                                       AlertDialog dialog) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("name", name);
        // The backend expects conversationId as a numeric Long; our Android
        // model stores it as a String, so parse defensively.
        try { body.put("conversationId", Long.parseLong(conversation.getId())); }
        catch (NumberFormatException e) { body.put("conversationId", conversation.getId()); }
        body.put("defaultPermission", permission);
        if (description != null && !description.isEmpty()) body.put("description", description);
        // Note: parentFolderId left null — we don't track folder IDs on Android
        // yet, so all folders create at root. The web's hierarchy support is
        // left for a future port.

        ApiService api = ApiClient.getInstance(this).getApiService();
        api.createFolder(body).enqueue(new Callback<com.magizhchi.share.model.FolderResponse>() {
            @Override public void onResponse(Call<com.magizhchi.share.model.FolderResponse> call,
                                              Response<com.magizhchi.share.model.FolderResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    Toast.makeText(ChatActivity.this,
                            "📁 \"" + name + "\" created.", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    loadFiles(0, true);
                } else {
                    // Show the actual backend reason — usually means
                    // "you are not a member of this conversation" or a
                    // validation issue with the conversationId type.
                    String reason = readErrorBody(response);
                    Toast.makeText(ChatActivity.this,
                            "Could not create folder: " + reason,
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override public void onFailure(Call<com.magizhchi.share.model.FolderResponse> call, Throwable t) {
                Toast.makeText(ChatActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /**
     * Open the file's INLINE preview URL in the browser. The backend's
     * /preview-url endpoint returns a presigned link with inline
     * Content-Disposition, so PDFs, images, and videos render in the browser
     * tab instead of triggering a download. View-only files are previewable.
     */
    private void previewFile(FileMessageResponse file) {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.getPreviewUrl(file.getId()).enqueue(new Callback<Map<String, String>>() {
            @Override public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String url = response.body().get("url");
                    if (url == null) url = response.body().get("previewUrl");
                    if (url != null) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                        return;
                    }
                }
                Toast.makeText(ChatActivity.this, "Couldn't open preview", Toast.LENGTH_SHORT).show();
            }
            @Override public void onFailure(Call<Map<String, String>> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void downloadFile(FileMessageResponse file) {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.getDownloadUrl(file.getId()).enqueue(new Callback<Map<String, String>>() {
            @Override
            public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String url = response.body().get("url");
                    if (url == null) url = response.body().get("downloadUrl");
                    if (url != null) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                        startActivity(browserIntent);
                    } else {
                        Toast.makeText(ChatActivity.this, "No download URL returned", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(ChatActivity.this, "Failed to get download URL", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Map<String, String>> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmDeleteFile(FileMessageResponse file, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete File")
                .setMessage("Delete \"" + file.getOriginalFileName() + "\"?")
                .setPositiveButton("Delete", (dialog, which) -> deleteFile(file, position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFile(FileMessageResponse file, int position) {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.deleteFile(file.getId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    allFiles.remove(file);
                    applyFilterAndSearch();   // adapter rebuilds rows from allFiles
                    Toast.makeText(ChatActivity.this, "File deleted", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ChatActivity.this, "Failed to delete file", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmExitGroup() {
        new AlertDialog.Builder(this)
                .setTitle("Exit Group")
                .setMessage("Are you sure you want to leave this group?")
                .setPositiveButton("Exit", (dialog, which) -> exitGroup())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void exitGroup() {
        String userId = TokenManager.getInstance(this).getUserId();
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.removeMember(conversation.getId(), userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ChatActivity.this, "Left the group", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(ChatActivity.this, "Failed to leave group", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmUnfriend() {
        new AlertDialog.Builder(this)
                .setTitle("Unfriend")
                .setMessage("Remove this person from your connections?")
                .setPositiveButton("Unfriend", (dialog, which) -> unfriend())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void unfriend() {
        String otherId = conversation.getOtherUserId();
        if (otherId == null || otherId.isEmpty()) {
            Toast.makeText(this, "Cannot unfriend: missing user id.", Toast.LENGTH_LONG).show();
            return;
        }
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.unfriend(otherId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ChatActivity.this, "Unfriended.", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    // Show the actual server-side reason instead of a generic
                    // "Failed to unfriend" — e.g. 404 means "not connected",
                    // 403 means "blocked", etc.
                    Toast.makeText(ChatActivity.this,
                            "Failed to unfriend: " + readErrorBody(response),
                            Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    /** Pull a friendly message out of a Retrofit error body. */
    private static String readErrorBody(Response<?> response) {
        if (response == null) return "no response";
        try {
            String raw = response.errorBody() != null ? response.errorBody().string() : "";
            if (raw.isEmpty()) return "HTTP " + response.code();
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

    private void confirmBlock() {
        new AlertDialog.Builder(this)
                .setTitle("Block User")
                .setMessage("Block this user? They won't be able to share files with you.")
                .setPositiveButton("Block", (dialog, which) -> blockUser())
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** Reads all bytes from an InputStream (compatible with minSdk 26). */
    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = is.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private void blockUser() {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.blockUser(conversation.getOtherUserId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ChatActivity.this, "User blocked", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(ChatActivity.this, "Failed to block user", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    // ── Bulk delete (selection toolbar) ──────────────────────────────────────

    private void confirmDeleteSelected() {
        List<FileMessageResponse> sel = fileAdapter.getSelectedFiles();
        if (sel.isEmpty()) {
            Toast.makeText(this, "No files selected.", Toast.LENGTH_SHORT).show();
            return;
        }
        // Only files YOU uploaded can be deleted server-side.
        String me = TokenManager.getInstance(this).getUserId();
        List<FileMessageResponse> mine = new ArrayList<>();
        for (FileMessageResponse f : sel) {
            if (f.getSenderId() != null && f.getSenderId().equals(me)) mine.add(f);
        }
        int skipped = sel.size() - mine.size();
        if (mine.isEmpty()) {
            Toast.makeText(this, "You can only delete files you uploaded.", Toast.LENGTH_LONG).show();
            return;
        }
        String msg = "Delete " + mine.size() + " file" + (mine.size() != 1 ? "s" : "") + "?"
                + (skipped > 0
                    ? "\n\n(" + skipped + " other file" + (skipped != 1 ? "s" : "")
                      + " in your selection " + (skipped != 1 ? "are" : "is")
                      + " not yours and will be skipped.)"
                    : "");
        new AlertDialog.Builder(this)
                .setTitle("Delete files")
                .setMessage(msg)
                .setPositiveButton("Delete", (d, w) -> {
                    for (FileMessageResponse f : mine) deleteFileSilently(f);
                    exitSelectionMode();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteFileSilently(FileMessageResponse f) {
        ApiClient.getInstance(this).getApiService().deleteFile(f.getId())
                .enqueue(new Callback<ResponseBody>() {
            @Override public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    allFiles.remove(f);
                    applyFilterAndSearch();
                }
            }
            @Override public void onFailure(Call<ResponseBody> call, Throwable t) { /* ignore */ }
        });
    }

    private void confirmDeleteFolder(FileMessageAdapter.Row r, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Delete folder")
                .setMessage("Delete folder \"" + r.folderName + "\" and the " + r.folderItemCount
                        + " file" + (r.folderItemCount != 1 ? "s" : "") + " inside?")
                .setPositiveButton("Delete", (d, w) -> {
                    for (FileMessageResponse f : filesUnder(r.folderPath)) deleteFileSilently(f);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ── Properties screen ────────────────────────────────────────────────────

    /**
     * Launch the dedicated FilePropertiesActivity instead of showing an
     * AlertDialog. The activity handles both files and folders behind a
     * single intent extra, so this method just packages the FileMessageResponse
     * as JSON and starts it.
     */
    private void showFileProperties(FileMessageResponse f) {
        Intent intent = new Intent(this, FilePropertiesActivity.class);
        intent.putExtra(FilePropertiesActivity.EXTRA_MODE, FilePropertiesActivity.MODE_FILE);
        intent.putExtra(FilePropertiesActivity.EXTRA_FILE_JSON, new Gson().toJson(f));
        startActivity(intent);
    }

    private void showFolderProperties(FileMessageAdapter.Row r) {
        Intent intent = new Intent(this, FilePropertiesActivity.class);
        intent.putExtra(FilePropertiesActivity.EXTRA_MODE, FilePropertiesActivity.MODE_FOLDER);
        intent.putExtra(FilePropertiesActivity.EXTRA_FOLDER_NAME,        r.folderName);
        intent.putExtra(FilePropertiesActivity.EXTRA_FOLDER_PATH,        r.folderPath);
        intent.putExtra(FilePropertiesActivity.EXTRA_FOLDER_ITEM_COUNT,  r.folderItemCount);
        intent.putExtra(FilePropertiesActivity.EXTRA_FOLDER_TOTAL_BYTES, r.folderTotalBytes);
        intent.putExtra(FilePropertiesActivity.EXTRA_FOLDER_LATEST_AT,   r.folderLatestSentAt);
        startActivity(intent);
    }
}
