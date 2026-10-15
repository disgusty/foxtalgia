package com.disgusty.foxtalgia.data;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import com.disgusty.foxtalgia.App;

import java.util.ArrayList;
import java.util.List;

/** Bookmarks with folders (parent = folder id, 0 = root). Small enough to use on the UI thread. */
public final class Bookmarks {
    public static final class Item {
        public long id;
        public String url;
        public String title;
        public long parent;
        public boolean folder;
        public int position;
    }

    private Bookmarks() {
    }

    private static SQLiteDatabase db() {
        return Db.get(App.get()).getWritableDatabase();
    }

    public static List<Item> children(long parent) {
        List<Item> out = new ArrayList<Item>();
        Cursor c = db().rawQuery("SELECT id, url, title, parent, folder, position FROM bookmarks WHERE parent = ? ORDER BY folder DESC, position, id",
                new String[]{String.valueOf(parent)});
        while (c.moveToNext()) out.add(item(c));
        c.close();
        return out;
    }

    public static List<Item> search(String q, int limit) {
        List<Item> out = new ArrayList<Item>();
        String like = "%" + q.replace("%", "\\%").replace("_", "\\_") + "%";
        Cursor c = db().rawQuery("SELECT id, url, title, parent, folder, position FROM bookmarks WHERE folder = 0 AND (url LIKE ? ESCAPE '\\' OR title LIKE ? ESCAPE '\\')"
                + " ORDER BY id DESC LIMIT " + limit, new String[]{like, like});
        while (c.moveToNext()) out.add(item(c));
        c.close();
        return out;
    }

    public static List<Item> recent(int limit) {
        List<Item> out = new ArrayList<Item>();
        Cursor c = db().rawQuery("SELECT id, url, title, parent, folder, position FROM bookmarks WHERE folder = 0 ORDER BY created DESC LIMIT " + limit, null);
        while (c.moveToNext()) out.add(item(c));
        c.close();
        return out;
    }

    public static Item find(String url) {
        if (url == null) return null;
        Cursor c = db().rawQuery("SELECT id, url, title, parent, folder, position FROM bookmarks WHERE folder = 0 AND url = ? LIMIT 1", new String[]{url});
        Item i = c.moveToFirst() ? item(c) : null;
        c.close();
        return i;
    }

    public static Item byId(long id) {
        Cursor c = db().rawQuery("SELECT id, url, title, parent, folder, position FROM bookmarks WHERE id = ?", new String[]{String.valueOf(id)});
        Item i = c.moveToFirst() ? item(c) : null;
        c.close();
        return i;
    }

    public static long add(String url, String title, long parent) {
        ContentValues v = new ContentValues();
        v.put("url", url);
        v.put("title", title);
        v.put("parent", parent);
        v.put("folder", 0);
        v.put("position", Integer.MAX_VALUE / 2);
        v.put("created", System.currentTimeMillis());
        return db().insert("bookmarks", null, v);
    }

    public static long addFolder(String title, long parent) {
        ContentValues v = new ContentValues();
        v.put("title", title);
        v.put("parent", parent);
        v.put("folder", 1);
        v.put("created", System.currentTimeMillis());
        return db().insert("bookmarks", null, v);
    }

    public static void update(long id, String url, String title, long parent) {
        ContentValues v = new ContentValues();
        if (url != null) v.put("url", url);
        v.put("title", title);
        v.put("parent", parent);
        db().update("bookmarks", v, "id = ?", new String[]{String.valueOf(id)});
    }

    /** Deletes a bookmark or a folder with everything inside. */
    public static void delete(long id) {
        for (Item child : children(id)) delete(child.id);
        db().delete("bookmarks", "id = ?", new String[]{String.valueOf(id)});
    }

    private static Item item(Cursor c) {
        Item i = new Item();
        i.id = c.getLong(0);
        i.url = c.getString(1);
        i.title = c.getString(2);
        i.parent = c.getLong(3);
        i.folder = c.getInt(4) != 0;
        i.position = c.getInt(5);
        return i;
    }
}
