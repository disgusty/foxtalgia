package com.disgusty.foxtalgia.ui.browser;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.engine.Engine;
import com.disgusty.foxtalgia.util.IO;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebRequest;
import org.mozilla.geckoview.WebResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Site icons for the start page, tabs and lists. Fetched through Gecko's network stack (GeckoWebExecutor), so
 * modern TLS works on every Android version; cached in memory and in files/icons.
 */
public final class Favicons {
    public interface Callback {
        void onIcon(Bitmap icon);
    }

    private static final Map<String, Bitmap> MEMORY = new LinkedHashMap<String, Bitmap>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Bitmap> e) {
            return size() > 120;
        }
    };
    private static final Map<String, List<Callback>> PENDING = new HashMap<String, List<Callback>>();
    private static final ExecutorService IO_POOL = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static GeckoWebExecutor executor;

    private Favicons() {
    }

    public static String host(String url) {
        try {
            String h = Uri.parse(url).getHost();
            return h == null ? null : h.toLowerCase(java.util.Locale.US);
        } catch (Exception e) {
            return null;
        }
    }

    private static File dir() {
        File d = new File(App.get().getCacheDir(), "icons");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    /** Calls back on the UI thread with the icon, or never if the site has none. */
    public static void load(final String url, final Callback cb) {
        final String host = host(url);
        if (host == null || !(url.startsWith("http:") || url.startsWith("https:"))) return;
        synchronized (MEMORY) {
            Bitmap b = MEMORY.get(host);
            if (b != null) {
                cb.onIcon(b);
                return;
            }
        }
        synchronized (PENDING) {
            List<Callback> list = PENDING.get(host);
            if (list != null) {
                list.add(cb);
                return;
            }
            list = new ArrayList<Callback>();
            list.add(cb);
            PENDING.put(host, list);
        }
        IO_POOL.execute(new Runnable() {
            @Override
            public void run() {
                final File f = new File(dir(), host + ".png");
                if (f.exists()) {
                    Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
                    if (b != null) {
                        deliver(host, b);
                        return;
                    }
                }
                MAIN.post(new Runnable() {
                    @Override
                    public void run() {
                        fetch(host, url.startsWith("http:") ? "http" : "https", f);
                    }
                });
            }
        });
    }

    private static void fetch(final String host, String scheme, final File out) {
        if (executor == null) executor = new GeckoWebExecutor(Engine.runtime(App.get()));
        WebRequest req = new WebRequest.Builder(scheme + "://" + host + "/favicon.ico").build();
        executor.fetch(req).accept(new GeckoResult.Consumer<WebResponse>() {
            @Override
            public void accept(final WebResponse r) {
                IO_POOL.execute(new Runnable() {
                    @Override
                    public void run() {
                        Bitmap b = null;
                        try {
                            if (r.statusCode == 200 && r.body != null) {
                                byte[] data = IO.readLimited(r.body, 512 * 1024);
                                b = BitmapFactory.decodeByteArray(data, 0, data.length);
                            }
                        } catch (Exception ignored) {
                            // no icon
                        } finally {
                            IO.close(r.body);
                        }
                        if (b != null) {
                            FileOutputStream o = null;
                            try {
                                o = new FileOutputStream(out);
                                b.compress(Bitmap.CompressFormat.PNG, 100, o);
                            } catch (Exception ignored) {
                                // cache is optional
                            } finally {
                                IO.close(o);
                            }
                        }
                        deliver(host, b);
                    }
                });
            }
        }, new GeckoResult.Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                deliver(host, null);
            }
        });
    }

    private static void deliver(final String host, final Bitmap b) {
        if (b != null) {
            synchronized (MEMORY) {
                MEMORY.put(host, b);
            }
        }
        final List<Callback> list;
        synchronized (PENDING) {
            list = PENDING.remove(host);
        }
        if (b == null || list == null) return;
        MAIN.post(new Runnable() {
            @Override
            public void run() {
                for (Callback cb : list) cb.onIcon(b);
            }
        });
    }

    /** A letter tile in a color derived from the host, used until (or instead of) the real icon. */
    public static Bitmap letter(Context ctx, String url, int sizePx, float radius, Typeface tf) {
        String host = host(url);
        String label = host == null || host.length() == 0 ? "?" : host.replaceFirst("^www\\.", "").substring(0, 1).toUpperCase();
        int hash = host == null ? 0 : host.hashCode();
        float[] hsv = {Math.abs(hash % 360), 0.45f, 0.75f};
        Bitmap b = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(android.graphics.Color.HSVToColor(hsv));
        c.drawRoundRect(new RectF(0, 0, sizePx, sizePx), radius, radius, p);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(tf, Typeface.BOLD));
        p.setTextSize(sizePx * 0.5f);
        Paint.FontMetrics fm = p.getFontMetrics();
        c.drawText(label, sizePx / 2f, sizePx / 2f - (fm.ascent + fm.descent) / 2, p);
        return b;
    }
}
