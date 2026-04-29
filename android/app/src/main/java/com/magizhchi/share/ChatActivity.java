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
import com.magizhchi.share.network.TokenManager;
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

    private ConversationResponse conversation;
    private FileMessageAdapter fileAdapter;
    private List<FileMessageResponse> allFiles = new ArrayList<>();
    private ProgressBar progressBar;
    private RecyclerView recyclerFiles;
    private Button btnFilter;
    private LinearLayout selectionToolbar;
    private TextView tvSelectionCount;
    private ImageButton btnSelectAll, btnDeleteSelected, btnExitSelection;
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

        // Parse conversation from intent
        String json = getIntent().getStringExtra(EXTRA_CONVERSATION);
        if (json == null) { finish(); return; }
        conversation = new Gson().fromJson(json, ConversationResponse.class);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(conversation.getName() != null ? conversation.getName() : "Chat");
        }

        String subtitle = "DIRECT".equalsIgnoreCase(conversation.getType())
                ? "Direct file share"
                : conversation.getMemberCount() + " members";
        TextView tvSubtitle = findViewById(R.id.tvSubtitle);
        if (tvSubtitle != null) tvSubtitle.setText(subtitle);

        initViews();
        setupFilterChips();
        setupSearch();
        setupUploadFab();
        loadFiles(0, true);
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
        selectionToolbar  = findViewById(R.id.selectionToolbar);
        tvSelectionCount  = findViewById(R.id.tvSelectionCount);
        btnSelectAll      = findViewById(R.id.btnSelectAll);
        btnDeleteSelected = findViewById(R.id.btnDeleteSelected);
        btnExitSelection  = findViewById(R.id.btnExitSelection);

        String currentUserId = TokenManager.getInstance(this).getUserId();
        fileAdapter = new FileMessageAdapter(this, currentUserId);
        recyclerFiles.setLayoutManager(new LinearLayoutManager(this));
        recyclerFiles.setAdapter(fileAdapter);

        fileAdapter.setListener(new FileMessageAdapter.OnFileActionListener() {
            @Override public void onPreview(FileMessageResponse f) { downloadFile(f); /* preview = open URL */ }
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

        List<FileMessageAdapter.Row> rows = new ArrayList<>();
        // Folder rows first — newest-folder first (driven by max sentAt of files inside)
        List<Map.Entry<String, List<FileMessageResponse>>> folderEntries =
                new ArrayList<>(folderBuckets.entrySet());
        folderEntries.sort(Comparator.comparing(
                (Map.Entry<String, List<FileMessageResponse>> e) -> e.getValue().get(0).getSentAt(),
                Comparator.nullsLast(Comparator.reverseOrder())));
        for (Map.Entry<String, List<FileMessageResponse>> e : folderEntries) {
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
            rows.add(FileMessageAdapter.Row.folder(path, name, e.getValue().size(), total, latest));
        }
        // Then files at this level
        for (FileMessageResponse f : looseFiles) rows.add(FileMessageAdapter.Row.file(f));

        fileAdapter.setRows(rows);
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
            List<Uri> uris = new ArrayList<>();
            if (data.getClipData() != null) {
                int count = data.getClipData().getItemCount();
                for (int i = 0; i < count; i++) {
                    uris.add(data.getClipData().getItemAt(i).getUri());
                }
            } else if (data.getData() != null) {
                uris.add(data.getData());
            }
            if (!uris.isEmpty()) {
                showCaptionDialog(uris);
            }
        }
    }

    private void showCaptionDialog(List<Uri> uris) {
        EditText etCaption = new EditText(this);
        etCaption.setHint("Add a caption (optional)");
        new AlertDialog.Builder(this)
                .setTitle("Upload " + uris.size() + " file(s)")
                .setView(etCaption)
                .setPositiveButton("Upload", (dialog, which) -> {
                    String caption = etCaption.getText().toString().trim();
                    for (Uri uri : uris) {
                        uploadFile(uri, caption);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
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
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.unfriend(conversation.getOtherUserId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ChatActivity.this, "Unfriended", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(ChatActivity.this, "Failed to unfriend", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(ChatActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
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

    // ── Properties dialogs ───────────────────────────────────────────────────

    private void showFileProperties(FileMessageResponse f) {
        StringBuilder b = new StringBuilder();
        appendRow(b, "Name",        f.getOriginalFileName());
        appendRow(b, "Extension",   extensionOf(f.getOriginalFileName()));
        appendRow(b, "Type",        f.getCategory());
        appendRow(b, "Status",      "⬆ Uploaded");
        appendRow(b, "Size",        FormatUtils.formatBytes(f.getFileSizeBytes()));
        appendRow(b, "Description", f.getCaption() != null && !f.getCaption().isEmpty() ? f.getCaption() : "—");
        appendRow(b, "Permission",  permissionLabel(safe(f.getDownloadPermission())));
        appendRow(b, "Uploaded by", f.getSenderName());
        appendRow(b, "Uploaded on", FormatUtils.formatDateTime(f.getSentAt()));
        if (f.getFolderPath() != null) appendRow(b, "Folder", f.getFolderPath());
        new AlertDialog.Builder(this)
                .setTitle("📄 File properties")
                .setMessage(b.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showFolderProperties(FileMessageAdapter.Row r) {
        StringBuilder b = new StringBuilder();
        appendRow(b, "Name",        r.folderName);
        appendRow(b, "Path",        r.folderPath);
        appendRow(b, "Status",      "📁 Folder");
        appendRow(b, "Items",       r.folderItemCount + " file" + (r.folderItemCount != 1 ? "s" : ""));
        appendRow(b, "Total size",  FormatUtils.formatBytes(r.folderTotalBytes));
        appendRow(b, "Updated",     FormatUtils.formatDateTime(r.folderLatestSentAt));
        new AlertDialog.Builder(this)
                .setTitle("📁 Folder properties")
                .setMessage(b.toString())
                .setPositiveButton("Close", null)
                .show();
    }

    private static void appendRow(StringBuilder b, String label, String value) {
        b.append(label).append(":  ").append(value == null || value.isEmpty() ? "—" : value).append("\n");
    }

    private static String extensionOf(String name) {
        if (name == null) return "—";
        int i = name.lastIndexOf('.');
        return (i <= 0 || i == name.length() - 1) ? "—" : name.substring(i + 1).toUpperCase();
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private static String permissionLabel(String p) {
        if ("VIEW_ONLY".equals(p))           return "👁 View only · downloads disabled";
        if ("ADMIN_ONLY_DOWNLOAD".equals(p)) return "🛡 Admin only · only group admins can download";
        return "⬇ Anyone with access can download";
    }
}
