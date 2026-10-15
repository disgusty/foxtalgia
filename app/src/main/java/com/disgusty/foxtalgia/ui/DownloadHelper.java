package com.disgusty.foxtalgia.ui;

import android.app.Activity;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.engine.Downloads;
import com.disgusty.foxtalgia.engine.Engine;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.browser.Prompts;
import com.disgusty.foxtalgia.ui.kit.Field;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.util.IO;
import com.disgusty.foxtalgia.util.Text;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebRequest;
import org.mozilla.geckoview.WebResponse;

/** Confirms and starts downloads. */
final class DownloadHelper {
    private DownloadHelper() {
    }

    static void confirm(final Activity a, final Kit k, final WebResponse r, final boolean isPrivate) {
        final I18n t = I18n.get();
        if (Prompts.needsStoragePermission(a)) {
            k.toast(t.text("downloads.needPermission"));
            IO.close(r.body);
            return;
        }
        final Field name = k.field(t.text("downloads.fileName"), false);
        name.text(Downloads.fileName(r));
        long len = Downloads.length(r);
        final boolean[] answered = new boolean[1];
        k.dialog().icon(Ic.DOWNLOAD).title(t.text("downloads.confirmTitle"))
                .message((len > 0 ? Text.fileSize(len) + " · " : "") + com.disgusty.foxtalgia.engine.Urls.displayHost(r.uri))
                .view(name.view)
                .negative(t.text("general.cancel"), new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        IO.close(r.body);
                    }
                })
                .positive(t.text("downloads.download"), new Runnable() {
                    @Override
                    public void run() {
                        answered[0] = true;
                        String n = name.text().trim();
                        Downloads.get().start(App.get(), r, isPrivate, n.length() > 0 ? n : null);
                        k.toast(t.text("downloads.started"));
                    }
                })
                .onDismiss(new Runnable() {
                    @Override
                    public void run() {
                        if (!answered[0]) IO.close(r.body);
                    }
                }).show();
    }

    /** Downloads a URL directly (context menu "Save image", "Download link"). */
    static void fetch(final Activity a, final Kit k, String url, final boolean isPrivate) {
        GeckoWebExecutor ex = new GeckoWebExecutor(Engine.runtime(a));
        ex.fetch(new WebRequest.Builder(url).build(), isPrivate ? GeckoWebExecutor.FETCH_FLAGS_PRIVATE : GeckoWebExecutor.FETCH_FLAGS_NONE)
                .accept(new GeckoResult.Consumer<WebResponse>() {
                    @Override
                    public void accept(WebResponse r) {
                        confirm(a, k, r, isPrivate);
                    }
                }, new GeckoResult.Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable e) {
                        k.toast(I18n.get().text("downloads.failed"));
                    }
                });
    }
}
