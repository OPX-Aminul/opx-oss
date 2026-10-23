package com.opx.oss.ota;

public final class opxOSSEndpoints {

    public static final String GITHUB_REPO = "https://github.com/OPX-Aminul/opx-oss";

    public static final String MANIFEST_URL =
            "https://raw.githubusercontent.com/OPX-Aminul/opx-oss/main/opxoss_manifest.json";

    public static final String FALLBACK_CHROOT_64 =
            "https://github.com/OPX-Aminul/opx-oss/releases/download/chroot-main/chroot64-debian.tar.gz";

    private static final String ROOTLESS_BASE =
            "https://github.com/OPX-Aminul/opx-oss/releases/download/rootless-main/";
    public static final String FALLBACK_ROOTLESS_QEMU     = ROOTLESS_BASE + "qemu-system-aarch64";
    public static final String FALLBACK_ROOTLESS_KERNEL   = ROOTLESS_BASE + "Image";
    public static final String FALLBACK_ROOTLESS_LIBSLIRP = ROOTLESS_BASE + "libslirp.so";
    public static final String FALLBACK_ROOTLESS_INITRD   = ROOTLESS_BASE + "initrd.img";
    public static final String FALLBACK_ROOTLESS_ROOTFS   = ROOTLESS_BASE + "rootfs.imgz";

    public static final String PREFS = "opxoss_ota";

    private opxOSSEndpoints() {
    }
}
