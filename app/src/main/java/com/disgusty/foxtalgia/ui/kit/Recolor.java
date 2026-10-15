package com.disgusty.foxtalgia.ui.kit;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.NinePatch;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.NinePatchDrawable;
import android.util.SparseArray;

/**
 * Recolors the accent of framework bitmaps, the way the Android Holo Colors Generator made custom-colored Holo
 * themes: pixels whose hue is close to the original accent (holo blue, the 2.x orange) get the new hue, with
 * saturation and brightness scaled by the ratio between the two colors. Greys, whites, blacks and other hues
 * (e.g. the green check mark of Android 2.x) are left alone. Results are cached per resource and color.
 */
public final class Recolor {
    private static final float HUE_WINDOW = 28f;
    private static final float MIN_SATURATION = 0.18f;
    private static final SparseArray<Object[]> CACHE = new SparseArray<Object[]>();
    private static int cachedFrom, cachedTo;

    private Recolor() {
    }

    /** The drawable for {@code id}, recolored when the scheme asks for it. Only PNG / 9-patch resources are handled. */
    @SuppressWarnings("deprecation")
    public static Drawable drawable(Resources res, int id, Scheme c) {
        if (!c.recolor) return res.getDrawable(id);
        synchronized (CACHE) {
            if (cachedFrom != c.recolorFrom || cachedTo != c.recolorTo) {
                CACHE.clear();
                cachedFrom = c.recolorFrom;
                cachedTo = c.recolorTo;
            }
            Object[] hit = CACHE.get(id);
            if (hit != null) return make(res, (Bitmap) hit[0], (byte[]) hit[1], (Rect) hit[2]);
        }
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = true;
        Bitmap src = BitmapFactory.decodeResource(res, id, o);
        if (src == null) return res.getDrawable(id);
        byte[] chunk = src.getNinePatchChunk();
        Rect padding = new Rect();
        if (chunk != null) {
            Drawable original = res.getDrawable(id);
            original.getPadding(padding);
        }
        Bitmap out = recolor(src, c.recolorFrom, c.recolorTo);
        out.setDensity(src.getDensity());
        synchronized (CACHE) {
            CACHE.put(id, new Object[]{out, chunk, padding});
        }
        return make(res, out, chunk, padding);
    }

    @SuppressWarnings("deprecation")
    private static Drawable make(Resources res, Bitmap b, byte[] chunk, Rect padding) {
        if (chunk != null && NinePatch.isNinePatchChunk(chunk)) {
            return new NinePatchDrawable(res, b, chunk, padding, null);
        }
        BitmapDrawable d = new BitmapDrawable(res, b);
        return d;
    }

    public static void clearCache() {
        synchronized (CACHE) {
            CACHE.clear();
        }
    }

    /** One color through the same mapping (for gradients the framework defined in XML). */
    public static int color(int color, Scheme c) {
        if (!c.recolor) return color;
        Bitmap b = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        b.setPixel(0, 0, color);
        return recolor(b, c.recolorFrom, c.recolorTo).getPixel(0, 0);
    }

    public static Bitmap recolor(Bitmap src, int from, int to) {
        float[] f = new float[3];
        float[] t = new float[3];
        Color.colorToHSV(from, f);
        Color.colorToHSV(to, t);
        float satRatio = f[1] <= 0 ? 1 : t[1] / f[1];
        float valRatio = f[2] <= 0 ? 1 : t[2] / f[2];
        int w = src.getWidth(), h = src.getHeight();
        int[] px = new int[w * h];
        src.getPixels(px, 0, w, 0, 0, w, h);
        float[] hsv = new float[3];
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int a = p >>> 24;
            if (a == 0) continue;
            Color.colorToHSV(p, hsv);
            if (hsv[1] < MIN_SATURATION) continue;
            float d = Math.abs(hsv[0] - f[0]);
            if (d > 180) d = 360 - d;
            if (d > HUE_WINDOW) continue;
            // Keep the relative hue offset so gradients inside the artwork survive.
            float delta = hsv[0] - f[0];
            if (delta > 180) delta -= 360;
            if (delta < -180) delta += 360;
            hsv[0] = (t[0] + delta + 360) % 360;
            hsv[1] = Math.min(1, hsv[1] * satRatio);
            hsv[2] = Math.min(1, hsv[2] * valRatio);
            px[i] = Color.HSVToColor(a, hsv);
        }
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        out.setPixels(px, 0, w, 0, 0, w, h);
        return out;
    }
}
