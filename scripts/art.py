#!/usr/bin/env python3
"""Builds, checks and shows the art of Cobblemon Dynamics from its sources under art/ (ADR-0012, the spec's ART rows).

Sources, each mapped to src/main/resources/assets/cobblemon_dynamics/<textures|models>/<path>:
  art/textures/<path>.pixels          a texture: 'size W H', a 'palette' and H rows of W 'pixels' (see parse_pixels)
  art/textures/<path>.pixels.mcmeta   the texture's .png.mcmeta, copied as it is
  art/models/<path>.json              a Minecraft JSON model, copied as it is
  art/logo/logo-256.png, art/logo/icon-512.png   the approver's logo (CD-ART-05)

Usage: py scripts/art.py build
       py scripts/art.py check [--references auto|JAR ...] [--release] [--record FILE]
       py scripts/art.py review [--record FILE] [--note TEXT]
       py scripts/art.py import PNG SOURCE
       py scripts/art.py --self-check
Exit code 0 when every check passes, 1 when a check fails, and 2 when an input is missing or unreadable.
"""
from __future__ import annotations

import argparse
import hashlib
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


# The review spritesheet (CD-ART-10)

SHEET_WIDTH = 1700
MARGIN = 24
GAP = 8
BIG = 8
TEXT = 2
PAGE = bytes.fromhex("ececef")
INK = bytes.fromhex("18181b")
MUTED = bytes.fromhex("50505a")
LIGHT_SLOT = bytes.fromhex("8b8b8b")
DARK_SLOT = bytes.fromhex("2e2e33")
LABEL_CHARS = 25
# The GUI textures by folder under textures/, with the name that the sheet gives each group, in the sheet's order.
GUI_GROUPS = {
    "gui/sprites/work_mode": "Work Mode button",
    "gui/stamina_bar": "Work Stamina bar",
    "gui/state": "State icon",
}
# The order of the sprites inside a group. A sprite that is not listed follows, in path order.
SPRITE_ORDER = ("on", "off", "hover", "disabled", "panel_bar", "panel_fill", "head_bar", "head_fill",
                "idle", "traveling", "working", "standby", "tired", "sleeping", "healing")
