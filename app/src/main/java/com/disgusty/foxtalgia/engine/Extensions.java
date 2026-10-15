package com.disgusty.foxtalgia.engine;

import android.os.Handler;
import android.os.Looper;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.util.Log;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Add-ons as in desktop Firefox: install from addons.mozilla.org or a file, enable/disable, options pages,
 * browser and page actions with popups, the tabs API, private browsing access. uBlock Origin ships inside the APK
 * as the AMO-signed XPI and is installed on the first start; afterwards it is an ordinary add-on (updates from
 * AMO, can be disabled or removed).
 */
public final class Extensions {
    public static final String UBLOCK_ID = "uBlock0@raymondhill.net";
    public static final String UBLOCK_XPI = "resource://android/assets/extensions/ublock_origin.xpi";
    private static final String FLAG_BUNDLED = "bundledUblockInstalled";

    /** The UI: install confirmations, popups, the action list in the menu. */
    public interface Ui {
        GeckoResult<WebExtension.PermissionPromptResponse> confirmInstall(WebExtension ext, String[] permissions, String[] origins, String[] dataCollection);

        GeckoResult<AllowOrDeny> confirmOptional(WebExtension ext, String[] permissions, String[] origins);

        GeckoResult<AllowOrDeny> confirmUpdate(WebExtension ext, String[] newPermissions, String[] newOrigins);

        /** Shows an action popup; the returned session is opened by Gecko. */
        GeckoSession showPopup(WebExtension ext, WebExtension.Action action);

        void onActionsChanged();

        void onInstallFailed(WebExtension ext, WebExtension.InstallException e);

        void onInstalled(WebExtension ext);
    }

    /** One extension's browser/page action, merged per tab. */
    public static final class ActionState {
        public WebExtension ext;
        public WebExtension.Action browserDefault;
        public WebExtension.Action pageDefault;
        public final Map<Long, WebExtension.Action> browserPerTab = new HashMap<Long, WebExtension.Action>();
        public final Map<Long, WebExtension.Action> pagePerTab = new HashMap<Long, WebExtension.Action>();

        public WebExtension.Action browser(Tab t) {
            WebExtension.Action a = t == null ? null : browserPerTab.get(t.id);
            if (a != null && browserDefault != null) return a.withDefault(browserDefault);
            return a != null ? a : browserDefault;
        }

        public WebExtension.Action page(Tab t) {
            WebExtension.Action a = t == null ? null : pagePerTab.get(t.id);
            if (a != null && pageDefault != null) return a.withDefault(pageDefault);
            return a != null ? a : pageDefault;
        }
    }

    private static Extensions instance;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<WebExtension> installed = new ArrayList<WebExtension>();
    private final Map<String, ActionState> actions = new HashMap<String, ActionState>();
    private GeckoRuntime runtime;
    private WebExtensionController controller;
    private Ui ui;
    private boolean autoApproveBundled;

    private Extensions() {
    }

    public static synchronized Extensions get() {
        if (instance == null) instance = new Extensions();
        return instance;
    }

    public void setUi(Ui u) {
        ui = u;
    }

    public WebExtensionController controller() {
        return controller;
    }

    public List<WebExtension> installed() {
        return new ArrayList<WebExtension>(installed);
    }

    public List<ActionState> actions() {
        List<ActionState> out = new ArrayList<ActionState>();
        for (WebExtension e : installed) {
            ActionState a = actions.get(e.id);
            if (a != null && e.metaData.enabled && (a.browserDefault != null || a.pageDefault != null || !a.browserPerTab.isEmpty())) out.add(a);
        }
        return out;
    }

