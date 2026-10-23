#!/usr/bin/env python3
"""Apply the Xiaomi/MIUI USB speed correction to a Linux kernel hub.c.

Xiaomi/MIUI devices misreport full-speed USB devices as low-speed.
The device descriptor still says maxpacket=64, which is impossible for
a real low-speed device (spec allows <=8) and the kernel rejects it as
"Invalid ep0 maxpacket: 64".

This inserts an else-if branch before that error block: when a low-speed
device claims maxpacket=64 we trust the descriptor and re-qualify it as
full-speed, mirroring upstream's Podroid fix but written to match whatever
kernel tree it is pointed at (it locates the block structurally rather
than by an exact version string).

Usage: apply-xiaomi-hub-patch.py <path/to/drivers/usb/core/hub.c>
"""

import sys

FNAME = sys.argv[1] if len(sys.argv) > 1 else "drivers/usb/core/hub.c"

with open(FNAME) as f:
    content = f.read()

anchor = "\t} else {\n\t\t/* Initial guess is wrong and descriptor's value is invalid */\n"
if anchor not in content:
    print(f"ERROR: error block not found in {FNAME}", file=sys.stderr)
    sys.exit(1)

if "Xiaomi/MIUI" in content:
    print("hub.c already patched, leaving it alone")
    sys.exit(0)

old_block = (
    "\t} else {\n"
    "\t\t/* Initial guess is wrong and descriptor's value is invalid */\n"
    "\t\tdev_err(&udev->dev, \"Invalid ep0 maxpacket: %d\\n\", maxp0);\n"
    "\t\tretval = -EMSGSIZE;\n"
    "\t\tgoto fail;\n"
    "\t}"
)

new_block = (
    "\t} else if (udev->speed == USB_SPEED_LOW && i == 64) {\n"
    "\t\t/* Xiaomi/MIUI host stack bug: full-speed devices misreported\n"
    "\t\t * as low-speed. Accept maxpacket=64 and treat as full-speed\n"
    "\t\t * (low-speed maxpacket must be <=8 per USB spec).\n"
    "\t\t */\n"
    "\t\tudev->speed = USB_SPEED_FULL;\n"
    "\t\tudev->ep0.desc.wMaxPacketSize = cpu_to_le16(i);\n"
    "\t\tusb_ep0_reinit(udev);\n"
    "\t} else {\n"
    "\t\t/* Initial guess is wrong and descriptor's value is invalid */\n"
    "\t\tdev_err(&udev->dev, \"Invalid ep0 maxpacket: %d\\n\", maxp0);\n"
    "\t\tretval = -EMSGSIZE;\n"
    "\t\tgoto fail;\n"
    "\t}"
)

if old_block not in content:
    print(f"ERROR: exact error block not found in {FNAME}", file=sys.stderr)
    sys.exit(1)

content = content.replace(old_block, new_block, 1)

with open(FNAME, "w") as f:
    f.write(content)

print("Kernel hub.c Xiaomi/MIUI patch applied")
