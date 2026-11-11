# M8: Final art, the multiplayer audit and the Phase 2 gate

Part of the [Phase 2 plan](2026-11-10-phase-2-plan.md). Read its Global Constraints, including the multiplayer rules,
and its section "Rows that this plan handles in a special way" first. Branch: `m8-art-gate`. Builds on M7.

**Milestone goal:** every asset ships from a source under `art/` and passes the art checks. The Work Leash has original
pixel art in Cobblemon's item style, and the UI sprites are final. Blocks keep art that represents their 3D object. The
approver sees the art on one review spritesheet. The whole mod passes a multiplayer code audit. Every `MUST` and `MUST NOT` row
of the spec sheet passes on one commit. Then the work stops for the approver's play-test and art review.

**The approver's art ruling (2026-11-10):** "the block art can just be a representative of the actual 3d object, like it
currently is for the beds -- things like the work leash that you never place, are the only ones that need better art."
The spec amendments of the plan index (CD-ART-01, CD-ART-02 and CD-ART-12) state it as rows.

**Assets of the first release.** Phase 2 adds no item and no block: Telekinesis, the Magnet, the Fan Catalyst and
Tailwind work on blocks that already exist. The creative tab shows the items below and needs no icon of its own.

| Asset | Kind | Art in M8 | Rows |
| --- | --- | --- | --- |
| Work Leash | item, never placed | new original 16 × 16 pixel art in Cobblemon's item style (Task 3) | CD-ART-03, CD-ART-06, CD-ART-09 |
| Hay Bed, Wool Bed, Plush Bed, Starlit Bed | blocks | the Phase 1 models and textures, which represent the placed bed, imported as release art (Task 2) | CD-ART-01, CD-ART-02 |
| Charge Cell | block (from the approver-changes pull request) | the model and textures that the pull request ships, imported as release art (Task 2) | CD-ART-01, CD-ART-02 |
| Work Mode button: on, off, disabled, hover | UI sprites | new (Task 4) | CD-ART-04 |
| Work Stamina bar: panel bar, panel fill, head bar, head fill | UI sprites | new (Task 4) | CD-ART-04 |
| State icons: Idle, Traveling, Working, Standby, Tired, Sleeping, Healing | UI sprites | redrawn from the Phase 1 shapes (Task 4) | CD-ART-04, CD-UI-07, CD-UI-08 |
| Logo `logo.png` (256 × 256) and the project icon (512 × 512) | the approver's own image | unchanged | CD-ART-05, CD-ART-14 |

Before Task 1:

- The art tools use the Python standard library only. Task 1's script ran on 2026-11-10 against a copy of the Phase 1
  assets. It decoded all 12,088 PNG files of Minecraft 1.21.1, Cobblemon 1.8.4, Create 6.0.10 and AE2 19.2.19 in 38 s. It
  found a tile copied from `minecraft:block/dirt`, and no copy among the Phase 1 textures. It imported every Phase 1 texture
  into the source format and back with no pixel change, and it drew the bed models from above.
- Checked in the jars on 2026-11-10: GUI sprites live in `assets/<namespace>/textures/gui/sprites/`, with a
  `.png.mcmeta` for nine-slice scaling (vanilla's `widget/button.png.mcmeta`). `GuiGraphics.blitSprite(ResourceLocation,
  int, int, int, int)` and `WidgetSprites` exist.
- Checked on 2026-11-11: the Charge Cell's block id is `cobblemon_dynamics:charge_cell`. It ships
  `blockstates/charge_cell.json`, `models/block/charge_cell.json` (parent `minecraft:block/cube_all`),
  `models/item/charge_cell.json` (parent `cobblemon_dynamics:block/charge_cell`) and `textures/block/charge_cell.png`.

---

### Task 1: The art pipeline

**Rows:** CD-ART-05, CD-ART-06, CD-ART-07 (the check that needs no game), CD-ART-08, CD-ART-09, CD-ART-11, CD-ART-13.

**Files:**
- Create: `scripts/art.py` (mode 100755)
- Create: `src/test/kotlin/codes/honor/cobblemondynamics/build/ArtFilesTest.kt`
- Modify: `build.gradle` (the `test` task's inputs include `art/**`)
- Modify: `README.md` (a section "Art sources": the format and the four commands)

**Interfaces:**
- Produces: the source format and the commands of the plan index's command table. A source
  `art/textures/<path>.pixels` ships as `src/main/resources/assets/cobblemon_dynamics/textures/<path>.png`, a
  `art/textures/<path>.pixels.mcmeta` as `<path>.png.mcmeta`, and `art/models/<path>.json` as
  `assets/cobblemon_dynamics/models/<path>.json`. `check` prints `CASE cd-art-NN: PASS` or `FAIL` for each row that it
  checks, then `ART CHECK PASS` or `ART CHECK FAIL`. Task 8's gate report reads those lines.

A source is text, so a contributor edits it in any editor, and a pull request shows each pixel that changed:

```text
# Work Leash, first draft (M8 Task 3)
size 16 16
palette
  . transparent
  k #241c2e   outline
  b #9a6232   rope
pixels
................
...kkkk.........
(16 rows of 16 characters in all)
```

- [ ] **Step 1: Failing tests.** `ArtFilesTest` repeats in `./gradlew build` the checks that need no reference jar, so CI
  catches them. It reads no review record, so CI accepts art that the approver has not approved yet (CD-ART-11):

```kotlin
/** The art rows that need no game and no reference jar. `scripts/art.py check` runs the full checks. */
class ArtFilesTest {
    private val root = File(System.getProperty("cd.projectDir"))
    private val assets = File(root, "src/main/resources/assets/cobblemon_dynamics")
    private val art = File(root, "art")
    private val licenseWords = Regex("licen[cs]e|copyright|all rights reserved|creative commons|\\bcc[- ]by", RegexOption.IGNORE_CASE)

    private fun shipped(kind: String): List<File> = File(assets, kind).walk().filter { it.isFile }.toList()

    /** The source of a shipped texture, mcmeta or model. */
    private fun sourceOf(file: File): File {
        val path = file.relativeTo(assets).invariantSeparatorsPath
        return File(art, when {
            path.endsWith(".png") -> path.removeSuffix(".png") + ".pixels"
            path.endsWith(".png.mcmeta") -> path.removeSuffix(".png.mcmeta") + ".pixels.mcmeta"
            else -> path
        })
    }

    @Test
    fun `CD-ART-06 every block and item texture is 16 x 16`() {
        val textures = listOf("block", "item").flatMap { shipped("textures/$it") }.filter { it.extension == "png" }
        assertTrue(textures.isNotEmpty(), "no block or item texture")
        for (png in textures) {
            val image = ImageIO.read(png)
            assertEquals(16 to 16, image.width to image.height, png.path)
        }
    }

    @Test
    fun `CD-ART-13 LICENSE is MPL-2_0 and no asset states a license of its own`() {
        assertTrue(File(root, "LICENSE").readText().contains("Mozilla Public License Version 2.0"))
        val stated = (art.walk() + assets.walk()).filter { it.isFile && it.extension in setOf("json", "mcmeta", "pixels", "md", "txt") }
            .filter { licenseWords.containsMatchIn(it.readText()) }.map { it.relativeTo(root).path }.toList()
        assertTrue(stated.isEmpty(), "asset files that state a license: $stated")
    }

