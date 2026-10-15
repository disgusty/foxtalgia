package com.disgusty.foxtalgia.ui;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Base64;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.data.Bookmarks;
import com.disgusty.foxtalgia.data.History;
import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.engine.Downloads;
import com.disgusty.foxtalgia.engine.Engine;
import com.disgusty.foxtalgia.engine.Extensions;
import com.disgusty.foxtalgia.engine.Tab;
import com.disgusty.foxtalgia.engine.Tabs;
import com.disgusty.foxtalgia.engine.Urls;
import com.disgusty.foxtalgia.ui.browser.AddressBar;
import com.disgusty.foxtalgia.ui.browser.ExtensionUi;
import com.disgusty.foxtalgia.ui.browser.FindBar;
import com.disgusty.foxtalgia.ui.browser.HomeView;
import com.disgusty.foxtalgia.ui.browser.Prompts;
import com.disgusty.foxtalgia.ui.browser.Suggestions;
import com.disgusty.foxtalgia.ui.browser.TabsTray;
import com.disgusty.foxtalgia.ui.kit.Action;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.util.Log;
import com.disgusty.foxtalgia.util.Sdk;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;

import java.util.ArrayList;
import java.util.List;

/** The browser: address bar, page, start page, tab switcher, menu. */
public final class BrowserActivity extends BaseActivity implements Tabs.Host, AddressBar.Listener {
    public static final String EXTRA_PRIVATE = "private";

    private FrameLayout root;
    private LinearLayout main;
    private FrameLayout content;
    private GeckoView geckoView;
    private AddressBar bar;
    private HomeView home;
    private Suggestions suggestions;
    private TabsTray tray;
    private FindBar findBar;
    private Prompts prompts;
    private ExtensionUi extensionUi;
    private Tab shown;
    private boolean fullscreen;

