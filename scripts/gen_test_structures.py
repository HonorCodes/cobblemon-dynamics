#!/usr/bin/env python3
"""Writes empty GameTest structure templates with a stone floor at y = 0.

Usage: py scripts/gen_test_structures.py
Output: src/gametest/resources-shared/data/cobblemon_dynamics/structure/floor_<x>x<y>x<z>.nbt (both GameTest runs)
"""
import gzip
import pathlib
import struct

DATA_VERSION = 3955  # Minecraft 1.21.1
SIZES = [(9, 5, 9), (15, 6, 15), (23, 10, 23), (23, 17, 23), (41, 8, 41)]
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUT = ROOT / "src/gametest/resources-shared/data/cobblemon_dynamics/structure"


def name(tag_type: int, key: str) -> bytes:
    raw = key.encode("utf-8")
    return struct.pack(">bH", tag_type, len(raw)) + raw


def string_payload(value: str) -> bytes:
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw


def int_list(key: str, values) -> bytes:
    return name(9, key) + struct.pack(">bi", 3, len(values)) + b"".join(struct.pack(">i", v) for v in values)


def structure(x: int, y: int, z: int) -> bytes:
    palette = name(9, "palette") + struct.pack(">bi", 10, 1) + name(8, "Name") + string_payload("minecraft:stone") + b"\x00"
    blocks = [(bx, 0, bz) for bx in range(x) for bz in range(z)]
    block_list = name(9, "blocks") + struct.pack(">bi", 10, len(blocks))
    for pos in blocks:
        block_list += int_list("pos", pos) + name(3, "state") + struct.pack(">i", 0) + b"\x00"
    entities = name(9, "entities") + struct.pack(">bi", 10, 0)
    body = (
        name(3, "DataVersion") + struct.pack(">i", DATA_VERSION)
        + int_list("size", (x, y, z))
        + palette + block_list + entities
    )
    return name(10, "") + body + b"\x00"


def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for x, y, z in SIZES:
        path = OUT / f"floor_{x}x{y}x{z}.nbt"
        path.write_bytes(gzip.compress(structure(x, y, z), mtime=0))
        print(path.relative_to(ROOT).as_posix())


if __name__ == "__main__":
    main()
