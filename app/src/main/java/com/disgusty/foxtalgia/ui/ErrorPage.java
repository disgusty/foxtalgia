package com.disgusty.foxtalgia.ui;

import android.text.TextUtils;

import com.disgusty.foxtalgia.i18n.I18n;
import com.disgusty.foxtalgia.ui.kit.Kit;

import org.mozilla.geckoview.WebRequestError;

import java.util.Locale;

/** Load error pages in the colors of the active style. */
final class ErrorPage {
    private ErrorPage() {
    }

    private static String css(int argb) {
        return String.format(Locale.US, "rgba(%d,%d,%d,%.3f)", (argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, ((argb >>> 24) & 0xFF) / 255f);
    }

    static String key(WebRequestError e) {
        switch (e.code) {
            case WebRequestError.ERROR_UNKNOWN_HOST:
                return "errors.unknownHost";
            case WebRequestError.ERROR_CONNECTION_REFUSED:
                return "errors.refused";
            case WebRequestError.ERROR_NET_TIMEOUT:
                return "errors.timeout";
            case WebRequestError.ERROR_OFFLINE:
                return "errors.offline";
            case WebRequestError.ERROR_SECURITY_BAD_CERT:
            case WebRequestError.ERROR_BAD_HSTS_CERT:
                return "errors.badCert";
            case WebRequestError.ERROR_SECURITY_SSL:
                return "errors.ssl";
            case WebRequestError.ERROR_HTTPS_ONLY:
                return "errors.httpsOnly";
            case WebRequestError.ERROR_SAFEBROWSING_MALWARE_URI:
            case WebRequestError.ERROR_SAFEBROWSING_PHISHING_URI:
            case WebRequestError.ERROR_SAFEBROWSING_UNWANTED_URI:
            case WebRequestError.ERROR_SAFEBROWSING_HARMFUL_URI:
                return "errors.unsafe";
            case WebRequestError.ERROR_MALFORMED_URI:
            case WebRequestError.ERROR_UNKNOWN_PROTOCOL:
                return "errors.badAddress";
            case WebRequestError.ERROR_REDIRECT_LOOP:
                return "errors.redirectLoop";
            case WebRequestError.ERROR_CONTENT_CRASHED:
                return "errors.crashed";
            case WebRequestError.ERROR_PORT_BLOCKED:
                return "errors.portBlocked";
            default:
                return "errors.generic";
        }
    }

    static String html(Kit k, String uri, WebRequestError e) {
        I18n t = I18n.get();
        String k1 = key(e);
        String title = TextUtils.htmlEncode(t.text(k1 + ".title"));
        String body = TextUtils.htmlEncode(t.text(k1 + ".text", "host", com.disgusty.foxtalgia.engine.Urls.displayHost(uri)));
        String safeUri = TextUtils.htmlEncode(uri == null ? "" : uri);
        String retry = TextUtils.htmlEncode(t.text("errors.retry"));
        int radius = "md3".equals(k.style) ? 20 : "md1".equals(k.style) ? 2 : 4;
        String btnText = "md1".equals(k.style) ? retry.toUpperCase(Locale.getDefault()) : retry;
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
                + "<title>" + title + "</title><style>"
                + "body{margin:0;padding:48px 24px;font-family:" + ("classic".equals(k.style) ? "'Droid Sans',sans-serif" : "Roboto,sans-serif")
                + ";background:" + css(k.c.background) + ";color:" + css(k.c.onSurface) + "}"
                + "h1{font-weight:" + ("md3".equals(k.style) ? "400" : "500") + ";font-size:24px;margin:0 0 16px}"
                + "p{font-size:16px;line-height:1.5;color:" + css(k.c.onSurfaceVariant) + "}"
                + ".u{font-size:13px;word-break:break-all;color:" + css(k.c.onSurfaceVariant) + "}"
                + "a.b{display:inline-block;text-decoration:none;margin-top:24px;padding:10px 24px;font-size:15px;border:0;border-radius:" + radius + "px;background:"
                + css(k.c.primary) + ";color:" + css(k.c.onPrimary) + "}</style></head><body>"
                + "<h1>" + title + "</h1><p>" + body + "</p><p class='u'>" + safeUri + "</p>"
                + "<a class='b' href='" + safeUri + "'>" + btnText + "</a></body></html>";
    }
}
