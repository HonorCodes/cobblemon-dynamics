#!/usr/bin/env python3
"""Builds, checks and shows the art of Cobblemon Dynamics from its sources under art/ (ADR-0012, the spec's ART rows).

Sources, each mapped to src/main/resources/assets/cobblemon_dynamics/<textures|models>/<path>:
  art/textures/<path>.pixels          a texture: 'size W H', a 'palette' and H rows of W 'pixels' (see parse_pixels)
  art/textures/<path>.pixels.mcmeta   the texture's .png.mcmeta, copied as it is
  art/models/<path>.json              a Minecraft JSON model, copied as it is
  art/logo/logo-256.png, art/logo/icon-512.png   the approver's logo (CD-ART-05)

Usage: py scripts/art.py build
       py scripts/art.py check [--references auto|JAR ...] [--release] [--record FILE]
       py scripts/art.py review [--record FILE]
       py scripts/art.py import PNG SOURCE
       py scripts/art.py --self-check
Exit code 0 when every check passes, 1 when a check fails, and 2 when an input is missing or unreadable.
"""
from __future__ import annotations

import argparse
import hashlib
import html
import json
import os
import pathlib
import re
import struct
import sys
import zipfile
import zlib

ROOT = pathlib.Path(__file__).resolve().parent.parent
ART = ROOT / "art"
ASSETS = ROOT / "src/main/resources/assets/cobblemon_dynamics"
LOGO = ROOT / "src/main/resources/logo.png"
RECORD = ROOT / "docs/testing/art-review.md"
REVIEW = ROOT / "build/art-review"
NAMESPACE = "cobblemon_dynamics"
TILE = 16
PNG_MAGIC = b"\x89PNG\r\n\x1a\n"
CHANNELS = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}
PALETTE_LINE = re.compile(r"\s*(\S)\s+(transparent|#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?)(?:\s.*)?")
ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
LICENSE_WORDS = re.compile(r"licen[cs]e|copyright|all rights reserved|creative commons|\bcc[- ]by", re.IGNORECASE)
TEXT_SOURCES = (".pixels", ".mcmeta", ".json")
BEGIN = "<!-- art-review rows: written by scripts/art.py; the verdict, date and note columns are kept -->"
END = "<!-- art-review rows end -->"
SIDES = ("down", "up", "north", "south", "west", "east")
# The elements of the vanilla parents that this mod's models name, so that the checks and the previews see their faces.
VANILLA_ELEMENTS = {
    "minecraft:block/cube_all": [
        {"from": [0, 0, 0], "to": [16, 16, 16],
         "faces": {side: {"texture": "#all", "cullface": side} for side in SIDES}},
    ],
}
# The reference textures of CD-ART-09, as paths under the Gradle user home.
REFERENCES = (
    "caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar",
    "caches/modules-2/files-2.1/com.cobblemon/neoforge/1.8.4+1.21.1/*/neoforge-1.8.4+1.21.1.jar",
    "caches/modules-2/files-2.1/com.simibubi.create/create-1.21.1/6.0.10-280/*/create-1.21.1-6.0.10-280-slim.jar",
    "caches/modules-2/files-2.1/org.appliedenergistics/appliedenergistics2/19.2.19/*/appliedenergistics2-19.2.19.jar",
)


class ArtError(Exception):
    """A source, texture or input that cannot be read. main() exits with code 2."""


# The .pixels source format


def parse_pixels(text: str, name: str = "<source>") -> tuple[int, int, bytes]:
    """A source as width, height and 8-bit RGBA bytes.

    Lines that start with '#' are comments. The first line reads 'size W H'. A 'palette' line follows, with one entry
    per line: a character other than '#', then 'transparent', '#rrggbb' or '#rrggbbaa', then an optional comment. A
    'pixels' line follows, then H rows of W palette characters.
    """
    lines = [line.rstrip() for line in text.splitlines()]
    lines = [line for line in lines if line.strip() and not line.lstrip().startswith("#")]
    if not lines or not re.fullmatch(r"size \d+ \d+", lines[0]):
        raise ArtError(f"{name}: the first line must read 'size W H'")
    width, height = (int(value) for value in lines[0].split()[1:])
    if "palette" not in lines or "pixels" not in lines:
        raise ArtError(f"{name}: needs a 'palette' line and a 'pixels' line")
    start, rows_at = lines.index("palette") + 1, lines.index("pixels")
    palette: dict[str, bytes] = {}
    for line in lines[start:rows_at]:
        match = PALETTE_LINE.fullmatch(line)
        if not match:
            raise ArtError(f"{name}: bad palette line {line!r}")
        char, value = match.groups()
        if char in palette:
            raise ArtError(f"{name}: palette character {char!r} is defined twice")
        palette[char] = bytes(4) if value == "transparent" else bytes.fromhex(value[1:].ljust(8, "f"))
    rows = [line.strip() for line in lines[rows_at + 1:]]
    if len(rows) != height or any(len(row) != width for row in rows):
        raise ArtError(f"{name}: needs {height} pixel rows of {width} characters")
    try:
        return width, height, b"".join(palette[char] for row in rows for char in row)
    except KeyError as error:
        raise ArtError(f"{name}: pixel {error.args[0]!r} is not in the palette") from error


