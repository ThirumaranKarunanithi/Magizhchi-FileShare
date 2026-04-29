package com.magizhchi.share.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
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

/**
 * Renders both file rows AND folder rows in the chat screen.
 *
 * Wrapped in a Row record (kind=FILE|FOLDER); the adapter inflates a single
 * shared layout and toggles which buttons + icon are shown.
 *
 * Per-item action set (mirrors the web):
 *   - 👁 Preview
 *   - ⬇ Download   (folders → batch download)
 *   - 📌 Pin
 *   - 🔗 Share
 *   - 🗑 Delete    (red, only for owner / for any folder created by user)
 *   - ℹ Properties
 *
 * Long-press toggles selection mode; in selection mode the action row hides
 * and the row checkbox shows. The host activity drives the selection toolbar.
 */
public class FileMessageAdapter extends RecyclerView.Adapter<FileMessageAdapter.ViewHolder> {

    public enum Kind { FILE, FOLDER }

    /** Polymorphic row — either a file message or a folder summary. */
    public static class Row {
        public final Kind kind;
        public final FileMessageResponse file;          // FILE only
        public final String folderPath;                 // FOLDER only — e.g. "js/"
        public final String folderName;                 // FOLDER only
        public final int folderItemCount;               // FOLDER only
        public final long folderTotalBytes;             // FOLDER only
        public final String folderLatestSentAt;         // FOLDER only — newest file in folder

        private Row(Kind k, FileMessageResponse f, String path, String name,
                    int count, long bytes, String latestAt) {
            this.kind = k;
            this.file = f;
            this.folderPath = path;
            this.folderName = name;
            this.folderItemCount = count;
            this.folderTotalBytes = bytes;
            this.folderLatestSentAt = latestAt;
        }

        public static Row file(FileMessageResponse f) {
            return new Row(Kind.FILE, f, null, null, 0, 0L, null);
        }
        public static Row folder(String path, String name, int count, long bytes, String latestAt) {
            return new Row(Kind.FOLDER, null, path, name, count, bytes, latestAt);
        }

        /** Stable id used by the selection set. */
        public String stableKey() {
            return kind == Kind.FILE
                    ? "F:" + (file != null ? file.getId() : "")
                    : "D:" + folderPath;
        }
    }

    public interface OnFileActionListener {
        void onPreview(FileMessageResponse file);
        void onDownload(FileMessageResponse file);
        void onPin(FileMessageResponse file);
        void onShare(FileMessageResponse file);
        void onDelete(FileMessageResponse file, int position);
        void onProperties(FileMessageResponse file);

        void onFolderOpen(Row folderRow);
        void onFolderDownload(Row folderRow);
        void onFolderShare(Row folderRow);
        void onFolderDelete(Row folderRow, int position);
        void onFolderProperties(Row folderRow);

        void onSelectionChanged(int selectedCount);
        void onLongPressStartSelection();
    }

    private final Context context;
    private final String currentUserId;
    private List<Row> rows = new ArrayList<>();
    private OnFileActionListener listener;
    private boolean selectionMode = false;
    private final Set<String> selectedKeys = new HashSet<>();

    public FileMessageAdapter(Context context, String currentUserId) {
        this.context = context;
        this.currentUserId = currentUserId;
    }

    public void setListener(OnFileActionListener listener) {
        this.listener = listener;
    }

    public void setRows(List<Row> list) {
        this.rows = list != null ? list : new ArrayList<>();
        // Drop any selection that isn't in the new list.
        Set<String> keep = new HashSet<>();
        for (Row r : rows) keep.add(r.stableKey());
        selectedKeys.retainAll(keep);
        notifyDataSetChanged();
    }

    public void removeAt(int position) {
        if (position >= 0 && position < rows.size()) {
            selectedKeys.remove(rows.get(position).stableKey());
            rows.remove(position);
            notifyItemRemoved(position);
        }
    }

    public boolean isSelectionMode() { return selectionMode; }

    public void setSelectionMode(boolean on) {
        if (selectionMode == on) return;
        selectionMode = on;
        if (!on) selectedKeys.clear();
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(selectedKeys.size());
    }

    public void selectAll() {
        if (!selectionMode) setSelectionMode(true);
        for (Row r : rows) selectedKeys.add(r.stableKey());
        notifyDataSetChanged();
        if (listener != null) listener.onSelectionChanged(selectedKeys.size());
    }

