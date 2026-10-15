package com.disgusty.foxtalgia.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.util.Sdk;

/** All persisted preferences. */
public final class Settings {
    public static final String STYLE_AUTO = "auto";
    public static final String STYLE_CLASSIC = "classic";
    public static final String STYLE_HOLO = "holo";
    public static final String STYLE_MD1 = "md1";
    public static final String STYLE_MD3 = "md3";

    public static final String BRIGHTNESS_SYSTEM = "system";
    public static final String BRIGHTNESS_LIGHT = "light";
    public static final String BRIGHTNESS_DARK = "dark";

    /** The style's own accent (Classic orange, Holo blue, Material teal, Material You wallpaper colors). */
    public static final String ACCENT_DEFAULT = "default";
    /** One of {@link com.disgusty.foxtalgia.ui.kit.Palettes#NAMES}. */
    public static final String ACCENT_PRESET = "preset";
    /** Any color picked in the HSV picker. */
    public static final String ACCENT_CUSTOM = "custom";

    public static final String TOOLBAR_TOP = "top";
    public static final String TOOLBAR_BOTTOM = "bottom";

    public static final String TRACKING_OFF = "off";
    public static final String TRACKING_STANDARD = "standard";
    public static final String TRACKING_STRICT = "strict";

    public static final String HTTPS_OFF = "off";
    public static final String HTTPS_PRIVATE = "private";
    public static final String HTTPS_ALWAYS = "always";

    public static final String HOME_NATIVE = "home";
    public static final String HOME_BLANK = "blank";
    public static final String HOME_URL = "url";

    public static final String DOH_OFF = "off";
    /** DNS over HTTPS first, the system resolver when it fails (Gecko TRR mode 2). */
    public static final String DOH_INCREASED = "increased";
    /** DNS over HTTPS only (TRR mode 3). */
    public static final String DOH_MAX = "max";

    /** id, name, URL; the last entry is the user's own server. */
    public static final String[][] DOH_PROVIDERS = {
            {"cloudflare", "Cloudflare", "https://mozilla.cloudflare-dns.com/dns-query"},
            {"quad9", "Quad9", "https://dns.quad9.net/dns-query"},
            {"mullvad", "Mullvad", "https://dns.mullvad.net/dns-query"},
            {"adguard", "AdGuard", "https://dns.adguard-dns.com/dns-query"},
            {"nextdns", "NextDNS", "https://firefox.dns.nextdns.io/"},
            {"google", "Google", "https://dns.google/dns-query"},
            {"custom", null, null},
    };

    private final SharedPreferences prefs;
    private final boolean weakDevice;