TABS = ROOT / "src/main/kotlin/codes/honor/cobblemondynamics/registry/DynamicsTabs.kt"
STAMINA_BAR = ROOT / "src/main/kotlin/codes/honor/cobblemondynamics/client/StaminaBar.kt"
# The Work Stamina bar at the stamina of manual checklist check 49, with the tint that StaminaBar.color gives it there.
BAR_SAMPLES = ((100, "GREEN"), (40, "YELLOW"), (10, "RED"))
# A 5 x 9 bitmap font for the labels: each glyph lists its rows from the top, with '#' for a lit pixel. Rows 7 and 8
# hold the descenders, and the rows that a glyph leaves out are blank.
FONT = {
    " ": "",
    "A": ".###. #...# #...# ##### #...# #...# #...#",
    "B": "####. #...# #...# ####. #...# #...# ####.",
    "C": ".###. #...# #.... #.... #.... #...# .###.",
    "D": "####. #...# #...# #...# #...# #...# ####.",
    "E": "##### #.... #.... ####. #.... #.... #####",
    "F": "##### #.... #.... ####. #.... #.... #....",
    "G": ".###. #...# #.... #.### #...# #...# .####",
    "H": "#...# #...# #...# ##### #...# #...# #...#",
    "I": ".###. ..#.. ..#.. ..#.. ..#.. ..#.. .###.",
    "J": "..### ...#. ...#. ...#. ...#. #..#. .##..",
    "K": "#...# #..#. #.#.. ##... #.#.. #..#. #...#",
    "L": "#.... #.... #.... #.... #.... #.... #####",
    "M": "#...# ##.## #.#.# #.#.# #...# #...# #...#",
    "N": "#...# #...# ##..# #.#.# #..## #...# #...#",
    "O": ".###. #...# #...# #...# #...# #...# .###.",
    "P": "####. #...# #...# ####. #.... #.... #....",
    "Q": ".###. #...# #...# #...# #.#.# #..#. .##.#",
    "R": "####. #...# #...# ####. #.#.. #..#. #...#",
    "S": ".#### #.... #.... .###. ....# ....# ####.",
    "T": "##### ..#.. ..#.. ..#.. ..#.. ..#.. ..#..",
    "U": "#...# #...# #...# #...# #...# #...# .###.",
    "V": "#...# #...# #...# #...# #...# .#.#. ..#..",
    "W": "#...# #...# #...# #.#.# #.#.# #.#.# .#.#.",
    "X": "#...# #...# .#.#. ..#.. .#.#. #...# #...#",
    "Y": "#...# #...# .#.#. ..#.. ..#.. ..#.. ..#..",
    "Z": "##### ....# ...#. ..#.. .#... #.... #####",
    "a": "..... ..... .###. ....# .#### #...# .####",
    "b": "#.... #.... ####. #...# #...# #...# ####.",
    "c": "..... ..... .###. #.... #.... #...# .###.",
    "d": "....# ....# .#### #...# #...# #...# .####",
    "e": "..... ..... .###. #...# ##### #.... .###.",
    "f": "..##. .#..# .#... ###.. .#... .#... .#...",
    "g": "..... ..... .#### #...# #...# #...# .#### ....# .###.",
    "h": "#.... #.... #.##. ##..# #...# #...# #...#",
    "i": "..#.. ..... .##.. ..#.. ..#.. ..#.. .###.",
    "j": "...#. ..... ..##. ...#. ...#. ...#. ...#. #..#. .##..",
    "k": "#.... #.... #..#. #.#.. ##... #.#.. #..#.",
    "l": ".##.. ..#.. ..#.. ..#.. ..#.. ..#.. .###.",
    "m": "..... ..... ##.#. #.#.# #.#.# #.#.# #.#.#",
    "n": "..... ..... #.##. ##..# #...# #...# #...#",
    "o": "..... ..... .###. #...# #...# #...# .###.",
    "p": "..... ..... ####. #...# #...# #...# ####. #.... #....",
    "q": "..... ..... .#### #...# #...# #...# .#### ....# ....#",
    "r": "..... ..... #.##. ##..# #.... #.... #....",
    "s": "..... ..... .#### #.... .###. ....# ####.",
    "t": ".#... .#... ###.. .#... .#... .#..# ..##.",
    "u": "..... ..... #...# #...# #...# #..## .##.#",
    "v": "..... ..... #...# #...# #...# .#.#. ..#..",
    "w": "..... ..... #...# #...# #.#.# #.#.# .#.#.",
    "x": "..... ..... #...# .#.#. ..#.. .#.#. #...#",
    "y": "..... ..... #...# #...# #...# #...# .#### ....# .###.",
    "z": "..... ..... ##### ...#. ..#.. .#... #####",
    "0": ".###. #...# #..## #.#.# ##..# #...# .###.",
    "1": "..#.. .##.. ..#.. ..#.. ..#.. ..#.. .###.",
    "2": ".###. #...# ....# ...#. ..#.. .#... #####",
    "3": "##### ...#. ..#.. ...#. ....# #...# .###.",
    "4": "...#. ..##. .#.#. #..#. ##### ...#. ...#.",
    "5": "##### #.... ####. ....# ....# #...# .###.",
    "6": "..##. .#... #.... ####. #...# #...# .###.",
    "7": "##### ....# ...#. ..#.. .#... .#... .#...",
    "8": ".###. #...# #...# .###. #...# #...# .###.",
    "9": ".###. #...# #...# .#### ....# ...#. .##..",
    ".": "..... ..... ..... ..... ..... .##.. .##..",
    ",": "..... ..... ..... ..... ..... .##.. .##.. ..#.. .#...",
    ":": "..... .##.. .##.. ..... .##.. .##.. .....",
    ";": "..... .##.. .##.. ..... .##.. .##.. ..#.. .#...",
    "/": "....# ....# ...#. ..#.. .#... #.... #....",
    "_": "..... ..... ..... ..... ..... ..... ..... #####",
    "-": "..... ..... ..... .###. ..... ..... .....",
    "(": "...#. ..#.. .#... .#... .#... ..#.. ...#.",
    ")": ".#... ..#.. ...#. ...#. ...#. ..#.. .#...",
    "*": "..... ..#.. #.#.# .###. #.#.# ..#.. .....",
    "+": "..... ..#.. ..#.. ##### ..#.. ..#.. .....",
    "=": "..... ..... ##### ..... ##### ..... .....",
    "%": "##..# ##..# ...#. ..#.. .#... #..## #..##",
    "'": "..#.. ..#.. .#... ..... ..... ..... .....",
    "!": "..#.. ..#.. ..#.. ..#.. ..#.. ..... ..#..",
    "?": ".###. #...# ....# ...#. ..#.. ..... ..#..",
    "#": ".#.#. .#.#. ##### .#.#. ##### .#.#. .#.#.",
}


