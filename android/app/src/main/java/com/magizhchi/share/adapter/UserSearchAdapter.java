package com.magizhchi.share.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.magizhchi.share.R;
import com.magizhchi.share.model.UserSearchResponse;
import com.magizhchi.share.utils.FormatUtils;

import java.util.ArrayList;
import java.util.List;

public class UserSearchAdapter extends RecyclerView.Adapter<UserSearchAdapter.ViewHolder> {

    public interface OnAddUserListener {
        void onAddUser(UserSearchResponse user);
    }

    private final Context context;
    private List<UserSearchResponse> users = new ArrayList<>();
    private OnAddUserListener listener;

    public UserSearchAdapter(Context context) {
        this.context = context;
    }

    public void setListener(OnAddUserListener listener) {
        this.listener = listener;
    }

    public void setUsers(List<UserSearchResponse> list) {
        this.users = list != null ? list : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_user_search, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        UserSearchResponse user = users.get(position);

        if (user.getProfilePhotoUrl() != null && !user.getProfilePhotoUrl().isEmpty()) {
            holder.ivAvatar.setVisibility(View.VISIBLE);
            holder.tvInitials.setVisibility(View.GONE);
            Glide.with(context)
                    .load(user.getProfilePhotoUrl())
                    .apply(RequestOptions.circleCropTransform())
                    .placeholder(R.drawable.bg_pill_inactive)
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setVisibility(View.GONE);
            holder.tvInitials.setVisibility(View.VISIBLE);
            holder.tvInitials.setText(FormatUtils.initials(user.getDisplayName()));
        }

        holder.tvName.setText(user.getDisplayName() != null ? user.getDisplayName() : "Unknown");
        holder.tvStatus.setText(user.getConnectionStatus() != null ? user.getConnectionStatus() : "");

        holder.btnAdd.setOnClickListener(v -> {
            if (listener != null) listener.onAddUser(user);
        });
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvInitials;
        TextView tvName;
        TextView tvStatus;
        Button btnAdd;

        ViewHolder(View itemView) {
            super(itemView);
            ivAvatar   = itemView.findViewById(R.id.ivAvatar);
            tvInitials = itemView.findViewById(R.id.tvInitials);
            tvName     = itemView.findViewById(R.id.tvName);
            tvStatus   = itemView.findViewById(R.id.tvStatus);
            btnAdd     = itemView.findViewById(R.id.btnAdd);
        }
    }
}
