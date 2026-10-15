package com.disgusty.foxtalgia.ui.browser;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/** The tab switcher button: a rounded square with the number of open tabs (":D" above 99, like Chrome). */
public final class TabCountDrawable extends Drawable {
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private final float radius;
    private int count;

    public TabCountDrawable(int sizePx, float radiusPx, float strokePx, int color, Typeface tf) {
        size = sizePx;
        radius = radiusPx;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokePx);
        stroke.setColor(color);
        text.setColor(color);
        text.setTypeface(Typeface.create(tf, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void setCount(int n) {
        count = n;
        invalidateSelf();
    }

    @Override
    public int getIntrinsicWidth() {
        return size;
    }

    @Override
    public int getIntrinsicHeight() {
        return size;
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        float s = Math.min(b.width(), b.height()) * 0.72f;
        float half = stroke.getStrokeWidth() / 2;
        RectF r = new RectF(b.exactCenterX() - s / 2 + half, b.exactCenterY() - s / 2 + half, b.exactCenterX() + s / 2 - half, b.exactCenterY() + s / 2 - half);
        canvas.drawRoundRect(r, radius, radius, stroke);
        String label = count > 99 ? ":D" : String.valueOf(count);
        text.setTextSize(s * (label.length() > 1 ? 0.48f : 0.56f));
        Paint.FontMetrics fm = text.getFontMetrics();
        canvas.drawText(label, r.centerX(), r.centerY() - (fm.ascent + fm.descent) / 2, text);
    }

    @Override
    public void setAlpha(int alpha) {
        stroke.setAlpha(alpha);
        text.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        stroke.setColorFilter(cf);
        text.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