class Canvas:
    """An opaque RGB image that the review sheet is drawn on."""

    def __init__(self, width: int, height: int, color: bytes = PAGE):
        self.width, self.height = width, height
        self.pixels = bytearray(color * (width * height))

    def fill(self, x: int, y: int, width: int, height: int, color: bytes) -> None:
        x0, y0, x1, y1 = max(0, x), max(0, y), min(self.width, x + width), min(self.height, y + height)
        if x1 > x0:
            row = color * (x1 - x0)
            for line in range(y0, y1):
                start = (line * self.width + x0) * 3
                self.pixels[start:start + len(row)] = row

    def paste(self, x: int, y: int, other: Canvas) -> None:
        stride = other.width * 3
        for line in range(other.height):
            start = ((y + line) * self.width + x) * 3
            self.pixels[start:start + stride] = other.pixels[line * stride:(line + 1) * stride]

    def sprite(self, x: int, y: int, width: int, height: int, rgba: bytes, scale: int, background: bytes) -> None:
        """[rgba] scaled with no smoothing over [background], which shows 4 pixels around it."""
        self.fill(x, y, width * scale + 8, height * scale + 8, background)
        for sy in range(height):
            for sx in range(width):
                r, g, b, a = rgba[(sy * width + sx) * 4:(sy * width + sx + 1) * 4]
                if a:
                    color = bytes(round(c * a / 255 + k * (255 - a) / 255) for c, k in zip((r, g, b), background))
                    self.fill(x + 4 + sx * scale, y + 4 + sy * scale, scale, scale, color)

    def text(self, x: int, y: int, text: str, color: bytes, scale: int = TEXT) -> None:
        for index, char in enumerate(text):
            for row, bits in enumerate(FONT.get(char, FONT["?"]).split()):
                for column, bit in enumerate(bits):
                    if bit == "#":
                        self.fill(x + (index * 6 + column) * scale, y + row * scale, scale, scale, color)

    def png(self) -> bytes:
        rgba = bytearray(self.width * self.height * 4)
        for channel in range(3):
            rgba[channel::4] = self.pixels[channel::3]
        rgba[3::4] = b"\xff" * (self.width * self.height)
        return encode_png(self.width, self.height, bytes(rgba))


def text_width(text: str, scale: int = TEXT) -> int:
    return max(0, len(text) * 6 - 1) * scale


def line_height(scale: int = TEXT) -> int:
    return 11 * scale


def wrap(text: str, limit: int) -> list[str]:
    """[text] in lines of at most [limit] characters, broken after a '/' or at a space where it can be."""
    lines = []
    while len(text) > limit:
        cut = max(text.rfind("/", 0, limit), text.rfind(" ", 0, limit + 1))
        cut = cut + 1 if cut > 0 else limit
        lines.append(text[:cut].rstrip())
        text = text[cut:].lstrip()
    return lines + [text]


