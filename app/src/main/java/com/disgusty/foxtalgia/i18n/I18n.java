package com.disgusty.foxtalgia.i18n;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;
import com.disgusty.foxtalgia.util.IO;
import com.disgusty.foxtalgia.util.Log;
import com.disgusty.foxtalgia.util.Text;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Reads the translation files in assets/i18n/*.json (slang format, as in OldySend).
 *
 * Supported slang features: nested keys, key modifiers in parentheses (ignored), "@:" links (absolute and
 * relative "@:.key"), plural maps (zero/one/two/few/many/other) and arrays. Missing keys fall back to English.
 */
public final class I18n {
    public static final String SYSTEM = "system";

    /** Locale ids with a translation in assets/i18n. */
    public static final String[][] LOCALES = {
            {"en", "English"}, {"ru", "Русский"},
    };

    private static final String[] RTL = {"ar", "arc", "fa", "he", "ur"};

    private static volatile I18n current;

    private final String localeId;
    private final Map<String, Object> values = new HashMap<String, Object>();

    private I18n(String localeId) {
        this.localeId = localeId;
    }

    public static I18n get() {
        return current;
    }

    public static String t(String key) {
        return current.text(key);
    }

    public static String t(String key, Object... params) {
        return current.text(key, params);
    }

    public static synchronized void load(Context context, String setting) {
        String id = resolve(setting);
        I18n i18n = new I18n(id);
        i18n.merge(context, "i18n/en.json", "");
        if (!"en".equals(id)) {
            i18n.merge(context, "i18n/" + id + ".json", "");
        }
        current = i18n;
    }

    /** Maps the "system" setting to the best matching translation file. */
    public static String resolve(String setting) {
        if (setting != null && !SYSTEM.equals(setting)) {
            for (String[] l : LOCALES) if (l[0].equals(setting)) return setting;
        }
        Locale locale = Locale.getDefault();
        String lang = Text.lower(locale.getLanguage());
        String country = Text.upper(locale.getCountry());
        if ("iw".equals(lang)) lang = "he";
        if ("in".equals(lang)) lang = "id";
        if ("tl".equals(lang)) lang = "fil";
        // Aramaic: classical code and the Syriac / Assyrian Neo-Aramaic locales.
        if ("syr".equals(lang) || "aii".equals(lang)) lang = "arc";
        String full = lang + "-" + country;
        for (String[] l : LOCALES) if (l[0].equals(full)) return full;
        if ("zh".equals(lang)) return "MO".equals(country) || "HK".equals(country) ? "zh-HK" : ("TW".equals(country) ? "zh-TW" : "zh-CN");
        if ("pt".equals(lang)) return "pt-PT";
        if ("es".equals(lang)) return "es-ES";
        if ("fil".equals(lang)) return "fil-PH";
        for (String[] l : LOCALES) if (l[0].equals(lang)) return lang;
        return "en";
    }

    public static String nativeName(String id) {
        for (String[] l : LOCALES) if (l[0].equals(id)) return l[1];
        return id;
    }

    public String localeId() {
        return localeId;
    }

    public boolean isRtl() {
        for (String r : RTL) if (localeId.startsWith(r)) return true;
        return false;
    }

    private void merge(Context context, String asset, String prefix) {
        InputStream in = null;
        try {
            in = context.getAssets().open(asset);
            JSONObject root = new JSONObject(Text.utf8(IO.readAll(in)));
            flatten(prefix, root);
        } catch (java.io.FileNotFoundException ignored) {
            // Missing files fall back to English.
        } catch (Exception e) {
            Log.w("Could not load " + asset, e);
        } finally {
            IO.close(in);
        }
    }

    private void flatten(String prefix, JSONObject obj) throws Exception {
        Iterator<?> keys = obj.keys();
        while (keys.hasNext()) {
            String rawKey = (String) keys.next();
            if (rawKey.startsWith("@")) continue;
            String key = stripModifiers(rawKey);
            Object v = obj.get(rawKey);
            String full = prefix + key;
            if (v instanceof JSONObject) {
                JSONObject child = (JSONObject) v;
                if (isPluralMap(child)) {
                    Map<String, String> plural = new HashMap<String, String>();
                    Iterator<?> pk = child.keys();
                    while (pk.hasNext()) {
                        String k = (String) pk.next();
                        plural.put(k, child.optString(k));
                    }
                    values.put(full, plural);
                } else {
                    flatten(full + ".", child);
                }
            } else if (v instanceof JSONArray) {
                JSONArray arr = (JSONArray) v;
                String[] list = new String[arr.length()];
                for (int i = 0; i < arr.length(); i++) list[i] = arr.optString(i);
                values.put(full, list);
            } else if (v instanceof String) {
                if (((String) v).length() > 0) values.put(full, v);
            }
        }
    }

