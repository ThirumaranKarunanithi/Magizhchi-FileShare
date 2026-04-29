package com.magizhchi.share.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.magizhchi.share.AvatarViewerActivity;
import com.magizhchi.share.R;
import com.magizhchi.share.model.ConversationResponse;
import com.magizhchi.share.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

public class ConversationAdapter extends RecyclerView.Adapter<ConversationAdapter.ViewHolder> {

    public interface OnConversationClickListener {
        void onConversationClick(ConversationResponse conversation);
        void onConversationLongPress(ConversationResponse conversation, View anchorView);
    }

    private final Context context;
    private List<ConversationResponse> conversations = new ArrayList<>();
    private OnConversationClickListener listener;
    /** convId → unread file count. Mutated from MainActivity as WS events arrive. */
    private java.util.Map<String, Integer> unreadCounts = new java.util.HashMap<>();

    public ConversationAdapter(Context context) {
        this.context = context;
    }

    public void setListener(OnConversationClickListener listener) {
        this.listener = listener;
    }

    public void setConversations(List<ConversationResponse> list) {
        this.conversations = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    /** Replace the entire unread-count map and refresh affected rows. */
    public void setUnreadCounts(java.util.Map<String, Integer> counts) {
        this.unreadCounts = counts != null ? counts : new java.util.HashMap<>();
        notifyDataSetChanged();
    }

    /** Increment the count for a single conversation in-place. */
    public void bumpUnread(String conversationId, int delta) {
        if (conversationId == null) return;
        int v = unreadCounts.getOrDefault(conversationId, 0) + delta;
        unreadCounts.put(conversationId, Math.max(0, v));
        // Find and refresh just that row
        for (int i = 0; i < conversations.size(); i++) {
            if (conversationId.equals(conversations.get(i).getId())) {
                notifyItemChanged(i);
                break;
            }
        }
    }

    /** Clear the unread count for one conversation (called when user opens it). */
    public void clearUnread(String conversationId) {
        if (conversationId == null) return;
        if (unreadCounts.remove(conversationId) != null) {
            for (int i = 0; i < conversations.size(); i++) {
                if (conversationId.equals(conversations.get(i).getId())) {
                    notifyItemChanged(i);
                    break;
                }
            }
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_conversation, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ConversationResponse conv = conversations.get(position);
        boolean isDirect = "DIRECT".equalsIgnoreCase(conv.getType());

        // Card background
        holder.cardView.setCardBackgroundColor(Color.TRANSPARENT);
        holder.cardView.setBackground(context.getDrawable(
                isDirect ? R.drawable.bg_card_direct : R.drawable.bg_card_group));

        // Avatar — initials FIRST so the row is never blank, then attempt to
        // overlay the photo. Glide's cache is disabled because conv.iconUrl
        // is a presigned S3 URL that goes stale; a cached failure must not
        // poison future retries. On success the photo overlays + hides
        // initials; on failure the initials stay visible.
        holder.tvInitials.setVisibility(View.VISIBLE);
        holder.tvInitials.setText(FormatUtils.initials(conv.getName()));
        holder.tvInitials.setBackgroundResource(
                isDirect ? R.drawable.bg_avatar_direct : R.drawable.bg_avatar_group);
        holder.ivAvatar.setVisibility(View.GONE);
        // CRITICAL: cancel any in-flight Glide request on this recycled
        // ImageView before deciding what to do. Without this, a row whose
        // user has no photo will inherit the previous bind's still-loading
        // image and paint it on top of the wrong user's row — the "Anandh's
        // photo on Karnishh's row" bug. We also tag the ImageView with the
        // conversation id so the listener can verify it's still bound to
        // the right row before applying the result (defence in depth).
        Glide.with(context).clear(holder.ivAvatar);
        holder.ivAvatar.setImageDrawable(null);
        final String boundConvId = conv.getId();
        holder.ivAvatar.setTag(R.id.tagAvatarBoundKey, boundConvId);

        if (conv.getIconUrl() != null && !conv.getIconUrl().isEmpty()) {
            Glide.with(context)
                    .load(conv.getIconUrl())
                    .apply(RequestOptions.circleCropTransform()
                            .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.NONE)
                            .skipMemoryCache(true))
                    .listener(new com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable>() {
                        @Override public boolean onLoadFailed(@androidx.annotation.Nullable com.bumptech.glide.load.engine.GlideException e,
                                                              Object model,
                                                              com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                              boolean isFirstResource) {
                            // Keep initials visible; suppress Glide's own placeholder.
                            return true;
                        }
                        @Override public boolean onResourceReady(android.graphics.drawable.Drawable resource,
                                                                 Object model,
                                                                 com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable> target,
                                                                 com.bumptech.glide.load.DataSource dataSource,
                                                                 boolean isFirstResource) {
                            // Stale-bind guard — if the holder has been
                            // recycled into a different conversation while
                            // this load was in flight, drop the result
                            // instead of painting it onto the wrong row.
                            Object currentTag = holder.ivAvatar.getTag(R.id.tagAvatarBoundKey);
                            if (currentTag != null && !currentTag.equals(boundConvId)) {
                                return true;
                            }
                            holder.ivAvatar.setVisibility(View.VISIBLE);
                            holder.tvInitials.setVisibility(View.GONE);
                            return false;
                        }
                    })
                    .into(holder.ivAvatar);
        }

        // Name
        holder.tvName.setText(conv.getName() != null ? conv.getName() : "Unnamed");

        // Subtitle
        if (isDirect) {
            holder.tvSubtitle.setText("Direct file share");
        } else {
            int count = conv.getMemberCount();
            holder.tvSubtitle.setText(count + " member" + (count != 1 ? "s" : ""));
        }

        // Date badge
        if (conv.getLastFile() != null && conv.getLastFile().getSentAt() != null) {
            holder.tvDate.setVisibility(View.VISIBLE);
            holder.tvDate.setText(FormatUtils.formatDate(conv.getLastFile().getSentAt()));
        } else if (conv.getCreatedAt() != null) {
            holder.tvDate.setVisibility(View.VISIBLE);
            holder.tvDate.setText(FormatUtils.formatDate(conv.getCreatedAt()));
        } else {
            holder.tvDate.setVisibility(View.GONE);
        }

        // Unread file count badge — "📥 N"
        int unread = unreadCounts.getOrDefault(conv.getId(), 0);
        if (unread > 0) {
            holder.tvUnreadBadge.setVisibility(View.VISIBLE);
            holder.tvUnreadBadge.setText("📥 " + (unread > 99 ? "99+" : String.valueOf(unread)));
        } else {
            holder.tvUnreadBadge.setVisibility(View.GONE);
        }
        holder.unreadDot.setVisibility(View.GONE);

        // Clicks
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onConversationClick(conv);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onConversationLongPress(conv, v);
            return true;
        });

