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
import com.bumptech.glide.request.RequestOptions;
import com.magizhchi.share.AvatarViewerActivity;
import com.magizhchi.share.R;
import com.magizhchi.share.model.GroupMemberResponse;
import com.magizhchi.share.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

public class MemberAdapter extends RecyclerView.Adapter<MemberAdapter.ViewHolder> {

    public interface OnMemberActionListener {
        void onMakeAdmin(GroupMemberResponse member, int position);
        void onRemoveAdmin(GroupMemberResponse member, int position);
        void onRemoveMember(GroupMemberResponse member, int position);
    }

    private final Context context;
    private List<GroupMemberResponse> members = new ArrayList<>();
    private final String currentUserId;
    private boolean isCurrentUserAdmin = false;
    private OnMemberActionListener listener;

    public MemberAdapter(Context context, String currentUserId) {
        this.context = context;
        this.currentUserId = currentUserId;
    }

    public void setListener(OnMemberActionListener listener) {
        this.listener = listener;
    }

    public void setMembers(List<GroupMemberResponse> list) {
        this.members = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setCurrentUserAdmin(boolean isAdmin) {
        this.isCurrentUserAdmin = isAdmin;
        notifyDataSetChanged();
    }

    public void removeMember(int position) {
        if (position >= 0 && position < members.size()) {
            members.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void updateMember(int position, GroupMemberResponse member) {
        if (position >= 0 && position < members.size()) {
            members.set(position, member);
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_member, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        GroupMemberResponse member = members.get(position);
        boolean isSelf = member.getUserId() != null && member.getUserId().equals(currentUserId);
        boolean isMemberAdmin = "ADMIN".equalsIgnoreCase(member.getRole());

        // Avatar
        if (member.getProfilePhotoUrl() != null && !member.getProfilePhotoUrl().isEmpty()) {
            holder.ivAvatar.setVisibility(View.VISIBLE);
            holder.tvInitials.setVisibility(View.GONE);
            Glide.with(context)
                    .load(member.getProfilePhotoUrl())
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_pill_inactive)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setVisibility(View.GONE);
            holder.tvInitials.setVisibility(View.VISIBLE);
            holder.tvInitials.setText(FormatUtils.initials(member.getDisplayName()));
        }

        // Tap on the avatar opens the full-screen photo viewer. The
        // member's userId lets the viewer refresh the URL if the cached
        // one has expired.
        if (holder.avatarFrame != null) {
            holder.avatarFrame.setOnClickListener(v ->
                    AvatarViewerActivity.launch(context,
                            member.getProfilePhotoUrl(),
                            member.getDisplayName(),
                            member.getUserId()));
        }

        // Name
        holder.tvName.setText(member.getDisplayName() != null ? member.getDisplayName() : "Unknown");

        // Role badge
        if (isMemberAdmin) {
            holder.tvRoleBadge.setText("★ Admin");
            holder.tvRoleBadge.setBackgroundResource(R.drawable.bg_pill_active);
        } else {
            holder.tvRoleBadge.setText("Member");
            holder.tvRoleBadge.setBackgroundResource(R.drawable.bg_pill_inactive);
        }

        // Admin action buttons — only visible to admins, not for self
        if (isCurrentUserAdmin && !isSelf) {
            if (isMemberAdmin) {
                holder.btnMakeAdmin.setVisibility(View.GONE);
                holder.btnRemoveAdmin.setVisibility(View.VISIBLE);
            } else {
                holder.btnMakeAdmin.setVisibility(View.VISIBLE);
                holder.btnRemoveAdmin.setVisibility(View.GONE);
            }
            holder.btnRemove.setVisibility(View.VISIBLE);
        } else {
            holder.btnMakeAdmin.setVisibility(View.GONE);
            holder.btnRemoveAdmin.setVisibility(View.GONE);
            holder.btnRemove.setVisibility(View.GONE);
        }

        holder.btnMakeAdmin.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (listener != null) listener.onMakeAdmin(member, pos);
        });
        holder.btnRemoveAdmin.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (listener != null) listener.onRemoveAdmin(member, pos);
        });
        holder.btnRemove.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (listener != null) listener.onRemoveMember(member, pos);
        });
    }

    @Override
    public int getItemCount() {
        return members.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        FrameLayout avatarFrame;
        ImageView ivAvatar;
        TextView tvInitials;
        TextView tvName;
        TextView tvRoleBadge;
        Button btnMakeAdmin;
        Button btnRemoveAdmin;
        Button btnRemove;

        ViewHolder(View itemView) {
            super(itemView);
            avatarFrame   = itemView.findViewById(R.id.avatarFrame);
            ivAvatar      = itemView.findViewById(R.id.ivAvatar);
            tvInitials    = itemView.findViewById(R.id.tvInitials);
            tvName        = itemView.findViewById(R.id.tvName);
            tvRoleBadge   = itemView.findViewById(R.id.tvRoleBadge);
            btnMakeAdmin  = itemView.findViewById(R.id.btnMakeAdmin);
            btnRemoveAdmin= itemView.findViewById(R.id.btnRemoveAdmin);
            btnRemove     = itemView.findViewById(R.id.btnRemove);
        }
    }
}