    /** Debug builds: --es style holo --es brightness dark --es accentMode custom --ei accentColor 0xFFE91E63 --es toolbar bottom. */
    private boolean debugExtras(Intent i) {
        if (!com.disgusty.foxtalgia.BuildConfig.DEBUG || i == null) return false;
        Settings settings = App.get().settings;
        boolean changed = false;
        if (i.hasExtra("style")) { settings.setStyle(i.getStringExtra("style")); changed = true; }
        if (i.hasExtra("brightness")) { settings.setBrightness(i.getStringExtra("brightness")); changed = true; }
        if (i.hasExtra("accentMode")) { settings.setAccentMode(i.getStringExtra("accentMode")); changed = true; }
        if (i.hasExtra("accentPreset")) { settings.setAccentPreset(i.getIntExtra("accentPreset", 0)); changed = true; }
        if (i.hasExtra("accentColor")) { settings.setAccentColor(i.getIntExtra("accentColor", 0)); changed = true; }
        if (i.hasExtra("toolbar")) { settings.setToolbarPosition(i.getStringExtra("toolbar")); changed = true; }
        if (i.hasExtra("locale")) { settings.setLocale(i.getStringExtra("locale")); changed = true; }
        String screenName = i.getStringExtra("open");
        if (screenName != null) {
            i.removeExtra("open");
            Class<?> c = "appearance".equals(screenName) ? AppearanceActivity.class : "settings".equals(screenName) ? SettingsActivity.class
                    : "addons".equals(screenName) ? AddonsActivity.class : "history".equals(screenName) ? HistoryActivity.class
                    : "bookmarks".equals(screenName) ? BookmarksActivity.class : DownloadsActivity.class;
            startActivity(new Intent(App.get(), c).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        }
        if (changed) {
            i.removeExtra("style");
            i.removeExtra("brightness");
            i.removeExtra("accentMode");
            i.removeExtra("accentPreset");
            i.removeExtra("accentColor");
            i.removeExtra("toolbar");
            i.removeExtra("locale");
            App.get().appearanceChanged();
        }
        return changed;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        debugExtras(getIntent());
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void create() {
        prompts = new Prompts(this, k);
        extensionUi = new ExtensionUi(this, k);
        root = new FrameLayout(this);
        root.setBackgroundColor(k.c.background);
        main = k.vbox();
        bar = new AddressBar(k, this);
        content = new FrameLayout(this);
        geckoView = new GeckoView(this);
        content.addView(geckoView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        home = new HomeView(k, new HomeView.Listener() {
            @Override
            public void onOpen(String url, boolean newTab) {
                open(url, newTab);
            }

            @Override
            public void onSearchClicked() {
                bar.startEditing(null);
            }

            @Override
            public void onRemoveTopSite(final String url) {
                k.dialog().title(Urls.displayHost(url)).items(new CharSequence[]{s("browser.openInNewTab"), s("home.removeTopSite")},
                        new com.disgusty.foxtalgia.ui.kit.DialogBuilder.ItemListener() {
                            @Override
                            public void onItem(int index) {
                                if (index == 0) open(url, true);
                                else {
                                    History.get().delete(url);
                                    content.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            home.refresh();
                                        }
                                    }, 300);
                                }
                            }
                        }).show();
            }
        });
        content.addView(home.view(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        suggestions = new Suggestions(k, new Suggestions.Listener() {
            @Override
            public void onPick(String urlOrQuery, boolean isUrl) {
                submit(isUrl ? urlOrQuery : Urls.fromInput(urlOrQuery));
            }

            @Override
            public void onFill(String text) {
                bar.fill(text);
            }
        });
        suggestions.view().setVisibility(View.GONE);
        content.addView(suggestions.view(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        findBar = new FindBar(k);
        findBar.view().setVisibility(View.GONE);
        content.addView(findBar.view(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP));

        boolean bottom = Settings.TOOLBAR_BOTTOM.equals(settings.toolbarPosition());
        if (!bottom) main.addView(bar.view(), Kit.matchWrap());
        main.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (bottom) main.addView(bar.view(), Kit.matchWrap());
        root.addView(main, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        tray = new TabsTray(k, home, new TabsTray.Listener() {
            @Override
            public void onTabPicked(Tab tab) {
                hideTray();
                Tabs.get().select(tab);
            }

            @Override
            public void onNewTab(boolean isPrivate) {
                hideTray();
                Tabs.get().open(Tab.HOME, isPrivate, true, null);
                bar.startEditing(null);
            }

            @Override
            public void onClosed() {
                hideTray();
            }
        });
        tray.view().setVisibility(View.GONE);
        root.addView(tray.view(), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
        // Older Android gives the first EditText focus when the window opens, which would start address editing.
        root.setFocusableInTouchMode(true);
        root.requestFocus();

        Engine.runtime(this);
        Extensions.get().setUi(extensionUi);
        Tabs tabs = Tabs.get();
        tabs.setHost(this);
        tabs.restore();
        if (!handleIntent(getIntent()) && tabs.count() == 0) tabs.open(homeUrl(), false, true, null);
        if (tabs.current() != null) onSelected(tabs.current());
        bar.setTabCount(tabs.list(tabs.current() != null && tabs.current().isPrivate).size());
    }

    private String homeUrl() {
        String h = settings.homepage();
        if (Settings.HOME_BLANK.equals(h)) return "about:blank";
        if (Settings.HOME_URL.equals(h)) return settings.homepageUrl();
        return Tab.HOME;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (debugExtras(intent)) {
            rebuild();
            return;
        }
        handleIntent(intent);
    }

    /** VIEW (links from other apps), SEND (shared text), WEB_SEARCH. Returns true if a tab was opened. */
    private boolean handleIntent(Intent intent) {
        if (intent == null) return false;
        String action = intent.getAction();
        String url = null;
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            url = intent.getDataString();
        } else if (Intent.ACTION_SEND.equals(action)) {
            String text = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (text != null) url = Urls.fromInput(text);
        } else if (Intent.ACTION_WEB_SEARCH.equals(action) || "android.intent.action.SEARCH".equals(action)) {
            String q = intent.getStringExtra("query");
            if (q != null) url = Urls.fromInput(q);
        }
        boolean priv = intent.getBooleanExtra(EXTRA_PRIVATE, false);
        if (url == null && !priv) return false;
        Tabs.get().open(url == null ? Tab.HOME : url, priv, true, null);
        setIntent(new Intent(this, BrowserActivity.class));
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isFinishing()) return;
        Tabs.get().setHost(this);
        Extensions.get().setUi(extensionUi);
        Tab t = Tabs.get().current();
        if (t != null && t.isHome()) home.refresh();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Tabs.get().saveNow();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Tabs.get().host() == this) Tabs.get().setHost(null);
        if (geckoView != null) geckoView.releaseSession();
        if (isFinishing() && settings.clearOnExit()) Clearing.clearAll(this, null);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (Engine.started()) Engine.runtime(this).configurationChanged(newConfig);
    }

    // ---------------------------------------------------------------- navigation

    private void open(String url, boolean newTab) {
        Tab cur = Tabs.get().current();
        if (newTab || cur == null) Tabs.get().open(url, cur != null && cur.isPrivate, !settings.openLinksInBackground() || !newTab, cur);
        else Tabs.get().load(cur, url);
    }

    private void submit(String url) {
        bar.stopEditing();
        Tab cur = Tabs.get().current();
        if (cur == null) Tabs.get().open(url, false, true, null);
        else Tabs.get().load(cur, url);
    }

    @Override
    public void onSubmit(String text) {
        submit(Urls.fromInput(text));
    }

    @Override
    public void onQueryChanged(String text) {
        suggestions.update(text);
    }

    @Override
    public void onEditingChanged(boolean editing) {
        Tab cur = Tabs.get().current();
        suggestions.setPrivate(cur != null && cur.isPrivate);
        suggestions.view().setVisibility(editing ? View.VISIBLE : View.GONE);
        if (editing) suggestions.update("");
    }

    @Override
    public void onTabsClicked() {
        showTray();
    }

    @Override
    public void onReloadClicked() {
        Tab t = Tabs.get().current();
        if (t == null) return;
        if (t.loading) t.session.stop();
        else t.session.reload();
    }

    @Override
    public void onSiteClicked(View anchor) {
        Tab t = Tabs.get().current();
        if (t == null || t.isHome()) {
            bar.startEditing(null);
            return;
        }
        SiteInfo.show(this, k, t);
    }

    @Override
    public void onMenuClicked(View anchor) {
        k.optionsMenu(anchor, menuActions());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            onMenuClicked(bar.menuAnchor());
            return true;
        }
        if (keyCode == KeyEvent.KEYCODE_SEARCH) {
            bar.startEditing(null);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onBackPressed() {
        Tab t = Tabs.get().current();
        if (fullscreen && t != null) {
            t.session.exitFullScreen();
            return;
        }
        if (tray.view().getVisibility() == View.VISIBLE) {
            hideTray();
            return;
        }
        if (findBar.view().getVisibility() == View.VISIBLE) {
            findBar.hide();
            return;
        }
        if (bar.editing()) {
            bar.stopEditing();
            return;
        }
        if (t != null && t.canGoBack && !t.isHome()) {
            t.session.goBack();
            return;
        }
        if (t != null && t.parentId != 0 && Tabs.get().find(t.parentId) != null) {
            Tabs.get().close(t);
            return;
        }
        moveTaskToBack(true);
    }

    private List<Action> menuActions() {
        final Tab t = Tabs.get().current();
        boolean page = t != null && !t.isHome();
        boolean classic = Settings.STYLE_CLASSIC.equals(k.style);
        List<Action> a = new ArrayList<Action>();
        if (!classic && t != null) {
            a.add(new Action(Ic.ARROW_BACK, s("browser.back"), new Runnable() {
                @Override
                public void run() {
                    t.session.goBack();
                }
            }).enabled(t.canGoBack));
        }
        a.add(new Action(Ic.ARROW_FORWARD, s("browser.forward"), new Runnable() {
            @Override
            public void run() {
                if (t != null) t.session.goForward();
            }
        }).enabled(t != null && t.canGoForward));
        if (classic) {
            a.add(new Action(Ic.REFRESH, s("browser.reload"), new Runnable() {
                @Override
                public void run() {
                    onReloadClicked();
                }
            }));
        }
        a.add(new Action(Ic.ADD, s("browser.newTab"), new Runnable() {
            @Override
            public void run() {
                Tabs.get().open(Tab.HOME, false, true, null);
                bar.startEditing(null);
            }
        }));
        a.add(new Action(Ic.PRIVATE, s("browser.newPrivateTab"), new Runnable() {
            @Override
            public void run() {
                Tabs.get().open(Tab.HOME, true, true, null);
                bar.startEditing(null);
            }
        }));
        final boolean marked = page && Bookmarks.find(t.url) != null;
        a.add(new Action(marked ? Ic.BOOKMARK : Ic.BOOKMARK_BORDER, s(marked ? "browser.editBookmark" : "browser.addBookmark"), new Runnable() {
            @Override
            public void run() {
                BookmarksActivity.editOrAdd(BrowserActivity.this, k, t.url, t.title);
            }
        }).enabled(page));
        a.add(new Action(Ic.BOOKMARKS, s("bookmarks.title"), new Runnable() {
            @Override
            public void run() {
                startActivityForResult(new Intent(BrowserActivity.this, BookmarksActivity.class), 1);
            }
        }));
        a.add(new Action(Ic.HISTORY, s("history.title"), new Runnable() {
            @Override
            public void run() {
                startActivityForResult(new Intent(BrowserActivity.this, HistoryActivity.class), 1);
            }
        }));
        a.add(new Action(Ic.DOWNLOAD, s("downloads.title"), new Runnable() {
            @Override
            public void run() {
                open(DownloadsActivity.class);
            }
        }));
        for (final Extensions.ActionState st : Extensions.get().actions()) {
            final WebExtension.Action act = st.page(t) != null ? st.page(t) : st.browser(t);
            if (act == null) continue;
            String label = act.title != null && act.title.length() > 0 ? act.title : st.ext.metaData.name;
            if (act.badgeText != null && act.badgeText.length() > 0 && !label.contains(act.badgeText)) label = label + " (" + act.badgeText + ")";
            a.add(new Action(Ic.EXTENSION, label, new Runnable() {
                @Override
                public void run() {
                    act.click();
                }
            }).enabled(act.enabled == null || act.enabled));
        }
        a.add(new Action(Ic.EXTENSION, s("addons.title"), new Runnable() {
            @Override
            public void run() {
                startActivityForResult(new Intent(BrowserActivity.this, AddonsActivity.class), 1);
            }
        }));
        a.add(new Action(Ic.FIND_IN_PAGE, s("browser.findInPage"), new Runnable() {
            @Override
            public void run() {
                findBar.show(t);
            }
        }).enabled(page));
        a.add(new Action(Ic.DESKTOP_WINDOWS, s("browser.desktopSite"), new Runnable() {
            @Override
            public void run() {
                Tabs.get().setDesktop(t, !t.desktop);
            }
        }).checkable(t != null && t.desktop).enabled(page));
        a.add(new Action(Ic.SHARE, s("browser.share"), new Runnable() {
            @Override
            public void run() {
                share(t.url, t.title);
            }
        }).enabled(page));
        a.add(new Action(Ic.SETTINGS, s("settings.title"), new Runnable() {
            @Override
            public void run() {
                open(SettingsActivity.class);
            }
        }));
        a.add(new Action(Ic.EXIT_TO_APP, s("browser.quit"), new Runnable() {
            @Override
            public void run() {
                quit();
            }
        }));
        if (classic) {
            // The Android 2.3 Browser menu: New window, Bookmarks, Windows, Refresh, Forward, More.
            a.add(0, new Action(Ic.TAB, s("browser.tabs"), new Runnable() {
                @Override
                public void run() {
                    showTray();
                }
            }));
            String[] order = {s("browser.newTab"), s("bookmarks.title"), s("browser.tabs"), s("browser.reload"), s("browser.forward")};
            List<Action> sorted = new ArrayList<Action>();
            for (String label : order) for (Action x : a) if (x.label.equals(label) && !sorted.contains(x)) sorted.add(x);
            for (Action x : a) if (!sorted.contains(x)) sorted.add(x);
            return sorted;
        }
        return a;
    }

    private void quit() {
        Tabs.get().saveNow();
        if (settings.clearOnExit()) Clearing.clearAll(this, null);
        Tabs.get().closeAll(true);
        finish();
    }

    private void share(String url, String title) {
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_TEXT, url);
        if (title != null) i.putExtra(Intent.EXTRA_SUBJECT, title);
        try {
            startActivity(Intent.createChooser(i, s("browser.share")));
        } catch (Exception e) {
            k.toast(s("errors.noApp"));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == Prompts.REQUEST_FILE) {
            prompts.onFileResult(resultCode, data);
            return;
        }
        if (requestCode == ExtensionUi.REQUEST_XPI) {
            extensionUi.onFilePicked(resultCode, data);
            return;
        }
        if (resultCode == RESULT_OK && data != null && data.getStringExtra("url") != null) {
            open(data.getStringExtra("url"), data.getBooleanExtra("newTab", false));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == Prompts.REQUEST_PERMISSIONS) prompts.onPermissionsResult(permissions, grantResults);
    }

    // ---------------------------------------------------------------- tab switcher

    private void captureThumbnail(final Tab t) {
        if (t == null || t.isHome() || geckoView.getSession() != t.session) return;
        try {
            geckoView.capturePixels().accept(new GeckoResult.Consumer<Bitmap>() {
                @Override
                public void accept(Bitmap b) {
                    if (b == null) return;
                    // Keep the top of the page: thumbnails are wider than tall.
                    int ch = Math.min(b.getHeight(), Math.round(b.getWidth() * 0.9f));
                    Bitmap top = Bitmap.createBitmap(b, 0, 0, b.getWidth(), ch);
                    t.thumbnail = Bitmap.createScaledBitmap(top, Math.max(1, top.getWidth() / 3), Math.max(1, top.getHeight() / 3), true);
                    if (tray.view().getVisibility() == View.VISIBLE) tray.refresh();
                }
            });
        } catch (Throwable e) {
            Log.w("capturePixels", e);
        }
    }

    private void showTray() {
        Tab cur = Tabs.get().current();
        captureThumbnail(cur);
        bar.stopEditing();
        tray.show(cur != null && cur.isPrivate);
        tray.view().setVisibility(View.VISIBLE);
    }

    private void hideTray() {
        tray.view().setVisibility(View.GONE);
        Tab cur = Tabs.get().current();
        if (cur == null) Tabs.get().open(Tab.HOME, false, true, null);
    }

    // ---------------------------------------------------------------- Tabs.Host

    @Override
    public void onSelected(Tab t) {
        if (shown != null && shown != t) captureThumbnail(shown);
        shown = t;
        findBar.hide();
        if (t == null) {
            geckoView.releaseSession();
            bar.show(null);
            return;
        }
        if (geckoView.getSession() != t.session) {
            geckoView.releaseSession();
            geckoView.setSession(t.session);
        }
        bar.setTabCount(Tabs.get().list(t.isPrivate).size());
        updatePage(t);
    }

    private void updatePage(Tab t) {
        bar.show(t);
        boolean isHome = t.isHome();
        home.setPrivate(t.isPrivate);
        if (isHome) home.refresh();
        home.view().setVisibility(isHome ? View.VISIBLE : View.GONE);
        geckoView.setVisibility(isHome ? View.INVISIBLE : View.VISIBLE);
    }

    @Override
    public void onTabChanged(Tab t, int what) {
        if (t != Tabs.get().current()) {
            if (tray.view().getVisibility() == View.VISIBLE) tray.refresh();
            return;
        }
        if ((what & Tabs.CHANGE_URL) != 0) updatePage(t);
        else if ((what & Tabs.CHANGE_PROGRESS) != 0) {
            bar.setProgress(t);
            bar.show(t);
        } else bar.show(t);
    }

    @Override
    public void onTabListChanged() {
        Tab cur = Tabs.get().current();
        bar.setTabCount(Tabs.get().list(cur != null && cur.isPrivate).size());
        if (tray.view().getVisibility() == View.VISIBLE) tray.refresh();
    }

    @Override
    public void onContextMenu(final Tab tab, int x, int y, final GeckoSession.ContentDelegate.ContextElement el) {
        List<Action> a = new ArrayList<Action>();
        final String link = el.linkUri;
        final String src = el.srcUri;
        if (link != null) {
            a.add(new Action(Ic.TAB, s("browser.openInNewTab"), new Runnable() {
                @Override
                public void run() {
                    Tabs.get().open(link, tab.isPrivate, !settings.openLinksInBackground(), tab);
                    if (settings.openLinksInBackground()) k.toast(s("browser.openedInBackground"));
                }
            }));
            a.add(new Action(Ic.PRIVATE, s("browser.openInPrivateTab"), new Runnable() {
                @Override
                public void run() {
                    Tabs.get().open(link, true, true, tab);
                }
            }));
            a.add(new Action(Ic.CONTENT_COPY, s("browser.copyLink"), new Runnable() {
                @Override
                public void run() {
                    copy(link);
                }
            }));
            a.add(new Action(Ic.SHARE, s("browser.shareLink"), new Runnable() {
                @Override
                public void run() {
                    share(link, el.linkText);
                }
            }));
            a.add(new Action(Ic.DOWNLOAD, s("browser.downloadLink"), new Runnable() {
                @Override
                public void run() {
                    downloadUrl(tab, link);
                }
            }));
        }
        if (src != null && (el.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE || el.type == GeckoSession.ContentDelegate.ContextElement.TYPE_VIDEO
                || el.type == GeckoSession.ContentDelegate.ContextElement.TYPE_AUDIO)) {
            a.add(new Action(Ic.IMAGE, s(el.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE ? "browser.openImage" : "browser.openMedia"), new Runnable() {
                @Override
                public void run() {
                    Tabs.get().open(src, tab.isPrivate, true, tab);
                }
            }));
            a.add(new Action(Ic.SAVE_ALT, s(el.type == GeckoSession.ContentDelegate.ContextElement.TYPE_IMAGE ? "browser.saveImage" : "browser.saveMedia"), new Runnable() {
                @Override
                public void run() {
                    downloadUrl(tab, src);
                }
            }));
            a.add(new Action(Ic.CONTENT_COPY, s("browser.copyImageAddress"), new Runnable() {
                @Override
                public void run() {
                    copy(src);
                }
            }));
        }
        if (a.isEmpty()) return;
        String title = link != null ? link : src;
        final List<Action> fa = a;
        k.dialog().title(title).items(labels(a), new com.disgusty.foxtalgia.ui.kit.DialogBuilder.ItemListener() {
            @Override
            public void onItem(int index) {
                fa.get(index).run.run();
            }
        }).show();
    }

    private static CharSequence[] labels(List<Action> a) {
        CharSequence[] out = new CharSequence[a.size()];
        for (int i = 0; i < a.size(); i++) out[i] = a.get(i).label;
        return out;
    }

    private void copy(String text) {
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", text));
        k.toast(s("browser.copied"));
    }

    private void downloadUrl(Tab tab, String url) {
        if (url.startsWith("data:") || url.startsWith("blob:")) {
            Tabs.get().open(url, tab.isPrivate, true, tab);
            return;
        }
        DownloadHelper.fetch(this, k, url, tab.isPrivate);
    }

    @Override
    public void onFullScreen(Tab tab, boolean fs) {
        fullscreen = fs;
        bar.view().setVisibility(fs ? View.GONE : View.VISIBLE);
        if (fs) getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    @Override
    public void onExternalResponse(Tab tab, WebResponse response) {
        DownloadHelper.confirm(this, k, response, tab.isPrivate);
    }

    @Override
    public boolean openExternally(Tab tab, String uri) {
        try {
            Intent i;
            if (uri.startsWith("intent:")) {
                i = Intent.parseUri(uri, Intent.URI_INTENT_SCHEME);
                i.addCategory(Intent.CATEGORY_BROWSABLE);
                i.setComponent(null);
                if (Sdk.atLeast(15)) i.setSelector(null);
                if (getPackageManager().resolveActivity(i, 0) == null) {
                    String fallback = i.getStringExtra("browser_fallback_url");
                    if (fallback != null) Tabs.get().load(tab, fallback);
                    else if (i.getPackage() != null) startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + i.getPackage())));
                    return true;
                }
            } else {
                i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                i.addCategory(Intent.CATEGORY_BROWSABLE);
            }
            startActivity(i);
        } catch (Exception e) {
            k.toast(s("errors.noApp"));
        }
        return true;
    }

    @Override
    public GeckoSession.PromptDelegate promptDelegate() {
        return prompts;
    }

    @Override
    public GeckoSession.PermissionDelegate permissionDelegate() {
        return prompts;
    }

    @Override
    public GeckoSession.MediaDelegate mediaDelegate() {
        return prompts;
    }

    @Override
    public String errorPage(Tab tab, String uri, WebRequestError error) {
        String html = ErrorPage.html(k, uri, error);
        return "data:text/html;charset=utf-8;base64," + Base64.encodeToString(html.getBytes(java.nio.charset.Charset.forName("UTF-8")), Base64.NO_WRAP);
    }

    public HomeView homeView() {
        return home;
    }

    public AddressBar bar() {
        return bar;
    }
}
