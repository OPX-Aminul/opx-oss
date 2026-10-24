#!/usr/bin/env python3
"""Apply the Xiaomi/MIUI USB speed correction to QEMU's host-libusb.c.

This is the second half of the Xiaomi/MIUI fix (the first is the kernel
hub.c patch). MIUI's host stack misreports full-speed devices as low-speed,
so the device descriptor arrives with maxpacket=64, which is illegal for a
real low-speed device (spec allows <=8).

Upstream QEMU already rewrites the descriptor's maxpacket to 64 for
superspeed devices (the usb3ep0quirk). We extend that block: when the
device is still modelled as low-speed but the descriptor claims maxpacket=64
we trust the descriptor and re-qualify it as full-speed, mirroring the
kernel hub.c fix in this emulator host connector.

Usage: apply-xiaomi-qemu-patch.py <path/to/hw/usb/host-libusb.c>
"""

import sys

FNAME = sys.argv[1] if len(sys.argv) > 1 else "hw/usb/host-libusb.c"

with open(FNAME) as f:
    content = f.read()

anchor = "\n        if (r->usb3ep0quirk && xfer->actual_length >= 18 &&\n"
if anchor not in content:
    print(f"ERROR: usb3ep0quirk block not found in {FNAME}", file=sys.stderr)
    sys.exit(1)

if "Xiaomi/MIUI" in content:
    print("host-libusb.c already patched, leaving it alone")
    sys.exit(0)

old_block = (
    "\n        if (r->usb3ep0quirk && xfer->actual_length >= 18 &&\n"
    "            r->cbuf[7] == 9) {\n"
    "            r->cbuf[7] = 64;\n"
    "        }\n"
)

new_block = (
    "\n        if (r->usb3ep0quirk && xfer->actual_length >= 18 &&\n"
    "            r->cbuf[7] == 9) {\n"
    "            r->cbuf[7] = 64;\n"
    "        }\n"
    "        /* Xiaomi/MIUI host stack bug: full-speed devices are misreported\n"
    "         * as low-speed. The descriptor still claims maxpacket=64, which is\n"
    "         * impossible for a real low-speed device (spec allows <=8), so\n"
    "         * re-qualify it as full-speed like the kernel hub.c patch does.\n"
    "         */\n"
    "        if (udev->speed == USB_SPEED_LOW && xfer->actual_length >= 18 &&\n"
    "            r->cbuf[7] == 64) {\n"
    "            udev->speed = USB_SPEED_FULL;\n"
    "        }\n"
)

if old_block not in content:
    print(f"ERROR: exact usb3ep0quirk block not found in {FNAME}", file=sys.stderr)
    sys.exit(1)

content = content.replace(old_block, new_block, 1)

with open(FNAME, "w") as f:
    f.write(content)

print("QEMU host-libusb.c Xiaomi/MIUI patch applied")
