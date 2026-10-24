package com.opx.oss.license;

import android.content.Context;
import android.os.Build;
import android.provider.Settings;

import com.opx.oss.utils.Core;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * License-key activation and online validation against the JSONBin registry.
 *
 * <p>State the app keeps locally (in the encrypted {@link Core} prefs):
 * <ul>
 *   <li>{@code license_key} — the 12-char key bound to this install.</li>
 *   <li>{@code license_device_id} — the device id recorded at activation.</li>
 *   <li>{@code license_activated} — true once a key is bound.</li>
 *   <li>{@code license_banned} — true when the key is blacklisted.</li>
 * </ul>
 *
 * <p>The bin is an object with a single {@code licenses} array. Each entry:
 * <pre>
 * { "key":"ABCD-EFGH-IJKL", "status":"unused|used|banned",
 *   "createdAt":"...", "usedAt":"...", "bannedAt":"...", "banReason":"",
 *   "device":{"androidId":"...","model":"...","brand":"..."},
 *   "owner":{"username":"..."} }
 * </pre>
 */
public final class LicenseManager {

    public static final String KEY_KEY = "license_key";
    public static final String KEY_DEVICE_ID = "license_device_id";
    public static final String KEY_ACTIVATED = "license_activated";
    public static final String KEY_BANNED = "license_banned";

    public enum Activation {
        ACTIVATED,
        ALREADY_ACTIVE,
        INVALID,
        USED_ELSEWHERE,
        BANNED
    }

    public enum Check {
        OK,
        BANNED,
        NO_LICENSE
    }

    private LicenseManager() {
    }

    public static String deviceId(Context context) {
        try {
            return Settings.Secure.getString(context.getContentResolver(),
                    Settings.Secure.ANDROID_ID);
        } catch (Throwable t) {
            return "";
        }
    }

    /** Uppercase, alphanumerics only (dashes stripped). */
    public static String normalizeKey(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = Character.toUpperCase(raw.charAt(i));
            if ((c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) sb.append(c);
        }
        return sb.toString();
    }

    /** Formats 12 alphanumerics as XXXX-XXXX-XXXX. */
    public static String formatKey(String normalized) {
        if (normalized == null || normalized.length() != 12) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < normalized.length(); i++) {
            if (i > 0 && i % 4 == 0) sb.append('-');
            sb.append(normalized.charAt(i));
        }
        return sb.toString();
    }

    public static boolean looksValid(String raw) {
        return normalizeKey(raw).length() == 12;
    }

    // ---- activation -------------------------------------------------------

    /**
     * Validates a key against the registry and binds it to this device.
     * Run off the UI thread. Throws {@link IOException} only on network/parse
     * failures so callers can distinguish "offline" from a logical result.
     */
    public static Activation activate(Context context, String enteredKey) throws IOException {
        String normalized = normalizeKey(enteredKey);
        if (normalized.length() != 12) return Activation.INVALID;

        JSONObject record = JsonBinClient.readBin(context);
        JSONArray licenses = record.optJSONArray("licenses");
        if (licenses == null) {
            licenses = new JSONArray();
            try {
                record.put("licenses", licenses);
            } catch (JSONException ignored) {
            }
        }

        String formatted = formatKey(normalized);
        String deviceId = deviceId(context);
        String now = stamp();

        JSONObject target = null;
        int targetIndex = -1;
        try {
            for (int i = 0; i < licenses.length(); i++) {
                JSONObject item = licenses.optJSONObject(i);
                if (item == null) continue;
                if (formatted.equalsIgnoreCase(item.optString("key"))) {
                    target = item;
                    targetIndex = i;
                    break;
                }
            }
            if (target == null) return Activation.INVALID;

            String status = target.optString("status", "unused");
            if ("banned".equals(status)) return Activation.BANNED;

            String usedDevice = target.optJSONObject("device") == null
                    ? "" : target.optJSONObject("device").optString("androidId");

            boolean alreadyBoundToMe = "used".equals(status)
                    && deviceId != null && deviceId.equals(usedDevice);

            if ("used".equals(status) && !alreadyBoundToMe) {
                return Activation.USED_ELSEWHERE;
            }

            target.put("status", "used");
            if (!alreadyBoundToMe) {
                target.put("usedAt", now);
                JSONObject device = target.optJSONObject("device");
                if (device == null) {
                    device = new JSONObject();
                    target.put("device", device);
                }
                device.put("androidId", deviceId);
                device.put("model", Build.MODEL);
                device.put("brand", Build.MANUFACTURER);
                JSONObject owner = target.optJSONObject("owner");
                if (owner == null) {
                    owner = new JSONObject();
                    target.put("owner", owner);
                }
                owner.put("username", new Core(context).getString("username"));
            }
            if (target.has("lastSeen")) target.put("lastSeen", now);

            JsonBinClient.replaceBin(context, record);

            persistLocal(context, formatted, deviceId, false);
            return alreadyBoundToMe ? Activation.ALREADY_ACTIVE : Activation.ACTIVATED;
        } catch (JSONException e) {
            throw new IOException("Bad registry payload", e);
        }
    }

    // ---- online check -----------------------------------------------------

    /**
     * Re-checks the bound key against the registry. Run off the UI thread.
     * Updates the local banned flag. Throws {@link IOException} on network
     * failures so callers can treat them as "could not verify".
     */
    public static Check checkNow(Context context) throws IOException {
        Core core = new Core(context);
        if (!core.getBoolean(KEY_ACTIVATED)) return Check.NO_LICENSE;

        JSONObject record = JsonBinClient.readBin(context);
        JSONArray licenses = record.optJSONArray("licenses");
        String bound = core.getString(KEY_KEY);

        boolean banned = false;
        if (licenses != null && bound != null && !bound.isEmpty()) {
            for (int i = 0; i < licenses.length(); i++) {
                JSONObject item = licenses.optJSONObject(i);
                if (item != null && bound.equalsIgnoreCase(item.optString("key"))) {
                    banned = "banned".equals(item.optString("status"));
                    break;
                }
            }
        }
        core.putBoolean(KEY_BANNED, banned);
        return banned ? Check.BANNED : Check.OK;
    }

    // ---- local helpers ----------------------------------------------------

    public static boolean isActivated(Context context) {
        return new Core(context).getBoolean(KEY_ACTIVATED);
    }

    public static boolean isBanned(Context context) {
        return new Core(context).getBoolean(KEY_BANNED);
    }

    public static String boundKey(Context context) {
        return new Core(context).getString(KEY_KEY);
    }

    public static void persistLocal(Context context, String formattedKey,
                                   String deviceId, boolean banned) {
        Core core = new Core(context);
        core.putString(KEY_KEY, formattedKey);
        core.putString(KEY_DEVICE_ID, deviceId);
        core.putBoolean(KEY_ACTIVATED, true);
        core.putBoolean(KEY_BANNED, banned);
    }

    public static void clearLocal(Context context) {
        Core core = new Core(context);
        for (String key : new String[]{
                KEY_KEY, KEY_DEVICE_ID, KEY_ACTIVATED, KEY_BANNED}) {
            core.remove(key);
        }
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
                .format(new Date());
    }
}
