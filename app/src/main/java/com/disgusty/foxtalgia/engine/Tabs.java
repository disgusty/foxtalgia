package com.disgusty.foxtalgia.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.data.History;
import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.util.IO;
import com.disgusty.foxtalgia.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebRequestError;
import org.mozilla.geckoview.WebResponse;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** All open tabs, their GeckoSession delegates, and session persistence. */
public final class Tabs {
    public static final int CHANGE_URL = 1;
    public static final int CHANGE_TITLE = 2;
    public static final int CHANGE_PROGRESS = 4;
    public static final int CHANGE_SECURITY = 8;
    public static final int CHANGE_NAVIGATION = 16;
    public static final int CHANGE_THUMBNAIL = 32;
    public static final int CHANGE_BLOCKED = 64;
    public static final int CHANGE_ALL = 0xFFFF;

    /** The browser screen; null while no browser activity exists. */
    public interface Host {
        void onTabChanged(Tab tab, int what);

        void onTabListChanged();

        void onSelected(Tab tab);

        void onContextMenu(Tab tab, int x, int y, GeckoSession.ContentDelegate.ContextElement element);

        void onFullScreen(Tab tab, boolean fullScreen);

        void onExternalResponse(Tab tab, WebResponse response);

        /** Handles schemes Gecko cannot load (tel:, mailto:, intent:, market:...). Returns true if handled. */
        boolean openExternally(Tab tab, String uri);

        GeckoSession.PromptDelegate promptDelegate();

        GeckoSession.PermissionDelegate permissionDelegate();

        GeckoSession.MediaDelegate mediaDelegate();

        String errorPage(Tab tab, String uri, WebRequestError error);
    }

    private static Tabs instance;

    private final Context context;
    private final List<Tab> tabs = new ArrayList<Tab>();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Runnable saver = new Runnable() {
        @Override
        public void run() {
            save();
        }
    };
    private Tab current;
    private Host host;
    private long nextId = 1;
    private boolean restored;
    private int saveSeq;
    private int writtenSeq;

