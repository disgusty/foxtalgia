package com.disgusty.foxtalgia.ui;

import android.content.Context;

import com.disgusty.foxtalgia.data.History;
import com.disgusty.foxtalgia.engine.Engine;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.StorageController;

/** Clearing browsing data. */
public final class Clearing {
    public static final long HISTORY = 1;
    public static final long COOKIES = 2;
    public static final long CACHE = 4;
    public static final long SITE_SETTINGS = 8;
    public static final long DOWNLOADS = 16;

    private Clearing() {
    }

    public static void clearAll(Context c, Runnable done) {
        clear(c, HISTORY | COOKIES | CACHE | SITE_SETTINGS, done);
    }

    public static void clear(Context c, long what, final Runnable done) {
        if ((what & HISTORY) != 0) History.get().clear(0);
        if ((what & DOWNLOADS) != 0) {
            com.disgusty.foxtalgia.data.Db.get(c).getWritableDatabase().delete("downloads", null, null);
        }
        long flags = 0;
        if ((what & COOKIES) != 0) flags |= StorageController.ClearFlags.COOKIES | StorageController.ClearFlags.DOM_STORAGES | StorageController.ClearFlags.AUTH_SESSIONS;
        if ((what & CACHE) != 0) flags |= StorageController.ClearFlags.ALL_CACHES;
        if ((what & SITE_SETTINGS) != 0) flags |= StorageController.ClearFlags.SITE_SETTINGS;
        if (flags == 0) {
            if (done != null) done.run();
            return;
        }
        Engine.runtime(c).getStorageController().clearData(flags).accept(new GeckoResult.Consumer<Void>() {
            @Override
            public void accept(Void v) {
                if (done != null) done.run();
            }
        });
    }
}
