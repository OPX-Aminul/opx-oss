#!/usr/bin/env python3
import json
import os

path = "opxoss_manifest.json"

with open(path) as f:
    m = json.load(f)

app = m.setdefault("app", {})
app["versionCode"] = int(os.environ["VERSION_CODE"])
app["versionName"] = os.environ["VERSION_NAME"]
app["url"] = os.environ["APK_URL"]
app["sha256"] = os.environ["APK_SHA256"]
app["size"] = int(os.environ["APK_SIZE"])
app["changelog"] = os.environ.get("CHANGELOG", "").strip()

with open(path, "w") as f:
    json.dump(m, f, indent=2, ensure_ascii=False)
    f.write("\n")

print("Updated app block in manifest")