    void attach(GeckoRuntime rt) {
        runtime = rt;
        controller = rt.getWebExtensionController();
        controller.setPromptDelegate(new WebExtensionController.PromptDelegate() {
            @Override
            public GeckoResult<WebExtension.PermissionPromptResponse> onInstallPromptRequest(WebExtension ext, String[] permissions, String[] origins, String[] dataCollection) {
                if (autoApproveBundled && UBLOCK_ID.equals(ext.id)) {
                    return GeckoResult.fromValue(new WebExtension.PermissionPromptResponse(true, true, false));
                }
                if (ui == null) return GeckoResult.fromValue(new WebExtension.PermissionPromptResponse(false, false, false));
                return ui.confirmInstall(ext, permissions, origins, dataCollection);
            }

            @Override
            public GeckoResult<AllowOrDeny> onOptionalPrompt(WebExtension ext, String[] permissions, String[] origins, String[] dataCollection) {
                if (ui == null) return GeckoResult.deny();
                return ui.confirmOptional(ext, permissions, origins);
            }

            @Override
            public GeckoResult<AllowOrDeny> onUpdatePrompt(WebExtension ext, String[] newPermissions, String[] newOrigins, String[] newDataCollection) {
                if (ui == null) return GeckoResult.deny();
                return ui.confirmUpdate(ext, newPermissions, newOrigins);
            }
        });
        controller.setAddonManagerDelegate(new WebExtensionController.AddonManagerDelegate() {
            @Override
            public void onInstalled(WebExtension ext) {
                track(ext);
                if (ui != null) ui.onInstalled(ext);
            }

            @Override
            public void onUninstalled(WebExtension ext) {
                untrack(ext.id);
            }

            @Override
            public void onEnabled(WebExtension ext) {
                track(ext);
            }

            @Override
            public void onDisabled(WebExtension ext) {
                track(ext);
            }

            @Override
            public void onReady(WebExtension ext) {
                track(ext);
            }

            @Override
            public void onInstallationFailed(WebExtension ext, WebExtension.InstallException e) {
                Log.w("Add-on installation failed: " + e.code);
                if (ui != null) ui.onInstallFailed(ext, e);
            }
        });
        controller.setDebuggerDelegate(new WebExtensionController.DebuggerDelegate() {
            @Override
            public void onExtensionListUpdated() {
                refresh(null);
            }
        });
        refresh(new Runnable() {
            @Override
            public void run() {
                installBundled();
            }
        });
    }

