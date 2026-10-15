package com.disgusty.foxtalgia.ui.browser;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.foxtalgia.engine.Engine;
import com.disgusty.foxtalgia.engine.Extensions;
import com.disgusty.foxtalgia.engine.Tabs;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.ui.kit.Toggle;
import com.disgusty.foxtalgia.util.IO;
import com.disgusty.foxtalgia.util.Log;

import org.mozilla.geckoview.AllowOrDeny;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;
import org.mozilla.geckoview.GeckoView;
import org.mozilla.geckoview.WebExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

/** Add-on dialogs: install / update permissions, action popups, install from file. */
public final class ExtensionUi implements Extensions.Ui {
    public static final int REQUEST_XPI = 4201;

    private final Activity activity;
    private final Kit k;
    private final I18n t;
    private Dialog popup;

    public ExtensionUi(Activity a, Kit kit) {
        activity = a;
        k = kit;
        t = I18n.get();
    }

    private static String name(WebExtension e) {
        return e.metaData.name != null ? e.metaData.name : e.id;
    }

    /** Readable descriptions of WebExtension permissions, like Firefox's install doorhanger. */
    private String describe(String[] permissions, String[] origins) {
        Set<String> lines = new LinkedHashSet<String>();
        boolean allSites = false;
        if (origins != null) {
            for (String o : origins) {
                if ("<all_urls>".equals(o) || o.startsWith("*://*/") || o.equals("http://*/*") || o.equals("https://*/*")) allSites = true;
            }
            if (allSites) lines.add(t.text("addons.perm.allUrls"));
            else for (String o : origins) lines.add(t.text("addons.perm.site", "site", o.replace("*://", "").replace("/*", "")));
        }
        if (permissions != null) {
            for (String p : permissions) {
                String key = "addons.perm." + p.replace('.', '_');
                if (t.has(key)) lines.add(t.text(key));
            }
        }
        StringBuilder sb = new StringBuilder();
        for (String l : lines) sb.append("• ").append(l).append('\n');
        return sb.toString().trim();
    }

