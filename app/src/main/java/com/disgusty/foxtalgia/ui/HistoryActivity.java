package com.disgusty.foxtalgia.ui;

import android.content.Intent;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.foxtalgia.data.History;
import com.disgusty.foxtalgia.ui.browser.HomeView;
import com.disgusty.foxtalgia.ui.kit.Action;
import com.disgusty.foxtalgia.ui.kit.DialogBuilder;
import com.disgusty.foxtalgia.ui.kit.Field;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** Browsing history grouped by day, with search and clearing. */
public final class HistoryActivity extends BaseActivity {
    private LinearLayout list;
    private Field search;
    private HomeView icons;

    @Override
    protected void build() {
        screen.appBar.title(s("history.title")).up(finisher());
        List<Action> actions = new ArrayList<Action>();
        actions.add(new Action(Ic.DELETE, s("history.clear"), new Runnable() {
            @Override
            public void run() {
                clearDialog();
            }
        }).always());
        screen.appBar.actions(actions);
        icons = new HomeView(k, null);
        LinearLayout box = k.vbox();
        search = k.field(null, false);
        search.edit.setHint(s("history.search"));
        search.edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                refresh();
            }
        });
        LinearLayout.LayoutParams sp = Kit.matchWrap();
        sp.setMargins(k.pagePadding(), k.dp(8), k.pagePadding(), k.dp(4));
        box.addView(search.view, sp);
        list = k.vbox();
        box.addView(list, Kit.matchWrap());
        screen.setContent(k.scroll(box));
    }

    @Override
    protected void refresh() {
        list.removeAllViews();
        List<History.Entry> entries = History.get().recent(search.text().trim(), 500);
        if (entries.isEmpty()) {
            TextView e = k.text(Kit.T_SECONDARY, s("history.empty"));
            e.setGravity(Gravity.CENTER);
            e.setPadding(k.dp(24), k.dp(48), k.dp(24), 0);
            list.addView(e, Kit.matchWrap());
            return;
        }
        String lastDay = null;
        boolean grouped = search.text().trim().length() == 0;
        for (final History.Entry h : entries) {
            if (grouped) {
                String day = dayLabel(h.last);
                if (!day.equals(lastDay)) {
                    list.addView(k.sectionHeader(day), Kit.matchWrap());
                    lastDay = day;
                }
            }
            View row = k.row().leading(icons.icon(h.url, k.dp(32))).title(TextUtils.isEmpty(h.title) ? h.url : h.title).summary(h.url).summaryLines(1)
                    .onClick(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            pick(h.url, false);
                        }
                    }).onLongClick(new View.OnLongClickListener() {
                        @Override
                        public boolean onLongClick(View v) {
                            List<Action> a = new ArrayList<Action>();
                            a.add(new Action(Ic.TAB, s("browser.openInNewTab"), new Runnable() {
                                @Override
                                public void run() {
                                    pick(h.url, true);
                                }
                            }));
                            a.add(new Action(Ic.DELETE, s("history.delete"), new Runnable() {
                                @Override
                                public void run() {
                                    History.get().delete(h.url);
                                    list.postDelayed(new Runnable() {
                                        @Override
                                        public void run() {
                                            refresh();
                                        }
                                    }, 200);
                                }
                            }));
                            k.menu(v, h.title, a);
                            return true;
                        }
                    }).build();
            list.addView(row, Kit.matchWrap());
        }
    }

    private String dayLabel(long time) {
        if (DateUtils.isToday(time)) return s("history.today");
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -1);
        Calendar d = Calendar.getInstance();
        d.setTimeInMillis(time);
        if (c.get(Calendar.YEAR) == d.get(Calendar.YEAR) && c.get(Calendar.DAY_OF_YEAR) == d.get(Calendar.DAY_OF_YEAR)) return s("history.yesterday");
        return DateUtils.formatDateTime(this, time, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_WEEKDAY);
    }

    private void pick(String url, boolean newTab) {
        Intent r = new Intent();
        r.putExtra("url", url);
        r.putExtra("newTab", newTab);
        setResult(RESULT_OK, r);
        finish();
    }

    private void clearDialog() {
        CharSequence[] ranges = {s("history.lastHour"), s("history.today"), s("history.everything")};
        k.dialog().title(s("history.clear")).items(ranges, new DialogBuilder.ItemListener() {
            @Override
            public void onItem(int index) {
                long since = 0;
                if (index == 0) since = System.currentTimeMillis() - DateUtils.HOUR_IN_MILLIS;
                else if (index == 1) {
                    Calendar c = Calendar.getInstance();
                    c.set(Calendar.HOUR_OF_DAY, 0);
                    c.set(Calendar.MINUTE, 0);
                    c.set(Calendar.SECOND, 0);
                    since = c.getTimeInMillis();
                }
                History.get().clear(since);
                list.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        refresh();
                    }
                }, 200);
            }
        }).show();
    }
}