def card(images: Canvas, name: str, path: str) -> Canvas:
    """[images] above a label: the name in dark ink, then the path in grey, each wrapped to LABEL_CHARS."""
    lines = [(line, INK) for line in wrap(name, LABEL_CHARS)] + [(line, MUTED) for line in wrap(path, LABEL_CHARS)]
    out = Canvas(max([images.width] + [text_width(text) for text, _ in lines]),
                 images.height + GAP + len(lines) * line_height())
    out.paste(0, 0, images)
    for index, (text, color) in enumerate(lines):
        out.text(0, images.height + GAP + index * line_height(), text, color)
    return out


def sprite_images(width: int, height: int, rgba: bytes) -> Canvas:
    """A sprite at 8x and at 1x, on a light inventory slot (top) and on a dark one (bottom)."""
    big_width, big_height, small_width, small_height = width * BIG + 8, height * BIG + 8, width + 8, height + 8
    out = Canvas(big_width + GAP + small_width, 2 * big_height + GAP)
    for row, background in enumerate((LIGHT_SLOT, DARK_SLOT)):
        top = row * (big_height + GAP)
        out.sprite(0, top, width, height, rgba, BIG, background)
        out.sprite(big_width + GAP, top + (big_height - small_height) // 2, width, height, rgba, 1, background)
    return out


def isometric(textures: dict, elements: list, size: int) -> bytes:
    """A south-east view from above of a model's boxes as RGBA, with the top, south and east faces shaded as the GUI
    shades them."""
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
    return bytes(canvas)


def texture_card(source: pathlib.Path, name: str, path: str) -> Canvas:
    width, height, rgba = parse_pixels(source.read_text(encoding="utf-8"), source.name)
    return card(sprite_images(width, height, rgba), name, path)


def shipped_path(source: pathlib.Path) -> str:
    """The path of a source's shipped file under assets/cobblemon_dynamics/."""
    return output_of(source).relative_to(ASSETS).as_posix()


def gui_key(source: pathlib.Path) -> tuple:
    folder, _, stem = source.relative_to(ART / "textures").as_posix().removesuffix(".pixels").rpartition("/")
    groups = list(GUI_GROUPS)
    return (groups.index(folder) if folder in groups else len(groups), folder,
            SPRITE_ORDER.index(stem) if stem in SPRITE_ORDER else len(SPRITE_ORDER), stem)


def bar_tints() -> dict[str, bytes]:
    """The colours of StaminaBar.kt, by constant name."""
    text = STAMINA_BAR.read_text(encoding="utf-8") if STAMINA_BAR.is_file() else ""
    return {name: bytes.fromhex(value) for name, value in re.findall(r"const val (\w+) = 0xFF([0-9A-Fa-f]{6})", text)}


def drawn_bar(bar: pathlib.Path, fill: pathlib.Path, percent: int, tint: bytes) -> tuple[int, int, bytes]:
    """[bar] with [fill] over its inside, cropped to [percent] as StaminaBar.fillWidth crops it and tinted, as the game
    draws a Work Stamina bar."""
    width, height, base = parse_pixels(bar.read_text(encoding="utf-8"), bar.name)
    fill_width, fill_height, pixels = parse_pixels(fill.read_text(encoding="utf-8"), fill.name)
    out = bytearray(base)
    shown = 0 if percent <= 0 else max(1, fill_width * min(percent, 100) // 100)
    for y in range(fill_height):
        for x in range(shown):
            i, j = (y * fill_width + x) * 4, ((y + 1) * width + x + 1) * 4
            out[j:j + 4] = bytes(c * t // 255 for c, t in zip(pixels[i:i + 3], tint)) + pixels[i + 3:i + 4]
    return width, height, bytes(out)


def tab_item() -> str | None:
    """The id of the item that the creative tab shows as its icon, as DynamicsTabs names it."""
    text = TABS.read_text(encoding="utf-8") if TABS.is_file() else ""
    match = re.search(r"\.icon\(\s*Supplier\s*\{\s*ItemStack\(DynamicsItems\.(\w+)\.get\(\)\)", text)
    return match.group(1).lower() if match else None


def spritesheet(record: pathlib.Path, note: str) -> Canvas:
    """The review sheet: the Work Leash, the creative tab's icon, the leash candidates, the UI sprites, the bars as the
    game draws them and, for reference, the block items."""
    names = json.loads((ASSETS / "lang/en_us.json").read_text(encoding="utf-8"))
    textures = [source for source in sources() if source.name.endswith(".pixels")]
    items = [source for source in textures if source.parent == ART / "textures/item"]
    sections: list[tuple[str, list[Canvas]]] = []
    item_name = {source: names.get(f"item.{NAMESPACE}.{source.stem}", source.stem) for source in items}
    sections.append(("Items", [texture_card(source, item_name[source], shipped_path(source)) for source in items]))
    tab = ART / f"textures/item/{tab_item()}.pixels"
    if tab.is_file():
        label = f"Creative tab icon: the {item_name.get(tab, tab.stem)} item"
        sections.append(("Creative tab icon", [texture_card(tab, label, shipped_path(tab))]))
    for folder in sorted(path for path in (ART / "candidates/textures").rglob("*") if path.is_dir()):
        shipped = ART / "textures" / (folder.relative_to(ART / "candidates/textures").as_posix() + ".pixels")
        candidates = sorted(folder.glob("*.pixels"))
        if not candidates or not shipped.is_file():
            continue
        cards = []
        for source in candidates:
            first = source.read_text(encoding="utf-8").splitlines()[0]
            cards.append(texture_card(source, first.lstrip("# ") if first.startswith("#") else source.stem.upper(),
                                      rel(source)))
        letter = chr(ord("A") + len(candidates))
        cards.append(texture_card(shipped, f"{letter} *: the pick, which ships now", rel(shipped)))
        title = item_name.get(shipped, shipped.stem)
        sections.append((f"{title} candidates: * marks the pick", cards))
    gui = sorted((source for source in textures if source.relative_to(ART / "textures").parts[0] == "gui"), key=gui_key)
    for folder, group in GUI_GROUPS.items():
        members = [source for source in gui if source.parent == ART / "textures" / folder]
        cards = [texture_card(source, f"{group}: {source.stem.replace('_', ' ').capitalize()}", shipped_path(source))
                 for source in members]
        if cards:
            sections.append((f"UI sprites: {group}", cards))
    tints, drawn = bar_tints(), []
    for kind, name in (("panel", "Panel bar"), ("head", "Head bar")):
        bar, fill = (ART / f"textures/gui/stamina_bar/{kind}_{part}.pixels" for part in ("bar", "fill"))
        for percent, tint in BAR_SAMPLES:
            if bar.is_file() and fill.is_file() and tint in tints:
                images = sprite_images(*drawn_bar(bar, fill, percent, tints[tint]))
                drawn.append(card(images, f"{name} at {percent}% in game", f"the fill tinted {tint.lower()}"))
    if drawn:
        sections.append(("UI sprites: Work Stamina bar as the game draws it", drawn))
    others = [source for source in gui if source.parent.relative_to(ART / "textures").as_posix() not in GUI_GROUPS]
    if others:
        sections.append(("UI sprites: other", [texture_card(source, source.stem, shipped_path(source))
                                                for source in others]))
    blocks = []
    for source in sorted((ART / "models/item").glob("*.json")):
        model_textures, elements = resolve_model(output_of(source))
        if elements:
            image = Canvas(128 + 8, 128 + 8, LIGHT_SLOT)
            image.sprite(0, 0, 128, 128, isometric(model_textures, elements, 128), 1, LIGHT_SLOT)
            name = names.get(f"block.{NAMESPACE}.{source.stem}", source.stem)
            blocks.append(card(image, name, shipped_path(source)))
    if blocks:
        sections.append(("Blocks: representative 3D art, unchanged", blocks))
    header = ["Each sprite shows at 8x and at 1x, on a light inventory slot (top) and on a dark one (bottom).",
              f"Record each verdict in {rel(record)}. Made by py scripts/art.py review."]
    return compose("Cobblemon Dynamics art review", header + ([note] if note else []), sections)


def compose(title: str, header: list[str], sections: list[tuple[str, list[Canvas]]]) -> Canvas:
    """Lays the sections out one below the other, each a heading over its cards in rows."""
    limit = (SHEET_WIDTH - 2 * MARGIN) // (6 * TEXT)
    placed: list[tuple[int, int, Canvas]] = []
    texts: list[tuple[int, int, str, bytes, int]] = [(MARGIN, MARGIN, title, INK, 4)]
    y = MARGIN + line_height(4) + GAP
    for line in (part for text in header for part in wrap(text, limit)):
        texts.append((MARGIN, y, line, MUTED, TEXT))
        y += line_height()
    rules = []
    for heading, cards in sections:
        y += 3 * GAP
        texts.append((MARGIN, y, heading, INK, 3))
        y += line_height(3)
        rules.append(y)
        y += 2 * GAP
        x, row = MARGIN, 0
        for item in cards:
            if x > MARGIN and x + item.width > SHEET_WIDTH - MARGIN:
                x, y, row = MARGIN, y + row + 3 * GAP, 0
            placed.append((x, y, item))
            x, row = x + item.width + 4 * GAP, max(row, item.height)
        y += row
    sheet = Canvas(SHEET_WIDTH, y + MARGIN)
    for x, top, text, color, scale in texts:
        sheet.text(x, top, text, color, scale)
    for top in rules:
        sheet.fill(MARGIN, top, SHEET_WIDTH - 2 * MARGIN, 2, MUTED)
    for x, top, item in placed:
        sheet.paste(x, top, item)
    return sheet


def review(args: argparse.Namespace) -> int:
    stale = check_sources()
    if stale:
        raise ArtError("the shipped assets are not the build of their sources: " + "; ".join(stale))
    write_record(args.record)
    sheet = spritesheet(args.record, args.note)
    REVIEW.mkdir(parents=True, exist_ok=True)
    for old in REVIEW.iterdir():
        if old.is_file():
            old.unlink()
    target = REVIEW / "spritesheet.png"
    target.write_bytes(sheet.png())
    print(f"REVIEW wrote {rel(target)} ({sheet.width} x {sheet.height} pixels)")
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
    assert all(len(row) == 5 for glyph in FONT.values() for row in glyph.split())
    assert all(len(glyph.split()) <= 9 for glyph in FONT.values())
    path = "textures/gui/sprites/work_mode/disabled.png"
    assert wrap(path, 32) == ["textures/gui/sprites/work_mode/", "disabled.png"]
    assert wrap("a b", 5) == ["a b"] and text_width("ab") == 22
    canvas = Canvas(12, 12, bytes(3))
    canvas.sprite(0, 0, 1, 1, bytes.fromhex("ff000080"), 2, bytes.fromhex("0000ff"))
    assert canvas.pixels[(4 * 12 + 4) * 3:(4 * 12 + 5) * 3] == bytes((128, 0, 127))
    assert canvas.pixels[(6 * 12 + 6) * 3:(6 * 12 + 7) * 3] == bytes.fromhex("0000ff")
    assert decode_png(canvas.png())[:2] == (12, 12)
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
    reviews = commands.add_parser("review", help="write the review sheet build/art-review/spritesheet.png and the rows "
                                  "of the review record")
    reviews.add_argument("--record", type=pathlib.Path, default=RECORD)
    reviews.add_argument("--note", default="", help="a line to print under the sheet's title")
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
