package com.magizhchi.share;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.magizhchi.share.adapter.MemberAdapter;
import com.magizhchi.share.adapter.UserSearchAdapter;
import com.magizhchi.share.model.ConversationResponse;
import com.magizhchi.share.model.GroupMemberResponse;
import com.magizhchi.share.model.UserSearchResponse;
import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.network.ApiService;
import com.magizhchi.share.network.TokenManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GroupInfoActivity extends AppCompatActivity {

    public static final String EXTRA_CONVERSATION_ID   = "conversation_id";
    public static final String EXTRA_CONVERSATION_NAME = "conversation_name";

    private String conversationId;
    /** Mutable copy of the conversation name; updated locally after a rename so
     *  the toolbar title stays in sync without a full activity restart. */
    private String currentConversationName;
    private MemberAdapter memberAdapter;
    private UserSearchAdapter searchAdapter;
    private List<GroupMemberResponse> currentMembers = new ArrayList<>();
    private ProgressBar progressBar;
    private RecyclerView recyclerMembers;
    private RecyclerView recyclerSearchResults;
    private EditText etSearchUser;
    private Button btnExitGroup;
    private boolean isCurrentUserAdmin = false;

    /** Menu id for the "Rename group" toolbar action. Admin-only. */
    private static final int MENU_RENAME_ID = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_group_info);

        conversationId = getIntent().getStringExtra(EXTRA_CONVERSATION_ID);
        currentConversationName = getIntent().getStringExtra(EXTRA_CONVERSATION_NAME);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(currentConversationName != null ? currentConversationName : "Group Info");
        }

        initViews();
        loadMembers();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Single dynamic menu item — "Rename group". Visibility flips
        // based on isCurrentUserAdmin, which is resolved asynchronously
        // by loadMembers(); we call invalidateOptionsMenu() once that
        // resolves so the item appears for admins.
        menu.add(Menu.NONE, MENU_RENAME_ID, 0, "✎ Rename group")
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuItem rename = menu.findItem(MENU_RENAME_ID);
        if (rename != null) rename.setVisible(isCurrentUserAdmin);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        if (item.getItemId() == MENU_RENAME_ID) {
            showRenameGroupDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initViews() {
        progressBar        = findViewById(R.id.progressBar);
        recyclerMembers    = findViewById(R.id.recyclerMembers);
        recyclerSearchResults = findViewById(R.id.recyclerSearchResults);
        etSearchUser       = findViewById(R.id.etSearchUser);
        btnExitGroup       = findViewById(R.id.btnExitGroup);

        String currentUserId = TokenManager.getInstance(this).getUserId();

        memberAdapter = new MemberAdapter(this, currentUserId);
        recyclerMembers.setLayoutManager(new LinearLayoutManager(this));
        recyclerMembers.setAdapter(memberAdapter);

        memberAdapter.setListener(new MemberAdapter.OnMemberActionListener() {
            @Override
            public void onMakeAdmin(GroupMemberResponse member, int position) {
                updateRole(member, position, "ADMIN");
            }

            @Override
            public void onRemoveAdmin(GroupMemberResponse member, int position) {
                updateRole(member, position, "MEMBER");
            }

            @Override
            public void onRemoveMember(GroupMemberResponse member, int position) {
                confirmRemoveMember(member, position);
            }
        });

        searchAdapter = new UserSearchAdapter(this);
        recyclerSearchResults.setLayoutManager(new LinearLayoutManager(this));
        recyclerSearchResults.setAdapter(searchAdapter);
        recyclerSearchResults.setVisibility(View.GONE);

        searchAdapter.setListener(user -> addMember(user));

        etSearchUser.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                if (query.length() >= 2) {
                    searchUsers(query);
                } else {
                    recyclerSearchResults.setVisibility(View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnExitGroup.setOnClickListener(v -> confirmExitGroup());
    }

    private void loadMembers() {
        progressBar.setVisibility(View.VISIBLE);
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.getConversationMembers(conversationId).enqueue(new Callback<List<GroupMemberResponse>>() {
            @Override
            public void onResponse(Call<List<GroupMemberResponse>> call, Response<List<GroupMemberResponse>> response) {
                progressBar.setVisibility(View.GONE);
                if (response.isSuccessful() && response.body() != null) {
                    currentMembers = response.body();
                    memberAdapter.setMembers(currentMembers);

                    // Detect if current user is admin
                    String currentUserId = TokenManager.getInstance(GroupInfoActivity.this).getUserId();
                    for (GroupMemberResponse m : currentMembers) {
                        if (m.getUserId() != null && m.getUserId().equals(currentUserId)) {
                            isCurrentUserAdmin = "ADMIN".equalsIgnoreCase(m.getRole());
                            break;
                        }
                    }
                    memberAdapter.setCurrentUserAdmin(isCurrentUserAdmin);
                    // Re-evaluate the toolbar menu — the "Rename group" item
                    // is admin-only and the admin flag was unknown at create time.
                    invalidateOptionsMenu();
                } else {
                    Toast.makeText(GroupInfoActivity.this, "Failed to load members", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<List<GroupMemberResponse>> call, Throwable t) {
                progressBar.setVisibility(View.GONE);
                Toast.makeText(GroupInfoActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void searchUsers(String query) {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.searchUsers(query).enqueue(new Callback<List<UserSearchResponse>>() {
            @Override
            public void onResponse(Call<List<UserSearchResponse>> call, Response<List<UserSearchResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Filter: only CONNECTED users not already in the group
                    List<UserSearchResponse> filtered = new ArrayList<>();
                    for (UserSearchResponse u : response.body()) {
                        if (!"CONNECTED".equalsIgnoreCase(u.getConnectionStatus())) continue;
                        boolean alreadyMember = false;
                        for (GroupMemberResponse m : currentMembers) {
                            if (m.getUserId() != null && m.getUserId().equals(u.getId())) {
                                alreadyMember = true;
                                break;
                            }
                        }
                        if (!alreadyMember) filtered.add(u);
                    }
                    searchAdapter.setUsers(filtered);
                    recyclerSearchResults.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
                }
            }

            @Override
            public void onFailure(Call<List<UserSearchResponse>> call, Throwable t) {
                Toast.makeText(GroupInfoActivity.this, "Search error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void addMember(UserSearchResponse user) {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.addMember(conversationId, user.getId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(GroupInfoActivity.this,
                            user.getDisplayName() + " added to group", Toast.LENGTH_SHORT).show();
                    etSearchUser.setText("");
                    recyclerSearchResults.setVisibility(View.GONE);
                    loadMembers();
                } else {
                    Toast.makeText(GroupInfoActivity.this, "Failed to add member", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(GroupInfoActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateRole(GroupMemberResponse member, int position, String newRole) {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.updateMemberRole(conversationId, member.getUserId(), newRole)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        if (response.isSuccessful()) {
                            member.setRole(newRole);
                            memberAdapter.updateMember(position, member);
                            Toast.makeText(GroupInfoActivity.this,
                                    "Role updated to " + newRole, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(GroupInfoActivity.this, "Failed to update role", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        Toast.makeText(GroupInfoActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
    }

    private void confirmRemoveMember(GroupMemberResponse member, int position) {
        new AlertDialog.Builder(this)
                .setTitle("Remove Member")
                .setMessage("Remove " + member.getDisplayName() + " from the group?")
                .setPositiveButton("Remove", (dialog, which) -> removeMember(member, position))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void removeMember(GroupMemberResponse member, int position) {
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.removeMember(conversationId, member.getUserId()).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    currentMembers.remove(member);
                    memberAdapter.removeMember(position);
                    Toast.makeText(GroupInfoActivity.this, "Member removed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupInfoActivity.this, "Failed to remove member", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(GroupInfoActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
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

    // ── Rename group ──────────────────────────────────────────────────────────

    /**
     * Show the AlertDialog for renaming the group. Pre-seeds the input with
     * the current name so the user can do a quick fix-typo edit. Empty /
     * whitespace-only names are rejected client-side too — the server has
     * the same guard but it saves a round-trip.
     */
    private void showRenameGroupDialog() {
        EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setText(currentConversationName != null ? currentConversationName : "");
        input.setSelection(input.getText().length());
        input.setFilters(new android.text.InputFilter[] {
                new android.text.InputFilter.LengthFilter(80)
        });

        // Pad the EditText so it doesn't hug the AlertDialog edges.
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        FrameLayout container = new FrameLayout(this);
        container.setPadding(pad, pad / 2, pad, 0);
        container.addView(input);

        new AlertDialog.Builder(this)
                .setTitle("Rename group")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String newName = input.getText().toString().trim();
                    if (newName.isEmpty()) {
                        Toast.makeText(this, "Group name cannot be empty.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (newName.equals(currentConversationName)) return;
                    submitRename(newName);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void submitRename(String newName) {
        Map<String, String> body = new HashMap<>();
        body.put("name", newName);
        ApiClient.getInstance(this).getApiService()
                .renameGroup(conversationId, body)
                .enqueue(new Callback<ConversationResponse>() {
            @Override
            public void onResponse(Call<ConversationResponse> call,
                                   Response<ConversationResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    String confirmed = response.body().getName() != null
                            ? response.body().getName() : newName;
                    currentConversationName = confirmed;
                    if (getSupportActionBar() != null) {
                        getSupportActionBar().setTitle(confirmed);
                    }
                    Toast.makeText(GroupInfoActivity.this,
                            "Group renamed.", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(GroupInfoActivity.this,
                            "Could not rename: " + response.code(),
                            Toast.LENGTH_LONG).show();
                }
            }
            @Override
            public void onFailure(Call<ConversationResponse> call, Throwable t) {
                Toast.makeText(GroupInfoActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void exitGroup() {
        String userId = TokenManager.getInstance(this).getUserId();
        ApiService apiService = ApiClient.getInstance(this).getApiService();
        apiService.removeMember(conversationId, userId).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(GroupInfoActivity.this, "Left the group", Toast.LENGTH_SHORT).show();
                    // Go back to MainActivity
                    Intent intent = new Intent(GroupInfoActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    startActivity(intent);
                    finish();
                } else {
                    Toast.makeText(GroupInfoActivity.this, "Failed to leave group", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Toast.makeText(GroupInfoActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }
}
