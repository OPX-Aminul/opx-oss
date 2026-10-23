#!/usr/bin/env python3
"""Update opxoss_manifest.json.

Usage:
  scripts/update-manifest.py --repo X/Y --base-url URL --out opxoss_manifest.json [options]

Binary options (all optional; only the supplied ones are updated):
  --tag-chroot FILE      chroot64-debian.tar.gz  -> chroot tag
  --tag-qemu-kernel FILE Image                   -> qemu tag
  --tag-qemu-initrd FILE initrd.img              -> qemu tag
  --tag-qemu-rootfs FILE rootfs.imgz             -> qemu tag
  --tag-qemu-qemu FILE   qemu-system-aarch64     -> qemu tag
  --tag-qemu-libslirp FILE libslirp.so           -> qemu tag
  --tag-uml-linux FILE   linux-uml               -> uml tag
  --tag-uml-stub FILE    stub_exe                -> uml tag
"""

import argparse
import hashlib
import json
import os
import sys


def sha256_file(path: str) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def info(tag: str, filename: str, path: str, base_url: str) -> dict:
    return {
        "url": f"{base_url}/{tag}/{filename}",
        "sha256": sha256_file(path),
        "size": os.path.getsize(path),
    }


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("manifest", nargs="?", default="opxoss_manifest.json")
    p.add_argument("--out", default=None)
    p.add_argument("--repo", default=None)
    p.add_argument("--base-url", required=True)
    p.add_argument("--tag-chroot", default=None)
    p.add_argument("--tag-qemu-kernel", default=None)
    p.add_argument("--tag-qemu-initrd", default=None)
    p.add_argument("--tag-qemu-rootfs", default=None)
    p.add_argument("--tag-qemu-qemu", default=None)
    p.add_argument("--tag-qemu-libslirp", default=None)
    p.add_argument("--tag-uml-linux", default=None)
    p.add_argument("--tag-uml-stub", default=None)
    p.add_argument("--app-version-code", type=int, default=None)
    p.add_argument("--app-version-name", default=None)
    p.add_argument("--apk-file", default=None)
    args = p.parse_args()

    out = args.out or args.manifest
    base = args.base_url.rstrip("/")

    with open(args.manifest, "r") as f:
        m = json.load(f)

    qemu = [
        ("qemu", args.tag_qemu_qemu, "qemu-system-aarch64"),
        ("libslirp", args.tag_qemu_libslirp, "libslirp.so"),
        ("kernel", args.tag_qemu_kernel, "Image"),
        ("initrd", args.tag_qemu_initrd, "initrd.img"),
        ("rootfs", args.tag_qemu_rootfs, "rootfs.imgz"),
    ]
    uml = [
        ("uml_kernel", args.tag_uml_linux, "linux-uml"),
        ("uml_stub", args.tag_uml_stub, "stub_exe"),
    ]

    if args.tag_chroot or any(path for _, path, _ in qemu + uml):
        core = m.setdefault("core", {})
        if args.tag_chroot:
            for block in ("debian", "debian_v2"):
                b = core.setdefault(block, {})
                b["chroot64"] = info("chroot", "chroot64-debian.tar.gz", args.tag_chroot, base)
                if block == "debian_v2":
                    b["min_version_code"] = 650

        has_qemu = any(path for _, path, _ in qemu)
        has_uml = any(path for _, path, _ in uml)

        if has_qemu:
            rl = m.setdefault("rootless", {})
            for key, path, filename in qemu:
                if path:
                    rl[key] = info("qemu", filename, path, base)

        if has_qemu or has_uml:
            rv2 = m.setdefault("rootless_v2", {})
            rv2["min_version_code"] = 650
            for key, path, filename in qemu:
                if path:
                    rv2[key] = info("qemu", filename, path, base)
            for key, path, filename in uml:
                if path:
                    rv2[key] = info("uml", filename, path, base)

    if args.app_version_code is not None:
        app = m.setdefault("app", {})
        app["versionCode"] = args.app_version_code
        app["versionName"] = args.app_version_name
        if args.apk_file:
            app["url"] = (
                f"{base}/releases/download/v{args.app_version_name}/"
                f"opxOSS-{args.app_version_name}.apk"
            )
            app["sha256"] = sha256_file(args.apk_file)
            app["size"] = os.path.getsize(args.apk_file)

    with open(out, "w") as f:
        json.dump(m, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"Wrote {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
