package com.disgusty.foxtalgia.ui.browser;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.data.Bookmarks;
import com.disgusty.foxtalgia.data.History;
import com.disgusty.foxtalgia.engine.Engine;
import com.disgusty.foxtalgia.engine.Urls;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.util.IO;
import com.disgusty.foxtalgia.util.Text;

import org.json.JSONArray;
import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoWebExecutor;
import org.mozilla.geckoview.WebRequest;
import org.mozilla.geckoview.WebResponse;

import java.util.ArrayList;
import java.util.List;

/** The list under the address bar while typing: search suggestions, history and bookmark matches. */
public final class Suggestions {
    public interface Listener {
        void onPick(String urlOrQuery, boolean isUrl);

        void onFill(String text);
    }

    private final Kit k;
    private final Listener listener;
    private final ScrollView root;
    private final LinearLayout list;
    private final Handler main = new Handler(Looper.getMainLooper());
    private GeckoWebExecutor executor;
    private String query = "";
    private List<String> remote = new ArrayList<String>();
    private boolean isPrivate;
    private final Runnable fetchRemote = new Runnable() {
        @Override
        public void run() {
            fetch(query);
        }
    };

    public Suggestions(Kit kit, Listener l) {
        k = kit;
        listener = l;
        list = k.vbox();
        root = k.scroll(list);
        root.setBackgroundColor(k.c.background);
    }

    public View view() {
        return root;
    }

    public void setPrivate(boolean p) {
        isPrivate = p;
    }

    public void update(String q) {
        query = q == null ? "" : q.trim();
        remote = new ArrayList<String>();
        render();
        main.removeCallbacks(fetchRemote);
        if (query.length() > 0 && !isPrivate && App.get().settings.searchSuggestions()) main.postDelayed(fetchRemote, 180);
    }

    private void render() {
        list.removeAllViews();
        if (query.length() == 0) return;
        list.addView(searchRow(query, true), Kit.matchWrap());
        for (String s : remote) {
            if (s.equalsIgnoreCase(query)) continue;
            list.addView(searchRow(s, false), Kit.matchWrap());
        }
        List<String> seen = new ArrayList<String>();
        for (Bookmarks.Item b : Bookmarks.search(query, 4)) {
            seen.add(b.url);
            list.addView(urlRow(Ic.BOOKMARK, b.title, b.url), Kit.matchWrap());
        }
        for (History.Entry e : History.get().recent(query, 6)) {
            if (seen.contains(e.url)) continue;
            list.addView(urlRow(Ic.HISTORY, e.title, e.url), Kit.matchWrap());
        }
    }

    private View searchRow(final String text, boolean first) {
        String engine = Urls.engine()[1];
        boolean looksLikeUrl = first && !Urls.fromInput(text).equals(Urls.search(text));
        final View fill = k.iconButton(Ic.ARROW_UPWARD, null, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onFill(text);
            }
        });
        return k.row().icon(looksLikeUrl ? Ic.PUBLIC : Ic.SEARCH).title(text)
                .summary(looksLikeUrl ? null : first && engine != null ? com.disgusty.foxtalgia.i18n.I18n.get().text("browser.searchWith", "engine", engine) : null)
                .trailing(first ? null : fill)
                .onClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onPick(text, false);
                    }
                }).build();
    }

    private View urlRow(Ic icon, String title, final String url) {
        return k.row().icon(icon).title(TextUtils.isEmpty(title) ? url : title).summary(url).summaryLines(1)
                .onClick(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onPick(url, true);
                    }
                }).build();
    }

    private void fetch(final String q) {
        String url = Urls.suggestUrl(q);
        if (url == null) return;
        if (executor == null) executor = new GeckoWebExecutor(Engine.runtime(App.get()));
        executor.fetch(new WebRequest.Builder(url).build(), GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS).accept(new GeckoResult.Consumer<WebResponse>() {
            @Override
            public void accept(final WebResponse r) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        final List<String> out = new ArrayList<String>();
                        try {
                            if (r.body != null) {
                                String body = Text.utf8(IO.readLimited(r.body, 256 * 1024));
                                JSONArray arr = new JSONArray(body);
                                JSONArray items = arr.optJSONArray(1);
                                if (items != null) for (int i = 0; i < items.length() && out.size() < 5; i++) out.add(items.optString(i));
                            }
                        } catch (Exception ignored) {
                            // no suggestions
                        } finally {
                            IO.close(r.body);
                        }
                        main.post(new Runnable() {
                            @Override
                            public void run() {
                                if (!q.equals(query)) return;
                                remote = out;
                                render();
                            }
                        });
                    }
                }).start();
            }
        });
    }
}
