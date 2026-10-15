package com.disgusty.foxtalgia.data;

import android.annotation.TargetApi;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.util.Base64;

import com.disgusty.foxtalgia.App;
import com.disgusty.foxtalgia.util.Log;
import com.disgusty.foxtalgia.util.Sdk;

import org.mozilla.geckoview.Autocomplete;
import org.mozilla.geckoview.GeckoResult;

import java.security.KeyStore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Saved logins for Gecko's login manager (Autocomplete.StorageDelegate). Passwords are encrypted with an AES key
 * kept in the Android Keystore on Android 6.0+; older versions have no AES keys in the Keystore, there passwords
 * stay in the app's private database only.
 */
public final class Logins implements Autocomplete.StorageDelegate {
    private static final String KEY_ALIAS = "foxtalgia-logins";
    private static Logins instance;

    public static synchronized Logins get() {
        if (instance == null) instance = new Logins();
        return instance;
    }

    private static SQLiteDatabase db() {
        return Db.get(App.get()).getWritableDatabase();
    }

    /** "scheme://host[:port]" of a URL, as Gecko uses for login origins. */
    static String origin(String url) {
        try {
            Uri u = Uri.parse(url);
            if (u.getScheme() == null || u.getHost() == null) return url;
            return u.getScheme() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "");
        } catch (Exception e) {
            return url;
        }
    }

    @Override
    public GeckoResult<Autocomplete.LoginEntry[]> onLoginFetch(String domain) {
        return GeckoResult.fromValue(query("origin LIKE ? OR origin LIKE ?", new String[]{"%://" + domain, "%://" + domain + ":%"}));
    }

    @Override
    public GeckoResult<Autocomplete.LoginEntry[]> onLoginFetch() {
        return GeckoResult.fromValue(query(null, null));
    }

    @Override
    public void onLoginSave(Autocomplete.LoginEntry e) {
        if (!App.get().settings.loginAutofill()) return;
        ContentValues v = new ContentValues();
        String guid = e.guid != null ? e.guid : "{" + UUID.randomUUID() + "}";
        v.put("guid", guid);
        v.put("origin", e.origin);
        v.put("form_origin", e.formActionOrigin);
        v.put("realm", e.httpRealm);
        v.put("username", e.username);
        v.put("password", encrypt(e.password));
        v.put("changed", System.currentTimeMillis());
        int n = e.guid == null ? 0 : db().update("logins", v, "guid = ?", new String[]{e.guid});
        if (n == 0) {
            v.put("created", System.currentTimeMillis());
            db().insert("logins", null, v);
        }
    }

    @Override
    public void onLoginUsed(Autocomplete.LoginEntry e, int usedFields) {
        if (e.guid == null) return;
        ContentValues v = new ContentValues();
        v.put("used", System.currentTimeMillis());
        db().update("logins", v, "guid = ?", new String[]{e.guid});
    }

    public List<Autocomplete.LoginEntry> all() {
        List<Autocomplete.LoginEntry> out = new ArrayList<Autocomplete.LoginEntry>();
        for (Autocomplete.LoginEntry e : query(null, null)) out.add(e);
        return out;
    }

    public void delete(String guid) {
        db().delete("logins", "guid = ?", new String[]{guid});
    }

    private Autocomplete.LoginEntry[] query(String where, String[] args) {
        List<Autocomplete.LoginEntry> out = new ArrayList<Autocomplete.LoginEntry>();
        Cursor c = db().query("logins", new String[]{"guid", "origin", "form_origin", "realm", "username", "password"}, where, args, null, null, "used DESC");
        while (c.moveToNext()) {
            String pw = decrypt(c.getString(5));
            if (pw == null) continue;
            out.add(new Autocomplete.LoginEntry.Builder().guid(c.getString(0)).origin(c.getString(1)).formActionOrigin(c.getString(2))
                    .httpRealm(c.getString(3)).username(c.getString(4) == null ? "" : c.getString(4)).password(pw).build());
        }
        c.close();
        return out.toArray(new Autocomplete.LoginEntry[out.size()]);
    }

    // ---------------------------------------------------------------- encryption

    private static String encrypt(String plain) {
        if (Sdk.atLeast(23)) {
            try {
                return "k1:" + Api23.encrypt(plain);
            } catch (Exception e) {
                Log.w("Keystore encryption failed", e);
            }
        }
        return "p:" + plain;
    }

    private static String decrypt(String stored) {
        if (stored == null) return null;
        if (stored.startsWith("p:")) return stored.substring(2);
        if (stored.startsWith("k1:") && Sdk.atLeast(23)) {
            try {
                return Api23.decrypt(stored.substring(3));
            } catch (Exception e) {
                Log.w("Keystore decryption failed", e);
            }
        }
        return null;
    }

    @TargetApi(23)
    private static final class Api23 {
        static SecretKey key() throws Exception {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (ks.containsAlias(KEY_ALIAS)) return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
            KeyGenerator g = KeyGenerator.getInstance(android.security.keystore.KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            g.init(new android.security.keystore.KeyGenParameterSpec.Builder(KEY_ALIAS,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT | android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build());
            return g.generateKey();
        }

        static String encrypt(String plain) throws Exception {
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key());
            byte[] iv = c.getIV();
            byte[] data = c.doFinal(plain.getBytes("UTF-8"));
            return Base64.encodeToString(iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(data, Base64.NO_WRAP);
        }

        static String decrypt(String s) throws Exception {
            int p = s.indexOf(':');
            byte[] iv = Base64.decode(s.substring(0, p), Base64.NO_WRAP);
            byte[] data = Base64.decode(s.substring(p + 1), Base64.NO_WRAP);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
            return new String(c.doFinal(data), "UTF-8");
        }
    }
}
