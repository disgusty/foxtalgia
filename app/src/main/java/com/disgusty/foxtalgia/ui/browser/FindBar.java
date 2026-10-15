package com.disgusty.foxtalgia.ui.browser;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.engine.Tab;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.kit.Draw;
import com.disgusty.foxtalgia.ui.kit.Field;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;

import org.mozilla.geckoview.GeckoResult;
import org.mozilla.geckoview.GeckoSession;

/** Find in page: field, "3/12" counter, previous / next / close. */
public final class FindBar {
    private final Kit k;
    private final LinearLayout root;
    private final EditText edit;
    private final TextView count;
    private Tab tab;

    public FindBar(Kit kit) {
        k = kit;
        root = k.hbox();
        boolean md = Settings.STYLE_MD1.equals(k.style) || Settings.STYLE_MD3.equals(k.style);
        root.setBackgroundColor(md ? k.c.surfaceContainer : k.c.surfaceContainerHigh);
        if (md) Draw.elevate(root, k.dp(4), 0);
        root.setPadding(k.dp(8), k.dp(2), k.dp(4), k.dp(2));
        Field f = k.field(null, false);
        edit = f.edit;
        edit.setHint(I18n.get().text("browser.findHint"));
        edit.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int c, int a) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                find(GeckoSession.FINDER_FIND_FORWARD, true);
            }
        });
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                find(GeckoSession.FINDER_FIND_FORWARD, false);
                return true;
            }
        });
        root.addView(f.view, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        count = k.text(Kit.T_CAPTION, "");
        count.setPadding(k.dp(8), 0, k.dp(4), 0);
        root.addView(count);
        root.addView(k.iconButton(Ic.KEYBOARD_ARROW_UP, I18n.get().text("browser.findPrevious"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                find(GeckoSession.FINDER_FIND_BACKWARDS, false);
            }
        }));
        root.addView(k.iconButton(Ic.KEYBOARD_ARROW_DOWN, I18n.get().text("browser.findNext"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                find(GeckoSession.FINDER_FIND_FORWARD, false);
            }
        }));
        root.addView(k.iconButton(Ic.CLOSE, I18n.get().text("general.close"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                hide();
            }
        }));
    }

    public View view() {
        return root;
    }

    public void show(Tab t) {
        tab = t;
        root.setVisibility(View.VISIBLE);
        edit.requestFocus();
        InputMethodManager imm = (InputMethodManager) k.context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
    }

    public void hide() {
        if (root.getVisibility() != View.VISIBLE) return;
        root.setVisibility(View.GONE);
        InputMethodManager imm = (InputMethodManager) k.context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
        if (tab != null && tab.session != null) tab.session.getFinder().clear();
        edit.setText("");
        count.setText("");
    }

    private void find(int flags, boolean restart) {
        if (tab == null || tab.session == null) return;
        String q = edit.getText().toString();
        if (q.length() == 0) {
            tab.session.getFinder().clear();
            count.setText("");
            return;
        }
        tab.session.getFinder().setDisplayFlags(GeckoSession.FINDER_DISPLAY_HIGHLIGHT_ALL);
        tab.session.getFinder().find(restart ? q : null, flags).accept(new GeckoResult.Consumer<GeckoSession.FinderResult>() {
            @Override
            public void accept(GeckoSession.FinderResult r) {
                if (r == null) return;
                count.setText(r.found ? r.current + "/" + r.total : "0/0");
            }
        });
    }
}
