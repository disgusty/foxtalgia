package com.disgusty.foxtalgia.ui;

import android.graphics.Color;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.engine.Engine;
import com.disgusty.foxtalgia.ui.kit.ColorPicker;
import com.disgusty.foxtalgia.ui.kit.Draw;
import com.disgusty.foxtalgia.ui.kit.Field;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.ui.kit.Palettes;
import com.disgusty.foxtalgia.ui.kit.Scheme;
import com.disgusty.foxtalgia.ui.kit.Toggle;
import com.disgusty.foxtalgia.util.Sdk;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Appearance: light / dark theme, then the interface style (Classic, Holo, Material Design, Material You) with
 * live previews drawn by each style's own kit, then the accent color (style default, Material palette presets or
 * any color from the picker).
 */
public final class AppearanceActivity extends SettingsActivity {
    private static final String[] STYLES = {Settings.STYLE_AUTO, Settings.STYLE_CLASSIC, Settings.STYLE_HOLO, Settings.STYLE_MD1, Settings.STYLE_MD3};

    @Override
    protected void build() {
        super.build();
        screen.appBar.title(s("settings.appearance"));
    }

    private void changed() {
        App.get().appearanceChanged();
        Engine.apply(this);
        rebuild();
    }

    @Override
    protected void refresh() {
        list.removeAllViews();
        final Settings st = settings;

        // ---- theme
        header("appearance.theme");
        final String[] themes = {Settings.BRIGHTNESS_SYSTEM, Settings.BRIGHTNESS_LIGHT, Settings.BRIGHTNESS_DARK};
        String[] themeNames = {s("appearance.themeSystem"), s("appearance.themeLight"), s("appearance.themeDark")};
        for (int i = 0; i < themes.length; i++) {
            final int idx = i;
            radio(themeNames[i], null, themes[i].equals(st.brightness()), new Runnable() {
                @Override
                public void run() {
                    st.setBrightness(themes[idx]);
                    changed();
                }
            });
        }
        toggle(s("appearance.oled"), s("appearance.oledSummary"), new BoolGet() {
            public boolean get() {
                return st.oled();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setOled(v);
                changed();
            }
        });

        // ---- interface style
        header("appearance.style");
        String[] names = {s("appearance.styleAuto"), s("appearance.styleClassic"), s("appearance.styleHolo"), s("appearance.styleMd1"), s("appearance.styleMd3")};
        String[] eras = {t.text("appearance.styleAutoSummary", "style", styleName(Settings.autoStyle())), s("appearance.eraClassic"),
                s("appearance.eraHolo"), s("appearance.eraMd1"), s("appearance.eraMd3")};
        for (int i = 0; i < STYLES.length; i++) {
            final int idx = i;
            radio(names[i], eras[i], STYLES[i].equals(st.style()), new Runnable() {
                @Override
                public void run() {
                    st.setStyle(STYLES[idx]);
                    changed();
                }
            });
        }
        LinearLayout previews = k.hbox();
        previews.setPadding(k.dp(8), k.dp(4), k.dp(8), k.dp(8));
        for (int i = 1; i < STYLES.length; i++) {
            final int idx = i;
            View p = preview(STYLES[i], STYLES[i].equals(st.effectiveStyle()));
            p.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    st.setStyle(STYLES[idx]);
                    changed();
                }
            });
            previews.addView(p, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        }
        list.addView(Compat.hscroll(this, previews), Kit.matchWrap());

        // ---- accent color
        header("appearance.accent");
        String mode = st.accentMode();
        radio(s("appearance.accentDefault"), defaultAccentName(), Settings.ACCENT_DEFAULT.equals(mode), new Runnable() {
            @Override
            public void run() {
                st.setAccentMode(Settings.ACCENT_DEFAULT);
                changed();
            }
        });
        radio(s("appearance.accentPreset"), Settings.ACCENT_PRESET.equals(mode) ? Palettes.NAMES[st.accentPreset()] : null,
                Settings.ACCENT_PRESET.equals(mode), null);
        list.addView(swatches(), Kit.matchWrap());
        radio(s("appearance.accentCustom"), Settings.ACCENT_CUSTOM.equals(mode) ? hex(st.accentColor()) : s("appearance.accentCustomSummary"),
                Settings.ACCENT_CUSTOM.equals(mode), new Runnable() {
                    @Override
                    public void run() {
                        pickColor();
                    }
                });

        // ---- more
        header("appearance.more");
        final String[] pos = {Settings.TOOLBAR_TOP, Settings.TOOLBAR_BOTTOM};
        choice(s("appearance.toolbar"), new String[]{s("appearance.toolbarTop"), s("appearance.toolbarBottom")}, indexOf(pos, st.toolbarPosition()),
                new Choice() {
                    @Override
                    public void chosen(int index) {
                        st.setToolbarPosition(pos[index]);
                        App.get().appearanceChanged();
                    }
                });
        toggle(s("appearance.webDark"), s("appearance.webDarkSummary"), new BoolGet() {
            public boolean get() {
                return st.webDarkFollowsApp();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setWebDarkFollowsApp(v);
            }
        });
        toggle(s("appearance.icon"), s("appearance.iconSummary"), new BoolGet() {
            public boolean get() {
                return st.iconFollowsStyle();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setIconFollowsStyle(v);
            }
        });
        toggle(s("appearance.animations"), null, new BoolGet() {
            public boolean get() {
                return st.animations();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setAnimations(v);
                changed();
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        Launcher.sync(this, settings);
    }

    private String styleName(String style) {
        if (Settings.STYLE_CLASSIC.equals(style)) return s("appearance.styleClassic");
        if (Settings.STYLE_HOLO.equals(style)) return s("appearance.styleHolo");
        if (Settings.STYLE_MD1.equals(style)) return s("appearance.styleMd1");
        return s("appearance.styleMd3");
    }

    private String defaultAccentName() {
        String st = settings.effectiveStyle();
        if (Settings.STYLE_CLASSIC.equals(st)) return s("appearance.defaultClassic");
        if (Settings.STYLE_HOLO.equals(st)) return s("appearance.defaultHolo");
        if (Settings.STYLE_MD1.equals(st)) return s("appearance.defaultMd1");
        return Sdk.atLeast(31) ? s("appearance.defaultMd3Dynamic") : s("appearance.defaultMd3");
    }

    private static String hex(int c) {
        return String.format(Locale.US, "#%06X", c & 0xFFFFFF);
    }

    private void radio(String title, String summary, boolean checked, final Runnable onPick) {
        final Toggle tg = k.toggle(Kit.RADIO).passive();
        tg.setChecked(checked);
        list.addView(k.row().title(title).summary(summary).trailing(tg).onClick(onPick == null ? null : new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onPick.run();
            }
        }).build(), Kit.matchWrap());
    }

    /** A thumbnail of the style: its app bar, a switch and a button, made by that style's kit. */
    private View preview(String style, boolean current) {
        boolean dark = Scheme.resolveDark(this, settings, style);
        Scheme sc = Scheme.resolve(this, style, dark, settings.oled(), settings.accentMode(), settings.accentPreset(), settings.accentColor());
        Kit pk = Kit.create(this, sc);
        LinearLayout box = pk.vbox();
        box.setBackgroundColor(sc.background);
        View bar = pk.appBar().title(styleName(style)).view();
        box.addView(bar, Kit.matchWrap());
        LinearLayout row = pk.hbox();
        row.setPadding(pk.dp(8), pk.dp(8), pk.dp(8), pk.dp(4));
        Toggle tg = pk.toggle(Kit.SWITCH).passive();
        tg.setChecked(true);
        row.addView(tg);
        box.addView(row, Kit.matchWrap());
        View b = pk.button("OK", Kit.B_PRIMARY, null);
        b.setClickable(false);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.setMargins(pk.dp(8), pk.dp(4), pk.dp(8), pk.dp(10));
        box.addView(b, bp);
        FrameLayout frame = new FrameLayout(this);
        frame.setPadding(k.dp(3), k.dp(3), k.dp(3), k.dp(3));
        frame.setBackgroundDrawable(Draw.stroke(0, current ? k.c.primary : k.c.divider, k.dp(current ? 3 : 1), k.dp(6)));
        frame.addView(box, new FrameLayout.LayoutParams(k.dp(150), ViewGroup.LayoutParams.WRAP_CONTENT));
        frame.setClickable(true);
        FrameLayout wrap = new FrameLayout(this);
        wrap.setPadding(k.dp(4), 0, k.dp(4), 0);
        wrap.addView(frame);
        return wrap;
    }

    /** The 18 Material palette colors as tappable circles. */
    private View swatches() {
        LinearLayout grid = k.vbox();
        grid.setPadding(k.pagePadding(), 0, k.pagePadding(), k.dp(8));
        LinearLayout row = null;
        boolean preset = Settings.ACCENT_PRESET.equals(settings.accentMode());
        int perRow = 6;
        for (int i = 0; i < Palettes.NAMES.length; i++) {
            if (i % perRow == 0) {
                row = k.hbox();
                grid.addView(row, Kit.matchWrap());
            }
            final int idx = i;
            View sw = swatch(Palettes.MD1[i][0], preset && settings.accentPreset() == i);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    settings.setAccentMode(Settings.ACCENT_PRESET);
                    settings.setAccentPreset(idx);
                    changed();
                }
            });
            com.disgusty.foxtalgia.ui.Compat.description(sw, Palettes.NAMES[i]);
            row.addView(sw, new LinearLayout.LayoutParams(0, k.dp(48), 1));
        }
        return grid;
    }

    private View swatch(int color, boolean selected) {
        FrameLayout f = new FrameLayout(this);
        View dot = new View(this);
        dot.setBackgroundDrawable(selected ? Draw.stroke(color, k.c.onSurface, k.dp(3), k.dp(18)) : Draw.oval(color));
        f.addView(dot, new FrameLayout.LayoutParams(k.dp(36), k.dp(36), Gravity.CENTER));
        f.setClickable(true);
        f.setBackgroundDrawable(k.listSelector());
        return f;
    }

    /** HSV square + hue strip, HEX field, recent colors. */
    private void pickColor() {
        final int start = settings.accentColor();
        LinearLayout box = k.vbox();
        final ColorPicker picker = new ColorPicker(this);
        picker.setColor(start);
        box.addView(picker, Kit.matchWrap());
        LinearLayout hexRow = k.hbox();
        final View preview = new View(this);
        preview.setBackgroundDrawable(Draw.rect(start, k.dp(4)));
        hexRow.addView(preview, new LinearLayout.LayoutParams(k.dp(40), k.dp(40)));
        final Field hexField = k.field(null, false);
        hexField.text(hex(start));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        hp.leftMargin = k.dp(12);
        hexRow.addView(hexField.view, hp);
        box.addView(hexRow, Kit.matchWrap());
        final boolean[] updating = new boolean[1];
        picker.setListener(new ColorPicker.Listener() {
            @Override
            public void onColor(int argb) {
                updating[0] = true;
                hexField.text(hex(argb));
                updating[0] = false;
                preview.setBackgroundDrawable(Draw.rect(argb, k.dp(4)));
            }
        });
        hexField.edit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                if (updating[0]) return;
                Integer c = parse(e.toString());
                if (c == null) return;
                picker.setColor(c);
                preview.setBackgroundDrawable(Draw.rect(c, k.dp(4)));
            }
        });
        final List<Integer> recent = recent();
        if (!recent.isEmpty()) {
            TextView rl = k.text(Kit.T_CAPTION, s("appearance.recentColors"));
            rl.setPadding(0, k.dp(12), 0, k.dp(4));
            box.addView(rl);
            LinearLayout rr = k.hbox();
            for (final Integer c : recent) {
                View sw = swatch(c, false);
                sw.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        picker.setColor(c);
                        hexField.text(hex(c));
                        preview.setBackgroundDrawable(Draw.rect(c, k.dp(4)));
                    }
                });
                rr.addView(sw, new LinearLayout.LayoutParams(k.dp(44), k.dp(44)));
            }
            box.addView(rr);
        }
        k.dialog().title(s("appearance.accentCustom")).view(box)
                .negative(s("general.cancel"), null)
                .positive(s("general.ok"), new Runnable() {
                    @Override
                    public void run() {
                        Integer typed = parse(hexField.text());
                        int c = (typed != null ? typed : picker.color()) | 0xFF000000;
                        settings.setAccentMode(Settings.ACCENT_CUSTOM);
                        settings.setAccentColor(c);
                        remember(c);
                        changed();
                    }
                }).show();
    }

    private static Integer parse(String s) {
        s = s.trim();
        if (!s.startsWith("#")) s = "#" + s;
        if (s.length() != 7 && s.length() != 4) return null;
        if (s.length() == 4) s = "#" + s.charAt(1) + s.charAt(1) + s.charAt(2) + s.charAt(2) + s.charAt(3) + s.charAt(3);
        try {
            return Color.parseColor(s);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private List<Integer> recent() {
        List<Integer> out = new ArrayList<Integer>();
        for (String p : settings.recentColors().split(",")) {
            Integer c = parse(p);
            if (c != null) out.add(c);
        }
        return out;
    }

    private void remember(int c) {
        List<Integer> r = recent();
        r.remove(Integer.valueOf(c));
        r.add(0, c);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < r.size() && i < 8; i++) {
            if (sb.length() > 0) sb.append(',');
            sb.append(hex(r.get(i)));
        }
        settings.setRecentColors(sb.toString());
    }
}
