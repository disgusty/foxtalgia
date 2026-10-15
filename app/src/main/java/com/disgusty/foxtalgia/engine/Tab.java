package com.disgusty.foxtalgia.engine;

import android.graphics.Bitmap;

import org.mozilla.geckoview.GeckoSession;
import org.mozilla.geckoview.GeckoSessionSettings;

/** One browser tab: a GeckoSession plus the state the UI shows. */
public final class Tab {
    /** Internal URL of the native start page. */
    public static final String HOME = "about:home";

    public final long id;
    public final boolean isPrivate;
    public GeckoSession session;
    public String url = "";
    public String title = "";
    public int progress = 100;
    public boolean loading;
    public boolean canGoBack;
    public boolean canGoForward;
    public boolean secure;
    public boolean securityKnown;
    public String securityHost;
    public boolean desktop;
    public boolean fullscreen;
    public Bitmap thumbnail;
    public long parentId;
    public long lastUsed = System.currentTimeMillis();
    /** Serialized GeckoSession.SessionState read from the saved session. */
    public String state;
    /** Latest state reported by Gecko; serialized only when the tabs are saved, since it changes on every scroll. */
    public GeckoSession.SessionState sessionState;
    /** Number of items blocked by tracking protection on the current page. */
    public int blocked;

    Tab(long id, boolean isPrivate) {
        this.id = id;
        this.isPrivate = isPrivate;
    }

    /** The state to restore the session from, or null. */
    GeckoSession.SessionState restorableState() {
        if (sessionState != null) return sessionState;
        return state == null ? null : GeckoSession.SessionState.fromString(state);
    }

    public boolean isHome() {
        return HOME.equals(url) || url.length() == 0;
    }

    public String displayTitle() {
        if (isHome()) return "";
        return title != null && title.length() > 0 ? title : url;
    }

    static GeckoSessionSettings settings(boolean isPrivate, boolean desktop) {
        return new GeckoSessionSettings.Builder()
                .usePrivateMode(isPrivate)
                .useTrackingProtection(true)
                .userAgentMode(desktop ? GeckoSessionSettings.USER_AGENT_MODE_DESKTOP : GeckoSessionSettings.USER_AGENT_MODE_MOBILE)
                .viewportMode(desktop ? GeckoSessionSettings.VIEWPORT_MODE_DESKTOP : GeckoSessionSettings.VIEWPORT_MODE_MOBILE)
                .suspendMediaWhenInactive(false)
                .build();
    }
}