    private static boolean isPluralMap(JSONObject obj) {
        if (!obj.has("other") && !obj.has("one")) return false;
        Iterator<?> keys = obj.keys();
        while (keys.hasNext()) {
            String k = (String) keys.next();
            if (!("zero".equals(k) || "one".equals(k) || "two".equals(k) || "few".equals(k) || "many".equals(k) || "other".equals(k))) {
                return false;
            }
            try {
                if (!(obj.get(k) instanceof String)) return false;
            } catch (Exception e) {
                return false;
            }
        }
        return true;
    }

    private static String stripModifiers(String key) {
        int p = key.indexOf('(');
        return p < 0 ? key : key.substring(0, p);
    }

    public String text(String key) {
        Object v = values.get(key);
        if (v instanceof String) return resolveLinks(key, (String) v, 0);
        if (v instanceof Map) return plural(key, 2);
        return key;
    }

    public String text(String key, Object... params) {
        return applyParams(text(key), params);
    }

    public String[] array(String key) {
        Object v = values.get(key);
        if (v instanceof String[]) {
            String[] src = (String[]) v;
            String[] out = new String[src.length];
            for (int i = 0; i < src.length; i++) out[i] = resolveLinks(key, src[i], 0);
            return out;
        }
        return new String[0];
    }

    /**
     * Plural selection identical to LocalSend's resolver: 0 → zero/other, 1 → one/other, Hebrew 2 → two,
     * everything else → other (English and German use slang's built-in rule, which matches for these keys).
     */
    public String plural(String key, long n, Object... params) {
        Object v = values.get(key);
        if (!(v instanceof Map)) return applyParams(text(key), params);
        @SuppressWarnings("unchecked") Map<String, String> m = (Map<String, String>) v;
        String s = null;
        if (localeId.startsWith("ru") || localeId.startsWith("uk") || localeId.startsWith("be")) {
            // CLDR rules for East Slavic languages.
            long d = n % 10, h = n % 100;
            if (n == 0) s = m.get("zero");
            if (s == null) s = m.get(d == 1 && h != 11 ? "one" : d >= 2 && d <= 4 && (h < 12 || h > 14) ? "few" : "many");
        } else if (n == 0) s = m.get("zero");
        else if (n == 1) s = m.get("one");
        else if (n == 2 && localeId.startsWith("he")) s = m.get("two");
        if (s == null) s = m.get("other");
        if (s == null) s = m.get("one");
        if (s == null) return String.valueOf(n);
        Object[] all = new Object[params.length + 2];
        all[0] = "n";
        all[1] = n;
        System.arraycopy(params, 0, all, 2, params.length);
        return applyParams(resolveLinks(key, s, 0), all);
    }

    public boolean has(String key) {
        return values.containsKey(key);
    }

    private String resolveLinks(String key, String value, int depth) {
        int at = value.indexOf("@:");
        if (at < 0 || depth > 8) return value;
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (at >= 0) {
            sb.append(value, i, at);
            int end = at + 2;
            while (end < value.length()) {
                char c = value.charAt(end);
                boolean word = Character.isLetterOrDigit(c) || c == '_';
                // A trailing dot ends the sentence rather than continuing the path.
                boolean dot = c == '.' && end + 1 < value.length() && Character.isLetterOrDigit(value.charAt(end + 1));
                if (!word && !dot) break;
                end++;
            }
            String path = value.substring(at + 2, end);
            if (path.startsWith(".")) {
                int lastDot = key.lastIndexOf('.');
                String parent = lastDot < 0 ? "" : key.substring(0, lastDot);
                path = parent + path;
            }
            Object target = values.get(path);
            if (target instanceof String) {
                sb.append(resolveLinks(path, (String) target, depth + 1));
            } else if (target instanceof Map) {
                // Linked plurals keep their own parameter placeholder, resolved by the caller's params.
                @SuppressWarnings("unchecked") Map<String, String> m = (Map<String, String>) target;
                String other = m.get("other");
                sb.append(other == null ? "" : resolveLinks(path, other, depth + 1));
            } else {
                sb.append(path);
            }
            i = end;
            at = value.indexOf("@:", i);
        }
        sb.append(value, i, value.length());
        return sb.toString();
    }

    private static String applyParams(String s, Object... params) {
        if (params == null || params.length == 0 || s.indexOf('{') < 0) return s;
        for (int i = 0; i + 1 < params.length; i += 2) {
            s = Text.replace(s, "{" + params[i] + "}", String.valueOf(params[i + 1]));
        }
        return s;
    }

    /** All locale ids, for the language picker. */
    public static List<String> ids() {
        List<String> ids = new ArrayList<String>();
        for (String[] l : LOCALES) ids.add(l[0]);
        return ids;
    }
}
