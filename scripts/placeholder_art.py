#!/usr/bin/env python3
"""Writes the 16 x 16 placeholder textures (CD-ART-11): one flat color with a 1-pixel darker border.

Usage: py scripts/placeholder_art.py
Output: src/main/resources/assets/cobblemon_dynamics/textures/<path> for each row of PLACEHOLDERS.
"""
import pathlib
import struct
import zlib

SIZE = 16
ROOT = pathlib.Path(__file__).resolve().parent.parent
TEXTURES = ROOT / "src/main/resources/assets/cobblemon_dynamics/textures"

RED = (200, 40, 40)
WHITE = (240, 240, 240)
GOLD = (250, 210, 70)

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


def main():
    for path, base, corner in PLACEHOLDERS:
        out = TEXTURES / path
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(png(pixels(base, corner)))
        print(f"wrote {out.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
