package com.disgusty.foxtalgia.engine;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.webkit.MimeTypeMap;

import com.disgusty.foxtalgia.data.Db;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.util.IO;
import com.disgusty.foxtalgia.util.Log;
import com.disgusty.foxtalgia.util.Sdk;

import org.mozilla.geckoview.WebResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Saves responses Gecko does not display (onExternalResponse) to the Downloads folder. */
public final class Downloads {
    public static final int RUNNING = 0;
    public static final int DONE = 1;
    public static final int FAILED = 2;
    public static final int CANCELED = 3;

    public static final class Item {
        public long id;
        public String url;
        /** File path (Android < 10) or content:// URI (MediaStore, Android 10+). */
        public String file;
        public String name;
        public String mime;
        public long size;
        public long received;
        public int status;
        public long created;
    }

    public interface Listener {
        void onDownloadChanged(Item item);
    }

    private static final String CHANNEL = "downloads";
    private static Downloads instance;
    private final ExecutorService pool = Executors.newFixedThreadPool(3);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Listener> listeners = new ArrayList<Listener>();
    private final List<Long> cancelled = new ArrayList<Long>();
    private final java.util.Set<Long> active = new java.util.HashSet<Long>();

    private Downloads() {
    }

    public static synchronized Downloads get() {
        if (instance == null) instance = new Downloads();
        return instance;
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public void cancel(long id) {
        synchronized (cancelled) {
            cancelled.add(id);
        }
    }

    public static String fileName(WebResponse r) {
        String name = null;
        Map<String, String> h = r.headers;
        String cd = null;
        for (Map.Entry<String, String> e : h.entrySet()) {
            if ("content-disposition".equalsIgnoreCase(e.getKey())) cd = e.getValue();
        }
        if (cd != null) {
            int star = cd.toLowerCase(Locale.US).indexOf("filename*=");
            if (star >= 0) {
                String v = cd.substring(star + 10).split(";")[0].trim();
                int q = v.indexOf("''");
                if (q >= 0) v = v.substring(q + 2);
                try {
                    name = URLDecoder.decode(v.replace("\"", ""), "UTF-8");
                } catch (Exception ignored) {
                    name = v;
                }
            } else {
                int p = cd.toLowerCase(Locale.US).indexOf("filename=");
                if (p >= 0) name = cd.substring(p + 9).split(";")[0].trim().replace("\"", "");
            }
        }
        if (name == null || name.length() == 0) {
            String path = Uri.parse(r.uri).getLastPathSegment();
            name = path == null || path.length() == 0 ? "download" : path;
        }
        name = name.replace('/', '_').replace('\\', '_').replace(':', '_');
        String mime = mime(r);
        if (name.indexOf('.') < 0 && mime != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ext != null) name = name + "." + ext;
        }
        return name;
    }

    public static String mime(WebResponse r) {
        for (Map.Entry<String, String> e : r.headers.entrySet()) {
            if ("content-type".equalsIgnoreCase(e.getKey())) return e.getValue().split(";")[0].trim();
        }
        return null;
    }

    public static long length(WebResponse r) {
        for (Map.Entry<String, String> e : r.headers.entrySet()) {
            if ("content-length".equalsIgnoreCase(e.getKey())) {
                try {
                    return Long.parseLong(e.getValue().trim());
                } catch (NumberFormatException ignored) {
                    return -1;
                }
            }
        }
        return -1;
    }

    /** Starts saving the response body. {@code name} overrides the file name when not null. */
    public Item start(final Context context, final WebResponse response, boolean isPrivate, String name) {
        final Item it = new Item();
        it.url = response.uri;
        it.name = name != null ? name : fileName(response);
        it.mime = mime(response);
        it.size = length(response);
        it.created = System.currentTimeMillis();
        ContentValues v = new ContentValues();
        v.put("url", it.url);
        v.put("name", it.name);
        v.put("mime", it.mime);
        v.put("size", it.size);
        v.put("status", RUNNING);
        v.put("created", it.created);
        it.id = Db.get(context).getWritableDatabase().insert("downloads", null, v);
        synchronized (active) {
            active.add(it.id);
        }
        pool.execute(new Runnable() {
            @Override
            public void run() {
                save(context, response, it);
            }
        });
        return it;
    }

