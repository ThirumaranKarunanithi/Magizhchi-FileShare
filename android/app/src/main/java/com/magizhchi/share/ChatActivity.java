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
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
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
    private ChipGroup chipGroupFilter;
    private EditText etSearch;
    private Button btnLoadMore;
    private int currentPage = 0;
    private boolean hasMore = true;
    private String activeFilter = "ALL";
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
            finish();
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
        chipGroupFilter = findViewById(R.id.chipGroupFilter);
        etSearch      = findViewById(R.id.etSearch);
        btnLoadMore   = findViewById(R.id.btnLoadMore);

        String currentUserId = TokenManager.getInstance(this).getUserId();
        fileAdapter = new FileMessageAdapter(this, currentUserId);
        recyclerFiles.setLayoutManager(new LinearLayoutManager(this));
        recyclerFiles.setAdapter(fileAdapter);

        fileAdapter.setListener(new FileMessageAdapter.OnFileActionListener() {
            @Override
            public void onDownload(FileMessageResponse file) {
                downloadFile(file);
            }

            @Override
            public void onDelete(FileMessageResponse file, int position) {
                confirmDeleteFile(file, position);
            }
        });

        if (btnLoadMore != null) {
            btnLoadMore.setOnClickListener(v -> {
                if (hasMore) loadFiles(currentPage, false);
            });
        }
    }

    private void setupFilterChips() {
        if (chipGroupFilter == null) return;
        String[] filters = {"ALL", "IMAGE", "VIDEO", "DOCUMENT", "AUDIO", "ARCHIVE", "OTHER"};
        chipGroupFilter.removeAllViews();
        for (String filter : filters) {
            Chip chip = new Chip(this);
            chip.setText(filter);
            chip.setCheckable(true);
            chip.setChecked("ALL".equals(filter));
            chip.setChipBackgroundColorResource("ALL".equals(filter)
                    ? android.R.color.white : R.color.colorGlassWhite);
            chip.setOnClickListener(v -> {
                activeFilter = filter;
                applyFilterAndSearch();
            });
            chipGroupFilter.addView(chip);
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

    private void applyFilterAndSearch() {
        String query = etSearch != null ? etSearch.getText().toString().toLowerCase().trim() : "";
        List<FileMessageResponse> filtered = new ArrayList<>();
        for (FileMessageResponse f : allFiles) {
            if (!"ALL".equals(activeFilter) && !activeFilter.equalsIgnoreCase(f.getCategory())) {
                continue;
            }
            if (!query.isEmpty()) {
                boolean nameMatch    = f.getOriginalFileName() != null && f.getOriginalFileName().toLowerCase().contains(query);
                boolean captionMatch = f.getCaption() != null && f.getCaption().toLowerCase().contains(query);
                boolean senderMatch  = f.getSenderName() != null && f.getSenderName().toLowerCase().contains(query);
                if (!nameMatch && !captionMatch && !senderMatch) continue;
            }
            filtered.add(f);
        }
        fileAdapter.setFiles(filtered);
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
                    fileAdapter.removeFile(position);
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
}