def to_pixels(width: int, height: int, rgba: bytes, title: str) -> str:
    """An image as a .pixels source: '.' for a fully transparent pixel, and one character for each other colour, in
    the order of first use."""
    chars: dict[bytes, str] = {bytes(4): "."}
    rows = []
    for y in range(height):
        row = []
        for x in range(width):
            pixel = rgba[(y * width + x) * 4:(y * width + x + 1) * 4]
            key = bytes(4) if pixel[3] == 0 else pixel
            if key not in chars:
                if len(chars) > len(ALPHABET):
                    raise ArtError(f"{title}: more than {len(ALPHABET)} colours")
                chars[key] = ALPHABET[len(chars) - 1]
            row.append(chars[key])
        rows.append("".join(row))
    used = {char for row in rows for char in row}
    palette = [f"  {char} " + ("transparent" if key == bytes(4) else "#" + (key[:3] if key[3] == 255 else key).hex())
               for key, char in chars.items() if char in used]
    return "\n".join([f"# {title}", f"size {width} {height}", "palette", *palette, "pixels", *rows]) + "\n"


# PNG files


def _chunk(kind: bytes, body: bytes) -> bytes:
    return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)


def encode_png(width: int, height: int, rgba: bytes) -> bytes:
    """An 8-bit RGBA PNG with filter 0 on every row and no other chunk, so its pixels are all that it holds."""
    stride = width * 4
    raw = b"".join(b"\x00" + rgba[y * stride:(y + 1) * stride] for y in range(height))
    header = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    return PNG_MAGIC + _chunk(b"IHDR", header) + _chunk(b"IDAT", zlib.compress(raw, 9)) + _chunk(b"IEND", b"")


def png_chunks(data: bytes, name: str) -> list[tuple[bytes, bytes]]:
    if data[:8] != PNG_MAGIC:
        raise ArtError(f"{name}: not a PNG")
    chunks, pos = [], 8
    while pos + 8 <= len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        chunks.append((kind, data[pos + 8:pos + 8 + length]))
        pos += 12 + length
    return chunks


def png_size(data: bytes, name: str) -> tuple[int, int]:
    chunks = png_chunks(data, name)
    if not chunks or chunks[0][0] != b"IHDR" or len(chunks[0][1]) != 13:
        raise ArtError(f"{name}: the first chunk is not an IHDR")
    return struct.unpack(">II", chunks[0][1][:8])


def _unfilter(raw: bytes, height: int, stride: int, bpp: int) -> bytearray:
    out, prior = bytearray(), bytearray(stride)
    for y in range(height):
        base = y * (stride + 1)
        kind, line = raw[base], bytearray(raw[base + 1:base + 1 + stride])
        if kind == 1:
            for i in range(bpp, stride):
                line[i] = (line[i] + line[i - bpp]) & 0xFF
        elif kind == 2:
            for i in range(stride):
                line[i] = (line[i] + prior[i]) & 0xFF
        elif kind == 3:
            for i in range(stride):
                left = line[i - bpp] if i >= bpp else 0
                line[i] = (line[i] + ((left + prior[i]) >> 1)) & 0xFF
        elif kind == 4:
            for i in range(stride):
                a = line[i - bpp] if i >= bpp else 0
                b = prior[i]
                c = prior[i - bpp] if i >= bpp else 0
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 0xFF
        elif kind != 0:
            raise ArtError(f"unknown PNG filter {kind}")
        out += line
        prior = line
    return out


def _samples(data: bytearray, width: int, height: int, depth: int, channels: int):
    """One 8-bit sample per channel and pixel: sub-byte depths unpacked, 16-bit depths cut to their high byte."""
    if depth == 8:
        return data
    if depth == 16:
        return data[0::2]
    per_byte, row_bytes, mask, out = 8 // depth, (width * channels * depth + 7) // 8, (1 << depth) - 1, []
    for y in range(height):
        row = data[y * row_bytes:(y + 1) * row_bytes]
        values = [(byte >> (8 - depth * (k + 1))) & mask for byte in row for k in range(per_byte)]
        out += values[:width * channels]
    return out


