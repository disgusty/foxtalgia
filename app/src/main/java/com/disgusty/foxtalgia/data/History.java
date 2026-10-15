package com.disgusty.foxtalgia.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.disgusty.foxtalgia.App;

import org.mozilla.geckoview.GeckoResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Browsing history. Writes go to a background thread. */
public final class History {
    public static final class Entry {
        public String url;
        public String title;
        public int visits;
        public long last;
    }

    /** Below SQLite's default limit of 999 bound parameters. */
    private static final int VISITED_BATCH = 500;

    private static History instance;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private History() {
    }

    public static synchronized History get() {
        if (instance == null) instance = new History();
        return instance;
    }

    private SQLiteDatabase db() {
        return Db.get(App.get()).getWritableDatabase();
    }

    private static boolean recordable(String url) {
        return url != null && (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("file:"));
    }

    public void visit(final String url, final String title) {
        if (!recordable(url)) return;
        final long now = System.currentTimeMillis();
        io.execute(new Runnable() {
            @Override
            public void run() {
                SQLiteDatabase d = db();
                d.execSQL("UPDATE history SET visits = visits + 1, last = ? WHERE url = ?", new Object[]{now, url});
                Cursor c = d.rawQuery("SELECT changes()", null);
                long changed = c.moveToFirst() ? c.getLong(0) : 0;
                c.close();
                if (changed == 0) {
                    ContentValues v = new ContentValues();
                    v.put("url", url);
                    v.put("title", title);
                    v.put("visits", 1);
                    v.put("last", now);
                    d.insertWithOnConflict("history", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
        });
    }

    public void setTitle(final String url, final String title) {
        if (!recordable(url) || title == null || title.length() == 0) return;
        io.execute(new Runnable() {
            @Override
            public void run() {
                ContentValues v = new ContentValues();
                v.put("title", title);
                db().update("history", v, "url = ?", new String[]{url});
            }
        });
    }

    /** Answers Gecko's visited-link query on the io thread; pages can ask about hundreds of links at once. */
    public GeckoResult<boolean[]> visitedAsync(final String[] urls) {
        final GeckoResult<boolean[]> result = new GeckoResult<boolean[]>();
        io.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    result.complete(visited(urls));
                } catch (Throwable e) {
                    result.complete(new boolean[urls.length]);
                }
            }
        });
        return result;
    }

    private boolean[] visited(String[] urls) {
        boolean[] out = new boolean[urls.length];
        SQLiteDatabase d = db();
        HashSet<String> found = new HashSet<String>();
        for (int start = 0; start < urls.length; start += VISITED_BATCH) {
            int n = Math.min(VISITED_BATCH, urls.length - start);
            StringBuilder sql = new StringBuilder("SELECT url FROM history WHERE url IN (");
            String[] args = new String[n];
            for (int i = 0; i < n; i++) {
                sql.append(i == 0 ? "?" : ",?");
                args[i] = urls[start + i] == null ? "" : urls[start + i];
            }
            Cursor c = d.rawQuery(sql.append(')').toString(), args);
            while (c.moveToNext()) found.add(c.getString(0));
            c.close();
        }
        for (int i = 0; i < urls.length; i++) out[i] = found.contains(urls[i]);
        return out;
    }

    /** Most recent first; {@code filter} matches URL or title. */
    public List<Entry> recent(String filter, int limit) {
        List<Entry> out = new ArrayList<Entry>();
        Cursor c;
        if (filter == null || filter.length() == 0) {
            c = db().rawQuery("SELECT url, title, visits, last FROM history ORDER BY last DESC LIMIT " + limit, null);
        } else {
            String like = "%" + filter.replace("%", "\\%").replace("_", "\\_") + "%";
            c = db().rawQuery("SELECT url, title, visits, last FROM history WHERE url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\'"
                    + " ORDER BY visits DESC, last DESC LIMIT " + limit, new String[]{like, like});
        }
        while (c.moveToNext()) out.add(entry(c));
        c.close();
        return out;
    }

    /** Most visited sites for the start page, one entry per host. */
    public List<Entry> topSites(int limit) {
        List<Entry> out = new ArrayList<Entry>();
        List<String> hosts = new ArrayList<String>();
        Cursor c = db().rawQuery("SELECT url, title, visits, last FROM history ORDER BY visits DESC, last DESC LIMIT 200", null);
        while (c.moveToNext() && out.size() < limit) {
            Entry e = entry(c);
            String host = android.net.Uri.parse(e.url).getHost();
            if (host == null || hosts.contains(host)) continue;
            hosts.add(host);
            out.add(e);
        }
        c.close();
        return out;
    }

    private static Entry entry(Cursor c) {
        Entry e = new Entry();
        e.url = c.getString(0);
        e.title = c.getString(1);
        e.visits = c.getInt(2);
        e.last = c.getLong(3);
        return e;
    }

    public void delete(final String url) {
        io.execute(new Runnable() {
            @Override
            public void run() {
                db().delete("history", "url = ?", new String[]{url});
            }
        });
    }

    /** Deletes entries visited after {@code since} (0 = everything). */
    public void clear(final long since) {
        io.execute(new Runnable() {
            @Override
            public void run() {
                db().delete("history", "last >= ?", new String[]{String.valueOf(since)});
            }
        });
    }
}
