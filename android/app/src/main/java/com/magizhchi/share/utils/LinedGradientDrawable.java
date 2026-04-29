package com.magizhchi.share.utils;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Home-screen background. Mirrors the web sidebar exactly:
 *   - Sky-50 (#E0F2FE) wash
 *   - 22dp grid of soft white lines (~55% alpha) for the "lined paper" look.
 *
 * Drawn entirely in code so it tiles cleanly at any screen size without
 * needing a bitmap asset. The class name is kept for binary-compatibility
 * with the existing MainActivity import.
 */
public class LinedGradientDrawable extends Drawable {

    private static final int   COLOR_BG     = 0xFFE0F2FE; // sky-50, matches web sidebar
    private static final int   LINE_COLOR   = 0x8CFFFFFF; // white @ ~55 % — subtle grid
    private static final float TILE_DP      = 22f;        // matches `backgroundSize: 22px`
    private static final float LINE_DP      = 1f;

    private final Paint bgPaint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float tilePx;
    private final float linePx;

    public LinedGradientDrawable(@NonNull Resources res) {
        DisplayMetrics dm = res.getDisplayMetrics();
        tilePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TILE_DP, dm);
        linePx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, LINE_DP, dm);

        bgPaint.setColor(COLOR_BG);
        bgPaint.setStyle(Paint.Style.FILL);

        linePaint.setColor(LINE_COLOR);
        linePaint.setStrokeWidth(linePx);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        android.graphics.Rect b = getBounds();

        // 1) sky-50 wash
        canvas.drawRect(b.left, b.top, b.right, b.bottom, bgPaint);

        // 2) horizontal lines every TILE_DP
        for (float y = b.top + tilePx; y < b.bottom; y += tilePx) {
            canvas.drawLine(b.left, y, b.right, y, linePaint);
        }
        // 3) vertical lines every TILE_DP
        for (float x = b.left + tilePx; x < b.right; x += tilePx) {
            canvas.drawLine(x, b.top, x, b.bottom, linePaint);
        }
    }

    @Override public void setAlpha(int alpha) {
        bgPaint.setAlpha(alpha);
        linePaint.setAlpha((LINE_COLOR >>> 24) * alpha / 255);
        invalidateSelf();
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        bgPaint.setColorFilter(colorFilter);
        linePaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
