package com.magizhchi.share.adapter;

import android.content.Context;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
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

        // Avatar
        if (conv.getIconUrl() != null && !conv.getIconUrl().isEmpty()) {
            holder.ivAvatar.setVisibility(View.VISIBLE);
            holder.tvInitials.setVisibility(View.GONE);
            Glide.with(context)
                    .load(conv.getIconUrl())
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_pill_inactive)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setVisibility(View.GONE);
            holder.tvInitials.setVisibility(View.VISIBLE);
            holder.tvInitials.setText(FormatUtils.initials(conv.getName()));
            holder.tvInitials.setBackgroundResource(
                    isDirect ? R.drawable.bg_avatar_direct : R.drawable.bg_avatar_group);
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

        // Unread dot — hide for now (no unread count in API response)
        holder.unreadDot.setVisibility(View.GONE);

        // Clicks
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onConversationClick(conv);
        });
        holder.itemView.setOnLongClickListener(v -> {
            if (listener != null) listener.onConversationLongPress(conv, v);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return conversations.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CardView cardView;
        ImageView ivAvatar;
        TextView tvInitials;
        TextView tvName;
        TextView tvSubtitle;
        TextView tvDate;
        View unreadDot;

        ViewHolder(View itemView) {
            super(itemView);
            cardView    = itemView.findViewById(R.id.cardConversation);
            ivAvatar    = itemView.findViewById(R.id.ivAvatar);
            tvInitials  = itemView.findViewById(R.id.tvInitials);
            tvName      = itemView.findViewById(R.id.tvName);
            tvSubtitle  = itemView.findViewById(R.id.tvSubtitle);
            tvDate      = itemView.findViewById(R.id.tvDate);
            unreadDot   = itemView.findViewById(R.id.unreadDot);
        }
    }
}
