package com.disgusty.foxtalgia.ui;

import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.foxtalgia.engine.Downloads;
import com.disgusty.foxtalgia.files.MimeTypes;
import com.disgusty.foxtalgia.ui.kit.Action;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.util.Text;

import java.util.ArrayList;
import java.util.List;

/** Downloads list with progress, open, cancel and remove. */
public final class DownloadsActivity extends BaseActivity implements Downloads.Listener {
    private LinearLayout list;

    @Override
    protected void build() {
        screen.appBar.title(s("downloads.title")).up(finisher());
        list = k.vbox();
        screen.setContent(k.scroll(list));
    }

    @Override
    protected void onResume() {
        super.onResume();
        Downloads.get().addListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        Downloads.get().removeListener(this);
    }

    @Override
    public void onDownloadChanged(Downloads.Item item) {
        refresh();
    }

    private Ic icon(Downloads.Item d) {
        String m = d.mime == null ? MimeTypes.fromName(d.name == null ? "" : d.name) : d.mime;
        if (m.startsWith("image/")) return Ic.IMAGE;
        if (m.startsWith("video/")) return Ic.MOVIE;
        if (m.startsWith("audio/")) return Ic.AUDIOTRACK;
        if (m.equals("application/pdf")) return Ic.PICTURE_AS_PDF;
        if (m.equals("application/vnd.android.package-archive")) return Ic.ANDROID;
        if (m.contains("zip") || m.contains("compressed") || m.contains("tar")) return Ic.ARCHIVE;
        return Ic.INSERT_DRIVE_FILE;
    }

    @Override
    protected void refresh() {
        list.removeAllViews();
        List<Downloads.Item> items = Downloads.list(this);
        if (items.isEmpty()) {
            TextView e = k.text(Kit.T_SECONDARY, s("downloads.empty"));
            e.setGravity(Gravity.CENTER);
            e.setPadding(k.dp(24), k.dp(48), k.dp(24), 0);
            list.addView(e, Kit.matchWrap());
            return;
        }
        for (final Downloads.Item d : items) {
            String summary;
            if (d.status == Downloads.RUNNING) summary = Text.fileSize(d.received) + (d.size > 0 ? " / " + Text.fileSize(d.size) : "");
            else if (d.status == Downloads.DONE) summary = Text.fileSize(d.size > 0 ? d.size : d.received);
            else summary = s(d.status == Downloads.CANCELED ? "downloads.canceled" : "downloads.failed");
            View row = k.row().icon(icon(d)).title(d.name).summary(summary)
                    .onClick(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            if (d.status != Downloads.DONE) return;
                            try {
                                startActivity(Downloads.viewIntent(DownloadsActivity.this, d));
                            } catch (Exception e) {
                                k.toast(s("errors.noApp"));
                            }
                        }
                    }).onLongClick(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            List<Action> a = new ArrayList<Action>();
                            if (d.status == Downloads.RUNNING) {
                                a.add(new Action(Ic.CANCEL, s("downloads.cancel"), new Runnable() {
                                    @Override
                                    public void run() {
                                        Downloads.get().cancel(d.id);
                                    }
                                }));
                            } else {
                                a.add(new Action(Ic.DELETE, s("downloads.remove"), new Runnable() {
                                    @Override
                                    public void run() {
                                        Downloads.remove(DownloadsActivity.this, d.id);
                                        refresh();
                                    }
                                }));
                            }
                            k.menu(v, d.name, a);
                            return true;
                        }
                    }).build();
            list.addView(row, Kit.matchWrap());
            if (d.status == Downloads.RUNNING) {
                com.disgusty.foxtalgia.ui.kit.Progress p = k.progress(d.size <= 0);
                if (d.size > 0) p.setProgress(d.received / (float) d.size);
                LinearLayout.LayoutParams lp = Kit.matchWrap();
                lp.setMargins(k.pagePadding(), 0, k.pagePadding(), k.dp(8));
                list.addView(p.view(), lp);
            }
        }
    }
}
