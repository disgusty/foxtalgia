package com.disgusty.foxtalgia.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

/** History, bookmarks and downloads. */
public final class Db extends SQLiteOpenHelper {
    private static Db instance;

    private Db(Context context) {
        super(context.getApplicationContext(), "foxtalgia.db", null, 2);
    }

    public static synchronized Db get(Context context) {
        if (instance == null) instance = new Db(context);
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE history (url TEXT PRIMARY KEY, title TEXT, visits INTEGER NOT NULL DEFAULT 0, last INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX history_last ON history(last)");
        db.execSQL("CREATE TABLE bookmarks (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, title TEXT, parent INTEGER NOT NULL DEFAULT 0,"
                + " folder INTEGER NOT NULL DEFAULT 0, position INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX bookmarks_url ON bookmarks(url)");
        db.execSQL("CREATE TABLE downloads (id INTEGER PRIMARY KEY AUTOINCREMENT, url TEXT, file TEXT, name TEXT, mime TEXT,"
                + " size INTEGER NOT NULL DEFAULT -1, received INTEGER NOT NULL DEFAULT 0, status INTEGER NOT NULL DEFAULT 0, created INTEGER NOT NULL)");
        logins(db);
    }

    private static void logins(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE logins (guid TEXT PRIMARY KEY, origin TEXT NOT NULL, form_origin TEXT, realm TEXT, username TEXT,"
                + " password TEXT NOT NULL, created INTEGER NOT NULL DEFAULT 0, changed INTEGER NOT NULL DEFAULT 0, used INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX logins_origin ON logins(origin)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) logins(db);
    }
}