    public Set<String> getSelectedKeys() { return new HashSet<>(selectedKeys); }

    /** All file IDs currently selected (folders contribute nothing here). */
    public List<FileMessageResponse> getSelectedFiles() {
        List<FileMessageResponse> out = new ArrayList<>();
        for (Row r : rows) {
            if (r.kind == Kind.FILE && r.file != null && selectedKeys.contains(r.stableKey())) {
                out.add(r.file);
            }
        }
        return out;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_file_message, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int position) {
        Row row = rows.get(position);
        if (row.kind == Kind.FILE) bindFile(h, row, position);
        else                       bindFolder(h, row, position);

        // Common: selection-mode UI — checkbox + hide action row.
        h.checkBox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        h.actionRow.setVisibility(selectionMode ? View.GONE : View.VISIBLE);
        h.checkBox.setOnCheckedChangeListener(null);
        h.checkBox.setChecked(selectedKeys.contains(row.stableKey()));
        h.checkBox.setOnCheckedChangeListener((cb, checked) -> {
            if (checked) selectedKeys.add(row.stableKey());
            else         selectedKeys.remove(row.stableKey());
            if (listener != null) listener.onSelectionChanged(selectedKeys.size());
        });

        // Long-press anywhere on the row enters selection mode AND marks this row.
        h.itemView.setOnLongClickListener(v -> {
            if (!selectionMode) {
                selectionMode = true;
                selectedKeys.add(row.stableKey());
                notifyDataSetChanged();
                if (listener != null) {
                    listener.onLongPressStartSelection();
                    listener.onSelectionChanged(selectedKeys.size());
                }
            }
            return true;
        });

        // Tap in selection mode toggles the row's selection.
        h.itemView.setOnClickListener(v -> {
            if (selectionMode) {
                String key = row.stableKey();
                if (selectedKeys.contains(key)) selectedKeys.remove(key);
                else                            selectedKeys.add(key);
                notifyItemChanged(h.getAdapterPosition());
                if (listener != null) listener.onSelectionChanged(selectedKeys.size());
            } else if (row.kind == Kind.FOLDER && listener != null) {
                listener.onFolderOpen(row);
            } else if (row.kind == Kind.FILE && listener != null) {
                listener.onPreview(row.file);
            }
        });
    }

    // ── File-row binding ──────────────────────────────────────────────────────

    private void bindFile(ViewHolder h, Row row, int pos) {
        FileMessageResponse f = row.file;

        // Category emoji + glass pill background (NOT the yellow folder bg).
        h.tvIcon.setBackgroundResource(R.drawable.bg_pill_inactive);
        h.tvIcon.setText(FormatUtils.fileIcon(f.getCategory(), f.getContentType()));
        h.tvFileName.setText(f.getOriginalFileName() != null ? f.getOriginalFileName() : "Unknown file");

        // Date + TIME line: "9.1 MB · 29 Apr 2026, 3:42 PM"
        h.tvSizeDate.setText(
                FormatUtils.formatBytes(f.getFileSizeBytes())
                        + " · " + FormatUtils.formatDateTime(f.getSentAt()));

        // Caption (description)
        if (f.getCaption() != null && !f.getCaption().isEmpty()) {
            h.tvCaption.setVisibility(View.VISIBLE);
            h.tvCaption.setText("\"" + f.getCaption() + "\"");
        } else h.tvCaption.setVisibility(View.GONE);

        boolean isSelf = f.getSenderId() != null && f.getSenderId().equals(currentUserId);
        if (!isSelf && f.getSenderName() != null) {
            h.tvSender.setVisibility(View.VISIBLE);
            h.tvSender.setText("From: " + f.getSenderName());
        } else h.tvSender.setVisibility(View.GONE);

        h.tvCategory.setVisibility(View.VISIBLE);
        h.tvCategory.setText(f.getCategory() != null ? f.getCategory() : "FILE");

        // Action set: preview / download / pin / share / delete (owner) / properties
        h.btnPreview.setVisibility(View.VISIBLE);
        h.btnDownload.setVisibility(View.VISIBLE);
        h.btnPin.setVisibility(View.VISIBLE);
        h.btnShare.setVisibility(View.VISIBLE);
        h.btnDelete.setVisibility(isSelf ? View.VISIBLE : View.GONE);
        h.btnProperties.setVisibility(View.VISIBLE);

        h.btnPreview.setOnClickListener(v -> { if (listener != null) listener.onPreview(f); });
        h.btnDownload.setOnClickListener(v -> { if (listener != null) listener.onDownload(f); });
        h.btnPin.setOnClickListener(v -> { if (listener != null) listener.onPin(f); });
        h.btnShare.setOnClickListener(v -> { if (listener != null) listener.onShare(f); });
        h.btnDelete.setOnClickListener(v -> {
            int p = h.getAdapterPosition();
            if (listener != null) listener.onDelete(f, p);
        });
        h.btnProperties.setOnClickListener(v -> { if (listener != null) listener.onProperties(f); });
    }

