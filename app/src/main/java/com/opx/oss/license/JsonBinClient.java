package com.opx.oss.license;

import android.content.Context;

import com.opx.oss.ota.Net;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.json.JSONObject;

/**
 * Thin wrapper over JSONBin.io's v3 Bins REST API.
 *
 * <p>Only the operations the app needs are exposed: read the whole bin and
 * replace the whole bin. Authentication uses the scoped Access Key from
 * {@link LicenseConfig}, carried in the {@code X-Access-Key} header.
 */
public final class JsonBinClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private JsonBinClient() {
    }

    /** Reads the full license registry (the bin's record). */
    public static JSONObject readBin(Context context) throws IOException {
        String url = LicenseConfig.API_BASE + "/" + LicenseConfig.BIN_ID;
        Request request = baseBuilder(url)
                .header("X-Bin-Meta", "false")
                .build();
        return executeJson(context, request);
    }

    /** Replaces the whole bin with {@code record} and returns the stored result. */
    public static JSONObject replaceBin(Context context, JSONObject record) throws IOException {
        String url = LicenseConfig.API_BASE + "/" + LicenseConfig.BIN_ID;
        RequestBody body = RequestBody.create(record.toString(), JSON);
        Request request = baseBuilder(url)
                .put(body)
                .build();
        return executeJson(context, request);
    }

    private static okhttp3.Request.Builder baseBuilder(String url) {
        return new Request.Builder()
                .url(url)
                .header("X-Access-Key", LicenseConfig.ACCESS_KEY)
                .header("Content-Type", "application/json")
                .header("User-Agent", Net.userAgent());
    }

    private static JSONObject executeJson(Context context, Request request) throws IOException {
        try (Response response = Net.client().newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("HTTP " + response.code());
            }
            String text = response.body().string();
            try {
                return new JSONObject(text);
            } catch (Exception e) {
                throw new IOException("Bad JSON payload", e);
            }
        }
    }
}
