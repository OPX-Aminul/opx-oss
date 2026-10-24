package com.opx.oss.license;

/**
 * JSONBin.io configuration for the license-key system.
 *
 * <p>JSONBin (https://jsonbin.io) hosts a single private JSON bin that holds the
 * license registry. The app talks to it with a <b>scoped Access Key</b> so the
 * master key is never shipped inside the APK.
 *
 * <p>Fill these in before shipping:
 * <ul>
 *   <li>{@link #BIN_ID} — the bin id, e.g. {@code 65f00000a1a2b3c4d5e6f7}.</li>
 *   <li>{@link #ACCESS_KEY} — an Access Key created in the JSONBin console,
 *       scoped (read + update) to the licenses bin only.</li>
 * </ul>
 *
 * <p>How to get them:
 * <ol>
 *   <li>Log in at https://jsonbin.io/app/account.</li>
 *   <li>Create a private bin named {@code licenses}; copy its id from the URL
 *       (the 24-char token after {@code /b/}).</li>
 *   <li>Open the session's Access Keys, create one for this bin with read &
 *       update permissions, and paste it below.</li>
 * </ol>
 */
public final class LicenseConfig {

    private LicenseConfig() {
    }

    /** JSONBin API root. */
    public static final String API_BASE = "https://api.jsonbin.io/v3/b";

    /** Id of the private bin that stores the license registry. */
    public static final String BIN_ID = "YOUR_JSONBIN_BIN_ID";

    /** Scoped Access Key for reading/updating that bin (not the master key). */
    public static final String ACCESS_KEY = "YOUR_JSONBIN_ACCESS_KEY";

    /** Key format is XXXX-XXXX-XXXX (12 alphanumerics). */
    public static final int KEY_CHUNK = 4;

    public static final int KEY_PARTS = 3;
}
