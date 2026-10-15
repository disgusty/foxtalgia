package com.disgusty.foxtalgia.ui;

import android.content.Intent;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.foxtalgia.data.Bookmarks;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.browser.HomeView;
import com.disgusty.foxtalgia.ui.kit.Action;
import com.disgusty.foxtalgia.ui.kit.DialogBuilder;
import com.disgusty.foxtalgia.ui.kit.Field;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;

import java.util.ArrayList;
import java.util.List;

/** Bookmarks with folders. Picking one returns its URL to the browser. */
public final class BookmarksActivity extends BaseActivity {
    private long folder;
    private LinearLayout list;
    private HomeView icons;

    @Override
    protected void build() {
        folder = getIntent().getLongExtra("folder", 0);
        Bookmarks.Item f = folder == 0 ? null : Bookmarks.byId(folder);
        screen.appBar.title(f == null ? s("bookmarks.title") : f.title).up(finisher());
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action(Ic.CREATE_NEW_FOLDER, s("bookmarks.newFolder"), new Runnable() {
            @Override
            public void run() {
                folderDialog(null);
            }
        }).always());
        screen.appBar.actions(actions);
        icons = new HomeView(k, null);
        list = k.vbox();
        screen.setContent(k.scroll(list));
    }

    @Override
    protected void refresh() {
        list.removeAllViews();
        List<Bookmarks.Item> items = Bookmarks.children(folder);
        if (items.isEmpty()) {
            TextView e = k.text(Kit.T_SECONDARY, s("bookmarks.empty"));
            e.setGravity(Gravity.CENTER);
            e.setPadding(k.dp(24), k.dp(48), k.dp(24), 0);
            list.addView(e, Kit.matchWrap());
            return;
        }
        for (final Bookmarks.Item b : items) {
            View row;
            if (b.folder) {
                row = k.row().icon(Ic.FOLDER).title(b.title).onClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent i = new Intent(BookmarksActivity.this, BookmarksActivity.class);
                        i.putExtra("folder", b.id);
                        startActivityForResult(i, 1);
                    }
                }).onLongClick(new View.OnLongClickListener() {
                    @Override
                    public boolean onLongClick(View v) {
                        itemMenu(v, b);
                        return true;
                    }
                }).build();
            } else {
                row = k.row().leading(icons.icon(b.url, k.dp(32))).title(TextUtils.isEmpty(b.title) ? b.url : b.title).summary(b.url).summaryLines(1)
                        .onClick(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                pick(b.url, false);
                            }
                        }).onLongClick(new View.OnLongClickListener() {
                            @Override
                            public boolean onLongClick(View v) {
                                itemMenu(v, b);
                                return true;
                            }
                        }).build();
            }
            list.addView(row, Kit.matchWrap());
        }
    }

    private void pick(String url, boolean newTab) {
        Intent r = new Intent();
        r.putExtra("url", url);
        r.putExtra("newTab", newTab);
        setResult(RESULT_OK, r);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK && data != null) {
            setResult(RESULT_OK, data);
            finish();
        }
    }

    private void itemMenu(View anchor, final Bookmarks.Item b) {
        List<Action> a = new ArrayList<Action>();
        if (!b.folder) {
            a.add(new Action(Ic.TAB, s("browser.openInNewTab"), new Runnable() {
                @Override
                public void run() {
                    pick(b.url, true);
                }
            }));
        }
        a.add(new Action(Ic.EDIT, s("bookmarks.edit"), new Runnable() {
            @Override
            public void run() {
                if (b.folder) folderDialog(b);
                else edit(BookmarksActivity.this, k, b, new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                });
            }
        }));
        a.add(new Action(Ic.DELETE, s("bookmarks.delete"), new Runnable() {
            @Override
            public void run() {
                Bookmarks.delete(b.id);
                refresh();
            }
        }));
        k.menu(anchor, b.title, a);
    }

    private void folderDialog(final Bookmarks.Item existing) {
        final Field name = k.field(s("bookmarks.folderName"), false);
        if (existing != null) name.text(existing.title);
        k.dialog().title(s(existing == null ? "bookmarks.newFolder" : "bookmarks.edit")).view(name.view)
                .negative(s("general.cancel"), null)
                .positive(s("general.save"), new Runnable() {
                    @Override
                    public void run() {
                        String n = name.text().trim();
                        if (n.length() == 0) return;
                        if (existing == null) Bookmarks.addFolder(n, folder);
                        else Bookmarks.update(existing.id, null, n, existing.parent);
                        refresh();
                    }
                }).show();
    }

    /** From the browser menu: edits the bookmark for {@code url}, or adds one (with a confirmation toast). */
    static void editOrAdd(BaseActivity a, Kit k, String url, String title) {
        Bookmarks.Item b = Bookmarks.find(url);
        if (b == null) {
            Bookmarks.add(url, TextUtils.isEmpty(title) ? url : title, 0);
            k.toast(I18n.get().text("bookmarks.added"));
            return;
        }
        edit(a, k, b, null);
    }

    static void edit(BaseActivity a, final Kit k, final Bookmarks.Item b, final Runnable done) {
        final I18n t = I18n.get();
        LinearLayout box = k.vbox();
        final Field title = k.field(t.text("bookmarks.name"), false);
        title.text(b.title == null ? "" : b.title);
        final Field url = k.field(t.text("bookmarks.address"), false);
        url.text(b.url);
        box.addView(title.view, Kit.matchWrap());
        box.addView(url.view, Kit.matchWrap());
        final List<Bookmarks.Item> folders = new ArrayList<Bookmarks.Item>();
        collectFolders(0, folders);
        k.dialog().title(t.text("bookmarks.edit")).view(box)
                .neutral(t.text("bookmarks.delete"), new Runnable() {
                    @Override
                    public void run() {
                        Bookmarks.delete(b.id);
                        if (done != null) done.run();
                    }
                })
                .negative(t.text("bookmarks.moveTo"), new Runnable() {
                    @Override
                    public void run() {
                        CharSequence[] names = new CharSequence[folders.size() + 1];
                        names[0] = t.text("bookmarks.title");
                        int sel = 0;
                        for (int i = 0; i < folders.size(); i++) {
                            names[i + 1] = folders.get(i).title;
                            if (folders.get(i).id == b.parent) sel = i + 1;
                        }
                        k.dialog().title(t.text("bookmarks.moveTo")).singleChoice(names, sel, new DialogBuilder.ItemListener() {
                            @Override
                            public void onItem(int index) {
                                Bookmarks.update(b.id, b.url, b.title, index == 0 ? 0 : folders.get(index - 1).id);
                                if (done != null) done.run();
                            }
                        }).show();
                    }
                })
                .positive(t.text("general.save"), new Runnable() {
                    @Override
                    public void run() {
                        Bookmarks.update(b.id, url.text().trim(), title.text().trim(), b.parent);
                        if (done != null) done.run();
                    }
                }).show();
    }

    private static void collectFolders(long parent, List<Bookmarks.Item> out) {
        for (Bookmarks.Item i : Bookmarks.children(parent)) {
            if (!i.folder) continue;
            out.add(i);
            collectFolders(i.id, out);
        }
    }
}