def decode_png(data: bytes, name: str = "<png>") -> tuple[int, int, bytes]:
    """A PNG as width, height and 8-bit RGBA: every colour type, bit depths 1 to 16, with tRNS. Interlaced files are
    refused."""
    header, palette, trns, idat = b"", b"", None, []
    for kind, body in png_chunks(data, name):
        if kind == b"IHDR":
            header = body
        elif kind == b"PLTE":
            palette = body
        elif kind == b"tRNS":
            trns = body
        elif kind == b"IDAT":
            idat.append(body)
    if len(header) != 13 or not idat:
        raise ArtError(f"{name}: no IHDR or no IDAT chunk")
    width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", header)
    if interlace:
        raise ArtError(f"{name}: interlaced PNGs are not supported")
    if ctype not in CHANNELS:
        raise ArtError(f"{name}: unknown PNG colour type {ctype}")
    channels = CHANNELS[ctype]
    stride, bpp = (width * channels * depth + 7) // 8, max(1, channels * depth // 8)
    try:
        raw = zlib.decompress(b"".join(idat))
    except zlib.error as error:
        raise ArtError(f"{name}: {error}") from error
    samples = _samples(_unfilter(raw, height, stride, bpp), width, height, depth, channels)
    count = width * height
    if ctype == 6:
        return width, height, bytes(samples)
    if ctype == 3:
        alpha = (trns or b"") + b"\xff" * (256 - len(trns or b""))
        table = [palette[3 * i:3 * i + 3] + alpha[i:i + 1] for i in range(len(palette) // 3)]
        return width, height, b"".join(table[i] for i in samples)
    out = bytearray(count * 4)
    out[3::4] = b"\xff" * count
    if ctype == 4:
        out[0::4] = out[1::4] = out[2::4] = bytes(samples[0::2])
        out[3::4] = bytes(samples[1::2])
    elif ctype == 2:
        out[0::4], out[1::4], out[2::4] = bytes(samples[0::3]), bytes(samples[1::3]), bytes(samples[2::3])
        if trns:
            key = bytes(v >> 8 if depth == 16 else v for v in struct.unpack(">HHH", trns))
            for i in range(count):
                if out[4 * i:4 * i + 3] == key:
                    out[4 * i + 3] = 0
    else:
        scale = 1 if depth >= 8 else 255 // ((1 << depth) - 1)
        out[0::4] = out[1::4] = out[2::4] = bytes(v * scale for v in samples)
        if trns:
            gray = struct.unpack(">H", trns)[0]
            gray = gray >> 8 if depth == 16 else gray * scale
            for i in range(count):
                if out[4 * i] == gray:
                    out[4 * i + 3] = 0
    return width, height, bytes(out)


# Tiles (CD-ART-09)


def _clear_hidden(pixels: bytes) -> bytes:
    """A fully transparent pixel compares as (0, 0, 0, 0), whatever colour it stores."""
    if 0 not in pixels[3::4]:
        return pixels
    out = bytearray(pixels)
    for i in range(3, len(out), 4):
        if out[i] == 0:
            out[i - 3:i] = b"\x00\x00\x00"
    return bytes(out)


def _tile(rgba: bytes, width: int, tx: int, ty: int, channel: slice | None = None) -> bytes:
    rows = (rgba[((ty + r) * width + tx) * 4:((ty + r) * width + tx + TILE) * 4] for r in range(TILE))
    return b"".join(row[channel] if channel else row for row in rows)


def tiles(width: int, height: int, rgba: bytes):
    """Each aligned 16 x 16 tile that shows a pixel, with its corner."""
    for ty in range(0, height - TILE + 1, TILE):
        for tx in range(0, width - TILE + 1, TILE):
            tile = _tile(rgba, width, tx, ty)
            if any(tile[3::4]):
                yield (tx, ty), _clear_hidden(tile)


def scan(ours: dict[bytes, str], whole: dict[tuple[int, int, bytes], str],
         jars: list[pathlib.Path]) -> tuple[list[str], int]:
    """Each reference tile that equals one of [ours], and each reference texture that equals one of the [whole] textures
    that have no tile, as text, and the number of reference PNGs read."""
    alphas = {tile[3::4] for tile in ours}
    sizes = {(width, height) for width, height, _ in whole}
    matches, read = [], 0
    for jar_path in jars:
        with zipfile.ZipFile(jar_path) as jar:
            for entry in jar.namelist():
                if not (entry.startswith("assets/") and entry.endswith(".png")):
                    continue
                width, height, rgba = decode_png(jar.read(entry), entry)
                read += 1
                if (width, height) in sizes:
                    same = whole.get((width, height, _clear_hidden(rgba)))
                    if same:
                        matches.append(f"{same} equals {jar_path.name}!{entry}")
                for ty in range(0, height - TILE + 1, TILE):
                    for tx in range(0, width - TILE + 1, TILE):
                        if _tile(rgba, width, tx, ty, slice(3, None, 4)) not in alphas:
                            continue
                        tile = _clear_hidden(_tile(rgba, width, tx, ty))
                        if tile in ours:
                            matches.append(f"{ours[tile]} equals {jar_path.name}!{entry} at ({tx}, {ty})")
    return matches, read


# The sources and what they ship as


def sources() -> list[pathlib.Path]:
    """Every source of a shipped asset, in path order."""
    found = [*(ART / "textures").rglob("*.pixels"), *(ART / "textures").rglob("*.pixels.mcmeta"),
             *(ART / "models").rglob("*.json")]
    return sorted(path for path in found if path.is_file())


def output_of(source: pathlib.Path) -> pathlib.Path:
    relative = source.relative_to(ART).as_posix()
    if relative.endswith(".pixels"):
        return ASSETS / (relative[:-len(".pixels")] + ".png")
    if relative.endswith(".pixels.mcmeta"):
        return ASSETS / (relative[:-len(".pixels.mcmeta")] + ".png.mcmeta")
    return ASSETS / relative


def built(source: pathlib.Path) -> bytes:
    if source.name.endswith(".pixels"):
        return encode_png(*parse_pixels(source.read_text(encoding="utf-8"), source.name))
    return source.read_bytes()


def same_output(target: pathlib.Path, data: bytes, source: pathlib.Path) -> bool:
    """True when [target] holds [data]: the same pixels for a PNG, the same bytes for any other file."""
    old = target.read_bytes()
    if target.suffix == ".png":
        return decode_png(old, rel(target)) == decode_png(data, source.name)
    return old == data


def manifest() -> list[pathlib.Path]:
    """Every asset that the approver reviews: each source, and the two logo files."""
    return [*sources(), ART / "logo/logo-256.png", ART / "logo/icon-512.png"]


def sha256(path: pathlib.Path) -> str:
    """The SHA-256 of a source. A text source counts CRLF line ends as LF, so a Windows checkout and CI agree."""
    data = path.read_bytes()
    if path.name.endswith(TEXT_SOURCES):
        data = data.replace(b"\r\n", b"\n")
    return hashlib.sha256(data).hexdigest()


def rel(path: pathlib.Path) -> str:
    """[path] relative to the repository, or in full when it lies outside."""
    resolved = path.resolve()
    return resolved.relative_to(ROOT).as_posix() if resolved.is_relative_to(ROOT) else resolved.as_posix()


def title_of(source: pathlib.Path) -> str:
    """A source's path under art/ with no extension. A texture's metadata keeps '.mcmeta', so the two rows differ."""
    return re.sub(r"\.pixels(?=(\.mcmeta)?$)|\.(json|png)$", "", source.relative_to(ART).as_posix())


def build() -> int:
    written = 0
    for source in sources():
        target, data = output_of(source), built(source)
        if target.is_file() and same_output(target, data, source):
            continue
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(data)
        written += 1
    print(f"BUILD wrote {written} files")
    return 0


# The review record (CD-ART-10)


def read_record(path: pathlib.Path) -> dict[str, tuple[str, str, str, str]]:
    """Source path -> (SHA-256, verdict, date, note), from the rows between the record's markers."""
    if not path.is_file():
        return {}
    text = path.read_text(encoding="utf-8")
    if BEGIN not in text or END not in text:
        return {}
    rows = {}
    for line in text[text.find(BEGIN):text.find(END)].splitlines():
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) == 6 and cells[1].startswith("`art/"):
            rows[cells[1].strip("`")] = (cells[2].strip("`"), cells[3], cells[4], cells[5])
    return rows


def write_record(path: pathlib.Path) -> None:
    """Writes one row per asset between the record's markers. A row keeps its verdict, date and note while its source
    keeps its SHA-256, and goes back to 'pending' when the source changes."""
    old = read_record(path)
    lines = ["| Asset | Source | SHA-256 | Verdict | Date | Note |", "| --- | --- | --- | --- | --- | --- |"]
    for source in manifest():
        name, digest = rel(source), sha256(source)
        kept = old.get(name)
        verdict, date, note = kept[1:] if kept and kept[0] == digest else ("pending", "", "")
        lines.append(f"| {title_of(source)} | `{name}` | `{digest}` | {verdict} | {date} | {note} |")
    block = f"{BEGIN}\n\n" + "\n".join(lines) + f"\n\n{END}"
    text = path.read_text(encoding="utf-8") if path.is_file() else f"# Art review record\n\n{BEGIN}\n{END}\n"
    if BEGIN not in text or END not in text:
        raise ArtError(f"{rel(path)} has no '{BEGIN}' ... '{END}' block")
    text = text[:text.find(BEGIN)] + block + text[text.find(END) + len(END):]
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8", newline="\n")


# The checks


def check_logo() -> list[str]:
    """CD-ART-05: the two logo sizes, and the jar's logo is art/logo/logo-256.png."""
    problems = []
    for name, size in (("logo-256.png", 256), ("icon-512.png", 512)):
        path = ART / "logo" / name
        if not path.is_file():
            problems.append(f"art/logo/{name} is missing")
        elif png_size(path.read_bytes(), name) != (size, size):
            problems.append(f"art/logo/{name} is {png_size(path.read_bytes(), name)}, not {size} x {size}")
    logo = ART / "logo/logo-256.png"
    if not LOGO.is_file() or logo.is_file() and LOGO.read_bytes() != logo.read_bytes():
        problems.append("src/main/resources/logo.png differs from art/logo/logo-256.png")
    return problems


def check_sizes() -> list[str]:
    """CD-ART-06: every block and item texture is 16 x 16."""
    problems = []
    for kind in ("block", "item"):
        for png in sorted((ASSETS / "textures" / kind).rglob("*.png")):
            size = png_size(png.read_bytes(), png.name)
            if size != (TILE, TILE):
                problems.append(f"{rel(png)} is {size[0]} x {size[1]}")
    return problems


def check_sources() -> list[str]:
    """CD-ART-08: every shipped texture and model has its source, and is the build of that source."""
    problems = []
    expected = {output_of(source): source for source in sources()}
    for path in sorted([*(ASSETS / "textures").rglob("*"), *(ASSETS / "models").rglob("*.json")]):
        if path.is_file() and path not in expected:
            problems.append(f"{rel(path)} has no source under art/")
    for target, source in expected.items():
        if not target.is_file():
            problems.append(f"{rel(target)} is missing: run py scripts/art.py build")
        elif not same_output(target, built(source), source):
            problems.append(f"{rel(target)} differs from {rel(source)}: run py scripts/art.py build")
    return problems


def resolve_model(path: pathlib.Path, seen: frozenset = frozenset()) -> tuple[dict, list]:
    """The textures map and the elements of a model, with those of its parents in this mod or in VANILLA_ELEMENTS."""
    model = json.loads(path.read_text(encoding="utf-8"))
    textures, elements = {}, []
    parent = model.get("parent", "")
    if parent.startswith(f"{NAMESPACE}:") and parent not in seen:
        parent_path = ASSETS / "models" / (parent.split(":", 1)[1] + ".json")
        if not parent_path.is_file():
            raise ArtError(f"{rel(path)} names the parent {parent}, which does not exist")
        textures, elements = resolve_model(parent_path, seen | {parent})
    elif parent in VANILLA_ELEMENTS:
        elements = VANILLA_ELEMENTS[parent]
    textures = {**textures, **model.get("textures", {})}
    return textures, model.get("elements", elements)


def texture_path(textures: dict, ref: str) -> pathlib.Path | None:
    for _ in range(8):
        if not ref.startswith("#"):
            break
        ref = textures.get(ref[1:], "")
    if not ref.startswith(f"{NAMESPACE}:"):
        return None
    return ASSETS / "textures" / (ref.split(":", 1)[1] + ".png")


def check_models() -> list[str]:
    """CD-ART-07, the part that needs no game: each model parses, and every texture that it names exists.

    A model that another model names as its parent is a template: its children fill its '#' variables, so only they must
    resolve.
    """
    problems: set[str] = set()
    models = sorted((ASSETS / "models").rglob("*.json"))
    parents = set()
    for path in models:
        try:
            parents.add(json.loads(path.read_text(encoding="utf-8")).get("parent", ""))
        except json.JSONDecodeError as error:
            problems.add(f"{rel(path)} is not JSON: {error}")
    for path in models:
        name = f"{NAMESPACE}:" + path.relative_to(ASSETS / "models").with_suffix("").as_posix()
        try:
            textures, elements = resolve_model(path)
        except (ArtError, json.JSONDecodeError) as error:
            problems.add(str(error) if isinstance(error, ArtError) else f"{rel(path)} is not JSON: {error}")
            continue
        faces = [face.get("texture", "") for element in elements for face in element.get("faces", {}).values()]
        for ref in list(textures.values()) + faces:
            if ref.startswith("#") and name in parents and texture_path(textures, ref) is None:
                continue
            target = texture_path(textures, ref)
            if ref.startswith(("#", f"{NAMESPACE}:")) and (target is None or not target.is_file()):
                problems.add(f"{rel(path)} names the texture {ref}, which does not resolve to a shipped texture")
    return sorted(problems)


def check_license() -> list[str]:
    """CD-ART-13: LICENSE is MPL-2.0, and no asset file states a license of its own."""
    problems = []
    if "Mozilla Public License Version 2.0" not in (ROOT / "LICENSE").read_text(encoding="utf-8", errors="replace"):
        problems.append("LICENSE is not the MPL-2.0 text")
    for path in sorted([*ART.rglob("*"), *ASSETS.rglob("*")]):
        if not path.is_file():
            continue
        if path.suffix == ".png":
            kinds = (b"tEXt", b"zTXt", b"iTXt")
            texts = [kind.decode() for kind, _ in png_chunks(path.read_bytes(), path.name) if kind in kinds]
            if texts:
                problems.append(f"{rel(path)} holds text chunks {texts}")
        elif path.suffix in (".json", ".mcmeta", ".pixels", ".md", ".txt"):
            match = LICENSE_WORDS.search(path.read_text(encoding="utf-8", errors="replace"))
            if match:
                problems.append(f"{rel(path)} states '{match.group(0)}'")
    return problems


def find_references(names: list[str]) -> list[pathlib.Path]:
    if names != ["auto"]:
        return [pathlib.Path(name) for name in names]
    home = pathlib.Path(os.environ.get("GRADLE_USER_HOME", pathlib.Path.home() / ".gradle"))
    jars = []
    for pattern in REFERENCES:
        found = sorted(home.glob(pattern))
        if not found:
            raise ArtError(f"no reference jar {pattern} under {home}: run ./gradlew build first")
        jars.append(found[-1])
    return jars


def check_copies(jars: list[pathlib.Path]) -> tuple[list[str], str]:
    """CD-ART-09: no aligned 16 x 16 tile of a shipped texture equals a tile of Minecraft, Cobblemon, Create or AE2. A
    texture that has no such tile, such as a 70 x 12 button, is compared whole with each reference of its size."""
    ours: dict[bytes, str] = {}
    whole: dict[tuple[int, int, bytes], str] = {}
    for png in sorted((ASSETS / "textures").rglob("*.png")):
        width, height, rgba = decode_png(png.read_bytes(), png.name)
        found = list(tiles(width, height, rgba))
        if not found:
            whole.setdefault((width, height, _clear_hidden(rgba)), rel(png))
        for (tx, ty), tile in found:
            ours.setdefault(tile, f"{rel(png)} ({tx}, {ty})")
    matches, read = scan(ours, whole, jars)
    return matches, f"{read} reference PNGs read, {len(ours)} tiles and {len(whole)} whole textures compared"


def check_record(record: pathlib.Path) -> list[str]:
    """CD-ART-10: the review record approves every asset at its current SHA-256."""
    rows = read_record(record)
    problems = []
    for source in manifest():
        row = rows.get(rel(source))
        if row is None:
            problems.append(f"{rel(source)} is not in the record")
        elif row[0] != sha256(source):
            problems.append(f"{rel(source)} changed since its review")
        elif row[1] != "approved":
            problems.append(f"{rel(source)} is {row[1]}")
    return problems


def check(args: argparse.Namespace) -> int:
    cases: dict[str, list[str]] = {
        "cd-art-05": check_logo(),
        "cd-art-06": check_sizes(),
        "cd-art-07": check_models(),
        "cd-art-08": check_sources(),
        "cd-art-13": check_license(),
    }
    details = {}
    if args.references:
        cases["cd-art-09"], details["cd-art-09"] = check_copies(find_references(args.references))
    if args.release:
        cases["cd-art-10"] = check_record(args.record)
        cases["cd-art-12"] = cases["cd-art-08"] + cases["cd-art-10"]
    for case in sorted(cases):
        problems = cases[case]
        verdict = "PASS" if not problems else "FAIL"
        print(f"CASE {case}: {verdict}" + (f" {details[case]}" if case in details else ""))
        for problem in problems:
            print(f"  {problem}")
    failed = any(cases.values())
    print(f"ART CHECK {'FAIL' if failed else 'PASS'}")
    return 1 if failed else 0


# The review page (CD-ART-10)


def factor_for(width: int, height: int) -> int:
    """The scale of a render: up to 16, and at most 512 pixels on its longer side."""
    return max(1, min(16, 512 // max(width, height)))


def scaled(width: int, height: int, rgba: bytes, factor: int) -> tuple[int, int, bytes]:
    """[rgba] scaled up by [factor] with no smoothing, over a grey checkerboard, so that transparent pixels show."""
    out = bytearray()
    for y in range(height * factor):
        for x in range(width * factor):
            i = ((y // factor) * width + x // factor) * 4
            r, g, b, a = rgba[i:i + 4]
            bg = 200 if ((x // 8) + (y // 8)) % 2 else 160
            out += bytes(round(c * a / 255 + bg * (255 - a) / 255) for c in (r, g, b)) + b"\xff"
    return width * factor, height * factor, bytes(out)


def isometric(textures: dict, elements: list, size: int = 192) -> bytes:
    """A south-east view from above of a model's boxes, with the top, south and east faces shaded as the GUI shades
    them."""
    canvas = bytearray(size * size * 4)
    unit, cx, cy = size / 40.0, size / 2.0, size * 0.58

    def screen(x: float, y: float, z: float) -> tuple[float, float]:
        return cx + (x - z) * unit * 0.866, cy + (x + z - 16) * unit * 0.5 - (y - 8) * unit

    def face(corner, along_u, along_v, spec: dict, shade: float) -> None:
        path = texture_path(textures, spec.get("texture", ""))
        if path is None or not path.is_file():
            return
        width, height, rgba = decode_png(path.read_bytes(), path.name)
        ox, oy = screen(*corner)
        ax, ay = (a - b for a, b in zip(screen(*along_u), (ox, oy)))
        bx, by = (a - b for a, b in zip(screen(*along_v), (ox, oy)))
        det = ax * by - ay * bx
        if abs(det) < 1e-9:
            return
        u0, v0, u1, v1 = (value * width / 16 for value in spec.get("uv", [0, 0, 16, 16]))
        xs = [ox, ox + ax, ox + bx, ox + ax + bx]
        ys = [oy, oy + ay, oy + by, oy + ay + by]
        for py in range(max(0, int(min(ys))), min(size, int(max(ys)) + 1)):
            for px in range(max(0, int(min(xs))), min(size, int(max(xs)) + 1)):
                dx, dy = px + 0.5 - ox, py + 0.5 - oy
                s, t = (dx * by - dy * bx) / det, (ax * dy - ay * dx) / det
                if 0 <= s < 1 and 0 <= t < 1:
                    tu = min(width - 1, int(u0 + s * (u1 - u0)))
                    tv = min(height - 1, int(v0 + t * (v1 - v0)))
                    r, g, b, a = rgba[(tv * width + tu) * 4:(tv * width + tu + 1) * 4]
                    if a:
                        pixel = bytes((int(r * shade), int(g * shade), int(b * shade), 255))
                        canvas[(py * size + px) * 4:(py * size + px + 1) * 4] = pixel

    for element in sorted(elements, key=lambda e: (e["from"][0] + e["from"][2], e["from"][1])):
        (x0, y0, z0), (x1, y1, z1) = element["from"], element["to"]
        faces = element.get("faces", {})
        if "east" in faces:
            face((x1, y1, z1), (x1, y1, z0), (x1, y0, z1), faces["east"], 0.6)
        if "south" in faces:
            face((x0, y1, z1), (x1, y1, z1), (x0, y0, z1), faces["south"], 0.8)
        if "up" in faces:
            face((x0, y1, z0), (x1, y1, z0), (x0, y1, z1), faces["up"], 1.0)
    return encode_png(size, size, bytes(canvas))


def model_preview(model: pathlib.Path) -> bytes | None:
    """A model with boxes from above, or the first layer of a generated item model scaled up, as PNG bytes."""
    textures, elements = resolve_model(model)
    if elements:
        return isometric(textures, elements)
    layer = texture_path(textures, textures.get("layer0", ""))
    if layer is None or not layer.is_file():
        return None
    width, height, rgba = decode_png(layer.read_bytes(), layer.name)
    return encode_png(*scaled(width, height, rgba, factor_for(width, height)))


def pictures_of(source: pathlib.Path, stem: str) -> str:
    """Writes the images of one asset under build/art-review/ and returns their HTML."""
    name = source.relative_to(ART).as_posix()
    alt = html.escape(name)
    if source.name.endswith(".pixels"):
        width, height, rgba = parse_pixels(source.read_text(encoding="utf-8"), source.name)
        factor = factor_for(width, height)
        (REVIEW / f"{stem}-1x.png").write_bytes(encode_png(width, height, rgba))
        (REVIEW / f"{stem}.png").write_bytes(encode_png(*scaled(width, height, rgba, factor)))
        out = (f'<img src="{stem}.png" width="{width * factor}" height="{height * factor}" alt="{alt}, {factor}x">'
               f'<img src="{stem}-1x.png" width="{width}" height="{height}" alt="{alt}, 1x">')
        if name.startswith("textures/item/"):
            # As an inventory slot shows the item at GUI scales 1, 2 and 3.
            out += "".join(f'<span class="slot"><img src="{stem}-1x.png" width="{width * k}" height="{height * k}" '
                           f'alt="{alt} in a slot, {k}x"></span>' for k in (1, 2, 3))
        return out
    if source.suffix == ".json":
        preview = model_preview(output_of(source))
        if preview is None:
            return "<em>no preview</em>"
        (REVIEW / f"{stem}.png").write_bytes(preview)
        return f'<img src="{stem}.png" alt="{alt}">'
    if source.suffix == ".png":
        width, height, rgba = decode_png(source.read_bytes(), source.name)
        (REVIEW / f"{stem}.png").write_bytes(encode_png(width, height, rgba))
        return f'<img class="logo" src="{stem}.png" width="{min(width, 256)}" alt="{alt}">'
    return f"<pre>{html.escape(source.read_text(encoding='utf-8'))}</pre>"


PAGE_STYLE = (
    ":root{--bg:#f5f5f2;--fg:#17171a;--muted:#5d5d66;--line:#d3d3d8;--card:#fff;--slot:#8b8b8b;"
    "--pending:#a15c07;--approved:#16773a;--changes:#b42318}"
    "@media (prefers-color-scheme:dark){:root{--bg:#151517;--fg:#ececef;--muted:#a3a3ad;--line:#3d3d45;--card:#1e1e22;"
    "--pending:#f0a840;--approved:#4fc27a;--changes:#f2786b}}"
    "body{margin:0;padding:16px;background:var(--bg);color:var(--fg);font:15px/1.45 system-ui,sans-serif}"
    "h1{font-size:22px;margin:0 0 4px}p{margin:4px 0 12px;color:var(--muted);max-width:900px}"
    ".wrap{overflow-x:auto}table{border-collapse:collapse;background:var(--card)}"
    "th,td{border:1px solid var(--line);padding:8px;vertical-align:middle;text-align:left}"
    "th{font-size:13px;color:var(--muted)}code{font-size:12px}pre{margin:0;font-size:12px;white-space:pre-wrap}"
    "img{image-rendering:pixelated;margin:4px;vertical-align:middle}img.logo{height:auto}"
    ".slot{display:inline-block;background:var(--slot);padding:2px;margin:4px;line-height:0;vertical-align:middle}"
    ".slot img{margin:0}.pending{color:var(--pending)}.approved{color:var(--approved)}.changes{color:var(--changes)}"
)


def review(args: argparse.Namespace) -> int:
    stale = check_sources()
    if stale:
        raise ArtError("the shipped assets are not the build of their sources: " + "; ".join(stale))
    write_record(args.record)
    rows = read_record(args.record)
    REVIEW.mkdir(parents=True, exist_ok=True)
    for old in REVIEW.glob("*.png"):
        old.unlink()
    cells, counts = [], {}
    for source in manifest():
        stem = re.sub(r"[^a-z0-9]+", "-", source.relative_to(ART).as_posix().lower()).strip("-")
        _, verdict, date, note = rows.get(rel(source), ("", "pending", "", ""))
        counts[verdict] = counts.get(verdict, 0) + 1
        cells.append(f"<tr><td>{html.escape(title_of(source))}<br><code>{html.escape(rel(source))}</code></td>"
                     f"<td><code>{sha256(source)[:12]}</code></td><td>{pictures_of(source, stem)}</td>"
                     f'<td class="{html.escape(verdict)}">{html.escape(verdict)}<br>{html.escape(date)}</td>'
                     f"<td>{html.escape(note)}</td></tr>")
    summary = ", ".join(f"{count} {verdict}" for verdict, count in sorted(counts.items()))
    page = ("<!doctype html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">"
            "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            f"<title>Art review</title><style>{PAGE_STYLE}</style></head><body>"
            f"<h1>Cobblemon Dynamics art review</h1><p>{len(cells)} assets: {html.escape(summary)}. Each texture shows "
            "scaled up with no smoothing over a checkerboard, then at 1x. An item texture also shows in an inventory "
            "slot at GUI scales 1, 2 and 3. Each block model shows from above. Record each verdict in "
            f"<code>{html.escape(rel(args.record))}</code>.</p><div class=\"wrap\"><table>"
            "<tr><th>Asset</th><th>SHA-256</th><th>Images</th><th>Verdict</th><th>Note</th></tr>"
            + "".join(cells) + "</table></div></body></html>\n")
    (REVIEW / "index.html").write_text(page, encoding="utf-8", newline="\n")
    print(f"REVIEW wrote {rel(REVIEW / 'index.html')} for {len(cells)} assets")
    return 0


def import_png(args: argparse.Namespace) -> int:
    png, source = pathlib.Path(args.png), pathlib.Path(args.source)
    width, height, rgba = decode_png(png.read_bytes(), png.name)
    source.parent.mkdir(parents=True, exist_ok=True)
    text = to_pixels(width, height, rgba, f"{source.stem}: imported from {png.name}")
    source.write_text(text, encoding="utf-8", newline="\n")
    print(f"IMPORT wrote {source}")
    return 0


# Self-check on samples


def self_check() -> int:
    sample = ("# a sample\nsize 16 16\npalette\n  . transparent\n  k #1b1b24 outline\n  r #d23c3c80\npixels\n"
              + "\n".join(("." * y + "k" + "r" * 15)[:16] for y in range(16)) + "\n")
    width, height, rgba = parse_pixels(sample, "sample")
    assert (width, height) == (16, 16)
    assert rgba[:4] == bytes.fromhex("1b1b24ff") and rgba[4:8] == bytes.fromhex("d23c3c80")
    assert decode_png(encode_png(width, height, rgba)) == (width, height, rgba)
    assert parse_pixels(to_pixels(width, height, rgba, "again"), "again") == (width, height, rgba)
    assert parse_pixels(sample.replace("\n", "\r\n"), "crlf") == (width, height, rgba)
    for bad in ("size 2 1\npalette\n  a #000000\npixels\nab\n", "size 2 1\npalette\npixels\naa\n", "size 2\n",
                "size 1 1\npalette\n  a #000000\n  a #ffffff\npixels\na\n",
                "size 1 2\npalette\n  a #000000\npixels\na\n"):
        try:
            parse_pixels(bad, "bad")
        except ArtError:
            continue
        raise AssertionError(f"accepted {bad!r}")
    ours = {tile: "sample" for _, tile in tiles(width, height, rgba)}
    assert len(ours) == 1
    shifted = bytearray(rgba)
    shifted[3] = 0
    assert _clear_hidden(bytes(shifted))[:4] == bytes(4)
    assert list(tiles(70, 12, bytes(70 * 12 * 4))) == []
    on = ART / "textures/gui/sprites/work_mode/on.pixels"
    assert title_of(on) == "textures/gui/sprites/work_mode/on"
    assert title_of(on.with_name("on.pixels.mcmeta")) == "textures/gui/sprites/work_mode/on.mcmeta"
    assert title_of(ART / "models/block/hay_bed.json") == "models/block/hay_bed"
    assert scaled(1, 1, bytes.fromhex("ff000080"), 2)[:2] == (2, 2)
    print("self-check passed")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--self-check", action="store_true", help="check the parsers on built-in samples")
    commands = parser.add_subparsers(dest="command")
    commands.add_parser("build", help="write every shipped asset from its source")
    checks = commands.add_parser("check", help="check the art against the ART rows")
    checks.add_argument("--references", nargs="+", metavar="JAR",
                        help="'auto', or the jars of Minecraft, Cobblemon, Create and AE2")
    checks.add_argument("--release", action="store_true", help="also require the approver's approval of every asset")
    checks.add_argument("--record", type=pathlib.Path, default=RECORD)
    reviews = commands.add_parser("review", help="write build/art-review/ and the rows of the review record")
    reviews.add_argument("--record", type=pathlib.Path, default=RECORD)
    imports = commands.add_parser("import", help="write a .pixels source from a PNG")
    imports.add_argument("png")
    imports.add_argument("source")
    args = parser.parse_args()
    if args.self_check:
        return self_check()
    try:
        if args.command == "build":
            return build()
        if args.command == "check":
            return check(args)
        if args.command == "review":
            return review(args)
        if args.command == "import":
            return import_png(args)
    except (ArtError, OSError, ValueError, struct.error, zipfile.BadZipFile) as error:
        print(f"art: {error}", file=sys.stderr)
        return 2
    parser.print_help()
    return 2


if __name__ == "__main__":
    sys.exit(main())