    /** Reloads the list of add-ons from Gecko. */
    public void refresh(final Runnable then) {
        if (controller == null) return;
        controller.list().accept(new GeckoResult.Consumer<List<WebExtension>>() {
            @Override
            public void accept(List<WebExtension> list) {
                installed.clear();
                if (list != null) for (WebExtension e : list) track(e);
                if (ui != null) ui.onActionsChanged();
                if (then != null) then.run();
            }
        }, new GeckoResult.Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                Log.w("Listing add-ons failed", e);
                if (then != null) then.run();
            }
        });
    }

    private void installBundled() {
        final Settings s = App.get().settings;
        if (s.flag(FLAG_BUNDLED)) return;
        for (WebExtension e : installed) {
            if (UBLOCK_ID.equals(e.id)) {
                s.setFlag(FLAG_BUNDLED, true);
                return;
            }
        }
        autoApproveBundled = true;
        controller.install(UBLOCK_XPI, WebExtensionController.INSTALLATION_METHOD_ONBOARDING).accept(new GeckoResult.Consumer<WebExtension>() {
            @Override
            public void accept(WebExtension ext) {
                autoApproveBundled = false;
                s.setFlag(FLAG_BUNDLED, true);
                Log.i("uBlock Origin installed: " + (ext == null ? "?" : ext.metaData.version));
                if (ext != null) {
                    controller.setAllowedInPrivateBrowsing(ext, true);
                    track(ext);
                }
            }
        }, new GeckoResult.Consumer<Throwable>() {
            @Override
            public void accept(Throwable e) {
                autoApproveBundled = false;
                Log.w("Installing the bundled uBlock Origin failed", e);
            }
        });
    }

    private void track(WebExtension ext) {
        if (ext == null) return;
        for (int i = 0; i < installed.size(); i++) {
            if (installed.get(i).id.equals(ext.id)) {
                installed.set(i, ext);
                bindExtension(ext);
                return;
            }
        }
        installed.add(ext);
        bindExtension(ext);
    }

    private void untrack(String id) {
        for (int i = installed.size() - 1; i >= 0; i--) if (installed.get(i).id.equals(id)) installed.remove(i);
        actions.remove(id);
        if (ui != null) ui.onActionsChanged();
    }

    public WebExtension byId(String id) {
        for (WebExtension e : installed) if (e.id.equals(id)) return e;
        return null;
    }

    // ---------------------------------------------------------------- delegates

    private ActionState state(WebExtension ext) {
        ActionState a = actions.get(ext.id);
        if (a == null) {
            a = new ActionState();
            actions.put(ext.id, a);
        }
        a.ext = ext;
        return a;
    }

    private void bindExtension(final WebExtension ext) {
        state(ext);
        ext.setActionDelegate(new WebExtension.ActionDelegate() {
            @Override
            public void onBrowserAction(WebExtension e, GeckoSession session, WebExtension.Action action) {
                ActionState a = state(e);
                Tab t = session == null ? null : Tabs.get().find(session);
                if (t == null) a.browserDefault = action;
                else a.browserPerTab.put(t.id, action);
                if (ui != null) ui.onActionsChanged();
            }

            @Override
            public void onPageAction(WebExtension e, GeckoSession session, WebExtension.Action action) {
                ActionState a = state(e);
                Tab t = session == null ? null : Tabs.get().find(session);
                if (t == null) a.pageDefault = action;
                else a.pagePerTab.put(t.id, action);
                if (ui != null) ui.onActionsChanged();
            }

            @Override
            public GeckoResult<GeckoSession> onTogglePopup(WebExtension e, WebExtension.Action action) {
                return popup(e, action);
            }

            @Override
            public GeckoResult<GeckoSession> onOpenPopup(WebExtension e, WebExtension.Action action) {
                return popup(e, action);
            }
        });
        ext.setTabDelegate(new WebExtension.TabDelegate() {
            @Override
            public GeckoResult<GeckoSession> onNewTab(WebExtension e, WebExtension.CreateTabDetails details) {
                return GeckoResult.fromValue(Tabs.get().openForExtension(e, details));
            }

            @Override
            public void onOpenOptionsPage(WebExtension e) {
                openOptions(e);
            }
        });
        for (Tab t : Tabs.get().all()) bindSession(t, ext);
    }

    private GeckoResult<GeckoSession> popup(WebExtension e, WebExtension.Action action) {
        if (ui == null) return null;
        GeckoSession s = ui.showPopup(e, action);
        return s == null ? null : GeckoResult.fromValue(s);
    }

    /** Opens the add-on's options page in a tab. */
    public void openOptions(WebExtension e) {
        String url = e.metaData.optionsPageUrl;
        if (url == null) return;
        Tabs.get().open(url, false, true, Tabs.get().current());
    }

    void onSessionCreated(Tab t) {
        for (WebExtension e : installed) bindSession(t, e);
    }

    private void bindSession(final Tab t, WebExtension ext) {
        if (t.session == null) return;
        WebExtension.SessionController sc = t.session.getWebExtensionController();
        sc.setTabDelegate(ext, new WebExtension.SessionTabDelegate() {
            @Override
            public GeckoResult<AllowOrDeny> onCloseTab(WebExtension e, GeckoSession session) {
                Tab target = Tabs.get().find(session);
                if (target == null) return GeckoResult.deny();
                Tabs.get().close(target);
                return GeckoResult.allow();
            }

            @Override
            public GeckoResult<AllowOrDeny> onUpdateTab(WebExtension e, GeckoSession session, WebExtension.UpdateTabDetails details) {
                Tab target = Tabs.get().find(session);
                if (target == null) return GeckoResult.deny();
                if (details.active != null && details.active) Tabs.get().select(target);
                if (details.url != null) Tabs.get().load(target, details.url);
                return GeckoResult.allow();
            }
        });
        sc.setActionDelegate(ext, new WebExtension.ActionDelegate() {
            @Override
            public void onBrowserAction(WebExtension e, GeckoSession session, WebExtension.Action action) {
                state(e).browserPerTab.put(t.id, action);
                if (ui != null) ui.onActionsChanged();
            }

            @Override
            public void onPageAction(WebExtension e, GeckoSession session, WebExtension.Action action) {
                state(e).pagePerTab.put(t.id, action);
                if (ui != null) ui.onActionsChanged();
            }

            @Override
            public GeckoResult<GeckoSession> onTogglePopup(WebExtension e, WebExtension.Action action) {
                return popup(e, action);
            }

            @Override
            public GeckoResult<GeckoSession> onOpenPopup(WebExtension e, WebExtension.Action action) {
                return popup(e, action);
            }
        });
    }

    void setTabActive(Tab t, boolean active) {
        if (controller == null || t.session == null) return;
        try {
            controller.setTabActive(t.session, active);
        } catch (Throwable e) {
            Log.w("setTabActive", e);
        }
    }

    // ---------------------------------------------------------------- management

    public GeckoResult<WebExtension> install(String uri) {
        return controller.install(uri, uri.startsWith("http") ? WebExtensionController.INSTALLATION_METHOD_MANAGER
                : WebExtensionController.INSTALLATION_METHOD_FROM_FILE);
    }

    public GeckoResult<WebExtension> setEnabled(WebExtension e, boolean enabled) {
        return enabled ? controller.enable(e, WebExtensionController.EnableSource.USER)
                : controller.disable(e, WebExtensionController.EnableSource.USER);
    }

    public GeckoResult<Void> uninstall(WebExtension e) {
        return controller.uninstall(e);
    }

    public GeckoResult<WebExtension> update(WebExtension e) {
        return controller.update(e);
    }

    public GeckoResult<WebExtension> setPrivate(WebExtension e, boolean allowed) {
        return controller.setAllowedInPrivateBrowsing(e, allowed);
    }

    public void post(Runnable r) {
        main.post(r);
    }
}