    @Override
    public GeckoResult<WebExtension.PermissionPromptResponse> confirmInstall(final WebExtension ext, String[] permissions, String[] origins, String[] dataCollection) {
        final GeckoResult<WebExtension.PermissionPromptResponse> r = new GeckoResult<WebExtension.PermissionPromptResponse>();
        LinearLayout box = k.vbox();
        String perms = describe(permissions, origins);
        TextView msg = k.text(Kit.T_BODY, perms.length() == 0 ? t.text("addons.noPermissions") : t.text("addons.needs") + "\n" + perms);
        box.addView(msg, Kit.matchWrap());
        LinearLayout row = k.hbox();
        final Toggle priv = k.toggle(Kit.CHECKBOX).passive();
        TextView pl = k.text(Kit.T_BODY, t.text("addons.allowPrivate"));
        row.addView(priv);
        row.addView(pl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        row.setClickable(true);
        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                priv.toggle();
            }
        });
        row.setPadding(0, k.dp(12), 0, 0);
        box.addView(row, Kit.matchWrap());
        final boolean[] answered = new boolean[1];
        k.dialog().icon(Ic.EXTENSION).title(t.text("addons.addTitle", "name", name(ext))).view(box)
                .negative(t.text("general.cancel"), new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        r.complete(new WebExtension.PermissionPromptResponse(false, false, false));
                    }
                })
                .positive(t.text("addons.add"), new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        r.complete(new WebExtension.PermissionPromptResponse(true, priv.isChecked(), false));
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        if (!answered[0]) r.complete(new WebExtension.PermissionPromptResponse(false, false, false));
                    }
                }).show();
        return r;
    }

    private GeckoResult<AllowOrDeny> ask(String title, String message, String yes) {
        final GeckoResult<AllowOrDeny> r = new GeckoResult<AllowOrDeny>();
        final boolean[] answered = new boolean[1];
        k.dialog().icon(Ic.EXTENSION).title(title).message(message)
                .negative(t.text("general.cancel"), new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        r.complete(AllowOrDeny.DENY);
                    }
                })
                .positive(yes, new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        r.complete(AllowOrDeny.ALLOW);
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        if (!answered[0]) r.complete(AllowOrDeny.DENY);
                    }
                }).show();
        return r;
    }

    @Override
    public GeckoResult<AllowOrDeny> confirmOptional(WebExtension ext, String[] permissions, String[] origins) {
        return ask(t.text("addons.optionalTitle", "name", name(ext)), describe(permissions, origins), t.text("prompts.allow"));
    }

    @Override
    public GeckoResult<AllowOrDeny> confirmUpdate(WebExtension ext, String[] newPermissions, String[] newOrigins) {
        return ask(t.text("addons.updateTitle", "name", name(ext)), t.text("addons.updateNeeds") + "\n" + describe(newPermissions, newOrigins),
                t.text("addons.update"));
    }

    @Override
    public GeckoSession showPopup(WebExtension ext, WebExtension.Action action) {
        if (popup != null && popup.isShowing()) {
            popup.dismiss();
            return null;
        }
        final GeckoSession s = new GeckoSession(new GeckoSessionSettings.Builder()
                .usePrivateMode(Tabs.get().current() != null && Tabs.get().current().isPrivate).build());
        s.setProgressDelegate(new GeckoSession.ProgressDelegate() {
            @Override
            public void onPageStart(GeckoSession session, String url) {
                Log.i("Popup loads " + url);
            }
        });
        s.setContentDelegate(new GeckoSession.ContentDelegate() {
            @Override
            public void onCloseRequest(GeckoSession session) {
                if (popup != null) popup.dismiss();
            }
        });
        s.setNavigationDelegate(new GeckoSession.NavigationDelegate() {
            @Override
            public GeckoResult<GeckoSession> onNewSession(GeckoSession session, String uri) {
                if (popup != null) popup.dismiss();
                return GeckoResult.fromValue(Tabs.get().open(uri, false, true, Tabs.get().current()).session);
            }
        });
        Tabs.Host host = Tabs.get().host();
        if (host != null) {
            s.setPromptDelegate(host.promptDelegate());
            s.setPermissionDelegate(host.permissionDelegate());
        }
        final GeckoView gv = new GeckoView(activity);
        FrameLayout frame = new FrameLayout(activity);
        int h = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.65f);
        frame.addView(gv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h));
        popup = k.dialog().title(action.title != null && action.title.length() > 0 ? action.title : name(ext)).view(frame)
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        gv.releaseSession();
                        s.close();
                        popup = null;
                    }
                }).show();
        // GeckoView only loads the popup URL into the returned session (WebExtension.Action.openPopup); opening it is ours.
        s.open(Engine.runtime(activity));
        gv.setSession(s);
        return s;
    }

    @Override
    public void onActionsChanged() {
    }

    @Override
    public void onInstallFailed(WebExtension ext, WebExtension.InstallException e) {
        if (e.code == WebExtension.InstallException.ErrorCodes.ERROR_USER_CANCELED) return;
        String key;
        switch (e.code) {
            case WebExtension.InstallException.ErrorCodes.ERROR_SIGNEDSTATE_REQUIRED:
                key = "addons.error.unsigned";
                break;
            case WebExtension.InstallException.ErrorCodes.ERROR_INCOMPATIBLE:
                key = "addons.error.incompatible";
                break;
            case WebExtension.InstallException.ErrorCodes.ERROR_NETWORK_FAILURE:
                key = "addons.error.network";
                break;
            case WebExtension.InstallException.ErrorCodes.ERROR_CORRUPT_FILE:
                key = "addons.error.corrupt";
                break;
            case WebExtension.InstallException.ErrorCodes.ERROR_BLOCKLISTED:
            case WebExtension.InstallException.ErrorCodes.ERROR_SOFT_BLOCKED:
                key = "addons.error.blocked";
                break;
            default:
                key = "addons.error.generic";
        }
        String n = e.extensionName != null ? e.extensionName : ext != null ? name(ext) : "";
        k.dialog().icon(Ic.ERROR).title(t.text("addons.error.title")).message(t.text(key, "name", n, "code", e.code))
                .positive(t.text("general.ok"), null).show();
    }

    @Override
    public void onInstalled(WebExtension ext) {
        k.toast(t.text("addons.installed", "name", name(ext)));
    }

    // ---------------------------------------------------------------- install from file

    public void pickFile() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        try {
            activity.startActivityForResult(Intent.createChooser(i, t.text("addons.installFromFile")), REQUEST_XPI);
        } catch (Exception e) {
            k.toast(t.text("errors.noApp"));
        }
    }

    public void onFilePicked(int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        InputStream in = null;
        FileOutputStream out = null;
        try {
            File f = new File(activity.getCacheDir(), "install-" + System.currentTimeMillis() + ".xpi");
            in = activity.getContentResolver().openInputStream(uri);
            out = new FileOutputStream(f);
            IO.copy(in, out, null);
            out.close();
            out = null;
            Engine.runtime(activity);
            Extensions.get().install(Uri.fromFile(f).toString()).accept(new GeckoResult.Consumer<WebExtension>() {
                @Override
                public void accept(WebExtension e) {
                    Extensions.get().refresh(null);
                }
            }, new GeckoResult.Consumer<Throwable>() {
                @Override
                public void accept(Throwable e) {
                    Log.w("Install from file failed", e);
                    if (e instanceof WebExtension.InstallException) onInstallFailed(null, (WebExtension.InstallException) e);
                }
            });
        } catch (Exception e) {
            Log.w("Reading the add-on file failed", e);
            k.toast(t.text("addons.error.generic", "name", "", "code", 0));
        } finally {
            IO.close(in);
            IO.close(out);
        }
    }
}
