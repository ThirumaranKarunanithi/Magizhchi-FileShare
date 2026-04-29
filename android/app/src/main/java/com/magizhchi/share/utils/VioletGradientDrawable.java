package com.magizhchi.share.utils;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.util.DisplayMetrics;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Violet sky gradient + dot pattern — used by SharedFilesActivity so the
 * "shared with me" screen has a distinct purple identity, matching the web's
 * violet "Shared in this space" accent.
 *
 *   1. 135° linear gradient #6A1B9A → #7E22CE → #A855F7
 *   2. Faint white dots every 22 dp (same lattice as the chat screen)
 */
public class VioletGradientDrawable extends Drawable {

    private static final int   COLOR_TOP    = 0xFF6A1B9A; // deep violet
    private static final int   COLOR_MID    = 0xFF7E22CE; // purple-700
    private static final int   COLOR_BOTTOM = 0xFFA855F7; // purple-500
    private static final int   DOT_COLOR    = 0x2EFFFFFF; // white at ~18 %
    private static final float TILE_DP       = 22f;
    private static final float DOT_RADIUS_DP = 1.5f;

    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint      = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float tilePx;
    private final float dotRadiusPx;

    public VioletGradientDrawable(@NonNull Resources res) {
        DisplayMetrics dm = res.getDisplayMetrics();
        tilePx      = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, TILE_DP, dm);
        dotRadiusPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DOT_RADIUS_DP, dm);

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
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        android.graphics.Rect b = getBounds();
        canvas.drawRect(b, gradientPaint);
        for (float y = b.top + tilePx / 2f; y < b.bottom; y += tilePx) {
            for (float x = b.left + tilePx / 2f; x < b.right; x += tilePx) {
                canvas.drawCircle(x, y, dotRadiusPx, dotPaint);
            }
        }
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
