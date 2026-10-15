package com.disgusty.foxtalgia.ui.browser;

import android.graphics.Bitmap;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.disgusty.foxtalgia.data.Bookmarks;
import com.disgusty.foxtalgia.data.History;
import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.kit.Draw;
import com.disgusty.foxtalgia.ui.kit.Fonts;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.ui.kit.Row;

import java.util.List;

/** The native start page: logo, most visited sites and recent bookmarks. */
public final class HomeView {
    public interface Listener {
        void onOpen(String url, boolean newTab);

        void onSearchClicked();

        void onRemoveTopSite(String url);
    }

    private final Kit k;
    private final Listener listener;
    private final ScrollView root;
    private final LinearLayout content;
    private boolean isPrivate;

    public HomeView(Kit kit, Listener l) {
        k = kit;
        listener = l;
        content = k.vbox();
        content.setPadding(0, k.dp(24), 0, k.dp(24));
        root = k.scroll(content);
        root.setBackgroundColor(k.c.background);
    }

    public View view() {
        return root;
    }

    public void setPrivate(boolean p) {
        isPrivate = p;
    }

    public void refresh() {
        content.removeAllViews();
        I18n t = I18n.get();
        LinearLayout head = k.hbox();
        head.setGravity(Gravity.CENTER);
        ImageView logo = k.image(k.logo(k.dp(56)));
        head.addView(logo, new LinearLayout.LayoutParams(k.dp(56), k.dp(56)));
        TextView name = k.text(Kit.T_DISPLAY, isPrivate ? t.text("home.private") : "Foxtalgia");
        name.setPadding(k.dp(12), 0, 0, 0);
        head.addView(name);
        content.addView(head, Kit.matchWrap());
        if (isPrivate) {
            TextView info = k.text(Kit.T_BODY, t.text("home.privateInfo"));
            info.setPadding(k.dp(24), k.dp(16), k.dp(24), 0);
            info.setGravity(Gravity.CENTER);
            content.addView(info, Kit.matchWrap());
            return;
        }
        List<History.Entry> top = History.get().topSites(8);
        if (!top.isEmpty()) {
            content.addView(k.space(16));
            content.addView(k.sectionHeader(t.text("home.topSites")));
            content.addView(grid(top), Kit.matchWrap());
        }
        List<Bookmarks.Item> marks = Bookmarks.recent(6);
        if (!marks.isEmpty()) {
            content.addView(k.space(8));
            content.addView(k.sectionHeader(t.text("home.bookmarks")));
            for (final Bookmarks.Item b : marks) {
                Row r = k.row().title(TextUtils.isEmpty(b.title) ? b.url : b.title).summary(b.url).summaryLines(1)
                        .leading(icon(b.url, k.dp(32)))
                        .onClick(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                listener.onOpen(b.url, false);
                            }
                        });
                content.addView(r.build(), Kit.matchWrap());
            }
        }
        if (top.isEmpty() && marks.isEmpty()) {
            TextView hint = k.text(Kit.T_SECONDARY, t.text("home.empty"));
            hint.setGravity(Gravity.CENTER);
            hint.setPadding(k.dp(24), k.dp(32), k.dp(24), 0);
            content.addView(hint, Kit.matchWrap());
        }
    }

    private View grid(List<History.Entry> sites) {
        LinearLayout g = k.vbox();
        g.setPadding(k.dp(8), k.dp(8), k.dp(8), 0);
        LinearLayout row = null;
        int cols = 4;
        for (int i = 0; i < sites.size(); i++) {
            if (i % cols == 0) {
                row = k.hbox();
                row.setGravity(Gravity.TOP);
                g.addView(row, Kit.matchWrap());
            }
            row.addView(tile(sites.get(i)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        while (row != null && row.getChildCount() < cols) row.addView(new View(k.context), new LinearLayout.LayoutParams(0, 1, 1));
        return g;
    }

    private View tile(final History.Entry e) {
        LinearLayout box = k.vbox();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        box.setPadding(k.dp(4), k.dp(8), k.dp(4), k.dp(8));
        box.setBackgroundDrawable(k.listSelector());
        box.setClickable(true);
        int size = k.dp(48);
        View iconView = icon(e.url, size);
        FrameLayout frame = new FrameLayout(k.context);
        boolean md3 = Settings.STYLE_MD3.equals(k.style);
        boolean md1 = Settings.STYLE_MD1.equals(k.style);
        if (md3 || md1) {
            frame.setBackgroundDrawable(Draw.rect(md3 ? k.c.surfaceContainerHigh : k.c.surface, md3 ? k.dp(16) : k.dp(2)));
            if (md1) Draw.elevate(frame, k.dp(2), k.dp(2));
        }
        frame.setPadding(k.dp(10), k.dp(10), k.dp(10), k.dp(10));
        frame.addView(iconView, new FrameLayout.LayoutParams(size - k.dp(20), size - k.dp(20), Gravity.CENTER));
        box.addView(frame, new LinearLayout.LayoutParams(size + k.dp(8), size + k.dp(8)));
        TextView label = k.text(Kit.T_CAPTION, title(e));
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setGravity(Gravity.CENTER);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        label.setPadding(0, k.dp(6), 0, 0);
        box.addView(label, Kit.matchWrap());
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onOpen(e.url, false);
            }
        });
        box.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                listener.onRemoveTopSite(e.url);
                return true;
            }
        });
        return box;
    }

    private static String title(History.Entry e) {
        String h = Favicons.host(e.url);
        if (h == null) return e.url;
        return h.startsWith("www.") ? h.substring(4) : h;
    }

    /** An ImageView showing the letter tile, replaced by the favicon when it arrives. */
    public ImageView icon(String url, int sizePx) {
        final ImageView iv = new ImageView(k.context);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        boolean md3 = Settings.STYLE_MD3.equals(k.style);
        iv.setImageBitmap(Favicons.letter(k.context, url, sizePx, md3 ? sizePx / 2f : k.dp(3),
                Settings.STYLE_CLASSIC.equals(k.style) ? Fonts.classic(k.context, Fonts.BOLD) : Fonts.roboto(k.context, Fonts.MEDIUM)));
        final String tag = url;
        iv.setTag(tag);
        Favicons.load(url, new Favicons.Callback() {
            @Override
            public void onIcon(Bitmap icon) {
                if (tag.equals(iv.getTag())) iv.setImageBitmap(icon);
            }
        });
        return iv;
    }
}
