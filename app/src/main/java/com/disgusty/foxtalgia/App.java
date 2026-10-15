package com.disgusty.foxtalgia;

import android.app.Activity;
import android.app.Application;
import android.content.Context;

import androidx.multidex.MultiDex;

import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.kit.Recolor;

public final class App extends Application {
    private static App instance;
    public Settings settings;
    public final long startTime = System.currentTimeMillis();
    /** Bumped when style, theme, accent or language change; open screens rebuild on resume. */
    public static int appearanceVersion;
    private Activity foreground;

    public static App get() {
        return instance;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        settings = new Settings(this);
        I18n.load(this, settings.locale());
    }

    /** Call after any appearance setting changed. */
    public void appearanceChanged() {
        appearanceVersion++;
        Recolor.clearCache();
        I18n.load(this, settings.locale());
    }

    public void setForeground(Activity a) {
        foreground = a;
    }

    public void clearForeground(Activity a) {
        if (foreground == a) foreground = null;
    }

    public Activity foreground() {
        return foreground;
    }
}
