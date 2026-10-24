#!/bin/bash
set -euo pipefail

HERE=$(cd "$(dirname "$0")" && pwd)
. "$HERE/../lib/common.sh"

QEMU_VER=${QEMU_VER:-11.0.2}
GLIB_VER=${GLIB_VER:-2.80.5}
PCRE2_VER=${PCRE2_VER:-10.44}
PIXMAN_VER=${PIXMAN_VER:-0.42.2}
LIBUSB_VER=${LIBUSB_VER:-1.0.27}
SLIRP_REV=${SLIRP_REV:-26be815b86e8d49add8c9a8b320239b9594ff03d}
JOBS=${JOBS:-$(nproc)}
API=${API:-29}
PAGE=${PAGE:-16384}
O=${O:-$WORK_DIR/qemu}
DEST=$OUT_DIR/prebuilt
DEPS=$O/deps

if [ -z "${NDK:-}" ]; then
	for c in "${ANDROID_NDK_HOME:-}" "${ANDROID_NDK_ROOT:-}" \
	         "$HOME"/Android/Sdk/ndk/* "$HOME"/Library/Android/sdk/ndk/* \
	         /usr/lib/android-ndk /opt/android-ndk* \
	         "${TMPDIR:-/tmp}"/ndk/android-ndk-*; do
		[ -n "$c" ] && [ -x "$c/toolchains/llvm/prebuilt/linux-x86_64/bin/clang" ] && NDK=$c
	done
fi
TOOL=${NDK:-}/toolchains/llvm/prebuilt/linux-x86_64/bin
[ -x "$TOOL/clang" ] || die "no Android NDK found. Set NDK=/path/to/android-ndk-rXX"

need curl tar xz meson ninja python3 pkg-config make

mkdir -p "$O" "$DEST" "$DEPS"

CROSS=$TOOL/aarch64-linux-android$API

# Deps install into $DEPS; put their pkg-config on the search path for all
# meson dependency builds (glib needs pcre2, something meson resolves via
# pkg-config). Keep this BEFORE the glib build.
export PKG_CONFIG_PATH=$DEPS/lib/pkgconfig
export PKG_CONFIG_LIBDIR=$DEPS/lib/pkgconfig:$TOOL/../sysroot/usr/lib/aarch64-linux-android/$API/pkgconfig

# A meson cross file shared by QEMU and the dependency builds. Every library
# QEMU links against must be compiled for the target (aarch64-linux-android),
# not pulled from the host -- host glib/pixman are x86_64 and must not be used.
cat > "$O/cross.ini" <<EOF
[binaries]
c = '$CROSS-clang'
cpp = '$CROSS-clang++'
ar = '$TOOL/llvm-ar'
strip = '$TOOL/llvm-strip'
pkgconfig = 'pkg-config'

[built-in options]
c_args = ['-fPIC']
cpp_args = ['-fPIC']

[host_machine]
system = 'linux'
cpu_family = 'aarch64'
cpu = 'aarch64'
endian = 'little'
EOF

fetch_unpack() {
	local dir=$1 url=$2 file=$3
	[ -d "$dir" ] && return
	if [ ! -f "$O/$file" ]; then
		say "fetching $file"
		curl -fL --progress-bar -o "$O/$file.part" "$url"
		mv "$O/$file.part" "$O/$file"
	fi
	say "unpacking $file"
	mkdir -p "$dir"
	tar -C "$dir" --strip-components=1 -xf "$O/$file"
}

# pcre2 (autotools) -- glib 2.80 requires it for its regex engine (gregex.c),
# and meson finds it via pkg-config, so it must be built for the target first.
if [ ! -f "$DEPS/lib/libpcre2-8.a" ]; then
	SRC_PCRE2=$O/src-pcre2
	fetch_unpack "$SRC_PCRE2" \
		"https://github.com/PCRE2Project/pcre2/releases/download/pcre2-$PCRE2_VER/pcre2-$PCRE2_VER.tar.bz2" \
		"pcre2-$PCRE2_VER.tar.bz2"
	say "building pcre2 $PCRE2_VER for aarch64-linux-android"
	cd "$SRC_PCRE2"
	CC="$CROSS-clang" CFLAGS="-fPIC -O2" ./configure \
		--host=aarch64-linux-android \
		--prefix="$DEPS" \
		--disable-shared --enable-static \
		--enable-pcre2-8 \
		--disable-pcre2-16 --disable-pcre2-32 \
		--disable-jit
	make -j"$JOBS" && make install
	cd "$HERE"
fi

# glib (meson build). Its libffi subproject builds for the target too.
# Guard on glib's own pkg-config file, NOT on lib/pkgconfig existing --
# pcre2 (built above) already creates $DEPS/lib/pkgconfig, which would
# otherwise cause glib to be skipped on a cached run and leave
# glib-2.0.pc absent so slirp can't find it.
if [ ! -f "$DEPS/lib/pkgconfig/glib-2.0.pc" ]; then
	SRC_GLIB=$O/src-glib
	glib_major_minor="${GLIB_VER%.*}"
	fetch_unpack "$SRC_GLIB" \
		"https://download.gnome.org/sources/glib/$glib_major_minor/glib-$GLIB_VER.tar.xz" \
		"glib-$GLIB_VER.tar.xz"
	say "building glib $GLIB_VER for aarch64-linux-android"
	meson setup "$O/build-glib" "$SRC_GLIB" \
		--cross-file "$O/cross.ini" \
		--default-library=static \
		--prefix "$DEPS" \
		--libdir lib \
		-Dtests=false \
		-Dnls=disabled \
		-Dlibmount=disabled \
		-Dselinux=disabled \
		-Dman=false \
		-Dgtk_doc=false \
		-Dintrospection=disabled
	ninja -C "$O/build-glib" -j"$JOBS"
	ninja -C "$O/build-glib" install
fi

# pixman (meson build)
if [ ! -d "$DEPS/include/pixman-1" ]; then
	SRC_PIXMAN=$O/src-pixman
	fetch_unpack "$SRC_PIXMAN" \
		"https://www.cairographics.org/releases/pixman-$PIXMAN_VER.tar.gz" \
		"pixman-$PIXMAN_VER.tar.gz"
	say "building pixman $PIXMAN_VER for aarch64-linux-android"
	meson setup "$O/build-pixman" "$SRC_PIXMAN" \
		--cross-file "$O/cross.ini" \
		--default-library=static \
		--prefix "$DEPS" \
		--libdir lib \
		-Dtests=disabled \
		-Da64-neon=disabled
	ninja -C "$O/build-pixman" -j"$JOBS"
	ninja -C "$O/build-pixman" install
fi

# libusb (autotools) -- required so the usb-host device (host-libusb.c)
# is enabled at all; without it the quirk would be dead code.
if [ ! -f "$DEPS/lib/libusb-1.0.a" ]; then
	SRC_LIBUSB=$O/src-libusb
	fetch_unpack "$SRC_LIBUSB" \
		"https://github.com/libusb/libusb/releases/download/v$LIBUSB_VER/libusb-$LIBUSB_VER.tar.bz2" \
		"libusb-$LIBUSB_VER.tar.bz2"
	say "building libusb $LIBUSB_VER for aarch64-linux-android"
	cd "$SRC_LIBUSB"
	CC="$CROSS-clang" ./configure \
		--host=aarch64-linux-android \
		--prefix="$DEPS" \
		--disable-shared --enable-static \
		--disable-udev --disable-examples --disable-tests
	make -j"$JOBS" && make install
	cd "$HERE"
fi

# libslirp (meson build) -- the shipped libqemu.so links this as a
# SEPARATE shared library (NEEDED: libslirp.so), built from QEMU's own
# subproject. Build it shared so the app can load it alongside libqemu.so.
if [ ! -f "$DEPS/lib/libslirp.so" ]; then
	SRC_SLIRP=$O/src-slirp
	fetch_unpack "$SRC_SLIRP" \
		"https://gitlab.freedesktop.org/slirp/libslirp/-/archive/$SLIRP_REV/libslirp-$SLIRP_REV.tar.bz2" \
		"libslirp-$SLIRP_REV.tar.bz2"
	say "building libslirp $SLIRP_REV for aarch64-linux-android"
	meson setup "$O/build-slirp" "$SRC_SLIRP" \
		--cross-file "$O/cross.ini" \
		--default-library=shared \
		--prefix "$DEPS" \
		--libdir lib
	ninja -C "$O/build-slirp" -j"$JOBS"
	ninja -C "$O/build-slirp" install
fi

SRC=$O/src
if [ ! -d "$SRC" ]; then
	series=${QEMU_VER%%.*}
	tarball=qemu-$QEMU_VER.tar.xz
	fetch_unpack "$SRC" \
		"https://download.qemu.org/$tarball" \
		"$tarball"
fi
[ -f "$SRC/hw/usb/host-libusb.c" ] || die "no QEMU source at $SRC"

if [ -z "${SKIP_XIAOMI_PATCH:-0}" ]; then
	say "applying Xiaomi/MIUI USB speed patch (host-libusb.c)"
	python3 "$IMAGES_DIR/../scripts/apply-xiaomi-qemu-patch.py" "$SRC/hw/usb/host-libusb.c" \
		|| die "Xiaomi host-libusb.c patch failed (set SKIP_XIAOMI_PATCH=1 to ignore)"
fi

say "configuring QEMU $QEMU_VER ($JOBS jobs)"
meson setup "$O/build" "$SRC" \
	--cross-file "$O/cross.ini" \
	--prefix "$O/install" \
	--buildtype=release \
	--strip \
	-Db_lto=true \
	-Db_pie=true \
	-Dsystem=disabled \
	-Dtools=disabled \
	-Ddocs=disabled \
	-Dguest_agent=disabled \
	-Dfdt=internal \
	-Dslirp=enabled \
	-Dusb-redir=disabled \
	-Daudio=disabled \
	-Dvnc=disabled \
	-Dgtk=disabled \
	-Dopengl=disabled \
	-Dxkbcommon=disabled \
	-Dmodules=disabled \
	-Dqemu-vhost-user=disabled

say "building qemu-system-aarch64 with $JOBS jobs"
ninja -C "$O/build" -j"$JOBS" qemu-system-aarch64

# The shipped libqemu.so is a dynamic PIE that execs directly under bionic
# (it has a PT_INTERP of /system/bin/linker64 and NEEDED libslirp.so/libz.so
# /libm.so/libdl.so/libc.so). Do NOT require a static binary like umusb.
"$TOOL/llvm-readelf" -l "$O/build/qemu-system-aarch64" 2>/dev/null | grep -q INTERP \
	|| warn "qemu-system-aarch64 has no PT_INTERP; the app execs it via linker64"
"$TOOL/llvm-readelf" -d "$O/build/qemu-system-aarch64" 2>/dev/null | grep -q libslirp.so \
	|| warn "qemu-system-aarch64 does not NEEDED libslirp.so (expected dynamic slirp)"
"$TOOL/llvm-strip" "$O/build/qemu-system-aarch64" 2>/dev/null \
	|| warn "llvm-strip failed; shipping unstripped"

leak=$(grep -a -o -E '/(home|root|Users)/[A-Za-z0-9._-]+' "$O/build/qemu-system-aarch64" | sort -u || true)
[ -z "$leak" ] || die "the binary carries build-machine paths:
$leak"

for f in libqemu.so qemu-system-aarch64; do
	cp -f "$O/build/qemu-system-aarch64" "$DEST/$f"
	record_artifact "$DEST/$f"
done
cp -f "$DEPS/lib/libslirp.so" "$DEST/libslirp.so"
record_artifact "$DEST/libslirp.so"

say "done"
printf '%-22s %s\n' "libqemu.so:" "$DEST/libqemu.so"
printf '%-22s %s\n' "qemu-system-aarch64:" "$DEST/qemu-system-aarch64"
printf '%-22s %s\n' "libslirp.so:" "$DEST/libslirp.so"
printf '%-22s %s\n' "size:"  "$(human "$(stat -c%s "$DEST/libqemu.so")")"
printf '%-22s %s\n' "sha256:" "$(sha256_of "$DEST/libqemu.so")"
printf '\nApp ships these as libqemu.so + libslirp.so in jniLibs/arm64-v8a; nativeLibraryDir\n'
printf 'is the only directory an app may exec from, and it only keeps lib*.so. The app\n'
printf 'execs libqemu.so under linker64 with LD_LIBRARY_PATH=nativeLibraryDir, so libslirp\n'
printf 'must remain a separate shared library (as in the original build).\n'
