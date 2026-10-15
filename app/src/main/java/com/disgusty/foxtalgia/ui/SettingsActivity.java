package com.disgusty.foxtalgia.ui;

import android.content.Intent;
import android.text.InputType;
import android.view.View;
import android.widget.LinearLayout;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.BuildConfig;
import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.engine.Engine;
import com.disgusty.foxtalgia.engine.Urls;
import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.kit.DialogBuilder;
import com.disgusty.foxtalgia.ui.kit.Field;
import com.disgusty.foxtalgia.ui.kit.Ic;
import com.disgusty.foxtalgia.ui.kit.Kit;
import com.disgusty.foxtalgia.ui.kit.Toggle;
import com.disgusty.foxtalgia.util.Sdk;

/** Settings: a list of sections; each section is the same activity with a "page" extra. */
public class SettingsActivity extends BaseActivity {
    static final String PAGE_GENERAL = "general";
    static final String PAGE_PRIVACY = "privacy";
    static final String PAGE_SITES = "sites";
    static final String PAGE_ADVANCED = "advanced";
    static final String PAGE_ABOUT = "about";

    protected LinearLayout list;
    private String page;

    interface BoolGet {
        boolean get();
    }

    interface BoolSet {
        void set(boolean v);
    }

    interface Choice {
        void chosen(int index);
    }

    @Override
    protected void build() {
        page = getIntent().getStringExtra("page");
        list = k.vbox();
        screen.setContent(k.scroll(list));
        screen.appBar.up(finisher());
    }

    @Override
    protected void refresh() {
        list.removeAllViews();
        if (PAGE_GENERAL.equals(page)) general();
        else if (PAGE_PRIVACY.equals(page)) privacy();
        else if (PAGE_SITES.equals(page)) sites();
        else if (PAGE_ADVANCED.equals(page)) advanced();
        else if (PAGE_ABOUT.equals(page)) about();
        else root();
    }

    private void openPage(String p) {
        Intent i = new Intent(this, SettingsActivity.class);
        i.putExtra("page", p);
        startActivity(i);
    }

    // ---------------------------------------------------------------- helpers

    protected void header(String key) {
        list.addView(k.sectionHeader(s(key)), Kit.matchWrap());
    }

    protected void link(Ic icon, String title, String summary, View.OnClickListener l) {
        list.addView(k.row().icon(icon).title(title).summary(summary).onClick(l).build(), Kit.matchWrap());
    }

