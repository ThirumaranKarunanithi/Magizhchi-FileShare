package com.magizhchi.share.utils;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Chat-screen / auth-card background — mirrors the web ChatWindow:
 *
 *   1. 135° linear gradient #0369A1 → #0284C7 (40 %) → #0EA5E9
 *   2. Faint white dot texture every 22 dp (radial-gradient on web)
 *
 * Optionally clips to a rounded rectangle so the auth card can have curved
 * corners. Drawn entirely in code so it scales without bitmap assets.
 */
public class DottedGradientDrawable extends Drawable {

    private static final int   COLOR_TOP    = 0xFF0369A1;
    private static final int   COLOR_MID    = 0xFF0284C7;
    private static final int   COLOR_BOTTOM = 0xFF0EA5E9;
    private static final int   DOT_COLOR    = 0x2EFFFFFF; // white at ~18 %
    private static final float TILE_DP      = 22f;
    private static final float DOT_RADIUS_DP = 1.5f;

    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float tilePx;
    private final float dotRadiusPx;
    private final float cornerRadiusPx;
    private final Path  clipPath = new Path();

    /** Square-corner variant — used by full-bleed surfaces (chat screen). */
    public DottedGradientDrawable(@NonNull Resources res) {
        this(res, 0f);
    }

    /**
     * @param cornerRadiusDp curve to apply on all four corners (0 = sharp).
     */
    public DottedGradientDrawable(@NonNull Resources res, float cornerRadiusDp) {
        DisplayMetrics dm = res.getDisplayMetrics();
        tilePx          = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TILE_DP, dm);
        dotRadiusPx     = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DOT_RADIUS_DP, dm);
        cornerRadiusPx  = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, cornerRadiusDp, dm);

        dotPaint.setColor(DOT_COLOR);
        dotPaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onBoundsChange(@NonNull android.graphics.Rect bounds) {
        super.onBoundsChange(bounds);
        gradientPaint.setShader(new LinearGradient(
                bounds.left, bounds.top,
                bounds.right, bounds.bottom,
                new int[]{ COLOR_TOP, COLOR_MID, COLOR_BOTTOM },
                new float[]{ 0f, 0.4f, 1f },
                Shader.TileMode.CLAMP));
        // Recompute the rounded clip path for the new bounds.
        clipPath.reset();
        if (cornerRadiusPx > 0f) {
            clipPath.addRoundRect(new RectF(bounds), cornerRadiusPx, cornerRadiusPx, Path.Direction.CW);
        }
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        android.graphics.Rect b = getBounds();

        // Clip to the rounded path when corners are set so dots + gradient
        // stay inside the curve.
        int saveCount = -1;
        if (cornerRadiusPx > 0f) {
            saveCount = canvas.save();
            canvas.clipPath(clipPath);
        }

        canvas.drawRect(b, gradientPaint);
        // Dot grid — one dot at every 22dp×22dp lattice point.
        for (float y = b.top + tilePx / 2f; y < b.bottom; y += tilePx) {
            for (float x = b.left + tilePx / 2f; x < b.right; x += tilePx) {
                canvas.drawCircle(x, y, dotRadiusPx, dotPaint);
            }
        }

        if (saveCount >= 0) canvas.restoreToCount(saveCount);
    }

    @Override public void setAlpha(int alpha) {
        gradientPaint.setAlpha(alpha);
        dotPaint.setAlpha((DOT_COLOR >>> 24) * alpha / 255);
        invalidateSelf();
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        gradientPaint.setColorFilter(colorFilter);
        dotPaint.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
