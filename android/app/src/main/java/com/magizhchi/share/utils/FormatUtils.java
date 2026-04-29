package com.magizhchi.share.utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class FormatUtils {

    private FormatUtils() {}

    /**
     * Format bytes into human-readable size string.
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String prefix = "KMGTPE".charAt(exp - 1) + "";
        return String.format(Locale.getDefault(), "%.1f %sB", bytes / Math.pow(1024, exp), prefix);
    }

    /**
     * Format an ISO-8601 date string into a friendly display string.
     */
    public static String formatDate(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        try {
            SimpleDateFormat isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault());
            isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = isoFormat.parse(isoDate);
            if (date == null) return isoDate;

            long diff = System.currentTimeMillis() - date.getTime();
            long days = diff / (1000 * 60 * 60 * 24);

            if (days == 0) {
                SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm a", Locale.getDefault());
                return timeFormat.format(date);
            } else if (days == 1) {
                return "Yesterday";
            } else if (days < 7) {
                SimpleDateFormat dayFormat = new SimpleDateFormat("EEE", Locale.getDefault());
                return dayFormat.format(date);
            } else {
                SimpleDateFormat fullFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
                return fullFormat.format(date);
            }
        } catch (ParseException e) {
            // Try without milliseconds
            try {
                SimpleDateFormat isoFormat2 = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.getDefault());
                isoFormat2.setTimeZone(TimeZone.getTimeZone("UTC"));
                Date date = isoFormat2.parse(isoDate);
                if (date != null) {
                    SimpleDateFormat displayFormat = new SimpleDateFormat("MMM d", Locale.getDefault());
                    return displayFormat.format(date);
                }
            } catch (ParseException ignored) {}
            return isoDate.substring(0, Math.min(10, isoDate.length()));
        }
    }

    /**
     * Format an ISO-8601 timestamp into a fixed "d MMM yyyy, h:mm a" line —
     * used by the chat list rows where the user wants both date AND time.
     */
    public static String formatDateTime(String isoDate) {
        if (isoDate == null || isoDate.isEmpty()) return "";
        SimpleDateFormat[] parsers = {
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.getDefault()),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'",     Locale.getDefault()),
        };
        for (SimpleDateFormat p : parsers) {
            p.setTimeZone(TimeZone.getTimeZone("UTC"));
            try {
                Date d = p.parse(isoDate);
                if (d != null) {
                    SimpleDateFormat out = new SimpleDateFormat("d MMM yyyy, h:mm a", Locale.getDefault());
                    return out.format(d);
                }
            } catch (ParseException ignored) {}
        }
        return isoDate;
    }

    /**
     * Return an emoji representing the file category/mime type.
     */
    public static String fileIcon(String category, String contentType) {
        if (category == null) category = "";
        if (contentType == null) contentType = "";

        switch (category.toUpperCase()) {
            case "IMAGE":   return "🖼"; // 🖼
            case "VIDEO":   return "🎥"; // 🎥
            case "AUDIO":   return "🎵"; // 🎵
            case "DOCUMENT":
                if (contentType.contains("pdf"))   return "📄"; // 📄
                if (contentType.contains("word"))  return "📝"; // 📝
                if (contentType.contains("sheet") || contentType.contains("excel")) return "📊"; // 📊
                if (contentType.contains("presentation") || contentType.contains("powerpoint")) return "📊";
                return "📄"; // 📄
            case "ARCHIVE": return "📦"; // 📦
            default:        return "📁"; // 📁
        }
    }

    /**
     * Derive category from MIME type string.
     */
    public static String categoryFromMime(String mimeType) {
        if (mimeType == null) return "OTHER";
        if (mimeType.startsWith("image/"))  return "IMAGE";
        if (mimeType.startsWith("video/"))  return "VIDEO";
        if (mimeType.startsWith("audio/"))  return "AUDIO";
        if (mimeType.equals("application/pdf")
                || mimeType.contains("word")
                || mimeType.contains("excel")
                || mimeType.contains("spreadsheet")
                || mimeType.contains("powerpoint")
                || mimeType.contains("presentation")
                || mimeType.startsWith("text/")) {
            return "DOCUMENT";
        }
        if (mimeType.contains("zip")
                || mimeType.contains("tar")
                || mimeType.contains("gzip")
                || mimeType.contains("rar")
                || mimeType.contains("7z")) {
            return "ARCHIVE";
        }
        return "OTHER";
    }

    /**
     * Returns display-friendly initials (up to 2 characters) from a display name.
     */
    public static String initials(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) return "?";
        String[] parts = displayName.trim().split("\\s+");
        if (parts.length == 1) {
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase();
        }
        return (parts[0].substring(0, 1) + parts[1].substring(0, 1)).toUpperCase();
    }
}
