package com.disgusty.foxtalgia.engine;

import android.content.Context;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.data.Settings;
import com.disgusty.foxtalgia.ui.kit.Scheme;
import com.disgusty.foxtalgia.util.Log;

import org.mozilla.geckoview.ContentBlocking;
import org.mozilla.geckoview.GeckoPreferenceController;
import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.GeckoRuntimeSettings;

/** The single GeckoRuntime and the mapping from Foxtalgia settings to Gecko settings and prefs. */
public final class Engine {
    private static GeckoRuntime runtime;

    private Engine() {
    }

    public static synchronized boolean started() {
        return runtime != null;
    }

    public static synchronized GeckoRuntime runtime(Context context) {
        if (runtime == null) {
            Settings s = App.get().settings;
            GeckoRuntimeSettings.Builder b = new GeckoRuntimeSettings.Builder()
                    .aboutConfigEnabled(s.aboutConfig())
                    .remoteDebuggingEnabled(s.remoteDebugging())
                    .javaScriptEnabled(s.javascript())
                    .fontSizeFactor(s.textZoom() / 100f)
                    .forceUserScalableEnabled(s.forceZoom())
                    .loginAutofillEnabled(s.loginAutofill())
                    .globalPrivacyControlEnabled(s.globalPrivacyControl())
                    .preferredColorScheme(colorScheme(context, s))
                    .extensionsWebAPIEnabled(true)
                    .extensionsProcessEnabled(true)
                    .consoleOutput(com.disgusty.foxtalgia.BuildConfig.DEBUG)
                    .contentBlocking(contentBlocking(s));
            // Fission gives every site and every cross-site frame (ads) its own content process: too much
            // memory and process start-up work for old devices. Only takes effect on the next start.
            if (s.liteMode()) b.fissionEnabled(false);
            runtime = GeckoRuntime.create(context.getApplicationContext(), b.build());
            runtime.getSettings().setAllowInsecureConnections(httpsMode(s));
            applyDoh(runtime.getSettings(), s);
            applyPrefs(s);
            runtime.setAutocompleteStorageDelegate(com.disgusty.foxtalgia.data.Logins.get());
            Extensions.get().attach(runtime);
            Log.i("GeckoRuntime started, engine " + com.disgusty.foxtalgia.BuildConfig.ENGINE);
        }
        return runtime;
    }

    /** Pushes changed settings into the running engine. */
    public static synchronized void apply(Context context) {
        if (runtime == null) return;
        Settings s = App.get().settings;
        GeckoRuntimeSettings g = runtime.getSettings();
        g.setAboutConfigEnabled(s.aboutConfig());
        g.setRemoteDebuggingEnabled(s.remoteDebugging());
        g.setJavaScriptEnabled(s.javascript());
        g.setFontSizeFactor(s.textZoom() / 100f);
        g.setForceUserScalableEnabled(s.forceZoom());
        g.setLoginAutofillEnabled(s.loginAutofill());
        g.setGlobalPrivacyControl(s.globalPrivacyControl());
        g.setPreferredColorScheme(colorScheme(context, s));
        g.setAllowInsecureConnections(httpsMode(s));
        applyDoh(g, s);
        ContentBlocking.Settings cb = g.getContentBlocking();
        String tp = s.trackingProtection();
        cb.setEnhancedTrackingProtectionLevel(etpLevel(tp));
        cb.setAntiTracking(antiTracking(tp));
        cb.setCookieBehavior(cookieBehavior(tp));
        cb.setStrictSocialTrackingProtection(Settings.TRACKING_STRICT.equals(tp));
        applyPrefs(s);
    }

    private static int colorScheme(Context context, Settings s) {
        if (!s.webDarkFollowsApp()) return GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM;
        boolean dark = Scheme.resolveDark(context, s, s.effectiveStyle());
        return dark ? GeckoRuntimeSettings.COLOR_SCHEME_DARK : GeckoRuntimeSettings.COLOR_SCHEME_LIGHT;
    }

    private static int httpsMode(Settings s) {
        String h = s.httpsOnly();
        if (Settings.HTTPS_ALWAYS.equals(h)) return GeckoRuntimeSettings.HTTPS_ONLY;
        if (Settings.HTTPS_PRIVATE.equals(h)) return GeckoRuntimeSettings.HTTPS_ONLY_PRIVATE;
        return GeckoRuntimeSettings.ALLOW_ALL;
    }

