package com.disgusty.foxtalgia.ui.kit;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import com.disgusty.foxtalgia.ui.icon.SvgPath;

/**
 * Foxtalgia logo: a disc with a ring and a fox head, drawn in the style's colors.
 * Classic passes a gradient shader to get the glossy Android 2.x icon look.
 */
public class LogoDrawable extends Drawable {
    /** Fox head on a 24x24 grid; eyes and nose are holes (even-odd). */
    public static final String FOX = "M3 3L8.5 8Q12 7 15.5 8L21 3L20 10.5L22.5 13L16.5 15L12 21L7.5 15L1.5 13L4 10.5ZM7.2 11.8L9.6 11L9.8 12.4L8 12.8ZM16.8 11.8L14.4 11L14.2 12.4L16 12.8ZM11 18.9H13L12 20.3ZM5.2 5.6L8 8.6L6.2 9.6ZM18.8 5.6L16 8.6L17.8 9.6Z";

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int size;
    private final int ring;
    private final int fill;
    private final int glyph;
    private Shader fillShader;
    private boolean gloss;
    private final Path fox = new Path();
    private float angle;

    public LogoDrawable(int size, int ring, int fill, int glyph) {
        this.size = size;
        this.ring = ring;
        this.fill = fill;
        this.glyph = glyph;
    }

    /** Tilts only the fox, so ring, gradient and gloss keep their lighting. */
    public void setAngle(float degrees) {
        angle = degrees;
        invalidateSelf();
    }

    public LogoDrawable shader(Shader s) {
        fillShader = s;
        return this;
    }

    public LogoDrawable gloss() {
        gloss = true;
        return this;
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
        float s = Math.min(b.width(), b.height());
        float cx = b.exactCenterX(), cy = b.exactCenterY();
        float r = s / 2f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ring);
        canvas.drawCircle(cx, cy, r, paint);
        paint.setColor(fill);
        if (fillShader != null) {
            canvas.save();
            canvas.translate(cx - r, cy - r);
            paint.setShader(fillShader);
            canvas.drawCircle(r, r, r * 0.86f, paint);
            paint.setShader(null);
            canvas.restore();
        } else {
            canvas.drawCircle(cx, cy, r * 0.86f, paint);
        }
        Path src = SvgPath.parse(FOX);
        Matrix m = new Matrix();
        float g = s * 0.58f / 24f;
        m.postTranslate(-12f, -12f);
        m.postRotate(angle);
        m.postScale(g, g);
        m.postTranslate(cx, cy + s * 0.02f);
        src.transform(m, fox);
        fox.setFillType(Path.FillType.EVEN_ODD);
        paint.setColor(glyph);
        canvas.drawPath(fox, paint);
        if (gloss) {
            // Upper highlight kept inside the disc (no clipPath: unsupported with hardware acceleration before 4.3).
            paint.setColor(0x40FFFFFF);
            canvas.drawOval(new android.graphics.RectF(cx - r * 0.66f, cy - r * 0.82f, cx + r * 0.66f, cy + r * 0.02f), paint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        paint.setColorFilter(cf);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
