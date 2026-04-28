package com.magizhchi.share.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.magizhchi.share.R;
import com.magizhchi.share.model.FileMessageResponse;
import com.magizhchi.share.utils.FormatUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileMessageAdapter extends RecyclerView.Adapter<FileMessageAdapter.ViewHolder> {

    public interface OnFileActionListener {
        void onDownload(FileMessageResponse file);
        void onDelete(FileMessageResponse file, int position);
    }

    private final Context context;
    private List<FileMessageResponse> files = new ArrayList<>();
    private final String currentUserId;
    private OnFileActionListener listener;
    private boolean multiSelectMode = false;
    private final Set<String> selectedIds = new HashSet<>();

    public FileMessageAdapter(Context context, String currentUserId) {
        this.context = context;
        this.currentUserId = currentUserId;
    }

    public void setListener(OnFileActionListener listener) {
        this.listener = listener;
    }

    public void setFiles(List<FileMessageResponse> list) {
        this.files = list != null ? list : new ArrayList<>();
        selectedIds.clear();
        notifyDataSetChanged();
    }

    public void addFiles(List<FileMessageResponse> list) {
        if (list == null) return;
        int start = files.size();
        files.addAll(list);
        notifyItemRangeInserted(start, list.size());
    }

    public void removeFile(int position) {
        if (position >= 0 && position < files.size()) {
            files.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void prependFile(FileMessageResponse file) {
        files.add(0, file);
        notifyItemInserted(0);
    }

    public Set<String> getSelectedIds() {
        return new HashSet<>(selectedIds);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_file_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FileMessageResponse file = files.get(position);

        // Emoji icon
        String icon = FormatUtils.fileIcon(file.getCategory(), file.getContentType());
        holder.tvIcon.setText(icon);

        // File name
        holder.tvFileName.setText(file.getOriginalFileName() != null
                ? file.getOriginalFileName() : "Unknown file");

        // Size + date
        String sizeDate = FormatUtils.formatBytes(file.getFileSizeBytes())
                + " • " + FormatUtils.formatDate(file.getSentAt());
        holder.tvSizeDate.setText(sizeDate);

        // Caption
        if (file.getCaption() != null && !file.getCaption().isEmpty()) {
            holder.tvCaption.setVisibility(View.VISIBLE);
            holder.tvCaption.setText(file.getCaption());
        } else {
            holder.tvCaption.setVisibility(View.GONE);
        }

        // Sender (show only if not self)
        boolean isSelf = file.getSenderId() != null && file.getSenderId().equals(currentUserId);
        if (!isSelf && file.getSenderName() != null) {
            holder.tvSender.setVisibility(View.VISIBLE);
            holder.tvSender.setText("From: " + file.getSenderName());
        } else {
            holder.tvSender.setVisibility(View.GONE);
        }

        // Category badge
        holder.tvCategory.setText(file.getCategory() != null ? file.getCategory() : "FILE");

        // Download button
        holder.btnDownload.setOnClickListener(v -> {
            if (listener != null) listener.onDownload(file);
        });

        // Delete button — only for own files
        if (isSelf) {
            holder.btnDelete.setVisibility(View.VISIBLE);
            holder.btnDelete.setOnClickListener(v -> {
                int pos = holder.getAdapterPosition();
                if (listener != null) listener.onDelete(file, pos);
            });
        } else {
            holder.btnDelete.setVisibility(View.GONE);
        }

        // Multi-select checkbox
        if (multiSelectMode) {
            holder.checkBox.setVisibility(View.VISIBLE);
            holder.checkBox.setChecked(selectedIds.contains(file.getId()));
            holder.checkBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) selectedIds.add(file.getId());
                else selectedIds.remove(file.getId());
            });
        } else {
            holder.checkBox.setVisibility(View.GONE);
        }
    }

    @Override
    public int getItemCount() {
        return files.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView tvIcon;
        TextView tvFileName;
        TextView tvSizeDate;
        TextView tvCaption;
        TextView tvSender;
        TextView tvCategory;
        ImageButton btnDownload;
        ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            checkBox    = itemView.findViewById(R.id.checkBox);
            tvIcon      = itemView.findViewById(R.id.tvIcon);
            tvFileName  = itemView.findViewById(R.id.tvFileName);
            tvSizeDate  = itemView.findViewById(R.id.tvSizeDate);
            tvCaption   = itemView.findViewById(R.id.tvCaption);
            tvSender    = itemView.findViewById(R.id.tvSender);
            tvCategory  = itemView.findViewById(R.id.tvCategory);
            btnDownload = itemView.findViewById(R.id.btnDownload);
            btnDelete   = itemView.findViewById(R.id.btnDelete);
        }
    }
}