    private void save(Context context, WebResponse response, Item it) {
        InputStream in = response.body;
        OutputStream out = null;
        Uri mediaUri = null;
        File file = null;
        try {
            if (in == null) throw new java.io.IOException("empty response");
            response.setReadTimeoutMillis(120000);
            if (Sdk.atLeast(29)) {
                mediaUri = Api29.insert(context, it.name, it.mime);
                it.file = mediaUri.toString();
                out = context.getContentResolver().openOutputStream(mediaUri);
            } else {
                file = unique(downloadDir(context), it.name);
                it.name = file.getName();
                it.file = file.getAbsolutePath();
                out = new FileOutputStream(file);
            }
            update(context, it);
            byte[] buf = new byte[64 * 1024];
            long lastNotify = 0;
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                it.received += n;
                synchronized (cancelled) {
                    if (cancelled.remove(Long.valueOf(it.id))) throw new InterruptedException();
                }
                long now = System.currentTimeMillis();
                if (now - lastNotify > 500) {
                    lastNotify = now;
                    progress(context, it);
                }
            }
            out.close();
            out = null;
            if (mediaUri != null) Api29.publish(context, mediaUri);
            else scan(context, file);
            it.status = DONE;
        } catch (InterruptedException e) {
            it.status = CANCELED;
        } catch (Exception e) {
            Log.w("Download failed: " + it.url, e);
            it.status = FAILED;
        } finally {
            IO.close(out);
            IO.close(in);
        }
        if (it.status != DONE) {
            if (file != null && !file.delete()) Log.w("Could not delete partial download");
            if (mediaUri != null) {
                try {
                    context.getContentResolver().delete(mediaUri, null, null);
                } catch (Exception ignored) {
                    // already gone
                }
            }
        }
        update(context, it);
        synchronized (active) {
            active.remove(it.id);
        }
        progress(context, it);
    }

    @SuppressWarnings("deprecation")
    public static File downloadDir(Context context) {
        File d = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (d == null || (!d.isDirectory() && !d.mkdirs()) || !d.canWrite()) {
            d = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
        }
        if (d == null) d = new File(context.getFilesDir(), "downloads");
        if (!d.isDirectory() && !d.mkdirs()) Log.w("Could not create " + d);
        return d;
    }

    private static File unique(File dir, String name) {
        File f = new File(dir, name);
        if (!f.exists()) return f;
        String base = name, ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; ; i++) {
            f = new File(dir, base + " (" + i + ")" + ext);
            if (!f.exists()) return f;
        }
    }

    private static void scan(Context context, File f) {
        Intent i = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(f));
        context.sendBroadcast(i);
    }

    private void update(Context context, final Item it) {
        ContentValues v = new ContentValues();
        v.put("file", it.file);
        v.put("name", it.name);
        v.put("received", it.received);
        v.put("status", it.status);
        if (it.status == DONE) v.put("size", it.received);
        Db.get(context).getWritableDatabase().update("downloads", v, "id = ?", new String[]{String.valueOf(it.id)});
    }

    private void progress(Context context, final Item it) {
        main.post(new Runnable() {
            @Override
            public void run() {
                for (Listener l : new ArrayList<Listener>(listeners)) l.onDownloadChanged(it);
            }
        });
        notifyProgress(context, it);
    }

    @SuppressWarnings("deprecation")
    private static void notifyProgress(Context context, Item it) {
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder b;
        if (Sdk.atLeast(26)) b = Api26.builder(context, nm);
        else b = new Notification.Builder(context);
        I18n t = I18n.get();
        b.setContentTitle(it.name);
        b.setSmallIcon(it.status == RUNNING ? android.R.drawable.stat_sys_download : android.R.drawable.stat_sys_download_done);
        Intent open = new Intent(context, com.disgusty.foxtalgia.ui.DownloadsActivity.class);
        b.setContentIntent(PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT | (Sdk.atLeast(23) ? PendingIntent.FLAG_IMMUTABLE : 0)));
        if (it.status == RUNNING) {
            b.setOngoing(true);
            int max = it.size > 0 ? 1000 : 0;
            b.setProgress(max, it.size > 0 ? (int) (it.received * 1000 / it.size) : 0, it.size <= 0);
            b.setContentText(com.disgusty.foxtalgia.util.Text.fileSize(it.received) + (it.size > 0 ? " / " + com.disgusty.foxtalgia.util.Text.fileSize(it.size) : ""));
        } else {
            b.setAutoCancel(true);
            b.setContentText(t.text(it.status == DONE ? "downloads.done" : it.status == CANCELED ? "downloads.canceled" : "downloads.failed"));
        }
        nm.notify("dl", (int) it.id, b.getNotification());
    }

    public static List<Item> list(Context context) {
        List<Item> out = new ArrayList<Item>();
        Cursor c = Db.get(context).getReadableDatabase().rawQuery(
                "SELECT id, url, file, name, mime, size, received, status, created FROM downloads ORDER BY created DESC", null);
        while (c.moveToNext()) {
            Item i = new Item();
            i.id = c.getLong(0);
            i.url = c.getString(1);
            i.file = c.getString(2);
            i.name = c.getString(3);
            i.mime = c.getString(4);
            i.size = c.getLong(5);
            i.received = c.getLong(6);
            i.status = c.getInt(7);
            i.created = c.getLong(8);
            // A process death leaves RUNNING rows behind.
            if (i.status == RUNNING && !get().isActive(i.id)) i.status = FAILED;
            out.add(i);
        }
        c.close();
        return out;
    }

    private boolean isActive(long id) {
        synchronized (active) {
            return active.contains(id);
        }
    }

    public static void remove(Context context, long id) {
        Db.get(context).getWritableDatabase().delete("downloads", "id = ?", new String[]{String.valueOf(id)});
    }

    /** Opens the downloaded file with another app. */
    public static Intent viewIntent(Context context, Item it) {
        Uri uri;
        if (it.file != null && it.file.startsWith("content:")) uri = Uri.parse(it.file);
        else uri = com.disgusty.foxtalgia.files.FileProvider.uriFor(context, new File(it.file));
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, it.mime != null ? it.mime : "*/*");
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    @TargetApi(26)
    private static final class Api26 {
        static Notification.Builder builder(Context context, NotificationManager nm) {
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(new NotificationChannel(CHANNEL, I18n.get().text("downloads.title"), NotificationManager.IMPORTANCE_LOW));
            }
            return new Notification.Builder(context, CHANNEL);
        }
    }

    @TargetApi(29)
    private static final class Api29 {
        static Uri insert(Context context, String name, String mime) throws java.io.IOException {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
            if (mime != null) v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri u = context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new java.io.IOException("MediaStore insert failed");
            return u;
        }

        static void publish(Context context, Uri u) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(u, v, null, null);
        }
    }
}
