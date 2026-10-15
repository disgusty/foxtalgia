package com.disgusty.foxtalgia.ui.browser;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.foxtalgia.R;
import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.engine.Tab;
import com.disgusty.foxtalgia.engine.Urls;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.kit.Draw;
import com.disgusty.foxtalgia.ui.kit.Fonts;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.ui.kit.Progress;
import com.disgusty.foxtalgia.ui.kit.Recolor;
import com.disgusty.foxtalgia.ui.kit.Scheme;

/**
 * The browser toolbar in the look of each era:
 * Classic - the Android 2.3 Browser title bar (title_bar_portrait, search text field, lock icons);
 * Holo - the Android 4.x Browser action bar with an underlined Holo text field;
 * Material 1 - a 56dp colored toolbar with a translucent 2dp field (Chrome / Lollipop era);
 * Material 3 - a surface toolbar with a pill-shaped field (Material You browsers).
 */
public final class AddressBar {
    public interface Listener {
        void onSubmit(String text);

        void onQueryChanged(String text);

        void onEditingChanged(boolean editing);

        void onTabsClicked();

        void onMenuClicked(View anchor);

        void onSiteClicked(View anchor);

        void onReloadClicked();
    }

    private final Kit k;
    private final Scheme c;
    private final Context context;
    private final Listener listener;
    private final String style;
    private final LinearLayout root;
    private final LinearLayout bar;
    private final LinearLayout field;
    private final ImageView siteIcon;
    private final EditText edit;
    private final ImageView action;
    private final TabCountDrawable tabCount;
    private final View tabsButton;
    private final View menuButton;
    private final Progress progress;
    private final FrameLayout progressBox;
    private final int fg;
    private final int fgDim;
    private Tab tab;
    private boolean editing;
    private boolean ignoreText;
    /** Which icon each button currently shows, so repeated updates don't rebuild the drawables. */
    private int siteIconKey;
    private int actionKey;

