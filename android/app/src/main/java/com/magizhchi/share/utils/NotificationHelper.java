package com.magizhchi.share.utils;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.magizhchi.share.MainActivity;
import com.magizhchi.share.R;

/**
 * Centralised system-notification plumbing for the app.
 *
 * Single channel — "incoming_files" — used whenever someone shares a file
 * or folder with the user via the WebSocket NEW_FILE event. Tapping the
 * notification opens {@link MainActivity}.
 *
 * The channel is created lazily on first use; the runtime POST_NOTIFICATIONS
 * permission must already be granted by the caller (we request it from
 * MainActivity on first launch).
 */
public final class NotificationHelper {

    public static final String CHANNEL_ID  = "incoming_files";
    public static final String CHANNEL_NAME = "Incoming files";
    public static final String CHANNEL_DESC =
            "Alerts when someone shares a file or folder with you";

    private NotificationHelper() {}

    /** Idempotent — safe to call on every show. */
    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager nm = ctx.getSystemService(NotificationManager.class);
        if (nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH);
        ch.setDescription(CHANNEL_DESC);
        ch.enableVibration(true);
        nm.createNotificationChannel(ch);
    }

    /**
     * Surface a system notification for an incoming file / folder share.
     * Tapping it opens MainActivity (which decides whether to route into
     * a specific chat — passing the conversation id through the intent for
     * a future deep-link).
     *
     * @param senderName  display name of the person who shared, or "Someone"
     * @param fileName    name of the file/folder (folder uploads include
     *                    "(N files)" suffix from the server)
     * @param fileCount   number of files in this notification (1 for single
     *                    file uploads, N for folders)
     * @param convId      optional conversation id — used as the notification
     *                    tag so multiple drops in the same chat collapse
     */
    public static void showIncomingFile(Context ctx, String senderName,
                                         String fileName, int fileCount,
                                         String convId) {
        // Bail early if the user has revoked POST_NOTIFICATIONS.
        if (!hasPostPermission(ctx)) return;

        ensureChannel(ctx);

        Intent tap = new Intent(ctx, MainActivity.class);
        tap.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        if (convId != null) tap.putExtra("openConversationId", convId);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pi = PendingIntent.getActivity(ctx, 0, tap, flags);

        String who   = (senderName == null || senderName.isEmpty()) ? "Someone" : senderName;
        String title = fileCount > 1
                ? who + " shared " + fileCount + " files with you"
                : who + " shared a file with you";
        String body  = fileName != null && !fileName.isEmpty() ? fileName : "Tap to open";

        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(pi);

        // Use convId as the tag so a second arrival in the same chat replaces
        // the first instead of stacking. Stable id avoids the "thousands of
        // notifications" problem.
        int notifId = convId != null ? Math.abs(convId.hashCode()) : (int) System.currentTimeMillis();
        try {
            NotificationManagerCompat.from(ctx).notify(convId, notifId, builder.build());
        } catch (SecurityException ignored) {
            // POST_NOTIFICATIONS revoked between hasPostPermission() and notify()
        }
    }

    private static boolean hasPostPermission(Context ctx) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true;
        return ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }
}
