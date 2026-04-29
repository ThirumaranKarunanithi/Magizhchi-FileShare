package com.magizhchi.share;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.target.Target;
import com.magizhchi.share.model.UserSearchResponse;
import com.magizhchi.share.network.ApiClient;
import com.magizhchi.share.utils.FormatUtils;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Full-screen avatar viewer. Launched whenever the user taps a profile
 * picture anywhere in the app — conversation rows, friend-search results,
 * the chat toolbar, group-info member rows.
 *
 * <p>Robustness handling:
 * <ol>
 *   <li>Initials always render immediately so the screen is never blank.</li>
 *   <li>If a photo URL was supplied, Glide attempts to overlay the photo.</li>
 *   <li>If Glide fails (the most common cause is a presigned URL that
 *       expired while the user was sitting on the home screen) <em>and</em>
 *       a {@code userId} extra was supplied, the activity calls
 *       {@code GET /api/users/{id}} to grab a freshly-presigned URL, then
 *       retries the load. We do this exactly once to avoid loops.</li>
 *   <li>If the retry also fails (or no userId was supplied), the initials
 *       block stays visible.</li>
 * </ol>
 */
public class AvatarViewerActivity extends AppCompatActivity {

    public static final String EXTRA_PHOTO_URL    = "photoUrl";
    public static final String EXTRA_DISPLAY_NAME = "displayName";
    /**
     * Optional. When set, the viewer can re-fetch a fresh presigned photo
     * URL via {@code /api/users/{id}} if the supplied URL has expired.
     * Pass {@code conv.getOtherUserId()} for DIRECT chats, {@code u.getId()}
     * for friend-search rows, {@code member.getUserId()} for group members.
     * Group icons (which aren't tied to a single user) leave this null.
     */
    public static final String EXTRA_USER_ID      = "userId";

    /**
     * Convenience launcher — keeps the intent-extra keys local to this
     * class so callers don't have to know them.
     */
    public static void launch(Context ctx, @Nullable String photoUrl, @Nullable String displayName) {
        launch(ctx, photoUrl, displayName, null);
    }

    /** Same as {@link #launch(Context, String, String)} plus the user id used
     *  for the on-failure URL refresh. */
    public static void launch(Context ctx, @Nullable String photoUrl,
                               @Nullable String displayName, @Nullable String userId) {
        Intent intent = new Intent(ctx, AvatarViewerActivity.class);
        if (photoUrl != null)    intent.putExtra(EXTRA_PHOTO_URL, photoUrl);
        if (displayName != null) intent.putExtra(EXTRA_DISPLAY_NAME, displayName);
        if (userId != null)      intent.putExtra(EXTRA_USER_ID, userId);
        ctx.startActivity(intent);
    }

    private ImageView ivAvatar;
    private TextView tvInitials;
    private String displayName;
    private String userId;
    /** Guard so we only refresh the URL once, even if the second load also fails. */
    private boolean attemptedRefresh = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_avatar_viewer);

        String url  = getIntent().getStringExtra(EXTRA_PHOTO_URL);
        displayName = getIntent().getStringExtra(EXTRA_DISPLAY_NAME);
        userId      = getIntent().getStringExtra(EXTRA_USER_ID);

        ivAvatar   = findViewById(R.id.avatarImage);
        tvInitials = findViewById(R.id.avatarInitials);
        TextView tvName = findViewById(R.id.tvName);
        tvName.setText(displayName != null ? displayName : "");

        // Tap anywhere on the backdrop dismisses the viewer.
        findViewById(R.id.avatarRoot).setOnClickListener(v -> finish());
        findViewById(R.id.btnClose).setOnClickListener(v -> finish());

        // ALWAYS show initials immediately. This guarantees the screen is
        // never blank — if there's no URL, no network, or Glide silently
        // dies, the user still sees something. If a photo eventually loads,
        // we'll overlay it via onResourceReady.
        showInitials(displayName);

        if (url == null || url.isEmpty()) {
            // No URL given — try fetching one if we have a userId. This
            // covers the "tapped a row whose iconUrl was already null"
            // case: most often a user who never set a photo, but
            // occasionally a backend response that dropped the URL.
            if (userId != null) tryRefreshAndLoad();
            return;
        }

        loadInto(url);
    }

    /**
     * Kick off a Glide load. On success, overlay the photo over initials.
     * On failure, fall back to a one-shot URL refresh if we have a userId.
     */
    private void loadInto(String url) {
        Glide.with(this)
                .load(url)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .skipMemoryCache(true)
                .listener(new RequestListener<Drawable>() {
                    @Override
                    public boolean onLoadFailed(@Nullable GlideException e, Object model,
                                                 Target<Drawable> target, boolean isFirstResource) {
                        // Stale presigned URL? Try once more with a fresh
                        // URL fetched via /api/users/{id}. This is the
                        // critical path that fixes "tap → initials block
                        // because URL expired while on home screen."
                        if (!attemptedRefresh && userId != null) {
                            attemptedRefresh = true;
                            tryRefreshAndLoad();
                        }
                        // Returning true suppresses Glide's own error drawable.
                        return true;
                    }
                    @Override
                    public boolean onResourceReady(Drawable resource, Object model,
                                                    Target<Drawable> target, DataSource dataSource,
                                                    boolean isFirstResource) {
                        ivAvatar.setVisibility(View.VISIBLE);
                        tvInitials.setVisibility(View.GONE);
                        return false;
                    }
                })
                .into(ivAvatar);
    }

    /**
     * Re-fetch the user from the server to get a freshly-presigned profile
     * photo URL, then retry the load. Backed by the same endpoint the
     * chat toolbar already uses; the backend re-presigns on every read so
     * the URL we receive is always within its TTL.
     */
    private void tryRefreshAndLoad() {
        if (userId == null) return;
        ApiClient.getInstance(this).getApiService()
                .getUserById(userId)
                .enqueue(new Callback<UserSearchResponse>() {
                    @Override
                    public void onResponse(Call<UserSearchResponse> call,
                                           Response<UserSearchResponse> response) {
                        if (isFinishing() || isDestroyed()) return;
                        if (!response.isSuccessful() || response.body() == null) return;
                        String fresh = response.body().getProfilePhotoUrl();
                        if (fresh == null || fresh.isEmpty()) return;
                        // Mark the refresh attempt as done so a second
                        // failure doesn't trigger another fetch.
                        attemptedRefresh = true;
                        loadInto(fresh);
                    }

                    @Override
                    public void onFailure(Call<UserSearchResponse> call, Throwable t) {
                        // Network blip — initials stay visible. Nothing to do.
                    }
                });
    }

    private void showInitials(@Nullable String name) {
        ivAvatar.setVisibility(View.GONE);
        tvInitials.setText(FormatUtils.initials(name));
        tvInitials.setVisibility(View.VISIBLE);
    }
}
