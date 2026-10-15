package com.disgusty.foxtalgia.ui;

import android.content.Intent;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.foxtalgia.engine.Engine;
import com.disgusty.foxtalgia.engine.Extensions;
import com.disgusty.foxtalgia.ui.browser.ExtensionUi;
import com.disgusty.foxtalgia.ui.kit.Action;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.ui.kit.Toggle;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.WebExtension;

import java.util.ArrayList;
import java.util.List;

/** Installed add-ons, add-on details, install from addons.mozilla.org or from a file. */
public final class AddonsActivity extends BaseActivity implements Extensions.Ui {
    /** Every add-on, as on desktop: the desktop AMO catalog (the browser switches to desktop mode there). */
    public static final String AMO_ALL = "https://addons.mozilla.org/firefox/extensions/";
    public static final String AMO_ANDROID = "https://addons.mozilla.org/android/";

    private LinearLayout list;
    private ExtensionUi ui;

    @Override
    protected void build() {
        Engine.runtime(this);
        ui = new ExtensionUi(this, k);
        screen.appBar.title(s("addons.title")).up(finisher());
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action(Ic.SEARCH, s("addons.findMore"), new Runnable() {
            @Override
            public void run() {
                openUrl(AMO_ALL);
            }
        }).always());
        actions.add(new Action(Ic.SMARTPHONE, s("addons.findAndroid"), new Runnable() {
            @Override
            public void run() {
                openUrl(AMO_ANDROID);
            }
        }));
        actions.add(new Action(Ic.FOLDER_OPEN, s("addons.installFromFile"), new Runnable() {
            @Override
            public void run() {
                ui.pickFile();
            }
        }));
        actions.add(new Action(Ic.UPDATE, s("addons.checkUpdates"), new Runnable() {
            @Override
            public void run() {
                for (WebExtension e : Extensions.get().installed()) if (!e.isBuiltIn) Extensions.get().update(e);
                k.toast(s("addons.checkingUpdates"));
            }
        }));
        screen.appBar.actions(actions);
        list = k.vbox();
        screen.setContent(k.scroll(list));
    }

    private void openUrl(String url) {
        Intent r = new Intent();
        r.putExtra("url", url);
        r.putExtra("newTab", true);
        setResult(RESULT_OK, r);
        finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Extensions.get().setUi(this);
        Extensions.get().refresh(new Runnable() {
            @Override
            public void run() {
                render();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ExtensionUi.REQUEST_XPI) ui.onFilePicked(resultCode, data);
    }

    private void render() {
        list.removeAllViews();
        List<WebExtension> all = new ArrayList<WebExtension>();
        for (WebExtension e : Extensions.get().installed()) if (!e.isBuiltIn) all.add(e);
        list.addView(k.sectionHeader(s("addons.installedSection")));
        if (all.isEmpty()) {
            TextView e = k.text(Kit.T_SECONDARY, s("addons.none"));
            e.setGravity(Gravity.CENTER);
            e.setPadding(k.dp(24), k.dp(32), k.dp(24), 0);
            list.addView(e, Kit.matchWrap());
        }
        for (final WebExtension e : all) {
            final Toggle tg = k.toggle(Kit.SWITCH);
            tg.setChecked(e.metaData.enabled);
            tg.listener(new Toggle.Listener() {
                @Override
                public void onChanged(Toggle toggle, boolean checked) {
                    Extensions.get().setEnabled(e, checked).accept(new GeckoResult.Consumer<WebExtension>() {
                        @Override
                        public void accept(WebExtension x) {
                            Extensions.get().refresh(new Runnable() {
                                @Override
                                public void run() {
                                    render();
                                }
                            });
                        }
                    });
                }
            });
            String summary = e.metaData.version + (e.metaData.description != null ? " · " + e.metaData.description : "");
            View row = k.row().leading(icon(e)).title(e.metaData.name != null ? e.metaData.name : e.id).summary(summary).trailing(tg)
                    .onClick(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            details(e);
                        }
                    }).build();
            list.addView(row, Kit.matchWrap());
        }
        TextView hint = k.text(Kit.T_CAPTION, s("addons.hint"));
        hint.setPadding(k.pagePadding(), k.dp(16), k.pagePadding(), k.dp(16));
        list.addView(hint, Kit.matchWrap());
    }

    private View icon(WebExtension e) {
        final ImageView iv = k.image(k.icon(Ic.EXTENSION, k.iconColor(), 32));
        try {
            e.metaData.icon.getBitmap(k.dp(32)).accept(new GeckoResult.Consumer<Bitmap>() {
                @Override
                public void accept(Bitmap b) {
                    if (b != null) iv.setImageBitmap(b);
                }
            });
        } catch (Throwable ignored) {
            // no icon
        }
        return iv;
    }

    private void details(final WebExtension e) {
        LinearLayout box = k.vbox();
        WebExtension.MetaData m = e.metaData;
        if (!TextUtils.isEmpty(m.description)) box.addView(k.text(Kit.T_BODY, m.description), Kit.matchWrap());
        StringBuilder info = new StringBuilder();
        info.append(s("addons.version")).append(": ").append(m.version);
        if (m.creatorName != null) info.append('\n').append(s("addons.author")).append(": ").append(m.creatorName);
        if (m.updateDate != null) info.append('\n').append(s("addons.updated")).append(": ").append(m.updateDate.length() > 10 ? m.updateDate.substring(0, 10) : m.updateDate);
        if (m.requiredPermissions.length > 0 || m.requiredOrigins.length > 0) {
            info.append("\n\n").append(s("addons.permissions")).append(":\n");
            for (String p : m.requiredPermissions) info.append("• ").append(p).append('\n');
            for (String o : m.requiredOrigins) info.append("• ").append(o).append('\n');
        }
        TextView it = k.text(Kit.T_SECONDARY, info.toString().trim());
        it.setPadding(0, k.dp(8), 0, k.dp(8));
        box.addView(it, Kit.matchWrap());
        LinearLayout pr = k.hbox();
        final Toggle priv = k.toggle(Kit.SWITCH);
        priv.setChecked(m.allowedInPrivateBrowsing);
        priv.listener(new Toggle.Listener() {
            @Override
            public void onChanged(Toggle toggle, boolean checked) {
                Extensions.get().setPrivate(e, checked);
            }
        });
        pr.addView(k.text(Kit.T_BODY, s("addons.runInPrivate")), new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        pr.addView(priv);
        box.addView(pr, Kit.matchWrap());
        com.disgusty.foxtalgia.ui.kit.DialogBuilder d = k.dialog().title(m.name != null ? m.name : e.id).view(box)
                .negative(s("addons.remove"), new Runnable() {
                    @Override
                    public void run() {
                        confirmRemove(e);
                    }
                })
                .positive(s("general.close"), null);
        if (m.optionsPageUrl != null) {
            d.neutral(s("addons.settings"), new Runnable() {
                @Override
                public void run() {
                    if (Extensions.get().controller() != null) {
                        Intent r = new Intent();
                        r.putExtra("url", e.metaData.optionsPageUrl);
                        r.putExtra("newTab", true);
                        setResult(RESULT_OK, r);
                        finish();
                    }
                }
            });
        }
        d.show();
    }

    private void confirmRemove(final WebExtension e) {
        k.dialog().title(s("addons.removeTitle")).message(t.text("addons.removeMessage", "name", e.metaData.name))
                .negative(s("general.cancel"), null)
                .positive(s("addons.remove"), new Runnable() {
                    @Override
                    public void run() {
                        Extensions.get().uninstall(e).accept(new GeckoResult.Consumer<Void>() {
                            @Override
                            public void accept(Void v) {
                                Extensions.get().refresh(new Runnable() {
                                    @Override
                                    public void run() {
                                        render();
                                    }
                                });
                            }
                        });
                    }
                }).show();
    }

    // ---------------------------------------------------------------- Extensions.Ui (while this screen is open)

    @Override
    public GeckoResult<WebExtension.PermissionPromptResponse> confirmInstall(WebExtension ext, String[] p, String[] o, String[] d) {
        return ui.confirmInstall(ext, p, o, d);
    }

    @Override
    public GeckoResult<org.mozilla.geckoview.AllowOrDeny> confirmOptional(WebExtension ext, String[] p, String[] o) {
        return ui.confirmOptional(ext, p, o);
    }

    @Override
    public GeckoResult<org.mozilla.geckoview.AllowOrDeny> confirmUpdate(WebExtension ext, String[] p, String[] o) {
        return ui.confirmUpdate(ext, p, o);
    }

    @Override
    public org.mozilla.geckoview.GeckoSession showPopup(WebExtension ext, WebExtension.Action action) {
        return ui.showPopup(ext, action);
    }

    @Override
    public void onActionsChanged() {
    }

    @Override
    public void onInstallFailed(WebExtension ext, WebExtension.InstallException e) {
        ui.onInstallFailed(ext, e);
    }

    @Override
    public void onInstalled(WebExtension ext) {
        ui.onInstalled(ext);
        render();
    }
}
