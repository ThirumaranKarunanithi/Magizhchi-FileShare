package com.magizhchi.share.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.RequestOptions;
import com.magizhchi.share.AvatarViewerActivity;
import com.magizhchi.share.R;
import com.magizhchi.share.model.UserSearchResponse;
import com.magizhchi.share.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders user-search results on the home screen with a context-aware
 * action button driven by {@code connectionStatus}:
 * <pre>
 *   CONNECTED         → "📁 Open"     (action: openDirect)
 *   NONE              → "🤝 Connect"  (action: sendRequest)
 *   PENDING_SENT      → "⏳ Pending"  (disabled)
 *   PENDING_RECEIVED  → "✓ Accept"    (action: acceptRequest)
 *   BLOCKED_BY_ME     → row is filtered out by the host activity
 *   SELF              → row is filtered out by the host activity
 * </pre>
 *
 * <p>Mirrors the web Sidebar's "search people" section so users can find
 * non-connections via the same search bar that filters their existing chats.
 */
public class FriendSearchAdapter extends RecyclerView.Adapter<FriendSearchAdapter.ViewHolder> {

    public interface OnActionListener {
        /** Connection exists — open or create the direct chat. */
        void onOpenChat(UserSearchResponse user);
        /** No relationship — send a connection request. */
        void onSendRequest(UserSearchResponse user);
        /** Incoming request — accept it. */
        void onAcceptRequest(UserSearchResponse user);
    }

    private final Context context;
    private List<UserSearchResponse> users = new ArrayList<>();
    private OnActionListener listener;

    public FriendSearchAdapter(Context context) {
        this.context = context;
    }

    public void setListener(OnActionListener l) { this.listener = l; }

    public void setUsers(List<UserSearchResponse> list) {
        this.users = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(context).inflate(R.layout.item_friend_search, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        UserSearchResponse user = users.get(position);
        String name = user.getDisplayName() != null ? user.getDisplayName() : "Unknown";

        // Initials first, photo overlays on Glide success — same robust
        // pattern used by ConversationAdapter so an expired presigned URL
        // doesn't leave the avatar blank.
        h.tvInitials.setVisibility(View.VISIBLE);
        h.tvInitials.setText(FormatUtils.initials(name));
        h.ivAvatar.setVisibility(View.GONE);
        // Cancel any in-flight Glide load on this recycled ImageView before
        // deciding what to do; tag the view with the bound user id so the
        // listener can drop a result that arrives after the holder has been
        // re-bound to a different user (the "wrong user's photo" leak).
        Glide.with(context).clear(h.ivAvatar);
        h.ivAvatar.setImageDrawable(null);
        final String boundUserId = user.getId();
        h.ivAvatar.setTag(R.id.tagAvatarBoundKey, boundUserId);

        if (user.getProfilePhotoUrl() != null && !user.getProfilePhotoUrl().isEmpty()) {
            Glide.with(context)
                    .load(user.getProfilePhotoUrl())
                    .apply(RequestOptions.circleCropTransform()
                            .diskCacheStrategy(DiskCacheStrategy.NONE)
                            .skipMemoryCache(true))
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e,
                                                              Object model,
                                                              com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                              boolean isFirstResource) { return true; }
                        @Override public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                                                 Object model,
                                                                 com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                                 com.bumptech.glide.load.DataSource dataSource,
                                                                 boolean isFirstResource) {
                            // Stale-bind guard — drop the result if the
                            // holder has moved to a different user.
                            Object currentTag = h.ivAvatar.getTag(R.id.tagAvatarBoundKey);
                            if (currentTag != null && !currentTag.equals(boundUserId)) {
                                return true;
                            }
                            h.ivAvatar.setVisibility(View.VISIBLE);
                            h.tvInitials.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(h.ivAvatar);
        }

        h.tvName.setText(name);

        // Tap on JUST the avatar opens the photo viewer. The avatar
        // FrameLayout has clickable=true so the touch is consumed and
        // doesn't fire the row's other handlers. Passing user.getId()
        // lets the viewer re-fetch a fresh presigned photo URL if the
        // cached one has expired.
        if (h.avatarFrame != null) {
            h.avatarFrame.setOnClickListener(v ->
                    AvatarViewerActivity.launch(context,
                            user.getProfilePhotoUrl(), name, user.getId()));
        }

        String status = user.getConnectionStatus();
        h.tvSubtitle.setText(subtitleFor(user, status));

        // Configure the action button by status.
        h.btnAction.setEnabled(true);
        h.btnAction.setVisibility(View.VISIBLE);
        h.btnAction.setOnClickListener(null);

        if ("CONNECTED".equalsIgnoreCase(status)) {
            h.btnAction.setText("📁 Open");
            h.btnAction.setOnClickListener(v -> {
                if (listener != null) listener.onOpenChat(user);
            });
        } else if ("PENDING_SENT".equalsIgnoreCase(status)) {
            h.btnAction.setText("⏳ Pending");
            h.btnAction.setEnabled(false);
        } else if ("PENDING_RECEIVED".equalsIgnoreCase(status)) {
            h.btnAction.setText("✓ Accept");
            h.btnAction.setOnClickListener(v -> {
                if (listener != null) listener.onAcceptRequest(user);
            });
        } else {
            // NONE (and any unknown status) → offer to connect.
            h.btnAction.setText("🤝 Connect");
            h.btnAction.setOnClickListener(v -> {
                if (listener != null) listener.onSendRequest(user);
            });
        }
    }

    private String subtitleFor(UserSearchResponse u, String status) {
        if ("CONNECTED".equalsIgnoreCase(status)) {
            String mob = u.getMobileNumber();
            return (mob != null && !mob.isEmpty()) ? mob : "Connected";
        }
        if ("PENDING_SENT".equalsIgnoreCase(status))     return "Request sent";
        if ("PENDING_RECEIVED".equalsIgnoreCase(status)) return "Wants to connect with you";
        return "Tap Connect to send a request";
    }

    @Override
    public int getItemCount() { return users.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        FrameLayout avatarFrame;
        ImageView ivAvatar;
        TextView  tvInitials;
        TextView  tvName;
        TextView  tvSubtitle;
        Button    btnAction;

        ViewHolder(View v) {
            super(v);
            avatarFrame = v.findViewById(R.id.avatarFrame);
            ivAvatar    = v.findViewById(R.id.ivAvatar);
            tvInitials  = v.findViewById(R.id.tvInitials);
            tvName      = v.findViewById(R.id.tvName);
            tvSubtitle  = v.findViewById(R.id.tvSubtitle);
            btnAction   = v.findViewById(R.id.btnAction);
        }
    }
}