        // Tap on JUST the avatar opens the photo viewer instead of the
        // conversation. The avatar FrameLayout has its own clickable=true
        // so the touch is consumed here and doesn't bubble up to the row.
        // Pass otherUserId for DIRECT chats so the viewer can re-fetch a
        // fresh presigned URL if the cached one has expired (the "AS"
        // initials instead of Aswin's photo bug). Group icons aren't tied
        // to a single user, so userId stays null for groups — the viewer
        // just shows initials if the icon URL is stale.
        if (holder.avatarFrame != null) {
            String userIdForRefresh = isDirect ? conv.getOtherUserId() : null;
            holder.avatarFrame.setOnClickListener(v ->
                    AvatarViewerActivity.launch(context,
                            conv.getIconUrl(), conv.getName(), userIdForRefresh));
        }
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        FrameLayout avatarFrame;
        ImageView ivAvatar;
        TextView tvInitials;
        TextView tvName;
        TextView tvSubtitle;
        TextView tvDate;
        TextView tvUnreadBadge;
        View unreadDot;

        ViewHolder(View itemView) {
            super(itemView);
            cardView      = itemView.findViewById(R.id.cardConversation);
            avatarFrame   = itemView.findViewById(R.id.avatarFrame);
            ivAvatar      = itemView.findViewById(R.id.ivAvatar);
            tvInitials    = itemView.findViewById(R.id.tvInitials);
            tvName        = itemView.findViewById(R.id.tvName);
            tvSubtitle    = itemView.findViewById(R.id.tvSubtitle);
            tvDate        = itemView.findViewById(R.id.tvDate);
            tvUnreadBadge = itemView.findViewById(R.id.tvUnreadBadge);
            unreadDot     = itemView.findViewById(R.id.unreadDot);
        }
    }
}
