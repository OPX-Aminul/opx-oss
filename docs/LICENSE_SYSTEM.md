# License-key system (JSONBin-backed)

The app ships with a 12-character license activation tied to a private
[JSONBin](https://jsonbin.io) bin. This document explains the design, where to
put your real credentials, and how to generate/blacklist keys.

## How it works

- The app has a first-launch intro. A new **License** step asks for the key in
  `XXXX-XXXX-XXXX` form. Activation requires an internet connection.
- On activation the app reads the bin, finds the key, and if it is unused it
  binds the key to the device (device id + model + brand + username are pushed
  to the registry). A key bound to one phone cannot be reused on another.
- In the background (on every launch, and every 8h via WorkManager) the app
  re-checks that its key has not been blacklisted. If it was, a blocking dialog
  is shown and the app cannot be used.
- `admin/license-admin.html` is a standalone admin page to view users/devices,
  generate keys in bulk, and ban/unban.

## Credentials

> The values you sent are bcrypt hashes, not the plaintext API secrets. The
> JSONBin API requires the **plaintext** key in the `X-Master-Key` /
> `X-Access-Key` header. Fill the real values in the two places below.

### 1. Set up JSONBin
1. Log in at `https://jsonbin.io/app/account/`.
2. Create a **private** bin named `licenses`. It will hold one JSON object with
   a `licenses` array (see schema below).
3. Copy the bin id (the 24-character token after `/b/` in the bin URL).
4. In the console, open **Access Keys** and create an access key scoped to this
   bin with **read + update** permissions. This is for the app and keeps the
   master key out of the APK.

### 2. Configure the app
Edit `app/src/main/java/com/opx/oss/license/LicenseConfig.java`:

| constant | value |
|----------|-------|
| `BIN_ID` | the bin id from step 3 |
| `ACCESS_KEY` | the scoped access key from step 4 |

### 3. Configure the admin page
Edit `admin/license-admin.html` (top of the `<script>` block):

| variable | value |
|----------|-------|
| `BIN_ID` | the same bin id |
| `MASTER_KEY` | your JSONBin **master key** (plaintext) |

Host `admin/license-admin.html` anywhere (its own URL). It is passwordless on
purpose, and it contains the master key — keep the URL private and never link it
publicly.

## Data schema

```jsonc
{
  "licenses": [
    {
      "key": "ABCD-EFGH-IJKL",
      "status": "unused",          // unused | used | banned
      "createdAt": "2025-10-24T06:00:00Z",
      "usedAt": null,
      "bannedAt": null,
      "banReason": "",
      "device": { "androidId": "", "model": "", "brand": "" }, // filled when used
      "owner": { "username": "" }
    }
  ]
}
```

## Development notes

- The app never touches the master key; it only uses the scoped access key.
  Still, any key shipped inside an APK can be extracted by decompiling. The
  scoped access key limits the damage to this one bin.
- Keys are generated randomly (`A–Z0–9`, no lookalikes excluded) and checked
  against existing keys to avoid collisions.
- Free JSONBin bins have size limits; the free tier keeps each bin small. If the
  user base outgrows a single bin, split into `licenses` (all keys) + `devices`
  (per-device state) and adjust `JsonBinClient`/`LicenseManager` accordingly.

## Files

- `app/.../license/LicenseConfig.java` — credentials/constants.
- `app/.../license/JsonBinClient.java` — JSONBin REST calls.
- `app/.../license/LicenseManager.java` — activation + online checks + logic.
- `app/.../license/LicenseScheduler.java` / `LicenseCheckWorker.java` — background check.
- `app/.../appintro/slides/SlideLicense.java` — intro activation step.
- `admin/license-admin.html` — standalone admin dashboard.
