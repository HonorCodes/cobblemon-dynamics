#!/usr/bin/env python3
"""Writes the 16 x 16 placeholder textures (CD-ART-11).

Blocks and items are one flat color with a 1-pixel darker border. The state icons are shapes with a dark
outline on a transparent background, each a different shape, so they differ without color (CD-UI-08).

Usage: py scripts/placeholder_art.py
Output: src/main/resources/assets/cobblemon_dynamics/textures/<path> for each row of PLACEHOLDERS and STATE_ICONS.
"""
import math
import pathlib
import struct
import zlib

SIZE = 16
ROOT = pathlib.Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src/main/resources/assets/cobblemon_dynamics/textures"

RED = (200, 40, 40)
WHITE = (240, 240, 240)
GOLD = (250, 210, 70)
YELLOW = (255, 236, 64)

# (path under textures/, base color, 2 x 2 corner rows or None). Every row is a placeholder, replaced in M8.
PLACEHOLDERS = [
    ("item/work_leash.png", (122, 84, 48), (RED, WHITE)),
    ("block/hay_bed_top.png", (214, 178, 76), None),
    ("block/hay_bed_side.png", (168, 132, 52), None),
    ("block/wool_bed_top.png", (232, 232, 228), None),
    ("block/wool_bed_side.png", (186, 186, 182), None),
    ("block/plush_bed_top.png", (196, 92, 160), (GOLD, GOLD)),
    ("block/plush_bed_side.png", (150, 64, 122), None),
    ("block/starlit_bed_top.png", (44, 52, 128), (GOLD, WHITE)),
    ("block/starlit_bed_side.png", (30, 34, 92), None),
    ("block/charge_cell.png", (184, 108, 66), (YELLOW, YELLOW)),
]


def darker(rgb):
    return tuple(c * 3 // 5 for c in rgb)


def pixels(base, corner):
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            if x in (0, SIZE - 1) or y in (0, SIZE - 1):
                row.append(darker(base))
            elif corner is not None and x in (1, 2) and y in (1, 2):
                row.append(corner[y - 1])
            else:
                row.append(base)
        rows.append(row)
    return rows


def chunk(kind: bytes, data: bytes) -> bytes:
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)


def png(rows) -> bytes:
    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 2, 0, 0, 0)
    raw = b"".join(b"\x00" + bytes(c for rgb in row for c in rgb) for row in rows)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


OUTLINE = (24, 24, 24)


def disc(radius):
    return {(x, y) for x in range(SIZE) for y in range(SIZE) if math.dist((x + 0.5, y + 0.5), (8, 8)) <= radius}


def box(x0, y0, x1, y1):
    return {(x, y) for x in range(x0, x1 + 1) for y in range(y0, y1 + 1)}


def near_curve(points, width):
    return {(x, y) for x in range(SIZE) for y in range(SIZE) if any(math.dist((x + 0.5, y + 0.5), p) <= width for p in points)}


def arrow():
    head = {(x, y) for x in range(8, 14) for y in range(SIZE) if abs(y + 0.5 - 8) <= 13.5 - x}
    return box(2, 6, 8, 9) | head


def gear():
    ring = {(x, y) for x in range(SIZE) for y in range(SIZE) if 2.6 <= math.dist((x + 0.5, y + 0.5), (8, 8)) <= 5.2}
    teeth = set()
    for x in range(SIZE):
        for y in range(SIZE):
            dx, dy = x + 0.5 - 8, y + 0.5 - 8
            r = math.hypot(dx, dy)
            angle = math.degrees(math.atan2(dy, dx)) % 45
            if 5.2 <= r <= 7.2 and min(angle, 45 - angle) <= 11:
                teeth.add((x, y))
    return ring | teeth


def droop():
    curve = [(x / 4, 4.5 + 0.17 * (x / 4 - 8) ** 2) for x in range(8, 57)]
    return near_curve(curve, 1.3)


def sleep_z():
    diagonal = [(12.5 - 9 * i / 40, 5.5 + 5 * i / 40) for i in range(41)]
    return box(3, 3, 12, 4) | box(3, 11, 12, 12) | near_curve(diagonal, 1.1)


# (path under textures/, color, shape). StateIcons.kt maps the eight worker states to these seven.
STATE_ICONS = [
    ("gui/state/idle.png", (170, 170, 170), disc(4.5)),
    ("gui/state/traveling.png", (90, 160, 240), arrow()),
    ("gui/state/working.png", (100, 200, 90), gear()),
    ("gui/state/standby.png", (235, 200, 60), box(4, 3, 6, 12) | box(9, 3, 11, 12)),
    ("gui/state/tired.png", (240, 140, 50), droop()),
    ("gui/state/sleeping.png", (160, 120, 235), sleep_z()),
    ("gui/state/healing.png", (240, 110, 170), box(6, 2, 9, 13) | box(2, 6, 13, 9)),
]


def icon_pixels(color, shape):
    """RGBA rows: the shape in its color, a dark 1-pixel outline around it, and transparent elsewhere."""
    rows = []
    for y in range(SIZE):
        row = []
        for x in range(SIZE):
            if (x, y) in shape:
                row.append((*color, 255))
            elif any((x + dx, y + dy) in shape for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                row.append((*OUTLINE, 255))
            else:
                row.append((0, 0, 0, 0))
        rows.append(row)
    return rows


def png_rgba(rows) -> bytes:
    header = struct.pack(">IIBBBBB", SIZE, SIZE, 8, 6, 0, 0, 0)
    raw = b"".join(b"\x00" + bytes(c for rgba in row for c in rgba) for row in rows)
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b"")


def write(path, data):
    out = TEXTURES / path
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(data)
    print(f"wrote {out.relative_to(ROOT)}")


def main():
    for path, base, corner in PLACEHOLDERS:
        write(path, png(pixels(base, corner)))
    for path, color, shape in STATE_ICONS:
        write(path, png_rgba(icon_pixels(color, shape)))


if __name__ == "__main__":
    main()