    public AddressBar(Kit kit, Listener l) {
        k = kit;
        c = kit.c;
        context = kit.context;
        listener = l;
        style = kit.style;
        boolean classic = Settings.STYLE_CLASSIC.equals(style);
        boolean holo = Settings.STYLE_HOLO.equals(style);
        boolean md1 = Settings.STYLE_MD1.equals(style);
        boolean md3 = Settings.STYLE_MD3.equals(style);

        root = k.vbox();
        bar = k.hbox();
        int height = k.dp(classic ? 52 : holo ? 48 : md1 ? 56 : 64);
        bar.setMinimumHeight(height);
        if (classic) {
            bar.setBackgroundDrawable(res(R.drawable.cl_title_bar_portrait));
            bar.setPadding(k.dp(4), k.dp(3), k.dp(2), k.dp(3));
            fg = 0xFF000000;
            fgDim = 0xFF606060;
        } else if (holo) {
            bar.setBackgroundDrawable(res(c.dark ? R.drawable.ho_ab_solid_dark_holo : R.drawable.ho_ab_solid_light_holo));
            bar.setPadding(k.dp(4), 0, 0, 0);
            fg = c.onSurface;
            fgDim = c.onSurfaceVariant;
        } else if (md1) {
            bar.setBackgroundColor(c.appBar);
            bar.setPadding(k.dp(8), 0, k.dp(4), 0);
            fg = c.onAppBar;
            fgDim = Scheme.withAlpha(c.onAppBar, 0.7f);
        } else {
            bar.setBackgroundColor(c.surface);
            bar.setPadding(k.dp(12), 0, k.dp(4), 0);
            fg = c.onSurface;
            fgDim = c.onSurfaceVariant;
        }

        field = k.hbox();
        if (classic) {
            field.setBackgroundDrawable(Draw.states(res(R.drawable.cl_textfield_search_pressed), res(R.drawable.cl_textfield_search_selected), null, null,
                    res(R.drawable.cl_textfield_search_default)));
        } else if (holo) {
            field.setBackgroundDrawable(Draw.states(null, res(c.dark ? R.drawable.ho_textfield_search_selected_holo_dark : R.drawable.ho_textfield_search_selected_holo_light),
                    null, null, res(c.dark ? R.drawable.ho_textfield_search_default_holo_dark : R.drawable.ho_textfield_search_default_holo_light)));
        } else if (md1) {
            field.setBackgroundDrawable(Draw.rect(Scheme.withAlpha(c.onAppBar, 0.18f), k.dp(2)));
            field.setPadding(k.dp(4), 0, k.dp(2), 0);
        } else {
            field.setBackgroundDrawable(Draw.rect(c.surfaceContainerHighest, k.dp(24)));
            field.setPadding(k.dp(8), 0, k.dp(4), 0);
        }
        field.setMinimumHeight(k.dp(md3 ? 48 : md1 ? 40 : 40));

        siteIcon = new ImageView(context);
        siteIcon.setScaleType(ImageView.ScaleType.CENTER);
        siteIcon.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onSiteClicked(v);
            }
        });
        field.addView(siteIcon, new LinearLayout.LayoutParams(k.dp(36), k.dp(36)));

        edit = new EditText(context);
        edit.setBackgroundDrawable(null);
        edit.setPadding(k.dp(2), 0, k.dp(2), 0);
        edit.setSingleLine(true);
        edit.setEllipsize(TextUtils.TruncateAt.END);
        edit.setTextSize(TypedValue.COMPLEX_UNIT_SP, classic ? 16 : md3 ? 16 : 16);
        edit.setTypeface(classic ? Fonts.classic(context, Fonts.REGULAR) : Fonts.roboto(context, Fonts.REGULAR));
        edit.setTextColor(fg);
        edit.setHintTextColor(fgDim);
        edit.setHint(I18n.get().text("browser.addressHint"));
        edit.setSelectAllOnFocus(true);
        edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        edit.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        edit.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_GO || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_DOWN)) {
                    listener.onSubmit(edit.getText().toString());
                    return true;
                }
                return false;
            }
        });
        edit.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                setEditing(hasFocus);
            }
        });
        edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (!ignoreText && editing) listener.onQueryChanged(s.toString());
                updateAction();
            }
        });
        field.addView(edit, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        action = new ImageView(context);
        action.setScaleType(ImageView.ScaleType.CENTER);
        action.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (editing) edit.setText("");
                else listener.onReloadClicked();
            }
        });
        field.addView(action, new LinearLayout.LayoutParams(k.dp(36), k.dp(36)));

        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        fp.gravity = Gravity.CENTER_VERTICAL;
        bar.addView(field, fp);

        int iconColor = md1 ? c.onAppBar : classic ? 0xFFEEEEEE : k.iconColor();
        tabCount = new TabCountDrawable(k.dp(classic ? 28 : 24), k.dp(md3 ? 5 : classic ? 2 : 3), k.dp(classic ? 2 : 2), iconColor,
                classic ? Fonts.classic(context, Fonts.BOLD) : Fonts.roboto(context, Fonts.BOLD));
        tabsButton = k.barButton(tabCount, I18n.get().text("browser.tabs"), iconColor, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onTabsClicked();
            }
        });
        bar.addView(tabsButton);

        Drawable more;
        if (holo) more = res(c.dark ? R.drawable.ho_ic_menu_moreoverflow_normal_holo_dark : R.drawable.ho_ic_menu_moreoverflow_normal_holo_light);
        else if (classic) more = res(R.drawable.cl_ic_menu_more);
        else more = k.icon(Ic.MORE_VERT, iconColor, 24);
        final View[] anchor = new View[1];
        menuButton = k.barButton(more, I18n.get().text("browser.menu"), iconColor, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onMenuClicked(anchor[0]);
            }
        });
        anchor[0] = menuButton;
        if (classic) {
            ((ImageView) menuButton).setScaleType(ImageView.ScaleType.FIT_CENTER);
            menuButton.setPadding(k.dp(10), k.dp(10), k.dp(10), k.dp(10));
        }
        bar.addView(menuButton);

        root.addView(bar, Kit.matchWrap());
        progress = k.progress(false);
        progressBox = new FrameLayout(context);
        View pv = progress.view();
        int ph = classic ? k.dp(6) : holo ? k.dp(4) : k.dp(md3 ? 4 : 4);
        progressBox.addView(pv, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ph));
        if (md1 || md3) progressBox.setBackgroundColor(md1 ? c.appBar : c.surface);
        root.addView(progressBox, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ph));
        if (md1) Draw.elevate(root, k.dp(4), 0);
        updateAction();
    }

    private Drawable res(int id) {
        return Recolor.drawable(context.getResources(), id, c);
    }

    public View view() {
        return root;
    }

    public View menuAnchor() {
        return menuButton;
    }

    public boolean editing() {
        return editing;
    }

    public void setTabCount(int n) {
        tabCount.setCount(n);
    }

    /** Shows the tab's address, security state and progress. */
    public void show(Tab t) {
        tab = t;
        if (!editing) {
            ignoreText = true;
            String terms = t == null ? null : Urls.searchTerms(t.url);
            String text = t == null || t.isHome() ? "" : terms != null ? terms : Urls.display(t.url);
            // show() runs for every progress, title and blocked-tracker event; setText relayouts the bar.
            if (!text.contentEquals(edit.getText())) edit.setText(text);
            ignoreText = false;
        }
        updateSiteIcon();
        updateAction();
        setProgress(t);
    }

    public void setProgress(Tab t) {
        boolean show = t != null && t.loading && t.progress < 100;
        progress.view().setVisibility(show ? View.VISIBLE : View.INVISIBLE);
        if (show) progress.setProgress(Math.max(0.05f, t.progress / 100f));
    }

    private void updateSiteIcon() {
        boolean classic = Settings.STYLE_CLASSIC.equals(style);
        int key;
        if (editing || tab == null || tab.isHome()) key = 1;
        else if (tab.isPrivate) key = 2;
        else if (tab.securityKnown && tab.secure) key = 3;
        else if (tab.url.startsWith("http:")) key = 4;
        else key = 5;
        if (key == siteIconKey) return;
        siteIconKey = key;
        Drawable d;
        int color = fgDim;
        if (key == 1) {
            d = classic ? res(R.drawable.cl_ic_btn_search) : k.icon(Ic.SEARCH, color, 20);
        } else if (key == 2) {
            d = k.icon(Ic.PRIVATE, color, 20);
        } else if (key == 3) {
            d = classic ? res(R.drawable.cl_ic_secure) : k.icon(Ic.LOCK, color, 20);
        } else if (key == 4) {
            d = classic ? res(R.drawable.cl_ic_partial_secure) : k.icon(Ic.INFO, color, 20);
        } else {
            d = k.icon(Ic.PUBLIC, color, 20);
        }
        siteIcon.setImageDrawable(d);
        if (classic) {
            siteIcon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            siteIcon.setPadding(k.dp(6), k.dp(6), k.dp(6), k.dp(6));
        }
    }

    private void updateAction() {
        boolean classic = Settings.STYLE_CLASSIC.equals(style);
        if (editing) {
            action.setVisibility(edit.getText().length() > 0 ? View.VISIBLE : View.GONE);
            if (actionKey != 1) action.setImageDrawable(classic ? res(R.drawable.cl_ic_input_delete) : k.icon(Ic.CLOSE, fgDim, 20));
            actionKey = 1;
        } else if (tab != null && !tab.isHome()) {
            action.setVisibility(View.VISIBLE);
            boolean loading = tab.loading;
            int key = loading ? 2 : 3;
            if (actionKey != key) {
                if (classic) action.setImageDrawable(res(loading ? R.drawable.cl_ic_menu_stop : R.drawable.cl_ic_menu_refresh));
                else action.setImageDrawable(k.icon(loading ? Ic.CLOSE : Ic.REFRESH, fgDim, 20));
            }
            actionKey = key;
        } else {
            action.setVisibility(View.GONE);
        }
        if (classic) {
            action.setScaleType(ImageView.ScaleType.FIT_CENTER);
            action.setPadding(k.dp(6), k.dp(6), k.dp(6), k.dp(6));
        }
    }

    private void setEditing(boolean e) {
        if (editing == e) return;
        editing = e;
        if (e) {
            ignoreText = true;
            edit.setText(tab == null || tab.isHome() ? "" : tab.url);
            edit.selectAll();
            ignoreText = false;
        } else {
            show(tab);
        }
        tabsButton.setVisibility(e ? View.GONE : View.VISIBLE);
        menuButton.setVisibility(e ? View.GONE : View.VISIBLE);
        updateSiteIcon();
        updateAction();
        listener.onEditingChanged(e);
    }

    /** Focuses the field and opens the keyboard, optionally with prefilled text. */
    public void startEditing(String text) {
        edit.requestFocus();
        if (text != null) {
            edit.setText(text);
            edit.setSelection(edit.getText().length());
        }
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(edit, InputMethodManager.SHOW_IMPLICIT);
    }

    public void stopEditing() {
        InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(edit.getWindowToken(), 0);
        edit.clearFocus();
        bar.requestFocus();
        setEditing(false);
    }

    /** Puts a suggestion into the field without submitting it. */
    public void fill(String text) {
        edit.setText(text);
        edit.setSelection(text.length());
    }
}
