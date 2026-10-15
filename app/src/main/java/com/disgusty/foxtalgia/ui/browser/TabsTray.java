package com.disgusty.foxtalgia.ui.browser;

import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.engine.Tab;
import com.disgusty.foxtalgia.engine.Tabs;
import com.disgusty.foxtalgia.engine.Urls;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.kit.Action;
import com.disgusty.foxtalgia.ui.kit.AppBar;
import com.disgusty.foxtalgia.ui.kit.Draw;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.ui.kit.NavBar;
import com.disgusty.foxtalgia.ui.kit.Screen;

import java.util.ArrayList;
import java.util.List;

/** Tab switcher: a list (Classic, Holo) or a grid of cards (Material) with normal and private tabs. */
public final class TabsTray {
    public interface Listener {
        void onTabPicked(Tab tab);

        void onNewTab(boolean isPrivate);

        void onClosed();
    }

    private final Kit k;
    private final Listener listener;
    private final Screen screen;
    private final LinearLayout list;
    private final HomeView icons;
    private boolean showPrivate;

    public TabsTray(Kit kit, HomeView iconSource, Listener l) {
        k = kit;
        listener = l;
        icons = iconSource;
        final I18n t = I18n.get();
        screen = k.screen();
        AppBar bar = screen.appBar;
        bar.title(t.text("browser.tabs")).up(new Runnable() {
            @Override
            public void run() {
                listener.onClosed();
            }
        });
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action(Ic.ADD, t.text("browser.newTab"), new Runnable() {
            @Override
            public void run() {
                listener.onNewTab(showPrivate);
            }
        }).always());
        actions.add(new Action(Ic.CLEAR_ALL, t.text("browser.closeAllTabs"), new Runnable() {
            @Override
            public void run() {
                Tabs.get().closeAll(showPrivate);
                refresh();
            }
        }));
        bar.actions(actions);
        NavBar nav = k.navBar(new String[]{t.text("browser.normalTabs"), t.text("browser.privateTabs")}, new Ic[]{Ic.TAB, Ic.PRIVATE},
                new NavBar.Listener() {
                    @Override
                    public void onSelected(int index) {
                        showPrivate = index == 1;
                        refresh();
                    }
                });
        screen.setNav(nav);
        list = k.vbox();
        list.setPadding(k.dp(grid() ? 8 : 0), k.dp(grid() ? 8 : 0), k.dp(grid() ? 8 : 0), k.dp(8));
        screen.setContent(k.scroll(list));
    }

    private boolean grid() {
        return Settings.STYLE_MD1.equals(k.style) || Settings.STYLE_MD3.equals(k.style);
    }

    public View view() {
        return screen.root;
    }

    public Screen screen() {
        return screen;
    }

    public void show(boolean isPrivate) {
        showPrivate = isPrivate;
        screen.nav().select(isPrivate ? 1 : 0);
        refresh();
    }

    public void refresh() {
        list.removeAllViews();
        List<Tab> tabs = Tabs.get().list(showPrivate);
        if (tabs.isEmpty()) {
            TextView empty = k.text(Kit.T_SECONDARY, I18n.get().text(showPrivate ? "browser.noPrivateTabs" : "browser.noTabs"));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(k.dp(24), k.dp(48), k.dp(24), k.dp(24));
            list.addView(empty, Kit.matchWrap());
            return;
        }
        if (!grid()) {
            for (Tab t : tabs) {
                list.addView(row(t), Kit.matchWrap());
                list.addView(k.divider());
            }
            return;
        }
        LinearLayout row = null;
        for (int i = 0; i < tabs.size(); i++) {
            if (i % 2 == 0) {
                row = k.hbox();
                row.setGravity(Gravity.TOP);
                list.addView(row, Kit.matchWrap());
            }
            row.addView(card(tabs.get(i)), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        if (row != null && row.getChildCount() == 1) row.addView(new View(k.context), new LinearLayout.LayoutParams(0, 1, 1));
    }

    private String title(Tab t) {
        if (t.isHome()) return I18n.get().text("browser.homeTab");
        return TextUtils.isEmpty(t.title) ? Urls.display(t.url) : t.title;
    }

    private View thumbnail(Tab t, int w, int h) {
        ImageView iv = new ImageView(k.context);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (t.thumbnail != null) {
            iv.setImageBitmap(t.thumbnail);
        } else {
            iv.setBackgroundColor(k.c.surfaceContainerHighest);
            iv.setScaleType(ImageView.ScaleType.CENTER);
            iv.setImageDrawable(t.isHome() ? k.logo(k.dp(40)) : k.icon(Ic.PUBLIC, k.iconColor(), 32));
        }
        iv.setLayoutParams(new LinearLayout.LayoutParams(w, h));
        return iv;
    }

    private View closeButton(final Tab t) {
        return k.iconButton(Ic.CLOSE, I18n.get().text("browser.closeTab"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Tabs.get().close(t);
                refresh();
            }
        });
    }

    /** Classic / Holo: thumbnail on the left, title and address, close button. */
    private View row(final Tab t) {
        boolean current = t == Tabs.get().current();
        View thumb = thumbnail(t, k.dp(96), k.dp(64));
        View r = k.row().leading(thumb).title(title(t)).summary(t.isHome() ? null : Urls.display(t.url)).summaryLines(1)
                .trailing(closeButton(t))
                .onClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onTabPicked(t);
                    }
                }).build();
        // Row uses square icons; the thumbnail keeps its 3:2 size.
        thumb.setLayoutParams(new LinearLayout.LayoutParams(k.dp(96), k.dp(64)));
        if (current) r.setBackgroundDrawable(Draw.states(k.listSelector(), null, null, null, Draw.rect(k.c.highlight & 0x55FFFFFF, 0)));
        return r;
    }

    /** Material: a card with title bar and thumbnail; the current tab is outlined in the accent color. */
    private View card(final Tab t) {
        boolean md3 = Settings.STYLE_MD3.equals(k.style);
        boolean current = t == Tabs.get().current();
        float radius = k.dp(md3 ? 16 : 2);
        LinearLayout card = k.vbox();
        int bg = md3 ? k.c.surfaceContainerHigh : k.c.surface;
        card.setBackgroundDrawable(current ? Draw.stroke(bg, k.c.primary, k.dp(md3 ? 3 : 2), radius) : Draw.rect(bg, radius));
        if (!md3) Draw.elevate(card, k.dp(current ? 6 : 2), radius);
        int pad = k.dp(md3 ? 6 : 0);
        card.setPadding(pad, 0, pad, pad);
        LinearLayout head = k.hbox();
        head.setPadding(k.dp(8), 0, 0, 0);
        head.addView(icons.icon(t.url, k.dp(16)), new LinearLayout.LayoutParams(k.dp(16), k.dp(16)));
        TextView title = k.text(Kit.T_LABEL, title(t));
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        title.setPadding(k.dp(6), 0, 0, 0);
        head.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        head.addView(closeButton(t));
        card.addView(head, Kit.matchWrap());
        FrameLayout thumbBox = new FrameLayout(k.context);
        thumbBox.addView(thumbnail(t, ViewGroup.LayoutParams.MATCH_PARENT, k.dp(150)), new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, k.dp(150)));
        card.addView(thumbBox, Kit.matchWrap());
        card.setClickable(true);
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onTabPicked(t);
            }
        });
        FrameLayout wrap = new FrameLayout(k.context);
        wrap.setPadding(k.dp(6), k.dp(6), k.dp(6), k.dp(6));
        wrap.addView(card, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return wrap;
    }
}
