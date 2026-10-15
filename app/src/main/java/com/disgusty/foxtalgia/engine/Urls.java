package com.disgusty.foxtalgia.engine;

import android.net.Uri;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.data.Settings;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Pattern;

/** Address bar input handling and search engines. */
public final class Urls {
    /** id, name, search URL (%s = query), suggestions URL (OpenSearch JSON, %s = query) or null. */
    public static final String[][] ENGINES = {
            {"duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/ac/?type=list&q=%s"},
            {"google", "Google", "https://www.google.com/search?q=%s", "https://www.google.com/complete/search?client=firefox&q=%s"},
            {"bing", "Bing", "https://www.bing.com/search?q=%s", "https://api.bing.com/osjson.aspx?query=%s"},
            {"yandex", "Яндекс", "https://yandex.ru/search/?text=%s", "https://suggest.yandex.ru/suggest-ff.cgi?part=%s"},
            {"startpage", "Startpage", "https://www.startpage.com/sp/search?query=%s", null},
            {"ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "https://ac.ecosia.org/autocomplete?type=list&q=%s"},
            {"qwant", "Qwant", "https://www.qwant.com/?q=%s", null},
            {"brave", "Brave Search", "https://search.brave.com/search?q=%s", null},
            {"wikipedia", "Wikipedia", "https://%l.wikipedia.org/wiki/Special:Search?search=%s", "https://%l.wikipedia.org/w/api.php?action=opensearch&search=%s"},
            {"custom", null, null, null},
    };

    private static final Pattern SCHEME = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*:");
    private static final Pattern HOSTLIKE = Pattern.compile("^(localhost|\\[[0-9a-fA-F:.]+\\]|[0-9]{1,3}(\\.[0-9]{1,3}){3}|([\\p{L}\\p{N}-]+\\.)+[\\p{L}\\p{N}-]{2,})(:[0-9]{1,5})?([/?#].*)?$");
    private static final String[] INTERNAL = {"http:", "https:", "about:", "data:", "blob:", "file:", "javascript:", "moz-extension:", "resource:", "view-source:", "chrome:", "jar:", "ftp:"};

    private Urls() {
    }

    /** Turns address bar text into a URL: a URL as typed, a host with https://, or a search. */
    public static String fromInput(String input) {
        String s = input.trim();
        if (s.length() == 0) return Tab.HOME;
        if (s.indexOf(' ') < 0) {
            if (SCHEME.matcher(s).find() && !looksLikeHostPort(s)) return s;
            if (HOSTLIKE.matcher(s).matches()) return "https://" + s;
        }
        return search(s);
    }

    private static boolean looksLikeHostPort(String s) {
        // "example.com:8080/path" matches the scheme pattern but is a host with a port.
        int colon = s.indexOf(':');
        if (colon < 0 || colon + 1 >= s.length()) return false;
        return Character.isDigit(s.charAt(colon + 1)) && s.substring(0, colon).indexOf('.') > 0;
    }

    public static String[] engine() {
        Settings st = App.get().settings;
        String id = st.searchEngine();
        for (String[] e : ENGINES) if (e[0].equals(id)) return e;
        return ENGINES[0];
    }

    public static String search(String query) {
        Settings st = App.get().settings;
        String[] e = engine();
        String template = "custom".equals(e[0]) ? st.customSearch() : e[2];
        if (template == null || template.indexOf("%s") < 0) template = ENGINES[0][2];
        return template.replace("%l", lang()).replace("%s", encode(query));
    }

    public static String suggestUrl(String query) {
        String[] e = engine();
        if (e[3] == null) return null;
        return e[3].replace("%l", lang()).replace("%s", encode(query));
    }

    private static String lang() {
        String l = Locale.getDefault().getLanguage();
        return l == null || l.length() == 0 ? "en" : l;
    }

    public static String encode(String s) {
        try {
            return URLEncoder.encode(s, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return s;
        }
    }

    public static boolean isExternalScheme(String uri) {
        if (uri == null) return false;
        String l = uri.toLowerCase(Locale.US);
        for (String p : INTERNAL) if (l.startsWith(p)) return false;
        return SCHEME.matcher(l).find();
    }

    /** The host for display ("www." removed), or the whole URL for non-network schemes. */
    public static String displayHost(String url) {
        if (url == null) return "";
        try {
            Uri u = Uri.parse(url);
            String h = u.getHost();
            if (h == null || h.length() == 0) return url;
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return url;
        }
    }

    /** URL without the scheme for http(s), as browsers show it when not editing. */
    public static String display(String url) {
        if (url == null || Tab.HOME.equals(url)) return "";
        if (url.startsWith("https://")) url = url.substring(8);
        else if (url.startsWith("http://")) url = url.substring(7);
        if (url.endsWith("/") && url.indexOf('/') == url.length() - 1) url = url.substring(0, url.length() - 1);
        return url;
    }

    /** True for the query part of a search results URL of the current engine: shows the query instead of the URL. */
    public static String searchTerms(String url) {
        if (url == null) return null;
        String[] e = engine();
        String template = "custom".equals(e[0]) ? App.get().settings.customSearch() : e[2];
        if (template == null) return null;
        template = template.replace("%l", lang());
        int p = template.indexOf("%s");
        if (p < 0 || !url.startsWith(template.substring(0, p))) return null;
        try {
            Uri tu = Uri.parse(template.replace("%s", "QUERY"));
            Uri u = Uri.parse(url);
            for (String name : tu.getQueryParameterNames()) {
                if ("QUERY".equals(tu.getQueryParameter(name))) return u.getQueryParameter(name);
            }
        } catch (Exception ignored) {
            // not a hierarchical URI
        }
        return null;
    }
}