    @Test
    fun `CD-ART-11 only a public release requires the approver's approval of the art`() {
        assertFalse(File(root, ".github/workflows/build.yml").readText().contains("art.py check --release"))
    }
}
```

  Run `./gradlew test --tests "codes.honor.cobblemondynamics.build.ArtFilesTest"`: the three tests pass on the Phase 1 assets.
  They guard the rows from here on. `sourceOf` serves the CD-ART-08 test of Task 2. The failing test of this task is the
  script's own `--self-check` in Step 3, which fails until the script exists.

- [ ] **Step 2: Write `scripts/art.py`.** This is the script that ran on 2026-11-10, checked with `py -m ruff check` and
  `--self-check`:

````python
#!/usr/bin/env python3
"""Builds, checks and shows the art of Cobblemon Dynamics from its sources under art/ (ADR-0012, the ART rows of the spec).

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
ALPHABET = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
LICENSE_WORDS = re.compile(r"licen[cs]e|copyright|all rights reserved|creative commons|\bcc[- ]by", re.IGNORECASE)
BEGIN = "<!-- art-review rows: written by scripts/art.py; the verdict, date and note columns are kept -->"
END = "<!-- art-review rows end -->"
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

    Lines that start with '#' are comments. The first line reads 'size W H'. A 'palette' line follows, with one entry per line:
    a character, then 'transparent', '#rrggbb' or '#rrggbbaa', then an optional comment. A 'pixels' line follows, then H rows of
    W palette characters.
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
        match = re.fullmatch(r"\s*(\S)\s+(transparent|#[0-9a-fA-F]{6}(?:[0-9a-fA-F]{2})?)(?:\s.*)?", line)
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
    """The .pixels source of an image: '.' for a fully transparent pixel, then one character per colour, in order of first use."""
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
    palette = [f"  {char} " + ("transparent" if key == bytes(4) else "#" + (key[:3].hex() if key[3] == 255 else key.hex()))
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
    return struct.unpack(">II", png_chunks(data, name)[0][1][:8])


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
    """A PNG as width, height and 8-bit RGBA: every colour type, bit depths 1 to 16, with tRNS. Interlaced files are refused."""
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
    width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", header)
    if interlace:
        raise ArtError(f"{name}: interlaced PNGs are not supported")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride, bpp = (width * channels * depth + 7) // 8, max(1, channels * depth // 8)
    samples = _samples(_unfilter(zlib.decompress(b"".join(idat)), height, stride, bpp), width, height, depth, channels)
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


def _clear_hidden(tile: bytes) -> bytes:
    """A fully transparent pixel compares as (0, 0, 0, 0), whatever colour it stores."""
    if 0 not in tile[3::4]:
        return tile
    out = bytearray(tile)
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


def scan(ours: dict[bytes, str], jars: list[pathlib.Path]) -> tuple[list[str], int]:
    """Each reference texture tile that equals one of ours, as text, and the number of reference PNGs read."""
    alphas = {tile[3::4] for tile in ours}
    matches, read = [], 0
    for jar_path in jars:
        with zipfile.ZipFile(jar_path) as jar:
            for entry in jar.namelist():
                if not (entry.startswith("assets/") and entry.endswith(".png")):
                    continue
                width, height, rgba = decode_png(jar.read(entry), entry)
                read += 1
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
    found = [*(ART / "textures").rglob("*.pixels"), *(ART / "textures").rglob("*.pixels.mcmeta"), *(ART / "models").rglob("*.json")]
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


def manifest() -> list[pathlib.Path]:
    """Every asset that the approver reviews: each source, and the two logo files."""
    return [*sources(), ART / "logo/logo-256.png", ART / "logo/icon-512.png"]


def sha256(path: pathlib.Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def rel(path: pathlib.Path) -> str:
    return path.resolve().relative_to(ROOT).as_posix()


def build() -> int:
    written = 0
    for source in sources():
        target, data = output_of(source), built(source)
        if target.is_file():
            old = target.read_bytes()
            same = (decode_png(old, rel(target)) == decode_png(data, source.name)) if target.suffix == ".png" else old == data
            if same:
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
    rows = {}
    for line in text[text.find(BEGIN):text.find(END)].splitlines():
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if len(cells) == 6 and cells[1].startswith("`art/"):
            rows[cells[1].strip("`")] = (cells[2].strip("`"), cells[3], cells[4], cells[5])
    return rows


def write_record(path: pathlib.Path) -> None:
    old = read_record(path)
    lines = ["| Asset | Source | SHA-256 | Verdict | Date | Note |", "| --- | --- | --- | --- | --- | --- |"]
    for source in manifest():
        name, digest = rel(source), sha256(source)
        kept = old.get(name)
        verdict, date, note = kept[1:] if kept and kept[0] == digest else ("pending", "", "")
        title = re.sub(r"\.(pixels(\.mcmeta)?|json|png)$", "", source.relative_to(ART).as_posix())
        lines.append(f"| {title} | `{name}` | `{digest}` | {verdict} | {date} | {note} |")
    block = f"{BEGIN}\n\n" + "\n".join(lines) + f"\n\n{END}"
    text = path.read_text(encoding="utf-8") if path.is_file() else f"# Art review record\n\n{BEGIN}\n{END}\n"
    if BEGIN not in text or END not in text:
        raise ArtError(f"{rel(path)} has no '{BEGIN}' ... '{END}' block")
    text = text[:text.find(BEGIN)] + block + text[text.find(END) + len(END):]
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
    if not LOGO.is_file() or (ART / "logo/logo-256.png").is_file() and LOGO.read_bytes() != (ART / "logo/logo-256.png").read_bytes():
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
            continue
        data = built(source)
        same = decode_png(target.read_bytes(), rel(target)) == decode_png(data, source.name) if target.suffix == ".png" else target.read_bytes() == data
        if not same:
            problems.append(f"{rel(target)} differs from {rel(source)}: run py scripts/art.py build")
    return problems


def resolve_model(path: pathlib.Path, seen: frozenset = frozenset()) -> tuple[dict, list]:
    """The textures map and the elements of a model, with those of its parents in this mod."""
    model = json.loads(path.read_text(encoding="utf-8"))
    textures, elements = {}, []
    parent = model.get("parent", "")
    if parent.startswith(f"{NAMESPACE}:") and parent not in seen:
        parent_path = ASSETS / "models" / (parent.split(":", 1)[1] + ".json")
        if not parent_path.is_file():
            raise ArtError(f"{rel(path)} names the parent {parent}, which does not exist")
        textures, elements = resolve_model(parent_path, seen | {parent})
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

    A model that another model names as its parent is a template: its children fill its '#' variables, so only they must resolve.
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
        refs = list(textures.values()) + [face.get("texture", "") for element in elements for face in element.get("faces", {}).values()]
        for ref in refs:
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
            texts = [kind.decode() for kind, _ in png_chunks(path.read_bytes(), path.name) if kind in (b"tEXt", b"zTXt", b"iTXt")]
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


def check_copies(jars: list[pathlib.Path]) -> tuple[list[str], int]:
    """CD-ART-09: no aligned 16 x 16 tile of a shipped texture equals a tile of Minecraft, Cobblemon, Create or AE2."""
    ours: dict[bytes, str] = {}
    for png in sorted((ASSETS / "textures").rglob("*.png")):
        width, height, rgba = decode_png(png.read_bytes(), png.name)
        for (tx, ty), tile in tiles(width, height, rgba):
            ours.setdefault(tile, f"{rel(png)} ({tx}, {ty})")
    return scan(ours, jars)