    public Settings(Context context) {
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE);
        weakDevice = Sdk.isWeakDevice(context);
    }

    public SharedPreferences raw() {
        return prefs;
    }

    private String s(String key, String def) {
        return prefs.getString(key, def);
    }

    private boolean b(String key, boolean def) {
        return prefs.getBoolean(key, def);
    }

    private int i(String key, int def) {
        return prefs.getInt(key, def);
    }

    private void put(String key, String value) {
        prefs.edit().putString(key, value).commit();
    }

    private void put(String key, boolean value) {
        prefs.edit().putBoolean(key, value).commit();
    }

    private void put(String key, int value) {
        prefs.edit().putInt(key, value).commit();
    }

    // ---------------------------------------------------------------- appearance

    public String style() {
        return s("style", STYLE_AUTO);
    }

    public void setStyle(String v) {
        put("style", v);
    }

    /** The style actually used: Classic before 3.0, Holo for 3.0-4.4, Material 1 for 5-11, Material 3 for 12+. */
    public String effectiveStyle() {
        String st = style();
        return STYLE_AUTO.equals(st) ? autoStyle() : st;
    }

    public static String autoStyle() {
        if (Sdk.INT >= 31) return STYLE_MD3;
        if (Sdk.INT >= 21) return STYLE_MD1;
        if (Sdk.INT >= 11) return STYLE_HOLO;
        return STYLE_CLASSIC;
    }

    public String brightness() {
        return s("brightness", BRIGHTNESS_SYSTEM);
    }

    public void setBrightness(String v) {
        put("brightness", v);
    }

    /** Pure black backgrounds in dark mode. */
    public boolean oled() {
        return b("oled", false);
    }

    public void setOled(boolean v) {
        put("oled", v);
    }

    public String accentMode() {
        return s("accentMode", ACCENT_DEFAULT);
    }

    public void setAccentMode(String v) {
        put("accentMode", v);
    }

    public int accentPreset() {
        return i("accentPreset", 0);
    }

    public void setAccentPreset(int v) {
        put("accentPreset", v);
    }

    public int accentColor() {
        return i("accentColor", 0xFFE66000);
    }

    public void setAccentColor(int v) {
        put("accentColor", v);
    }

    /** Colors recently chosen in the picker, newest first, as "#AARRGGBB" separated by commas. */
    public String recentColors() {
        return s("recentColors", "");
    }

    public void setRecentColors(String v) {
        put("recentColors", v);
    }

    public boolean iconFollowsStyle() {
        return b("iconFollowsStyle", true);
    }

    public void setIconFollowsStyle(boolean v) {
        put("iconFollowsStyle", v);
    }

    public boolean animations() {
        return b("animations", true);
    }

    public void setAnimations(boolean v) {
        put("animations", v);
    }

    public String toolbarPosition() {
        return s("toolbarPosition", TOOLBAR_TOP);
    }

    public void setToolbarPosition(String v) {
        put("toolbarPosition", v);
    }

    /** Web pages follow the app's light/dark choice (prefers-color-scheme). */
    public boolean webDarkFollowsApp() {
        return b("webDarkFollowsApp", true);
    }

    public void setWebDarkFollowsApp(boolean v) {
        put("webDarkFollowsApp", v);
    }

    public String locale() {
        return s("locale", I18n.SYSTEM);
    }

    public void setLocale(String v) {
        put("locale", v);
    }

    // ---------------------------------------------------------------- general

    public String searchEngine() {
        return s("searchEngine", "duckduckgo");
    }

    public void setSearchEngine(String v) {
        put("searchEngine", v);
    }

    /** Custom search URL template with %s for the query. */
    public String customSearch() {
        return s("customSearch", "");
    }

    public void setCustomSearch(String v) {
        put("customSearch", v);
    }

    public boolean searchSuggestions() {
        return b("searchSuggestions", true);
    }

    public void setSearchSuggestions(boolean v) {
        put("searchSuggestions", v);
    }

    public String homepage() {
        return s("homepage", HOME_NATIVE);
    }

    public void setHomepage(String v) {
        put("homepage", v);
    }

    public String homepageUrl() {
        return s("homepageUrl", "https://www.mozilla.org/");
    }

    public void setHomepageUrl(String v) {
        put("homepageUrl", v);
    }

    public boolean restoreTabs() {
        return b("restoreTabs", true);
    }

    public void setRestoreTabs(boolean v) {
        put("restoreTabs", v);
    }

    public boolean openLinksInBackground() {
        return b("openLinksInBackground", false);
    }

    public void setOpenLinksInBackground(boolean v) {
        put("openLinksInBackground", v);
    }

    public boolean desktopMode() {
        return b("desktopMode", false);
    }

    public void setDesktopMode(boolean v) {
        put("desktopMode", v);
    }

    /** Page text size in percent (GeckoRuntimeSettings font size factor). */
    public int textZoom() {
        return i("textZoom", 100);
    }

    public void setTextZoom(int v) {
        put("textZoom", v);
    }

    public boolean forceZoom() {
        return b("forceZoom", false);
    }

    public void setForceZoom(boolean v) {
        put("forceZoom", v);
    }

    public boolean javascript() {
        return b("javascript", true);
    }

    public void setJavascript(boolean v) {
        put("javascript", v);
    }

    public boolean blockPopups() {
        return b("blockPopups", true);
    }

    public void setBlockPopups(boolean v) {
        put("blockPopups", v);
    }

    public boolean autoplay() {
        return b("autoplay", false);
    }

    public void setAutoplay(boolean v) {
        put("autoplay", v);
    }

    public boolean askDownloadLocation() {
        return b("askDownloadLocation", false);
    }

    public void setAskDownloadLocation(boolean v) {
        put("askDownloadLocation", v);
    }

    // ---------------------------------------------------------------- privacy

    public String trackingProtection() {
        return s("trackingProtection", TRACKING_STANDARD);
    }

    public void setTrackingProtection(String v) {
        put("trackingProtection", v);
    }

    public String httpsOnly() {
        return s("httpsOnly", HTTPS_PRIVATE);
    }

    public void setHttpsOnly(String v) {
        put("httpsOnly", v);
    }

    public String doh() {
        return s("doh", DOH_OFF);
    }

    public void setDoh(String v) {
        put("doh", v);
    }

    public String dohProvider() {
        return s("dohProvider", DOH_PROVIDERS[0][0]);
    }

    public void setDohProvider(String v) {
        put("dohProvider", v);
    }

    public String dohCustomUrl() {
        return s("dohCustomUrl", "");
    }

    public void setDohCustomUrl(String v) {
        put("dohCustomUrl", v);
    }

    /** The resolver URL for the chosen provider, or "" when a custom one isn't set yet. */
    public String dohUrl() {
        String id = dohProvider();
        for (String[] p : DOH_PROVIDERS) {
            if (p[0].equals(id)) return p[2] != null ? p[2] : dohCustomUrl();
        }
        return DOH_PROVIDERS[0][2];
    }

    public boolean globalPrivacyControl() {
        return b("gpc", false);
    }

    public void setGlobalPrivacyControl(boolean v) {
        put("gpc", v);
    }

    public boolean rememberHistory() {
        return b("rememberHistory", true);
    }

    public void setRememberHistory(boolean v) {
        put("rememberHistory", v);
    }

    public boolean clearOnExit() {
        return b("clearOnExit", false);
    }

    public void setClearOnExit(boolean v) {
        put("clearOnExit", v);
    }

    public boolean loginAutofill() {
        return b("loginAutofill", true);
    }

    public void setLoginAutofill(boolean v) {
        put("loginAutofill", v);
    }

    // ---------------------------------------------------------------- advanced

    public boolean remoteDebugging() {
        return b("remoteDebugging", false);
    }

    public void setRemoteDebugging(boolean v) {
        put("remoteDebugging", v);
    }

    public boolean aboutConfig() {
        return b("aboutConfig", true);
    }

    public void setAboutConfig(boolean v) {
        put("aboutConfig", v);
    }

    /** Lets extensions not signed by Mozilla be installed from a file (like Firefox Nightly's xpinstall.signatures.required=false). */
    public boolean unsignedExtensions() {
        return b("unsignedExtensions", false);
    }

    public void setUnsignedExtensions(boolean v) {
        put("unsignedExtensions", v);
    }

    /** Fewer content processes and less background work; on by default on old or low-memory devices. */
    public boolean liteMode() {
        return b("liteMode", weakDevice);
    }

    public void setLiteMode(boolean v) {
        put("liteMode", v);
    }

    // ---------------------------------------------------------------- state

    public boolean flag(String key) {
        return b("flag_" + key, false);
    }

    public void setFlag(String key, boolean v) {
        put("flag_" + key, v);
    }
}