    // ── Folder-row binding ────────────────────────────────────────────────────

    private void bindFolder(ViewHolder h, Row row, int pos) {
        // Yellow folder badge — only for folder rows, never for files.
        h.tvIcon.setBackgroundResource(R.drawable.bg_folder_icon);
        h.tvIcon.setText("📁");
        h.tvFileName.setText(row.folderName != null ? row.folderName : "Folder");
        h.tvSizeDate.setText(
                row.folderItemCount + " item" + (row.folderItemCount != 1 ? "s" : "")
                        + " · " + FormatUtils.formatBytes(row.folderTotalBytes)
                        + (row.folderLatestSentAt != null
                            ? "\nUpdated " + FormatUtils.formatDateTime(row.folderLatestSentAt)
                            : ""));
        h.tvSizeDate.setMaxLines(2);
        h.tvCaption.setVisibility(View.GONE);
        h.tvSender.setVisibility(View.GONE);
        h.tvCategory.setVisibility(View.GONE);

        // Folder action set: preview = open ; download = batch ; share ; delete ; properties.
        // Pin is not folder-applicable here (folder pin is server-side and the
        // adapter doesn't have folder ID context), so hide it.
        h.btnPreview.setVisibility(View.VISIBLE);
        h.btnDownload.setVisibility(View.VISIBLE);
        h.btnPin.setVisibility(View.GONE);
        h.btnShare.setVisibility(View.VISIBLE);
        h.btnDelete.setVisibility(View.VISIBLE);
        h.btnProperties.setVisibility(View.VISIBLE);

        h.btnPreview.setOnClickListener(v -> { if (listener != null) listener.onFolderOpen(row); });
        h.btnDownload.setOnClickListener(v -> { if (listener != null) listener.onFolderDownload(row); });
        h.btnShare.setOnClickListener(v -> { if (listener != null) listener.onFolderShare(row); });
        h.btnDelete.setOnClickListener(v -> {
            int p = h.getAdapterPosition();
            if (listener != null) listener.onFolderDelete(row, p);
        });
        h.btnProperties.setOnClickListener(v -> { if (listener != null) listener.onFolderProperties(row); });
    }

    @Override
    public int getItemCount() { return rows.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        CheckBox checkBox;
        TextView tvIcon;
        TextView tvFileName;
        TextView tvSizeDate;
        TextView tvCaption;
        TextView tvSender;
        TextView tvCategory;
        LinearLayout actionRow;
        ImageButton btnPreview, btnDownload, btnPin, btnShare, btnDelete, btnProperties;

        ViewHolder(View v) {
            super(v);
            checkBox      = v.findViewById(R.id.checkBox);
            tvIcon        = v.findViewById(R.id.tvIcon);
            tvFileName    = v.findViewById(R.id.tvFileName);
            tvSizeDate    = v.findViewById(R.id.tvSizeDate);
            tvCaption     = v.findViewById(R.id.tvCaption);
            tvSender      = v.findViewById(R.id.tvSender);
            tvCategory    = v.findViewById(R.id.tvCategory);
            actionRow     = v.findViewById(R.id.actionRow);
            btnPreview    = v.findViewById(R.id.btnPreview);
            btnDownload   = v.findViewById(R.id.btnDownload);
            btnPin        = v.findViewById(R.id.btnPin);
            btnShare      = v.findViewById(R.id.btnShare);
            btnDelete     = v.findViewById(R.id.btnDelete);
            btnProperties = v.findViewById(R.id.btnProperties);
        }
    }
}