def check_record(record: pathlib.Path) -> list[str]:
    """CD-ART-10: the review record approves every asset at its current SHA-256."""
    rows = read_record(record)
    problems = []
    for source in manifest():
        row = rows.get(rel(source))
        if row is None or row[0] != sha256(source) or row[1] != "approved":
            problems.append(f"{rel(source)} is {'not in the record' if row is None else row[1] if row[0] == sha256(source) else 'changed since its review'}")
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
        matches, read = check_copies(find_references(args.references))
        cases["cd-art-09"] = matches
        details["cd-art-09"] = f"{read} reference PNGs read"
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


def scaled(width: int, height: int, rgba: bytes, factor: int) -> tuple[int, int, bytes]:
    """[rgba] scaled up by [factor] with no smoothing, over a grey checkerboard, so that transparent pixels show."""
    out = bytearray()
    for y in range(height * factor):
        for x in range(width * factor):
            r, g, b, a = rgba[((y // factor) * width + x // factor) * 4:((y // factor) * width + x // factor + 1) * 4]
            bg = 200 if ((x // 8) + (y // 8)) % 2 else 160
            out += bytes(round(c * a / 255 + bg * (255 - a) / 255) for c in (r, g, b)) + b"\xff"
    return width * factor, height * factor, bytes(out)


def isometric(model: pathlib.Path, size: int = 192) -> bytes | None:
    """A south-east view from above of a block model's boxes, with the top, south and east faces shaded as the GUI shades them."""
    textures, elements = resolve_model(model)
    if not elements:
        return None
    canvas = bytearray(size * size * 4)
    unit, cx, cy = size / 40.0, size / 2.0, size * 0.58

    def screen(x: float, y: float, z: float) -> tuple[float, float]:
        return cx + (x - z) * unit * 0.866, cy + (x + z - 16) * unit * 0.5 - (y - 8) * unit

    def face(corner, along_u, along_v, ref: str, uv, shade: float) -> None:
        path = texture_path(textures, ref)
        if path is None or not path.is_file():
            return
        width, height, rgba = decode_png(path.read_bytes(), path.name)
        ox, oy = screen(*corner)
        ax, ay = (a - b for a, b in zip(screen(*along_u), (ox, oy)))
        bx, by = (a - b for a, b in zip(screen(*along_v), (ox, oy)))
        det = ax * by - ay * bx
        if abs(det) < 1e-9:
            return
        u0, v0, u1, v1 = (value * width / 16 for value in uv)
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
                        canvas[(py * size + px) * 4:(py * size + px + 1) * 4] = bytes((int(r * shade), int(g * shade), int(b * shade), 255))

    for element in sorted(elements, key=lambda e: (e["from"][0] + e["from"][2], e["from"][1])):
        (x0, y0, z0), (x1, y1, z1) = element["from"], element["to"]
        faces = element.get("faces", {})
        if "east" in faces:
            face((x1, y1, z1), (x1, y1, z0), (x1, y0, z1), faces["east"]["texture"], faces["east"].get("uv", [0, 0, 16, 16]), 0.6)
        if "south" in faces:
            face((x0, y1, z1), (x1, y1, z1), (x0, y0, z1), faces["south"]["texture"], faces["south"].get("uv", [0, 0, 16, 16]), 0.8)
        if "up" in faces:
            face((x0, y1, z0), (x1, y1, z0), (x0, y1, z1), faces["up"]["texture"], faces["up"].get("uv", [0, 0, 16, 16]), 1.0)
    return encode_png(size, size, bytes(canvas))


def review(args: argparse.Namespace) -> int:
    write_record(args.record)
    rows = read_record(args.record)
    REVIEW.mkdir(parents=True, exist_ok=True)
    cells = []
    for source in manifest():
        name = source.relative_to(ART).as_posix()
        stem = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
        if source.name.endswith(".pixels"):
            width, height, rgba = parse_pixels(source.read_text(encoding="utf-8"), source.name)
            (REVIEW / f"{stem}-1x.png").write_bytes(encode_png(width, height, rgba))
            (REVIEW / f"{stem}.png").write_bytes(encode_png(*scaled(width, height, rgba, max(1, min(16, 256 // max(width, height))))))
            images = [f"{stem}-1x.png", f"{stem}.png"]
        elif source.suffix == ".json" and "/block/" in f"/{name}":
            preview = isometric(output_of(source))
            if preview:
                (REVIEW / f"{stem}.png").write_bytes(preview)
            images = [f"{stem}.png"] if preview else []
        elif source.suffix == ".png":
            width, height, rgba = decode_png(source.read_bytes(), source.name)
            (REVIEW / f"{stem}.png").write_bytes(encode_png(width, height, rgba))
            images = [f"{stem}.png"]
        else:
            images = []
        verdict = rows.get(rel(source), ("", "pending", "", ""))[1]
        pictures = "".join(f'<img src="{html.escape(image)}" alt="{html.escape(name)}">' for image in images) or "<em>no image</em>"
        cells.append(f"<tr><td>{html.escape(name)}</td><td><code>{sha256(source)[:12]}</code></td><td>{pictures}</td>"
                     f"<td>{html.escape(verdict)}</td></tr>")
    page = ("<!doctype html><html lang=\"en\"><head><meta charset=\"utf-8\"><title>Cobblemon Dynamics art review</title>"
            "<style>body{font-family:sans-serif;background:#f4f4f4;color:#111;margin:16px}table{border-collapse:collapse}"
            "td,th{border:1px solid #bbb;padding:8px;vertical-align:middle}img{image-rendering:pixelated;margin:4px}"
            "@media (prefers-color-scheme:dark){body{background:#1b1b1b;color:#eee}td,th{border-color:#555}}</style></head><body>"
            "<h1>Cobblemon Dynamics art review</h1><p>Each asset at 1x and scaled up with no smoothing, and each block model "
            f"from above. Record each verdict in <code>{html.escape(rel(args.record))}</code>.</p>"
            "<table><tr><th>Asset</th><th>SHA-256</th><th>Images</th><th>Verdict</th></tr>" + "".join(cells) + "</table></body></html>\n")
    (REVIEW / "index.html").write_text(page, encoding="utf-8", newline="\n")
    print(f"REVIEW wrote {rel(REVIEW / 'index.html')} for {len(cells)} assets")
    return 0


def import_png(args: argparse.Namespace) -> int:
    png, source = pathlib.Path(args.png), pathlib.Path(args.source)
    width, height, rgba = decode_png(png.read_bytes(), png.name)
    source.parent.mkdir(parents=True, exist_ok=True)
    source.write_text(to_pixels(width, height, rgba, f"{source.stem}: imported from {png.name}"), encoding="utf-8", newline="\n")
    print(f"IMPORT wrote {source}")
    return 0


# Self-check on samples


def self_check() -> int:
    sample = ("# a sample\nsize 16 16\npalette\n  . transparent\n  k #1b1b24 outline\n  r #d23c3c80\npixels\n"
              + "\n".join(("." * y + "k" + "r" * 15)[:16] for y in range(16)) + "\n")
    width, height, rgba = parse_pixels(sample, "sample")
    assert (width, height) == (16, 16) and rgba[:4] == bytes.fromhex("1b1b24ff") and rgba[4:8] == bytes.fromhex("d23c3c80")
    assert decode_png(encode_png(width, height, rgba)) == (width, height, rgba)
    assert parse_pixels(to_pixels(width, height, rgba, "again"), "again") == (width, height, rgba)
    for bad in ("size 2 1\npalette\n  a #000000\npixels\nab\n", "size 2 1\npalette\npixels\naa\n", "size 2\n"):
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
    print("self-check passed")
    return 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--self-check", action="store_true", help="check the parsers on built-in samples")
    commands = parser.add_subparsers(dest="command")
    commands.add_parser("build", help="write every shipped asset from its source")
    checks = commands.add_parser("check", help="check the art against the ART rows")
    checks.add_argument("--references", nargs="+", metavar="JAR", help="'auto', or the jars of Minecraft, Cobblemon, Create and AE2")
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
    except (ArtError, OSError) as error:
        print(f"art: {error}", file=sys.stderr)
        return 2
    parser.print_help()
    return 2


if __name__ == "__main__":
    sys.exit(main())
````

  **As built (2026-11-11).** The committed `scripts/art.py` is this listing with these changes. The file is the reference
  from here on:
  - Lines wrap at 120 columns, as the other scripts do for super-linter's ruff.
  - `resolve_model` knows the box of `minecraft:block/cube_all` (`VANILLA_ELEMENTS`). The Charge Cell's model names that
    parent, so without it CD-ART-07 checked none of its faces and the review had no view of it.
  - `check_copies` also compares each texture that has no full 16 × 16 tile, such as the 70 × 12 button sprites and the
    bar sprites, whole with every reference PNG of its size. The CD-ART-09 line names the counts:
    `CASE cd-art-09: PASS 12088 reference PNGs read, N tiles and M whole textures compared`.
  - `sha256` counts CRLF line ends as LF in a text source, so a Windows checkout and CI agree on a source's hash.
  - The record's Asset column keeps `.mcmeta` for a texture's metadata (`title_of`), so the two rows of a sprite differ.
    `write_record` creates the record's folder.
  - `review` refuses to run while a shipped asset is not the build of its source. By the approver's ruling of
    2026-11-11 (plan index, ruling 3), it writes one PNG spritesheet, `build/art-review/spritesheet.png`, instead of the
    HTML page of this listing, and deletes the files of an earlier review first. The sheet shows the Work Leash, the
    creative tab's icon, the candidates under `art/candidates/` with the pick marked, and every UI sprite, each at 8x and
    1x on a light and a dark inventory slot, with a label of its name and texture path in a 5 × 9 bitmap font. A smaller
    section shows each block item from above, for reference. It needs no library outside Python's own. `--note TEXT`
    prints a line under the title.
  - A malformed PNG, JSON file or jar exits with code 2, as an unreadable input does.

- [ ] **Step 3: Run it.** `py scripts/art.py --self-check` prints `self-check passed`. `py -m ruff check scripts` passes.
  `git update-index --chmod=+x scripts/art.py` keeps the mode at 100755, as for the other scripts.

- [ ] **Step 4: The README section** "Art sources", after the build section:

```markdown
## Art sources

Every texture and model of the mod ships from a source under `art/`. A texture source (`.pixels`) is text: its size, a
palette of one character per color, and one row of characters per pixel row. Edit a source, then run
`py scripts/art.py build`, which writes the shipped files under `src/main/resources/assets/`. `py scripts/art.py check`
checks the sizes, the sources, the models and the license, and `--references auto` also compares every texture with the
textures of Minecraft, Cobblemon, Create and AE2, tile by tile. `py scripts/art.py review` writes the review spritesheet
`build/art-review/spritesheet.png`. `py scripts/art.py import PNG SOURCE` turns a PNG into a source. The art is under the
repository's MPL-2.0 license.
```

- [ ] **Step 5: Multiplayer check.** The script and the test run outside the game.

- [ ] **Step 6: Commit:** `git add -- scripts/art.py src/test/kotlin/codes/honor/cobblemondynamics/build/ArtFilesTest.kt build.gradle README.md`,
  then `git commit -m "Build, check and review the art from text sources under art/ [rag:1]"`.

---

### Task 2: Block art and models as release art

**Rows:** CD-ART-01, CD-ART-02, CD-ART-07, CD-ART-08, CD-ART-11, CD-ART-13.

**Files:**
- Create: `art/textures/block/*.pixels` (the eight bed textures and the Charge Cell's textures), `art/textures/gui/state/*.pixels`
  (the seven Phase 1 icons, which Task 4 redraws), `art/textures/item/work_leash.pixels` (the Phase 1 placeholder, which Task 3
  replaces), `art/models/**/*.json` (every shipped model)
- Delete: `scripts/placeholder_art.py` (its textures now come from `art/`, and a run would overwrite them)
- Modify: `src/test/kotlin/codes/honor/cobblemondynamics/bed/BedContentFilesTest.kt` (the KDoc of its CD-ART-11 test: the bed
  textures are release art)
- Modify: `docs/adr/0012-original-art-assets.md` (an amendment), `ARCHITECTURE.md` (section 11)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/build/ArtFilesTest.kt` (CD-ART-08)

**Interfaces:**
- Consumes: `py scripts/art.py import` and `build`, and `ArtFilesTest.sourceOf` (Task 1).
- Produces: a source under `art/` for every shipped texture and model.

- [ ] **Step 1: Failing test** in `ArtFilesTest`:

```kotlin
@Test
fun `CD-ART-08 every shipped texture and model has its source under art`() {
    val missing = (shipped("textures") + shipped("models")).filterNot { sourceOf(it).isFile }.map { it.relativeTo(root).path }
    assertTrue(missing.isEmpty(), "no source under art/ for $missing")
}
```

  Run it and see it fail: `art/` holds no texture or model source yet.

- [ ] **Step 2: Import every shipped texture.** For each PNG under `src/main/resources/assets/cobblemon_dynamics/textures/`:

```bash
cd src/main/resources/assets/cobblemon_dynamics
for png in $(find textures -name '*.png'); do py ../../../../../scripts/art.py import "$png" "../../../../../art/${png%.png}.pixels"; done
```

  Then copy every model as it is: `cp -r src/main/resources/assets/cobblemon_dynamics/models art/models`.

- [ ] **Step 3: Run** `py scripts/art.py build`: it prints `BUILD wrote 0 files`, because each source builds the pixels that
  ship. `py scripts/art.py check` prints `ART CHECK PASS`. `ArtFilesTest` passes now (CD-ART-08).

- [ ] **Step 4: Release art.** Delete `scripts/placeholder_art.py`. In the KDoc of `BedContentFilesTest`'s CD-ART-11 test, say
  that the bed textures are release art by the approver's ruling of 2026-11-10. Add to ADR-0012:

```markdown
## Amendment (2026-11-10)

The approver ruled: "the block art can just be a representative of the actual 3d object, like it currently is for the
beds -- things like the work leash that you never place, are the only ones that need better art." Blocks therefore keep art
that represents their placed 3D object: an item model that renders the block model, as the beds have. That art is release
art. The bed concepts above no longer bind. Original pixel art in Cobblemon's item style is for items that are never placed,
which is the Work Leash. The UI sprites stay as decided. Every asset still goes through the approver's review.
```

  In ARCHITECTURE section 11, replace the four bed rows with one row for the beds and one for the Charge Cell: "the block
  model and textures that represent the placed block, accepted as release art on 2026-11-10". The Work Leash row reads "a
  lead with a red-and-white clasp, in Cobblemon's item style".

- [ ] **Step 5: Multiplayer check.** Art files hold no state.

- [ ] **Step 6: Commit:** `git commit -m "Keep the block art as release art and give every texture and model a source under art/ [rag:2]"`.

---

### Task 3: The Work Leash in Cobblemon's item style

**Rows:** CD-ART-03, CD-ART-06, CD-ART-08, CD-ART-09.

**Files:**
- Modify: `art/textures/item/work_leash.pixels`, `src/main/resources/assets/cobblemon_dynamics/textures/item/work_leash.png`
  (from `build`)

Draw original 16 × 16 pixel art in the style of Cobblemon's item icons: a dark outline of one pixel, three tones on the
rope (lit, base, shade), two tones on each part of the clasp, a transparent background, and a silhouette that reads at
1×. The concept is the spec's: a lead with a red-and-white clasp. Do not trace or copy any texture of Minecraft, Cobblemon,
Create or AE2. CD-ART-09's scan checks it. A first draft, which renders as a coiled rope whose end holds a clasp in the
colors of a Poké Ball:

```text
# Work Leash, first draft (M8 Task 3): a coiled lead whose end holds a red-and-white clasp.
# Style: Cobblemon's item icons, a dark outline, three tones on the rope and two on the clasp.
size 16 16
palette
  . transparent
  k #241c2e   outline
  l #c98d4b   rope, lit
  b #9a6232   rope
  d #664021   rope, shade
  h #f36c6c   clasp red, lit
  r #d13838   clasp red
  m #2f2b38   clasp band
  w #f2f2f2   clasp white
  s #b7b7c4   clasp white, shade
pixels
................
...kkkk.........
..kllbbk........
.klkkkkbk.......
.kbk..kdk.......
.kbk..kbk.......
.kdkkkkbk.......
..kddbbbk.......
...kkkkkbk......
........kdkkk...
.........khrrk..
........khrrrrk.
........kmmwmmk.
........kwwwwsk.
.........kwwsk..
..........kkk...
```

- [ ] **Step 1: Write the source,** then run `py scripts/art.py build` and `py scripts/art.py review`, and look at
  the Work Leash on `build/art-review/spritesheet.png`. Refine the rope's twist and the clasp until the icon reads as a
  lead with a clasp at 1×.

  **As drawn (2026-11-11).** At inventory scale the draft read as a racket: a round loop on a short straight handle. The
  committed source keeps the concept but lets the rope arc over from the handle loop and hang into the clasp, so the lead
  reads as flexible. The clasp is 8 × 8 with a two-pixel button on its band. Each material has an outline in its own dark
  hue, as Cobblemon's items have: dark brown on the rope, dark red on the clasp's top, dark grey on its bottom. The rope
  has four tones and the clasp's red four, so the palette has 15 colors.
- [ ] **Step 2: Run** `py scripts/art.py check --references auto`: `CASE cd-art-09: PASS` and `ART CHECK PASS`.
- [ ] **Step 3: Multiplayer check.** An item texture holds no state.
- [ ] **Step 4: Commit:** `git commit -m "Draw the Work Leash in Cobblemon's item style"`.

---

### Task 4: The UI sprites

**Rows:** CD-ART-04, CD-UI-07, CD-UI-08, CD-ART-08, CD-ART-09.

**Files:**
- Create: `art/textures/gui/sprites/work_mode/on.pixels`, `off.pixels`, `disabled.pixels`, `hover.pixels` (70 × 12 each,
  the size of the button) and a `.pixels.mcmeta` for each
- Create: `art/textures/gui/stamina_bar/panel_bar.pixels` (50 × 4), `panel_fill.pixels` (48 × 2), `head_bar.pixels` (30 × 4),
  `head_fill.pixels` (28 × 2)
- Modify: `art/textures/gui/state/*.pixels` (the seven icons, 16 × 16)
- Create: `src/main/kotlin/codes/honor/cobblemondynamics/client/WorkModeSprites.kt`
- Modify: `src/main/kotlin/codes/honor/cobblemondynamics/client/WorkModeButton.kt` (`renderWidget` draws our sprite),
  `client/StaminaBar.kt` (the sprite ids and the fill width), `client/PanelBars.kt` and `client/HeadBars.kt` (draw the bars
  from the sprites)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/client/WorkModeSpritesTest.kt`, `client/StaminaBarTest.kt`

**Interfaces:**
- Produces:

```kotlin
/** The Work Mode button's sprite of each state (CD-ART-04), in the GUI sprite atlas. */
object WorkModeSprites {
    val ON: ResourceLocation = id("work_mode/on")
    val OFF: ResourceLocation = id("work_mode/off")
    val DISABLED: ResourceLocation = id("work_mode/disabled")
    val HOVER: ResourceLocation = id("work_mode/hover")

    /** A viewer who may not toggle sees the disabled sprite first, then a hovered button shows hover, then on or off. */
    fun of(workMode: Boolean, active: Boolean, hovered: Boolean): ResourceLocation = when {
        !active -> DISABLED
        hovered -> HOVER
        workMode -> ON
        else -> OFF
    }
}

object StaminaBar {
    // ... Phase 1 members, kept: fillWidth, parts, color, tooltip ...
    val PANEL_BAR: ResourceLocation = id("textures/gui/stamina_bar/panel_bar.png")
    val PANEL_FILL: ResourceLocation = id("textures/gui/stamina_bar/panel_fill.png")
    val HEAD_BAR: ResourceLocation = id("textures/gui/stamina_bar/head_bar.png")
    val HEAD_FILL: ResourceLocation = id("textures/gui/stamina_bar/head_fill.png")
}
```

- [ ] **Step 1: Failing unit tests.**

```kotlin
class WorkModeSpritesTest {
    @Test
    fun `CD-ART-04 the button shows a sprite for on, off, disabled and hover`() {
        assertEquals(WorkModeSprites.ON, WorkModeSprites.of(workMode = true, active = true, hovered = false))
        assertEquals(WorkModeSprites.OFF, WorkModeSprites.of(workMode = false, active = true, hovered = false))
        assertEquals(WorkModeSprites.HOVER, WorkModeSprites.of(workMode = true, active = true, hovered = true))
        assertEquals(WorkModeSprites.DISABLED, WorkModeSprites.of(workMode = true, active = false, hovered = true))
        assertEquals(4, setOf(WorkModeSprites.ON, WorkModeSprites.OFF, WorkModeSprites.DISABLED, WorkModeSprites.HOVER).size)
    }

    @Test
    fun `CD-ART-04 the jar holds the four button sprites and the bar sprites`() {
        val textures = File(System.getProperty("cd.projectDir"), "src/main/resources/assets/cobblemon_dynamics/textures")
        val sprites = listOf("on", "off", "disabled", "hover").map { "gui/sprites/work_mode/$it.png" } +
            listOf("panel_bar", "panel_fill", "head_bar", "head_fill").map { "gui/stamina_bar/$it.png" }
        for (sprite in sprites) assertTrue(File(textures, sprite).isFile, "missing $sprite")
    }
}
```

- [ ] **Step 2: Run them and see them fail** to compile.

- [ ] **Step 3: Draw the sprites.** The button: a slate body with a 1-pixel dark outline and a lighter top edge. On has a
  green accent along its left end, off a gray one, disabled is dimmed with no accent, and hover has a light border. The
  `.pixels.mcmeta` of each holds `{"gui": {"scaling": {"type": "nine_slice", "width": 70, "height": 12, "border": 3}}}`.
  The bars: a dark frame of 1 pixel around a dark-gray interior, and a fill in light grays, which the code tints green,
  yellow or red, as `StaminaBar.color` gives. The seven icons keep their Phase 1 shapes, which differ without color
  (CD-UI-08), with Cobblemon's outline and shading. Run `py scripts/art.py build` and `review`, and look at each render.

- [ ] **Step 4: Draw them in the game.**
  - `WorkModeButton.renderWidget` calls `graphics.blitSprite(WorkModeSprites.of(state?.workMode == true, active,
    isHoveredOrFocused), x, y, width, height)`, then `renderString(graphics, Minecraft.getInstance().font, if (active)
    0xFFFFFF else 0xA0A0A0)`.
  - `PanelBars.drawBar` blits `PANEL_BAR` over the bar's rectangle, then the left `StaminaBar.fillWidth(percent, 48)` columns
    of `PANEL_FILL` inside the frame, tinted with `StaminaBar.color(percent)` through `RenderSystem.setShaderColor`, which it
    resets afterwards.
  - `HeadBars.draw` draws `HEAD_BAR` and the cropped, tinted `HEAD_FILL` as textured quads with `RenderType.text`, as it
    draws the state icon.

- [ ] **Step 5: Run** the unit tests, `./gradlew build` and `py scripts/art.py check --references auto`. The look of the
  button, the bars and the icons goes to the art review (Task 5) and the play-test (manual checklist checks 6, 9, 10 and 22).

- [ ] **Step 6: Multiplayer check.** The sprites and their code are client only, in `client/`, behind
  `FMLEnvironment.dist.isClient`. They draw only what the server sent.

  **As built (2026-11-11).**
  - `StaminaBar` also gains `inner(bar)` and `fill(bar, percent)`, the inside of a bar and its filled part, which both
    bars draw from. `parts` stays, as this task says, but no code draws it now: the head bar no longer needs quads that do
    not overlap.
  - `HeadBars` draws the frame sprite 0.01 behind the name-tag plane and the fill on it, as vanilla puts a name tag's
    background 0.01 behind its text (`Font$StringRenderOutput.finish`, checked with `javap`). The frame's render type
    starts first, and `MultiBufferSource.BufferSource.getBuffer` draws a shared render type's quads when the next one
    starts (`RenderType.text` has no fixed buffer in `RenderBuffers`), so the fill always draws after the frame.
  - `WorkModeButton.renderWidget` repeats `AbstractButton.renderWidget` with our sprite: the widget's alpha, blend and depth
    test, and the label colour with the alpha in its top byte. `PanelBars` tints the fill with `GuiGraphics.setColor`,
    which sets `RenderSystem.setShaderColor` after it draws what is batched.
  - Tests beyond Step 1: `WorkModeSpritesTest` checks that each button sprite is the size of `PanelLayout.BUTTON` with a
    nine-slice `.mcmeta` of that size, and `StaminaBarTest` checks `fill` and that each bar sprite is the size of the bar
    that draws it.
  - The sprites: the button's body is slate, with a 1-pixel outline, round corners, a lighter top and left edge and a
    darker bottom and right edge. On and off have a 2-pixel accent at the left end, inside the 3-pixel nine-slice border.
    The bars have round corners and an inside that is darker under the top edge. The fills are white over light grey, so
    the tint shows as two tones. The seven icons keep the alpha masks of Phase 1. Each has an outline in a dark shade of
    its colour and a bevel lit from the top-left.

- [ ] **Step 7: Commit:** `git commit -m "Draw the Work Mode button, the Work Stamina bars and the state icons from final sprites"`.

---

### Task 5: The review spritesheet, the review record and the release check

**Rows:** CD-ART-02, CD-ART-03, CD-ART-04, CD-ART-05, CD-ART-10, CD-ART-12, CD-ART-14.

**Files:**
- Create: `docs/testing/art-review.md` (its rows come from `py scripts/art.py review`)
- Modify: `.github/workflows/release.yml` (a public tag checks the art's approvals)
- Test: `src/test/kotlin/codes/honor/cobblemondynamics/build/ReleaseWorkflowTest.kt`

**Interfaces:**
- Consumes: `review` and `check --release` (Task 1).
- Produces: the review record's table, one row for each asset: its source, its SHA-256, the verdict (`pending`, `approved`
  or `changes`), the date and the approver's note. A row whose source changed goes back to `pending`.

- [ ] **Step 1: Failing test** in `ReleaseWorkflowTest`:

```kotlin
/** CD-ART-12: a public release ships only art that the approver approved at its current source. */
@Test
fun `CD-ART-12 a public tag checks the approver's approval of every asset before it publishes`() {
    val art = steps("build").firstOrNull { (it["run"] as? String)?.contains("scripts/art.py check --release") == true }
        ?: fail("the build job has no art check")
    assertEquals("steps.tag.outputs.public == 'true'", art["if"])
}
```

  `steps` and `fail` are the class's own helper and import. Run the test and see it fail.

- [ ] **Step 2: Write the record and the step.** Create `docs/testing/art-review.md`:

```markdown
# Art review record

This record holds the approver's verdict on each asset of the first release (CD-ART-10). `py scripts/art.py review`
writes the review sheet `build/art-review/spritesheet.png` and the rows below: one row for each asset, with the SHA-256
of its source. A row whose source changed goes back to `pending`. The approver gives a verdict for each row: `approved`,
or `changes` with a note. `py scripts/art.py check --release` passes only when every row is `approved` at its current
SHA-256. The release workflow runs it for every public tag (CD-ART-12).

<!-- art-review rows: written by scripts/art.py; the verdict, date and note columns are kept -->
<!-- art-review rows end -->
```

  Run `py scripts/art.py review`, which fills the rows with `pending`. In `release.yml`, after "Check the version in the jar":

```yaml
      - name: Check the art of a public release
        if: steps.tag.outputs.public == 'true'
        run: python3 scripts/art.py check --release
```

  The release step has no `--references`: the build job resolves no AE2 jar. The copied-pixel scan (CD-ART-09) runs in the
  Phase 2 gate (Task 8) on the same sources.

- [ ] **Step 3: Run** the test, `./gradlew build`, and `py scripts/art.py check --release`. The last prints `CASE cd-art-10:
  FAIL` until the approver's review at the gate stop (Task 8).

- [ ] **Step 4: Multiplayer check.** The record and the sheet are files outside the game.

- [ ] **Step 5: Commit:** `git commit -m "Add the art review record, and check the approvals of a public release [rag:2]"`.
  The spritesheet code went in with the approver's ruling of 2026-11-11, one commit before Task 4's.

---

### Task 6: The multiplayer code audit

**Rows:** CD-SEC-01, CD-SEC-02, CD-SEC-03, CD-SEC-04, CD-SEC-05, CD-SEC-06, CD-SEC-07, CD-SEC-08, CD-SEC-09, CD-SEC-10,
CD-SEC-11, CD-PLAT-06, CD-FX-08, CD-UI-05, CD-UI-09.

The approver: "this MUST work in multiplayer ... we should make sure the code is made in-mind for multiplayer", and "no
multiplayer game tests, we don't have that capacity. just make sure you're confident in the code." This task reads the
whole mod against the eight multiplayer rules of the plan index and fixes what breaks one. It writes no GameTest. A fix
whose logic is pure gets a unit test first. The separate review of the Phase 1 code ran before M6, so this audit also
checks that its fixes still hold.

**Files:**
- Create: `docs/testing/multiplayer-audit.md` (the findings)
- Modify: each file that a finding fixes, with its unit test

- [ ] **Step 1: Inventory.** Run each search over `src/main` and read every hit:

| Rule | What to read | Search |
| --- | --- | --- |
| The server owns state | every client class that sends a payload or writes a value | `rg -n "ClientNet.send\|PacketDistributor.sendToServer" src/main` |
| Per-player state by UUID | every field that holds a player, and every map keyed by a player | `rg -n "ServerPlayer\?\|: Player\b\|Map<ServerPlayer\|currentPlayer\|\.player\b" src/main/kotlin --glob '!**/client/**'` |
| Payloads validated | every `playToServer` handler, and the checks that it runs | `rg -n "playToServer" src/main` |
| Server thread | thread changes and handler threads | `rg -n "executesOn\|HandlerThread\|Thread\(\|CompletableFuture\|parallelStream\|Executors" src/main` |
| No client class on a server | client imports outside the client packages | `rg -n "import net.minecraft.client\|import com.mojang.blaze3d" src/main --glob '!**/client/**'` |
| Static state cleared | every `object` field that changes, and its `ServerStoppedEvent` | `rg -n "^\s*(private )?(val\|var) \w+(: [^=]+)? = (HashMap\|HashSet\|LinkedHashMap\|mutableMapOf\|mutableSetOf\|mutableListOf\|ConcurrentHashMap\|WeakHashMap\|ArrayList)\|@Volatile" src/main/kotlin` |
| Effects reach every viewer | every packet send and its recipients | `rg -n "sendToPlayer\|sendToPlayers\|sendData\|setData\|displayClientMessage\|sendSystemMessage" src/main` |
| Owner and binder checks | every comparison with a player's UUID | `rg -n "\.uuid ==\|== player\|ownerUUID\|binder\|placer\|hasPermissions" src/main/kotlin` |

  Known points to read first: `PanelViewers.views` holds `ServerPlayer` objects (cleared on logout and on server stop?),
  `PokemonHeat.grants` (a `WeakHashMap`, cleared on server stop?), `EngineTiming`'s counters, `ClientState` (cleared when the
  client leaves a server?), `RoleRegistry`'s logged lines, `OnceLogger` causes (once per run is the intended semantic, so
  they stay), and the Phase 2 code: the claim memos, the fan worker mark, `FanState` and `TailwindBonus`.

- [ ] **Step 2: Record each finding** in `docs/testing/multiplayer-audit.md`: a table with the rule, the file and line, what
  the code does, the verdict (`holds` or `fix`) and, for a fix, its commit. Start the file with the eight rules, quoted
  from the plan index.

- [ ] **Step 3: Fix each `fix` finding,** test first where the logic is pure. Example: a static map that outlives an
  integrated server gets a `ServerStoppedEvent` listener that clears it, and a unit test that calls the listener's function
  and finds the map empty. A fix that changes no pure logic gets a note in the table that names the code that a reader
  checked.

- [ ] **Step 4: Run** `./gradlew build`, `./gradlew runGameTestServer` and `py scripts/testpack/testpack.py smoke`. The smoke
  run starts a dedicated server with the mod, so no client class loads on a server (CD-PLAT-06).

- [ ] **Step 5: Commit:** `git commit -m "Audit the mod for multiplayer and fix what the audit found [rag:2]"`.

---

### Task 7: The end-of-phase pass, the checklist and the guide

**Rows:** CD-TLK-05, CD-TLK-06, CD-TLK-09, CD-TLK-10, CD-TLK-11, CD-TLK-12, CD-MAG-07, CD-FAN-04, CD-FAN-11, CD-ART-02,
CD-ART-03, CD-ART-04, CD-ART-07 (the Blockbench Demo), CD-REL-08, CD-REL-09.

**Files:**
- Modify: the GameTest classes of the deferred list below
- Modify: `docs/testing/manual-checklist.md` (the Phase 2 Demo rows), `src/test/kotlin/codes/honor/cobblemondynamics/build/ManualChecklistTest.kt`
  (no area filter)
- Modify: this file's deferred list, if Tasks 1 to 6 deferred more

**End-of-phase pass for deferred GameTests.** Before Task 8, give every row in the list below its test. Write each
GameTest with one owner, as the rows state them, run them and fix what fails. The gate report states how each row was
checked. `scripts/gate_report.py --phase 2` reads this list, so keep its format: a group line per task, then one line per
row with its tests in backticks.

Deferred rows (by the index's execution rule 6, appended as milestones defer more). A row with a check after its id has
its end-of-phase test. Test names are `Class.method` in `src/gametest/kotlin/`, and checklist numbers are checks of
`docs/testing/manual-checklist.md`.

- M6 Task 3:
  - CD-TLK-09: `TelekinesisTests.cdTlk09SourceAtOffset9IsRefused`.
  - CD-TLK-10: `TelekinesisTests.cdTlk10SameChestTwiceIsRefused`.
  - CD-TLK-11: `TelekinesisTests.cdTlk11BrokenDestinationOrReplacedSourceClears`.
  - CD-TLK-12: `TelekinesisTests.cdTlk12DestinationAt33IsTooFar`.
- M6 Task 4:
  - CD-TLK-05: `TelekinesisTests.cdTlk05TwoSourcesFeedOneBarrel`.
  - CD-TLK-06: `TelekinesisTests.cdTlk06EmptySourceOrFullDestinationMeansStandby`.
- M6 Task 5:
  - CD-MAG-07: `MagnetTests.cdMag07NoItemsOrAFullChestMeansStandby`.
- M7 Task 2:
  - CD-FAN-11: `FanTests.cdFan11SmokingSurvivesARestart`.
- M7 Task 3:
  - CD-FAN-04: `FanCatalystTests.cdFan04StoneInFrontRefusesTheBind`.

The same pass checked that every Phase 2 role has its section in the player guide (`PlayerGuideTest`, CD-REL-08), and
that the manual checklist lists every Demo test of the spec sheet (CD-REL-09). The gate report's parser ends the list at
this paragraph's first three words, so keep them.

- [ ] **Step 1: Write the deferred GameTests** above, each as its row's acceptance test states it. Run
  `./gradlew runGameTestServer` twice in a row.

- [ ] **Step 2: Failing test.** `ManualChecklistTest` drops its area filter, so it counts the Phase 2 Demo rows:

```kotlin
@Test
fun `CD-REL-09 the manual checklist lists every Demo test of the spec sheet`() {
    val demos = rows.filter { (_, test) -> test.contains("Demo:") }.keys
    assertTrue(demos.isNotEmpty(), "no Demo rows in $SPEC")
    val missing = demos.filterNot { Regex("""\b$it\b""").containsMatchIn(checklist) }
    assertTrue(missing.isEmpty(), "Demo rows missing from the manual checklist: $missing")
}
```

  Delete `PHASE_2_AREAS` from the test. Run it: it fails on CD-ART-02, CD-ART-03, CD-ART-04 and CD-ART-07.

- [ ] **Step 3: The checklist entries,** in a new group "Art", after check 46:

```markdown
## Art

47. **CD-ART-02.** Setup: the review spritesheet `build/art-review/spritesheet.png`, and the four beds and a Charge Cell
    placed in the play-test world. Action: compare each block in the world with its view on the sheet. Expected: each block reads as
    the object that it is, and the approver approves it in the review record.
48. **CD-ART-03.** Setup: a Work Leash in the hotbar and one in the hand. Action: look at the icon in the inventory and in
    the hand. Expected: a lead with a red-and-white clasp, in the style of Cobblemon's items, and the approver approves it.
49. **CD-ART-04.** Setup: a pasture panel with the Work Mode button, a panel row with a bar, and a worker's head bar. Action:
    look at the button on, off, disabled and under the mouse, the bars at 100%, 40% and 10%, and the seven state icons.
    Expected: every sprite shows, and the approver approves each one.
50. **CD-ART-07.** Setup: Blockbench. Action: open each model file under `art/models/block/` as a Java block model. Expected:
    each model opens and shows the block.
```

  Run `ManualChecklistTest` and see it pass.

- [ ] **Step 4: Multiplayer check.** The deferred GameTests have one owner each, by the approver's ruling.

- [ ] **Step 5: Commit:** `git commit -m "Run the deferred Phase 2 GameTests and add the Phase 2 Demo rows to the checklist [rag:2]"`.

---

### Task 8: The Phase 2 gate

**Rows:** CD-REL-14 (the Phase 2 record), CD-ART-10 and CD-ART-12 (the art review at the stop), every `MUST` and `MUST NOT`
row of the spec sheet.

**Files:**
- Modify: `scripts/gate_report.py` (`--phase 2`, `--art-log`)
- Create: `docs/testing/phase-2-gate.md`
- Modify: `gradle.properties` (`mod_version=1.0.0-beta.1`, the version of the Phase 2 play-test build, by R56)

**Interfaces:**
- Consumes: the `CASE` lines of `py scripts/art.py check` (Task 1), the deferred lists of M5 Task 9 and of Task 7.
- Produces: `py scripts/gate_report.py --phase 2 --gradle-log FILE --testpack-log FILE ... --art-log FILE`. With no
  `--phase`, the script behaves as in Phase 1.

- [ ] **Step 1: Extend `gate_report.py`.** A `Phase` holds what differs between the two gates, and every function that read
  `REPORT`, `M5_PLAN`, `LATER` or `GATE_RECORD` takes the phase:

```python
from collections.abc import Callable  # with the other imports

M8_PLAN = ROOT / "docs/plans/2026-11-10-phase-2-m8-art-gate.md"


class Phase(NamedTuple):
    """One gate: the rows that its report holds, and the rows that it marks."""

    number: int
    report: pathlib.Path
    holds: Callable[[str], bool]                 # takes a row's area
    deferred_plans: tuple[pathlib.Path, ...]
    later: dict[str, str]                        # rows whose last part comes after this gate, by milestone
    record: str                                  # the row that this report and the play-test note complete
    earlier: dict[str, pathlib.Path]             # rows that an earlier gate completed, by its report
    art_review: frozenset[str]                   # rows that complete with the approver's art review


PHASES = {
    1: Phase(1, ROOT / "docs/testing/phase-1-gate.md", lambda area: area not in PHASE_2_AREAS, (M5_PLAN,),
             {"CD-REL-04": "M9", "CD-REL-14": "M8", "CD-REL-15": "M9"}, "CD-REL-13", {}, frozenset()),
    # CD-REL-14 needs every MUST and MUST NOT row of the sheet on one commit, so the Phase 2 report holds them all.
    2: Phase(2, ROOT / "docs/testing/phase-2-gate.md", lambda area: True, (M5_PLAN, M8_PLAN),
             {"CD-REL-04": "M9", "CD-REL-15": "M9"}, "CD-REL-14", {"CD-REL-13": ROOT / "docs/testing/phase-1-gate.md"},
             frozenset({"CD-ART-02", "CD-ART-03", "CD-ART-04", "CD-ART-10", "CD-ART-12"})),
}
```

  The changes, each with a sample in `--self-check`:
  - `read_spec(phase)` keeps a row when `phase.holds(area)`.
  - `read_deferred` reads the list of each plan in `phase.deferred_plans`, between `Deferred rows (` and
    `The same pass checked`, as it does for M5 Task 9.
  - `--art-log FILE` takes the output of `py scripts/art.py check --references auto --release`. Each
    `CASE cd-art-NN: PASS` or `FAIL` line becomes the result of `Ref("art", "cd-art-NN")` for the row CD-ART-NN. A log older
    than the commit is stale, as a test pack log is.
  - `evaluate` marks a row of `phase.art_review` `art review` while its art case fails, a row of `phase.earlier` `pass` when
    that report's play-test note has a date, and `phase.record` `play-test`.
  - The run summary gains a line `| Art checks | py scripts/art.py check --references auto --release | ... |`, and the
    totals name the phase.
  - `main` adds `--phase` (choices 1 and 2, default 1) and `--art-log`.

  Run `py scripts/gate_report.py --self-check` and `py -m ruff check scripts`.

- [ ] **Step 2: The report skeleton.** Create `docs/testing/phase-2-gate.md` in the shape of the Phase 1 report: a short
  introduction, the two generated blocks (`summary` and `rows`), and these sections that the script keeps:
  - "For the approver": the play-test client (`py scripts/testpack/testpack.py client`), the review spritesheet and the
    review record, and the steps below.
  - "Known issues and open decisions": the open items of this plan index and of Tasks 1 to 7.
  - "Play-test note": a table with a line for each check of the manual checklist that the Phase 2 play-test covers: checks
    20, 44 to 50, and the Phase 1 checks that Phase 2 code touches (6, 9, 10, 14, 15 and 22).
  - "Art review": a pointer to `docs/testing/art-review.md`.
  - "How this report is made": the commands of Step 3.

- [ ] **Step 3: Run every check on one commit,** with `JAVA_HOME` set to a JDK 21, one JVM at a time, and save each output to
  a file:
  1. Delete `build/test-results/test`.
  2. `./gradlew build runGameTestServer runGameTestServerCore runGameTestServerPerf --console=plain`.
  3. `py scripts/testpack/testpack.py` with `smoke`, `deps`, `compat`, `reload` and `restart`, one command at a time.
  4. `py scripts/art.py check --references auto --release`.
  5. `py scripts/testpack/testpack.py client`.
  6. `py scripts/gate_report.py --phase 2 --gradle-log <Gradle output> --testpack-log <each test pack output> --art-log <art output>`.

  Every automated row passes. The art review rows show `art review` until the approver's review.

- [ ] **Step 4: Commit, push, open the pull request.** `git commit -m "Write the Phase 2 gate report and assemble the play-test client [rag:3]"`.
  Then follow the rehydration note's pull request steps (HAR-809). Merge when CI is green and every thread is resolved.

- [ ] **Step 5: Stop.** Hand off to the approver for the Phase 2 play-test and the art review: the play-test client of
  Step 3, the checks of the play-test note, and the review spritesheet `build/art-review/spritesheet.png` (`py scripts/art.py review`)
  with the review record. Record each verdict that the approver gives, in the approver's words, in the review record, and the
  play-test note in the gate report. Fix what the approver rejects in a follow-up pull request, test first, and run the
  review again for each changed asset. CD-REL-14 completes when the gate report records a passing run on a named commit and
  the approver's play-test note, and the review record approves every asset. The `v1.0.0` tag and the publication are M9
  (Phase 3), which needs its own plan.