    private static void applyDoh(GeckoRuntimeSettings g, Settings s) {
        String mode = s.doh();
        String url = s.dohUrl();
        try {
            if (Settings.DOH_OFF.equals(mode) || url.length() == 0) {
                g.setTrustedRecursiveResolverMode(GeckoRuntimeSettings.TRR_MODE_DISABLED);
                return;
            }
            g.setTrustedRecursiveResolverUri(url);
            g.setTrustedRecursiveResolverMode(Settings.DOH_MAX.equals(mode)
                    ? GeckoRuntimeSettings.TRR_MODE_ONLY : GeckoRuntimeSettings.TRR_MODE_FIRST);
        } catch (Throwable e) {
            Log.w("DNS over HTTPS", e);
        }
    }

    private static ContentBlocking.Settings contentBlocking(Settings s) {
        String tp = s.trackingProtection();
        return new ContentBlocking.Settings.Builder()
                .enhancedTrackingProtectionLevel(etpLevel(tp))
                .antiTracking(antiTracking(tp))
                .cookieBehavior(cookieBehavior(tp))
                .cookieBehaviorPrivateMode(ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS)
                .strictSocialTrackingProtection(Settings.TRACKING_STRICT.equals(tp))
                .safeBrowsing(ContentBlocking.SafeBrowsing.DEFAULT)
                .build();
    }

    private static int etpLevel(String tp) {
        if (Settings.TRACKING_OFF.equals(tp)) return ContentBlocking.EtpLevel.NONE;
        if (Settings.TRACKING_STRICT.equals(tp)) return ContentBlocking.EtpLevel.STRICT;
        return ContentBlocking.EtpLevel.DEFAULT;
    }

    private static int antiTracking(String tp) {
        if (Settings.TRACKING_OFF.equals(tp)) return ContentBlocking.AntiTracking.NONE;
        if (Settings.TRACKING_STRICT.equals(tp)) return ContentBlocking.AntiTracking.STRICT;
        return ContentBlocking.AntiTracking.DEFAULT;
    }

    private static int cookieBehavior(String tp) {
        if (Settings.TRACKING_OFF.equals(tp)) return ContentBlocking.CookieBehavior.ACCEPT_ALL;
        return ContentBlocking.CookieBehavior.ACCEPT_FIRST_PARTY_AND_ISOLATE_OTHERS;
    }

    /** Settings that only exist as Gecko prefs. */
    private static void applyPrefs(Settings s) {
        setPref("dom.disable_open_during_load", s.blockPopups());
        // 0 = allow, 1 = block audible, 5 = block audible and inaudible
        setPref("media.autoplay.default", s.autoplay() ? 0 : 1);
        setPref("browser.display.use_document_fonts", 1);
        if (s.unsignedExtensions()) setPref("xpinstall.signatures.required", false);
        // Without app animations sites are asked for reduced motion too, which also saves rendering work.
        if (s.animations()) clearPref("ui.prefersReducedMotion");
        else setPref("ui.prefersReducedMotion", 1);
        if (s.liteMode()) {
            // One shared content process, no spare process kept warm.
            setPref("dom.ipc.processCount", 1);
            setPref("dom.ipc.processPrelaunch.enabled", false);
            // No speculative connections, link prefetch or predictor: each costs a TLS handshake or disk I/O.
            setPref("network.http.speculative-parallel-limit", 0);
            setPref("network.prefetch-next", false);
            setPref("network.predictor.enabled", false);
            // AV1 has no hardware decoder on these devices and software decoding can't keep up; sites fall back to VP9/H.264.
            setPref("media.av1.enabled", false);
            // Session state is collected from the page less often (default 10 s).
            setPref("browser.sessionstore.interval", 30000);
        } else {
            // GeckoRuntimeSettings' own default; clearing the pref would mean all.js' 8.
            setPref("dom.ipc.processCount", 2);
            for (String p : LITE_PREFS) clearPref(p);
        }
    }

    private static final String[] LITE_PREFS = {"dom.ipc.processPrelaunch.enabled",
            "network.http.speculative-parallel-limit", "network.prefetch-next", "network.predictor.enabled",
            "media.av1.enabled", "browser.sessionstore.interval"};

    private static void clearPref(String name) {
        try {
            GeckoPreferenceController.clearGeckoUserPref(name);
        } catch (Throwable e) {
            Log.w("Pref " + name, e);
        }
    }

    private static void setPref(String name, boolean value) {
        try {
            GeckoPreferenceController.setGeckoPref(name, value, GeckoPreferenceController.PREF_BRANCH_USER);
        } catch (Throwable e) {
            Log.w("Pref " + name, e);
        }
    }

    private static void setPref(String name, int value) {
        try {
            GeckoPreferenceController.setGeckoPref(name, value, GeckoPreferenceController.PREF_BRANCH_USER);
        } catch (Throwable e) {
            Log.w("Pref " + name, e);
        }
    }
}