    protected void toggle(String title, String summary, final BoolGet get, final BoolSet set) {
        final Toggle tg = k.toggle(Kit.SWITCH).passive();
        tg.setChecked(get.get());
        tg.listener(new Toggle.Listener() {
            @Override
            public void onChanged(Toggle toggle, boolean checked) {
                set.set(checked);
                Engine.apply(SettingsActivity.this);
            }
        });
        list.addView(k.row().title(title).summary(summary).trailing(tg).onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                tg.toggle();
            }
        }).build(), Kit.matchWrap());
    }

    protected void choice(final String title, final String[] labels, final int current, final Choice c) {
        list.addView(k.row().title(title).summary(current >= 0 && current < labels.length ? labels[current] : "").onClick(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                k.dialog().title(title).singleChoice(labels, current, new DialogBuilder.ItemListener() {
                    @Override
                    public void onItem(int index) {
                        c.chosen(index);
                        Engine.apply(SettingsActivity.this);
                        refresh();
                    }
                }).show();
            }
        }).build(), Kit.matchWrap());
    }

    protected static int indexOf(String[] values, String v) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(v)) return i;
        return 0;
    }

    // ---------------------------------------------------------------- pages

    private void root() {
        screen.appBar.title(s("settings.title"));
        link(Ic.TUNE, s("settings.general"), s("settings.generalSummary"), go(PAGE_GENERAL));
        link(Ic.PALETTE, s("settings.appearance"), s("settings.appearanceSummary"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(AppearanceActivity.class);
            }
        });
        link(Ic.SHIELD, s("settings.privacy"), s("settings.privacySummary"), go(PAGE_PRIVACY));
        link(Ic.PUBLIC, s("settings.sites"), s("settings.sitesSummary"), go(PAGE_SITES));
        link(Ic.EXTENSION, s("addons.title"), s("settings.addonsSummary"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                open(AddonsActivity.class);
            }
        });
        link(Ic.CODE, s("settings.advanced"), s("settings.advancedSummary"), go(PAGE_ADVANCED));
        link(Ic.INFO, s("settings.about"), "Foxtalgia " + BuildConfig.VERSION_NAME, go(PAGE_ABOUT));
    }

    private View.OnClickListener go(final String p) {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPage(p);
            }
        };
    }

    private void general() {
        final Settings st = settings;
        screen.appBar.title(s("settings.general"));
        header("settings.search");
        final String[] ids = new String[Urls.ENGINES.length];
        final String[] names = new String[Urls.ENGINES.length];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = Urls.ENGINES[i][0];
            names[i] = Urls.ENGINES[i][1] != null ? Urls.ENGINES[i][1] : s("settings.customEngine");
        }
        choice(s("settings.searchEngine"), names, indexOf(ids, st.searchEngine()), new Choice() {
            @Override
            public void chosen(int index) {
                st.setSearchEngine(ids[index]);
                if ("custom".equals(ids[index])) customEngine();
            }
        });
        toggle(s("settings.suggestions"), s("settings.suggestionsSummary"), new BoolGet() {
            public boolean get() {
                return st.searchSuggestions();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setSearchSuggestions(v);
            }
        });
        header("settings.tabs");
        final String[] homeVals = {Settings.HOME_NATIVE, Settings.HOME_BLANK, Settings.HOME_URL};
        choice(s("settings.homepage"), new String[]{s("settings.homeNative"), s("settings.homeBlank"), s("settings.homeUrl")},
                indexOf(homeVals, st.homepage()), new Choice() {
                    @Override
                    public void chosen(int index) {
                        st.setHomepage(homeVals[index]);
                        if (index == 2) homeUrlDialog();
                    }
                });
        toggle(s("settings.restoreTabs"), null, new BoolGet() {
            public boolean get() {
                return st.restoreTabs();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setRestoreTabs(v);
            }
        });
        toggle(s("settings.linksBackground"), s("settings.linksBackgroundSummary"), new BoolGet() {
            public boolean get() {
                return st.openLinksInBackground();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setOpenLinksInBackground(v);
            }
        });
        toggle(s("settings.desktopDefault"), null, new BoolGet() {
            public boolean get() {
                return st.desktopMode();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setDesktopMode(v);
            }
        });
        header("settings.languageSection");
        final String[] locIds = new String[I18n.LOCALES.length + 1];
        final String[] locNames = new String[locIds.length];
        locIds[0] = I18n.SYSTEM;
        locNames[0] = s("settings.systemLanguage");
        for (int i = 0; i < I18n.LOCALES.length; i++) {
            locIds[i + 1] = I18n.LOCALES[i][0];
            locNames[i + 1] = I18n.LOCALES[i][1];
        }
        choice(s("settings.language"), locNames, indexOf(locIds, st.locale()), new Choice() {
            @Override
            public void chosen(int index) {
                st.setLocale(locIds[index]);
                App.get().appearanceChanged();
                rebuild();
            }
        });
    }

    private void customEngine() {
        final Field f = k.field(s("settings.customEngineHint"), false);
        f.text(settings.customSearch().length() > 0 ? settings.customSearch() : "https://example.com/search?q=%s");
        k.dialog().title(s("settings.customEngine")).view(f.view)
                .negative(s("general.cancel"), null)
                .positive(s("general.save"), new Runnable() {
                    @Override
                    public void run() {
                        String v = f.text().trim();
                        if (v.indexOf("%s") < 0) {
                            k.toast(s("settings.customEngineInvalid"));
                            return;
                        }
                        settings.setCustomSearch(v);
                        refresh();
                    }
                }).show();
    }

    private void homeUrlDialog() {
        final Field f = k.field(s("settings.homeUrl"), false);
        Compat.inputType(f.edit, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        f.text(settings.homepageUrl());
        k.dialog().title(s("settings.homeUrl")).view(f.view)
                .negative(s("general.cancel"), null)
                .positive(s("general.save"), new Runnable() {
                    @Override
                    public void run() {
                        settings.setHomepageUrl(Urls.fromInput(f.text()));
                        refresh();
                    }
                }).show();
    }

    private void privacy() {
        final Settings st = settings;
        screen.appBar.title(s("settings.privacy"));
        header("settings.trackingSection");
        final String[] tp = {Settings.TRACKING_STANDARD, Settings.TRACKING_STRICT, Settings.TRACKING_OFF};
        choice(s("settings.tracking"), new String[]{s("settings.trackingStandard"), s("settings.trackingStrict"), s("settings.trackingOff")},
                indexOf(tp, st.trackingProtection()), new Choice() {
                    @Override
                    public void chosen(int index) {
                        st.setTrackingProtection(tp[index]);
                    }
                });
        final String[] https = {Settings.HTTPS_OFF, Settings.HTTPS_PRIVATE, Settings.HTTPS_ALWAYS};
        choice(s("settings.httpsOnly"), new String[]{s("settings.httpsOff"), s("settings.httpsPrivate"), s("settings.httpsAlways")},
                indexOf(https, st.httpsOnly()), new Choice() {
                    @Override
                    public void chosen(int index) {
                        st.setHttpsOnly(https[index]);
                    }
                });
        final String[] doh = {Settings.DOH_OFF, Settings.DOH_INCREASED, Settings.DOH_MAX};
        choice(s("settings.doh"), new String[]{s("settings.dohOff"), s("settings.dohIncreased"), s("settings.dohMax")},
                indexOf(doh, st.doh()), new Choice() {
                    @Override
                    public void chosen(int index) {
                        st.setDoh(doh[index]);
                        if (index > 0 && st.dohUrl().length() == 0) dohCustomDialog();
                    }
                });
        if (!Settings.DOH_OFF.equals(st.doh())) {
            final String[] ids = new String[Settings.DOH_PROVIDERS.length];
            final String[] names = new String[ids.length];
            for (int i = 0; i < ids.length; i++) {
                ids[i] = Settings.DOH_PROVIDERS[i][0];
                names[i] = Settings.DOH_PROVIDERS[i][1] != null ? Settings.DOH_PROVIDERS[i][1]
                        : st.dohCustomUrl().length() > 0 ? st.dohCustomUrl() : s("settings.dohCustom");
            }
            choice(s("settings.dohProvider"), names, indexOf(ids, st.dohProvider()), new Choice() {
                @Override
                public void chosen(int index) {
                    st.setDohProvider(ids[index]);
                    if (Settings.DOH_PROVIDERS[index][2] == null) dohCustomDialog();
                }
            });
        }
        toggle(s("settings.gpc"), s("settings.gpcSummary"), new BoolGet() {
            public boolean get() {
                return st.globalPrivacyControl();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setGlobalPrivacyControl(v);
            }
        });
        header("settings.historySection");
        toggle(s("settings.rememberHistory"), null, new BoolGet() {
            public boolean get() {
                return st.rememberHistory();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setRememberHistory(v);
            }
        });
        toggle(s("settings.logins"), s("settings.loginsSummary"), new BoolGet() {
            public boolean get() {
                return st.loginAutofill();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setLoginAutofill(v);
            }
        });
        toggle(s("settings.clearOnExit"), s("settings.clearOnExitSummary"), new BoolGet() {
            public boolean get() {
                return st.clearOnExit();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setClearOnExit(v);
            }
        });
        link(Ic.DELETE, s("settings.clearData"), s("settings.clearDataSummary"), new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearDialog();
            }
        });
    }

    private void dohCustomDialog() {
        final Field f = k.field(s("settings.dohCustomHint"), false);
        Compat.inputType(f.edit, InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        f.text(settings.dohCustomUrl().length() > 0 ? settings.dohCustomUrl() : "https://");
        k.dialog().title(s("settings.dohCustom")).view(f.view)
                .negative(s("general.cancel"), null)
                .positive(s("general.save"), new Runnable() {
                    @Override
                    public void run() {
                        String v = f.text().trim();
                        if (!v.startsWith("https://") || v.length() <= "https://".length()) {
                            k.toast(s("settings.dohCustomInvalid"));
                            return;
                        }
                        settings.setDohProvider("custom");
                        settings.setDohCustomUrl(v);
                        Engine.apply(SettingsActivity.this);
                        refresh();
                    }
                }).show();
    }

    private void clearDialog() {
        final CharSequence[] items = {s("settings.clearHistory"), s("settings.clearCookies"), s("settings.clearCache"), s("settings.clearPermissions"),
                s("settings.clearDownloads")};
        final boolean[] checked = {true, true, true, false, false};
        final long[] flags = {Clearing.HISTORY, Clearing.COOKIES, Clearing.CACHE, Clearing.SITE_SETTINGS, Clearing.DOWNLOADS};
        k.dialog().title(s("settings.clearData")).multiChoice(items, checked, null)
                .negative(s("general.cancel"), null)
                .positive(s("settings.clear"), new Runnable() {
                    @Override
                    public void run() {
                        long what = 0;
                        for (int i = 0; i < flags.length; i++) if (checked[i]) what |= flags[i];
                        Clearing.clear(SettingsActivity.this, what, new Runnable() {
                            @Override
                            public void run() {
                                k.toast(s("settings.cleared"));
                            }
                        });
                    }
                }).show();
    }

    private void sites() {
        final Settings st = settings;
        screen.appBar.title(s("settings.sites"));
        toggle(s("settings.javascript"), null, new BoolGet() {
            public boolean get() {
                return st.javascript();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setJavascript(v);
            }
        });
        toggle(s("settings.blockPopups"), null, new BoolGet() {
            public boolean get() {
                return st.blockPopups();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setBlockPopups(v);
            }
        });
        toggle(s("settings.autoplay"), s("settings.autoplaySummary"), new BoolGet() {
            public boolean get() {
                return st.autoplay();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setAutoplay(v);
            }
        });
        final int[] zooms = {80, 90, 100, 110, 125, 150, 175, 200};
        String[] zl = new String[zooms.length];
        int cur = 2;
        for (int i = 0; i < zooms.length; i++) {
            zl[i] = zooms[i] + "%";
            if (zooms[i] == st.textZoom()) cur = i;
        }
        choice(s("settings.textSize"), zl, cur, new Choice() {
            @Override
            public void chosen(int index) {
                st.setTextZoom(zooms[index]);
            }
        });
        toggle(s("settings.forceZoom"), s("settings.forceZoomSummary"), new BoolGet() {
            public boolean get() {
                return st.forceZoom();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setForceZoom(v);
            }
        });
    }

    private void advanced() {
        final Settings st = settings;
        screen.appBar.title(s("settings.advanced"));
        toggle(s("settings.remoteDebugging"), s("settings.remoteDebuggingSummary"), new BoolGet() {
            public boolean get() {
                return st.remoteDebugging();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setRemoteDebugging(v);
            }
        });
        toggle(s("settings.aboutConfig"), s("settings.aboutConfigSummary"), new BoolGet() {
            public boolean get() {
                return st.aboutConfig();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setAboutConfig(v);
            }
        });
        toggle(s("settings.unsigned"), s("settings.unsignedSummary"), new BoolGet() {
            public boolean get() {
                return st.unsignedExtensions();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setUnsignedExtensions(v);
            }
        });
        toggle(s("settings.liteMode"), s("settings.liteModeSummary"), new BoolGet() {
            public boolean get() {
                return st.liteMode();
            }
        }, new BoolSet() {
            public void set(boolean v) {
                st.setLiteMode(v);
                k.toast(s("settings.restartToApply"));
            }
        });
    }

    private void about() {
        screen.appBar.title(s("settings.about"));
        LinearLayout head = k.vbox();
        head.setGravity(android.view.Gravity.CENTER);
        head.setPadding(0, k.dp(24), 0, k.dp(16));
        head.addView(k.image(k.logo(k.dp(72))), new LinearLayout.LayoutParams(k.dp(72), k.dp(72)));
        head.addView(k.text(Kit.T_HEADLINE, "Foxtalgia " + BuildConfig.VERSION_NAME));
        String gecko = org.mozilla.geckoview.BuildConfig.MOZ_APP_VERSION;
        head.addView(k.text(Kit.T_SECONDARY, t.text("about.engine", "version", gecko, "android", Sdk.release())));
        list.addView(head, Kit.matchWrap());
        android.widget.TextView body = k.text(Kit.T_BODY, s("about.text"));
        body.setPadding(k.pagePadding(), 0, k.pagePadding(), k.dp(16));
        list.addView(body, Kit.matchWrap());
        link(Ic.CODE, s("about.source"), "github.com/mozilla-firefox/firefox", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInBrowser("https://github.com/mozilla-firefox/firefox");
            }
        });
        link(Ic.DESCRIPTION, s("about.licenses"), "MPL 2.0, Apache 2.0, GPL-3.0 (uBlock Origin)", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openInBrowser("about:license");
            }
        });
    }

    private void openInBrowser(String url) {
        Intent i = new Intent(this, BrowserActivity.class);
        i.setAction(Intent.ACTION_VIEW);
        i.setData(android.net.Uri.parse(url));
        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }
}
