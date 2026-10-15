package com.disgusty.foxtalgia.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.R;
import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.ui.kit.Scheme;
import com.disgusty.foxtalgia.ui.kit.Screen;
import com.disgusty.foxtalgia.util.Sdk;

/** Common screen plumbing: style kit, menu key, rebuild on appearance changes. */
public abstract class BaseActivity extends Activity {
    protected Settings settings;
    protected Kit k;
    protected Screen screen;
    protected I18n t;
    private int appearance;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        settings = App.get().settings;
        t = I18n.get();
        appearance = App.appearanceVersion;
        boolean dark = Scheme.resolveDark(this, settings, settings.effectiveStyle());
        setTheme(dark ? R.style.FoxDark : R.style.FoxLight);
        super.onCreate(savedInstanceState);
        k = Kit.create(this);
        k.applyWindow();
        if (Sdk.atLeast(17)) Api17.direction(this, t.isRtl());
        create();
    }

    /** Builds the content. The default creates {@link #screen} with an app bar and calls {@link #build()}. */
    protected void create() {
        screen = k.screen();
        build();
        setContentView(screen.root);
    }

    protected void build() {
    }

    protected void refresh() {
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        App.get().setForeground(this);
        if (appearance != App.appearanceVersion) {
            rebuild();
            return;
        }
        refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        App.get().clearForeground(this);
    }

    public boolean isResumedCompat() {
        return resumed;
    }

    public Kit kit() {
        return k;
    }

    /** Restarts the activity without animation. */
    protected void rebuild() {
        Intent i = getIntent();
        finish();
        overridePendingTransition(0, 0);
        startActivity(i);
        overridePendingTransition(0, 0);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU && screen != null) {
            if (k.onMenuKey(screen)) return true;
            if (screen.appBar != null && !screen.appBar.actions().isEmpty()) {
                screen.appBar.openMenu();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    protected String s(String key) {
        return t.text(key);
    }

    protected String s(String key, Object... params) {
        return t.text(key, params);
    }

    protected void open(Class<? extends Activity> cls) {
        startActivity(new Intent(this, cls));
    }

    protected Runnable finisher() {
        return new Runnable() {
            @Override
            public void run() {
                finish();
            }
        };
    }

    @TargetApi(17)
    private static final class Api17 {
        static void direction(Activity a, boolean rtl) {
            a.getWindow().getDecorView().setLayoutDirection(rtl ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        }
    }
}
