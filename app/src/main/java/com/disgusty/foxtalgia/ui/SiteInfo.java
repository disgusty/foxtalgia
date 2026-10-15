package com.disgusty.foxtalgia.ui;

import android.app.Activity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.foxtalgia.engine.Engine;
import com.disgusty.foxtalgia.engine.Tab;
import com.disgusty.foxtalgia.engine.Urls;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;

import org.mozilla.geckoview.StorageController;

/** The dialog behind the lock icon: connection security, blocked trackers, clear site data. */
final class SiteInfo {
    private SiteInfo() {
    }

    static void show(final Activity a, final Kit k, final Tab t) {
        final I18n i = I18n.get();
        final String host = Urls.displayHost(t.url);
        LinearLayout box = k.vbox();
        String sec;
        if (t.url.startsWith("https:") && t.securityKnown && t.secure) sec = i.text("site.secure");
        else if (t.url.startsWith("https:")) sec = i.text("site.partlySecure");
        else if (t.url.startsWith("http:")) sec = i.text("site.notSecure");
        else sec = i.text("site.internal");
        box.addView(k.text(Kit.T_BODY, sec), Kit.matchWrap());
        TextView tracking = k.text(Kit.T_SECONDARY, i.plural("site.blocked", t.blocked));
        tracking.setPadding(0, k.dp(8), 0, 0);
        box.addView(tracking, Kit.matchWrap());
        k.dialog().icon(t.secure ? Ic.LOCK : Ic.INFO).title(host).view(box)
                .neutral(i.text("site.clearData"), new Runnable() {
                    @Override
                    public void run() {
                        String h = android.net.Uri.parse(t.url).getHost();
                        if (h == null) return;
                        Engine.runtime(a).getStorageController().clearDataFromBaseDomain(h, StorageController.ClearFlags.ALL);
                        k.toast(i.text("site.cleared"));
                        t.session.reload();
                    }
                })
                .positive(i.text("general.close"), null).show();
    }
}