    private Tabs(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized Tabs get() {
        if (instance == null) instance = new Tabs(App.get());
        return instance;
    }

    public void setHost(Host h) {
        host = h;
        for (Tab t : tabs) bindUi(t);
    }

    public Host host() {
        return host;
    }

    public List<Tab> all() {
        return new ArrayList<Tab>(tabs);
    }

    public List<Tab> list(boolean isPrivate) {
        List<Tab> out = new ArrayList<Tab>();
        for (Tab t : tabs) if (t.isPrivate == isPrivate) out.add(t);
        return out;
    }

    public int count() {
        return tabs.size();
    }

    public Tab current() {
        return current;
    }

    public Tab find(GeckoSession s) {
        for (Tab t : tabs) if (t.session == s) return t;
        return null;
    }

    public Tab find(long id) {
        for (Tab t : tabs) if (t.id == id) return t;
        return null;
    }

    // ---------------------------------------------------------------- lifecycle

    /** Opens a tab. The session is opened (and loads {@code url}) unless {@code forNewSession} is true. */
    public Tab open(String url, boolean isPrivate, boolean select, Tab parent) {
        Tab t = create(isPrivate, App.get().settings.desktopMode());
        if (parent != null) t.parentId = parent.id;
        int index = parent != null ? tabs.indexOf(parent) + 1 : tabs.size();
        while (index < tabs.size() && tabs.get(index).parentId == t.parentId && parent != null) index++;
        tabs.add(Math.min(index, tabs.size()), t);
        t.session.open(Engine.runtime(context));
        Extensions.get().onSessionCreated(t);
        load(t, url);
        if (select || current == null) select(t);
        changed();
        return t;
    }

    private Tab create(boolean isPrivate, boolean desktop) {
        Tab t = new Tab(nextId++, isPrivate);
        t.desktop = desktop;
        t.session = new GeckoSession(Tab.settings(isPrivate, desktop));
        bind(t);
        return t;
    }

    public void load(Tab t, String url) {
        if (url == null || url.length() == 0 || Tab.HOME.equals(url)) {
            t.url = Tab.HOME;
            t.title = "";
            t.progress = 100;
            t.loading = false;
            changed(t, CHANGE_ALL);
            return;
        }
        t.url = url;
        t.session.loadUri(url);
        changed(t, CHANGE_URL);
    }

    public void select(Tab t) {
        if (t == null) return;
        Tab old = current;
        current = t;
        t.lastUsed = System.currentTimeMillis();
        if (old != null && old != t && old.session != null) {
            old.session.setActive(false);
            Extensions.get().setTabActive(old, false);
        }
        if (t.session != null) {
            t.session.setActive(true);
            Extensions.get().setTabActive(t, true);
        }
        if (host != null) host.onSelected(t);
        scheduleSave();
    }

    public void close(Tab t) {
        int i = tabs.indexOf(t);
        if (i < 0) return;
        tabs.remove(i);
        if (t.session != null) t.session.close();
        if (current == t) {
            current = null;
            Tab next = null;
            if (t.parentId != 0) next = find(t.parentId);
            if (next == null || next.isPrivate != t.isPrivate) {
                List<Tab> same = list(t.isPrivate);
                if (!same.isEmpty()) next = same.get(Math.min(Math.max(0, i - 1), same.size() - 1));
            }
            if (next == null && !tabs.isEmpty()) next = tabs.get(Math.min(i, tabs.size() - 1));
            if (next != null) select(next);
            else if (host != null) host.onSelected(null);
        }
        changed();
    }

    public void closeAll(boolean isPrivate) {
        for (Tab t : list(isPrivate)) close(t);
    }

    /** Switches between mobile and desktop mode by replacing the session settings and reloading. */
    public void setDesktop(Tab t, boolean desktop) {
        t.desktop = desktop;
        t.session.getSettings().setUserAgentMode(desktop ? org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP
                : org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_MOBILE);
        t.session.getSettings().setViewportMode(desktop ? org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_DESKTOP
                : org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
        t.session.reload();
    }

    private void changed() {
        if (host != null) host.onTabListChanged();
        scheduleSave();
    }

    private void changed(Tab t, int what) {
        if (host != null) host.onTabChanged(t, what);
    }

    // ---------------------------------------------------------------- delegates

    private void bindUi(Tab t) {
        if (host == null || t.session == null) return;
        t.session.setPromptDelegate(host.promptDelegate());
        t.session.setPermissionDelegate(host.permissionDelegate());
        t.session.setMediaDelegate(host.mediaDelegate());
    }

    private void bind(final Tab t) {
        final GeckoSession s = t.session;
        bindUi(t);
        s.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public void onLocationChange(GeckoSession session, String url, List<GeckoSession.PermissionDelegate.ContentPermission> perms, Boolean hasUserGesture) {
                if (url == null) return;
                if ("about:blank".equals(url) && t.isHome()) return;
                t.url = url;
                t.blocked = 0;
                // The first activation can arrive before the tab's Gecko window exists; repeat it.
                if (t == current) Extensions.get().setTabActive(t, true);
                changed(t, CHANGE_URL | CHANGE_BLOCKED);
            }

            @Override
            public void onCanGoBack(GeckoSession session, boolean canGoBack) {
                t.canGoBack = canGoBack;
                changed(t, CHANGE_NAVIGATION);
            }

            @Override
            public void onCanGoForward(GeckoSession session, boolean canGoForward) {
                t.canGoForward = canGoForward;
                changed(t, CHANGE_NAVIGATION);
            }

            @Override
            public GeckoResult<AllowOrDeny> onLoadRequest(GeckoSession session, LoadRequest request) {
                String uri = request.uri;
                if (Tab.HOME.equals(uri)) {
                    load(t, Tab.HOME);
                    return GeckoResult.deny();
                }
                if (!t.desktop && isAmoDesktopCatalog(uri)) {
                    // The desktop catalog lists every add-on; with a mobile user agent AMO only offers the Android ones.
                    t.desktop = true;
                    session.getSettings().setUserAgentMode(org.mozilla.geckoview.GeckoSessionSettings.USER_AGENT_MODE_DESKTOP);
                    session.getSettings().setViewportMode(org.mozilla.geckoview.GeckoSessionSettings.VIEWPORT_MODE_MOBILE);
                    session.loadUri(uri);
                    return GeckoResult.deny();
                }
                if (Urls.isExternalScheme(uri)) {
                    if (host != null && host.openExternally(t, uri)) return GeckoResult.deny();
                }
                return GeckoResult.allow();
            }

            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
                Tab nt = create(t.isPrivate, t.desktop);
                nt.parentId = t.id;
                nt.url = uri;
                int index = tabs.indexOf(t) + 1;
                tabs.add(Math.min(index, tabs.size()), nt);
                Extensions.get().onSessionCreated(nt);
                select(nt);
                changed();
                return GeckoResult.fromValue(nt.session);
            }

            @Override
            public GeckoResult<String> onLoadError(GeckoSession session, String uri, WebRequestError error) {
                if (host == null) return null;
                String page = host.errorPage(t, uri, error);
                return page == null ? null : GeckoResult.fromValue(page);
            }
        });
        s.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                t.loading = true;
                t.progress = 5;
                t.securityKnown = false;
                changed(t, CHANGE_PROGRESS | CHANGE_SECURITY);
            }

            @Override
            public void onPageStop(GeckoSession session, boolean success) {
                t.loading = false;
                t.progress = 100;
                changed(t, CHANGE_PROGRESS);
            }

            @Override
            public void onProgressChange(GeckoSession session, int progress) {
                t.progress = progress;
                changed(t, CHANGE_PROGRESS);
            }

            @Override
            public void onSecurityChange(GeckoSession session, SecurityInformation info) {
                t.secure = info.isSecure;
                t.securityKnown = true;
                t.securityHost = info.host;
                changed(t, CHANGE_SECURITY);
            }

            @Override
            public void onSessionStateChange(GeckoSession session, GeckoSession.SessionState state) {
                if (!t.isPrivate) t.sessionState = state;
                scheduleSave();
            }
        });
        s.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onTitleChange(GeckoSession session, String title) {
                t.title = title == null ? "" : title;
                changed(t, CHANGE_TITLE);
                if (!t.isPrivate && App.get().settings.rememberHistory()) History.get().setTitle(t.url, t.title);
            }

            @Override
            public void onCloseRequest(GeckoSession session) {
                close(t);
            }

            @Override
            public void onFullScreen(GeckoSession session, boolean fullScreen) {
                t.fullscreen = fullScreen;
                if (host != null) host.onFullScreen(t, fullScreen);
            }

            @Override
            public void onContextMenu(GeckoSession session, int screenX, int screenY, ContextElement element) {
                if (host != null) host.onContextMenu(t, screenX, screenY, element);
            }

            @Override
            public void onExternalResponse(GeckoSession session, WebResponse response) {
                if (host != null) host.onExternalResponse(t, response);
                else Downloads.get().start(context, response, t.isPrivate, null);
            }

            @Override
            public void onCrash(GeckoSession session) {
                Log.w("Content process crashed in tab " + t.id);
                recover(t);
            }

            @Override
            public void onKill(GeckoSession session) {
                Log.w("Content process killed in tab " + t.id);
                recover(t);
            }

            @Override
            public void onFirstContentfulPaint(GeckoSession session) {
                changed(t, CHANGE_THUMBNAIL);
            }
        });
        s.setHistoryDelegate(new GeckoSession.HistoryDelegate() {
            @Override
            public GeckoResult<Boolean> onVisited(GeckoSession session, String url, String lastVisitedURL, int flags) {
                if (t.isPrivate || !App.get().settings.rememberHistory()) return GeckoResult.fromValue(false);
                if ((flags & VISIT_TOP_LEVEL) == 0 || (flags & VISIT_UNRECOVERABLE_ERROR) != 0) return GeckoResult.fromValue(false);
                if ((flags & (VISIT_REDIRECT_SOURCE | VISIT_REDIRECT_SOURCE_PERMANENT)) != 0) return GeckoResult.fromValue(false);
                History.get().visit(url, t.title);
                return GeckoResult.fromValue(true);
            }

            @Override
            public GeckoResult<boolean[]> getVisited(GeckoSession session, String[] urls) {
                return History.get().visitedAsync(urls);
            }
        });
        s.setContentBlockingDelegate(new ContentBlocking.Delegate() {
            @Override
            public void onContentBlocked(GeckoSession session, ContentBlocking.BlockEvent event) {
                t.blocked++;
                changed(t, CHANGE_BLOCKED);
            }
        });
    }

    static boolean isAmoDesktopCatalog(String uri) {
        return uri != null && uri.startsWith("https://addons.mozilla.org/") && uri.indexOf("/firefox/") > 0;
    }

    /** After a content process crash the session is closed and reopened from its last state. */
    private void recover(Tab t) {
        boolean wasCurrent = current == t;
        GeckoSession old = t.session;
        t.session = new GeckoSession(Tab.settings(t.isPrivate, t.desktop));
        bind(t);
        try {
            old.close();
        } catch (Throwable ignored) {
            // already gone
        }
        t.session.open(Engine.runtime(context));
        Extensions.get().onSessionCreated(t);
        GeckoSession.SessionState st = t.restorableState();
        if (st != null) t.session.restoreState(st);
        else if (!t.isHome()) t.session.loadUri(t.url);
        if (wasCurrent) select(t);
        changed(t, CHANGE_ALL);
    }

    // ---------------------------------------------------------------- persistence

    private File file() {
        return new File(context.getFilesDir(), "tabs.json");
    }

    private void scheduleSave() {
        main.removeCallbacks(saver);
        main.postDelayed(saver, 1500);
    }

    public void saveNow() {
        main.removeCallbacks(saver);
        final String json = serialize();
        if (json != null) write(json, ++saveSeq);
    }

    private void save() {
        final String json = serialize();
        if (json == null) return;
        final int seq = ++saveSeq;
        io.execute(new Runnable() {
            @Override
            public void run() {
                write(json, seq);
            }
        });
    }

    private String serialize() {
        Settings s = App.get().settings;
        try {
            JSONArray arr = new JSONArray();
            if (s.restoreTabs()) {
                for (Tab t : tabs) {
                    if (t.isPrivate) continue;
                    if (t.sessionState != null) {
                        t.state = t.sessionState.toString();
                        t.sessionState = null;
                    }
                    JSONObject o = new JSONObject();
                    o.put("url", t.url);
                    o.put("title", t.title);
                    o.put("desktop", t.desktop);
                    if (t.state != null) o.put("state", t.state);
                    if (t == current) o.put("current", true);
                    arr.put(o);
                }
            }
            return arr.toString();
        } catch (Exception e) {
            Log.w("Saving tabs failed", e);
            return null;
        }
    }

    /** Writes on the io thread or, from saveNow, on the main thread; an older snapshot never replaces a newer one. */
    private synchronized void write(String json, int seq) {
        if (seq < writtenSeq) return;
        writtenSeq = seq;
        try {
            File tmp = new File(context.getFilesDir(), "tabs.json.tmp");
            FileOutputStream out = new FileOutputStream(tmp);
            try {
                out.write(json.getBytes("UTF-8"));
            } finally {
                IO.close(out);
            }
            if (!tmp.renameTo(file())) Log.w("Could not save tabs");
        } catch (Exception e) {
            Log.w("Saving tabs failed", e);
        }
    }

    /** Restores the last session once per process. Returns true if at least one tab exists afterwards. */
    public boolean restore() {
        if (restored) return !tabs.isEmpty();
        restored = true;
        if (!App.get().settings.restoreTabs() || !file().exists()) return false;
        try {
            JSONArray arr = new JSONArray(IO.readText(file()));
            Tab sel = null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                Tab t = create(false, o.optBoolean("desktop"));
                t.url = o.optString("url", Tab.HOME);
                t.title = o.optString("title", "");
                t.state = o.optString("state", null);
                tabs.add(t);
                t.session.open(Engine.runtime(context));
                Extensions.get().onSessionCreated(t);
                GeckoSession.SessionState st = t.restorableState();
                if (st != null) t.session.restoreState(st);
                else if (!t.isHome()) t.session.loadUri(t.url);
                if (o.optBoolean("current")) sel = t;
            }
            if (sel == null && !tabs.isEmpty()) sel = tabs.get(tabs.size() - 1);
            if (sel != null) select(sel);
            changed();
        } catch (Exception e) {
            Log.w("Restoring tabs failed", e);
        }
        return !tabs.isEmpty();
    }

    /** Extension API: tabs.create. */
    public GeckoSession openForExtension(WebExtension ext, WebExtension.CreateTabDetails d) {
        Tab t = create(false, App.get().settings.desktopMode());
        tabs.add(t);
        if (d.url != null) t.url = d.url;
        Extensions.get().onSessionCreated(t);
        if (d.active == null || d.active) select(t);
        changed();
        return t.session;
    }
}
