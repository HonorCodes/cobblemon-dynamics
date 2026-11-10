#!/usr/bin/env python3
"""Clean NeoForge 1.21.1 test servers with only the mods a scenario needs. Never the COHO pack.

Usage: py scripts/testpack/testpack.py {resolve,deps,compat,client,<scenario>}   (python3 on Linux)
Everything is built under run/testpack/. Downloads are verified against mods.json and cached in
run/testpack/cache/. Each scenario_<name> function below is a command. `smoke` is the first one.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import os
import pathlib
import re
import secrets
import shutil
import socket
import struct
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections.abc import Callable
from typing import NamedTuple, Self

ROOT = pathlib.Path(__file__).resolve().parents[2]
PACK = ROOT / "run" / "testpack"
CACHE = PACK / "cache"
BASES = PACK / "base"
PINS_FILE = pathlib.Path(__file__).with_name("mods.json")
PINS = json.loads(PINS_FILE.read_text(encoding="utf-8"))
UA = {"User-Agent": "testpack-script/1.0"}
MODRINTH = "https://api.modrinth.com/v2"
NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/net/neoforged/neoforge"
MOD_ID = "cobblemon_dynamics"
ARGS_FILE = "win_args.txt" if os.name == "nt" else "unix_args.txt"
BOOT_TIMEOUT = 300
STOP_TIMEOUT = 120
INSTALL_TIMEOUT = 900
RELOAD_TIMEOUT = 120
DONE = r"Done \("
OUR_MOD_LOADING = r"Cobblemon Dynamics .* loading"
# Vanilla's recipe manager logs this on every data pack load: at start and after each /reload.
RECIPES_LOADED = r"Loaded \d+ recipes"
SKIPPED_ROLE = r"Skipping role file"
CLAIMS_EMPTY = "No claims and no leash bindings"
# CD-CFG-08: a world data pack replaces the Stoker's role file with one that names a handler nobody registers. The role
# registry logs one line for that role after the next /reload.
STOKER_FILE = ROOT / "src/main/resources/data/cobblemon_dynamics/cobblemon_dynamics/roles/stoker.json"
PROBE_PACK = "cd_reload_probe"
PROBE_HANDLER = "cobblemon_dynamics:reload_probe"
PROBE_APPLIED = rf"Role cobblemon_dynamics:stoker is disabled: handler {re.escape(PROBE_HANDLER)} is not registered"
DATA_PACK_FORMAT = 48  # Minecraft 1.21.1
# CD-CFG-01: the server config file and the keys of the CD-CFG-01 test in ConfigTest, which checks that both lists match.
SERVER_CONFIG = pathlib.Path("config", f"{MOD_ID}-server.toml")
SERVER_CONFIG_KEYS = (
    "workArea.horizontalRadius", "workArea.verticalRadius", "engine.scanIntervalTicks",
    "engine.maxBlockChecksPerTick", "engine.travelTimeoutTicks", "engine.retryWaitTicks",
    "engine.standbyMoveTicks", "engine.effectBroadcastRadius", "workMode.toggleCooldownTicks",
    "leash.pendingSelectionTicks", "stamina.ticksPerLevel", "stamina.healerRecoveryMinutesAtLevel1",
    "stamina.healerRecoveryMinutesAtLevel100", "stamina.flushIntervalTicks", "beds.speedFactors",
    "heal.cost", "heal.lowStaminaThreshold", "roles.disabled", "compat.cobbleworkers", "compat.cobblebase",
)
# CD-WHL-07, CD-CRK-08: Create's water wheel and hand crank carry our drive behaviour, which saves the turn direction with
# the block entity under this key. Both blocks sit in the force-loaded chunk 0, 0, in the air above the flat floor.
DRIVE_DIRECTION = "cd_direction"
DRIVES = {"create:water_wheel": (2, -50, 2), "create:hand_crank": (6, -50, 2)}
BLOCK_DATA = r"has the following block data: (-?\d+)"
DRIVE_TIMEOUT = 30
# FML ModSorter headers for a required dependency, and for an installed optional one, outside the range.
# Lines of the form "Mod ID: '<id>', Requested by: '<mod>', Expected range: '<range>'" follow them.
REJECTED = r"Missing or unsupported mandatory dependencies:|Unsupported installed optional dependencies:"
SERVER_PROPERTIES = {
    "online-mode": "false",
    "server-ip": "127.0.0.1",
    "enable-rcon": "true",
    "level-type": "minecraft\\:flat",
    # Explicit layers, because a flat world with empty generator-settings logs an ERROR on every start.
    "generator-settings": '{"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},'
                          '{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}]}',
    "spawn-protection": "0",
    "max-players": "4",
    "motd": "testpack",
    "view-distance": "4",
    "simulation-distance": "4",
}


class TestpackError(Exception):
    """A failure with a one-line reason. main() prints it as '<COMMAND> FAIL: <reason>'."""


def say(message: str) -> None:
    print(message, flush=True)


def rel(path: pathlib.Path) -> str:
    return path.relative_to(ROOT).as_posix() if path.is_relative_to(ROOT) else str(path)


def java() -> str:
    """The java of JAVA_HOME. The java on the PATH can be older than 21 (Java 8 on the dev box)."""
    home = os.environ.get("JAVA_HOME", "")
    exe = pathlib.Path(home, "bin", "java.exe" if os.name == "nt" else "java")
    if not home or not exe.is_file():
        raise TestpackError(f"JAVA_HOME must point at a JDK 21 (no java at {exe})")
    return str(exe)


# Downloads and pins


def http_get(url: str):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=60)


def sha512_of(path: pathlib.Path) -> str:
    digest = hashlib.sha512()
    with path.open("rb") as f:
        while chunk := f.read(1 << 20):
            digest.update(chunk)
    return digest.hexdigest()


def fetch(url: str, dest: pathlib.Path, sha512: str) -> pathlib.Path:
    """Download url to dest unless dest already has this SHA-512. A download with another hash is an error."""
    if dest.is_file() and sha512_of(dest) == sha512:
        return dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    part = dest.with_name(dest.name + ".part")
    digest = hashlib.sha512()
    with http_get(url) as response, part.open("wb") as out:
        while chunk := response.read(1 << 20):
            digest.update(chunk)
            out.write(chunk)
    if digest.hexdigest() != sha512:
        part.unlink()
        raise TestpackError(f"deleted the download of {url}: its SHA-512 does not match mods.json")
    os.replace(part, dest)
    return dest


def pin_name(pin: dict) -> str:
    return f"{pin.get('project', 'neoforge')} {pin['version']}"


def fetch_pin(pin: dict) -> pathlib.Path:
    if not pin.get("url"):
        raise TestpackError(f"{pin_name(pin)} has no file in mods.json. Run the resolve command.")
    return fetch(pin["url"], CACHE / pin["filename"], pin["sha512"])


def modrinth_versions(project: str) -> list[dict]:
    query = urllib.parse.urlencode({
        "loaders": json.dumps(["neoforge"]),
        "game_versions": json.dumps([PINS["minecraft"]]),
        "include_changelog": "false",
    })
    with http_get(f"{MODRINTH}/project/{project}/version?{query}") as response:
        return json.load(response)


def names_version(pin: str, version_number: str) -> bool:
    """True when a Modrinth version number names the pinned version.

    Projects decorate their numbers, so the pin also matches as a whole dotted token: 1.8.4 matches
    1.8.4NF and 6.0.10 matches 6.0.10+mc1.21.1, but 6.0.1 does not match 6.0.10. A trailing .x
    matches any last component: 19.1.x matches 19.1.3-beta.
    """
    if version_number == pin:
        return True
    pattern = re.escape(pin.removesuffix(".x")) + (r"\.\d+" if pin.endswith(".x") else "")
    return re.search(rf"(?<![\d.]){pattern}(?![\d.])", version_number) is not None


def resolve_modrinth(pin: dict, versions: list[dict]) -> str:
    exact = [v for v in versions if v["version_number"] == pin["version"]]
    named = exact or [v for v in versions if names_version(pin["version"], v["version_number"])]
    chosen = max(named, key=lambda v: v["date_published"], default=None)
    if chosen is None:
        pin.update(version_number=None, filename=None, url=None, sha512=None)
        return f"{pin_name(pin)}: no NeoForge {PINS['minecraft']} release on Modrinth, recorded null"
    file = next((f for f in chosen["files"] if f["primary"]), chosen["files"][0])
    pin.update(version_number=chosen["version_number"], filename=file["filename"], url=file["url"],
               sha512=file["hashes"]["sha512"])
    return f"{pin_name(pin)}: {file['filename']} (Modrinth version {chosen['version_number']})"


def resolve_neoforge(pin: dict) -> str:
    filename = f"neoforge-{pin['version']}-installer.jar"
    url = f"{NEOFORGE_MAVEN}/{pin['version']}/{filename}"
    try:
        with http_get(url + ".sha512") as response:
            sha512 = response.read().decode("ascii").split()[0]
    except urllib.error.HTTPError as error:
        if error.code != 404:
            raise
        pin.update(filename=None, url=None, sha512=None)
        return f"{pin_name(pin)}: not on the NeoForge maven, recorded null"
    pin.update(filename=filename, url=url, sha512=sha512)
    return f"{pin_name(pin)}: {filename}"


def resolve() -> None:
    """Fill the file name, URL and SHA-512 of every pin in mods.json and write the file back."""
    versions: dict[str, list[dict]] = {}
    for pin in [PINS["neoforge"], *PINS["mods"].values(), *PINS["demo_versions"].values(), *PINS["compat"].values()]:
        if "project" not in pin:
            say(resolve_neoforge(pin))
            continue
        if pin["project"] not in versions:
            versions[pin["project"]] = modrinth_versions(pin["project"])
        say(resolve_modrinth(pin, versions[pin["project"]]))
    PINS_FILE.write_text(json.dumps(PINS, indent=2) + "\n", encoding="utf-8", newline="\n")


# Profiles


def clear(folder: pathlib.Path) -> None:
    if folder.exists():
        try:
            shutil.rmtree(folder)
        except OSError as error:
            raise TestpackError(f"cannot clear {rel(folder)} ({error}). A server from an earlier run can lock it.")


def link_or_copy(src, dst):
    """Hard-link where the file system allows it, so profiles share the large jars."""
    try:
        os.link(src, dst)
    except OSError:
        shutil.copy2(src, dst)
    return dst


def args_file(folder: pathlib.Path, version: str) -> pathlib.Path:
    return folder / "libraries" / "net" / "neoforged" / "neoforge" / version / ARGS_FILE


def server_base(pin: dict) -> pathlib.Path:
    """A NeoForge server installed once per version under run/testpack/base/. Profiles copy it."""
    version = pin["version"]
    base = BASES / f"neoforge-{version}"
    if args_file(base, version).is_file():
        return base
    installer = fetch_pin(pin)
    work = base.with_name(base.name + ".part")
    clear(base)
    clear(work)
    work.mkdir(parents=True)
    say(f"installing the NeoForge {version} server into {rel(base)} (once per version)")
    output = work / "installer-output.log"
    try:
        with output.open("wb") as out:
            code = subprocess.run([java(), "-jar", str(installer), "--installServer", str(work)], cwd=work,
                                  stdout=out, stderr=subprocess.STDOUT, timeout=INSTALL_TIMEOUT, check=False).returncode
    except subprocess.TimeoutExpired:
        raise TestpackError(f"the NeoForge {version} installer did not finish in {INSTALL_TIMEOUT}s. "
                            f"See {rel(output)}.")
    if code != 0 or not args_file(work, version).is_file():
        raise TestpackError(f"the NeoForge {version} installer failed with exit code {code}. See {rel(output)}.")
    work.rename(base)
    return base


def mod_set(overrides: dict[str, dict | None] | None = None) -> list[dict]:
    """The pinned mods, with overrides applied (see install)."""
    mods = {**PINS["mods"], **(overrides or {})}
    return [pin for pin in mods.values() if pin is not None]


def mod_jar() -> pathlib.Path:
    """The newest build/libs/cobblemon_dynamics-*.jar that is not a sources or dev jar."""
    jars = [jar for jar in (ROOT / "build" / "libs").glob(f"{MOD_ID}-*.jar")
            if not re.search(r"-(sources|dev|javadoc)\.jar$", jar.name)]
    if not jars:
        raise TestpackError("no mod jar in build/libs. Run ./gradlew build first.")
    return max(jars, key=lambda jar: jar.stat().st_mtime)


def free_ports(count: int) -> list[int]:
    sockets = [socket.socket() for _ in range(count)]
    try:
        for s in sockets:
            s.bind(("127.0.0.1", 0))
        return [s.getsockname()[1] for s in sockets]
    finally:
        for s in sockets:
            s.close()


def read_properties(path: pathlib.Path) -> dict[str, str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    return dict(line.split("=", 1) for line in lines if "=" in line and not line.startswith("#"))


def install(profile: str, overrides: dict[str, dict | None] | None = None,
            neoforge: dict | None = None) -> pathlib.Path:
    """Build run/testpack/<profile>/ from scratch and return it.

    The profile gets a NeoForge server (the pinned version, or the pin in neoforge), eula.txt,
    server.properties with a random RCON password and free ports, the pinned mods and the built jar.
    overrides maps a mod key of mods.json to a replacement pin, or to None to leave that mod out.
    """
    folder = PACK / profile
    base = server_base(neoforge or PINS["neoforge"])
    clear(folder)
    shutil.copytree(base, folder, copy_function=link_or_copy)
    (folder / "eula.txt").write_text("eula=true\n", encoding="utf-8")
    server_port, rcon_port = free_ports(2)
    properties = {**SERVER_PROPERTIES, "server-port": server_port, "rcon.port": rcon_port,
                  "rcon.password": secrets.token_hex(16)}
    lines = [f"{key}={value}\n" for key, value in properties.items()]
    (folder / "server.properties").write_text("".join(lines), encoding="utf-8")
    mods = folder / "mods"
    mods.mkdir()
    for pin in mod_set(overrides):
        link_or_copy(fetch_pin(pin), mods / pin["filename"])
    jar = mod_jar()
    shutil.copy2(jar, mods / jar.name)  # a copy, because the next build rewrites build/libs in place
    names = ", ".join(sorted(p.name for p in mods.iterdir()))
    say(f"installed {rel(folder)}: NeoForge {(neoforge or PINS['neoforge'])['version']}, mods: {names}")
    return folder


# Server control


class Rcon:
    """Minecraft RCON: little-endian int32 length, request id and type, then the payload and two NUL bytes.

    The server reads each packet with a single 1460-byte read, so send one packet at a time, wait for
    its reply, and keep commands under about 1400 bytes.
    """

    LOGIN, COMMAND = 3, 2
    MAX_REPLY_PACKET = 4096  # longer replies arrive split over several packets

    def __init__(self, host: str, port: int, password: str, timeout: float = 30):
        try:
            self.sock = socket.create_connection((host, port), timeout=timeout)
        except OSError as error:
            raise TestpackError(f"RCON connect to {host}:{port} failed: {error}")
        self.timeout = timeout
        self.next_id = 0
        try:
            if self._exchange(self.LOGIN, password)[0] == -1:
                raise TestpackError("RCON login rejected")
        except BaseException:
            self.close()
            raise

    def __enter__(self) -> Self:
        return self

    def __exit__(self, *exc) -> None:
        self.close()

    def close(self) -> None:
        self.sock.close()

    def command(self, text: str) -> str:
        return self._exchange(self.COMMAND, text)[1]

    def _exchange(self, kind: int, payload: str) -> tuple[int, str]:
        self.next_id += 1
        body = struct.pack("<ii", self.next_id, kind) + payload.encode("utf-8") + b"\0\0"
        self.sock.sendall(struct.pack("<i", len(body)) + body)
        request_id, data = self._read_packet()
        parts = [data]
        self.sock.settimeout(1)
        try:
            while len(parts[-1]) >= self.MAX_REPLY_PACKET:
                parts.append(self._read_packet()[1])
        except TimeoutError:
            pass  # the reply was exactly one full packet long
        finally:
            self.sock.settimeout(self.timeout)
        return request_id, b"".join(parts).decode("utf-8", "replace")

    def _read_packet(self) -> tuple[int, bytes]:
        (length,) = struct.unpack("<i", self._recv(4))
        packet = self._recv(length)
        request_id, _kind = struct.unpack("<ii", packet[:8])
        return request_id, packet[8:-2]

    def _recv(self, size: int) -> bytes:
        data = b""
        while len(data) < size:
            chunk = self.sock.recv(size - len(data))
            if not chunk:
                raise ConnectionError("RCON connection closed by the server")
            data += chunk
        return data


class Server:
    """The server of one profile folder from install().

    Use it as a context manager, so a failed scenario never leaves a java process running.
    Log searches always cover logs/latest.log from its first line.
    """

    def __init__(self, folder: pathlib.Path):
        self.folder = folder
        self.log = folder / "logs" / "latest.log"
        self.console = folder / "console.log"
        properties = read_properties(folder / "server.properties")
        self.rcon_port = int(properties["rcon.port"])
        self.rcon_password = properties["rcon.password"]
        self.process: subprocess.Popen | None = None
        self.lines: list[str] = []
        self._read_bytes = 0

    def __enter__(self) -> Self:
        return self

    def __exit__(self, *exc) -> None:
        if self.process and self.process.poll() is None:
            self.process.kill()
            self.process.wait()

    def start(self, jvm_args: list[str] | None = None) -> None:
        """Start the server headless. Its stdout and stderr go to console.log in the profile folder."""
        args = sorted(self.folder.glob(f"libraries/net/neoforged/neoforge/*/{ARGS_FILE}"))
        if len(args) != 1:
            raise TestpackError(f"expected one NeoForge {ARGS_FILE} in {rel(self.folder)}, found {len(args)}")
        command = [java(), "-Xmx3G", *(jvm_args or []), "@user_jvm_args.txt",
                   "@" + args[0].relative_to(self.folder).as_posix(), "nogui"]
        with self.console.open("wb") as console:
            self.process = subprocess.Popen(command, cwd=self.folder, stdin=subprocess.DEVNULL, stdout=console,
                                            stderr=subprocess.STDOUT)
        say(f"started the server in {rel(self.folder)} (pid {self.process.pid})")

    def _read_log(self) -> None:
        """Append the complete lines written to latest.log since the last call to self.lines."""
        if not self.log.is_file():
            return
        with self.log.open("rb") as f:
            f.seek(self._read_bytes)
            data = f.read()
        end = data.rfind(b"\n") + 1
        self.lines += data[:end].decode("utf-8", "replace").splitlines()
        self._read_bytes += end

    def find_log(self, pattern: str) -> str | None:
        """The first line of latest.log so far that matches the regular expression pattern."""
        self._read_log()
        regex = re.compile(pattern)
        return next((line for line in self.lines if regex.search(line)), None)

    def count_log(self, pattern: str) -> int:
        """The number of lines of latest.log so far that match the regular expression pattern."""
        self._read_log()
        regex = re.compile(pattern)
        return sum(1 for line in self.lines if regex.search(line))

    def first_error(self) -> str | None:
        line = self.find_log(r"/(ERROR|FATAL)\]")
        return line and line.strip()

    def wait_log(self, pattern: str, timeout: float, count: int = 1) -> str:
        """Wait until count lines of latest.log match pattern and return the last of them.

        Fails when timeout seconds pass or the server exits first.
        """
        deadline = time.monotonic() + timeout
        regex = re.compile(pattern)
        while True:
            exited = self.process.poll() is not None  # checked before the read, so the last lines are seen
            self._read_log()
            matches = [line for line in self.lines if regex.search(line)]
            if len(matches) >= count:
                return matches[count - 1]
            if exited:
                raise TestpackError(f"the server exited with code {self.process.returncode} before {pattern!r} "
                                    f"appeared in {rel(self.log)}. First error: {self.first_error()}")
            if time.monotonic() > deadline:
                raise TestpackError(f"no {pattern!r} in {rel(self.log)} after {timeout:.0f}s")
            time.sleep(1)

    def rcon(self, command: str) -> str:
        """Run a console command over RCON and return the reply. Waits for RCON to come up first."""
        self.wait_log(r"RCON running on ", BOOT_TIMEOUT)
        with Rcon("127.0.0.1", self.rcon_port, self.rcon_password) as client:
            return client.command(command)

    def wait_exit(self, timeout: float) -> int:
        """Wait for the server process to end and return its exit code. Kills it and fails after timeout."""
        try:
            return self.process.wait(timeout)
        except subprocess.TimeoutExpired:
            self.process.kill()
            self.process.wait()
            raise TestpackError(f"the server did not exit in {timeout:.0f}s. The script killed it.")

    def stop(self) -> int:
        """Send stop over RCON and wait for the process to end. Returns the exit code."""
        if self.process.poll() is None:
            try:
                self.rcon("stop")
            except ConnectionError:
                pass  # the server can close RCON while it stops, before the reply arrives
        return self.wait_exit(STOP_TIMEOUT)

    def restart(self) -> None:
        """Stop the server and start it again in the same folder, as an owner does.

        The first run's logs/latest.log and console.log become before-restart.log beside them, so the log
        methods read only the new run, from its first line.
        """
        code = self.stop()
        if code != 0:
            raise TestpackError(f"the server exited with code {code} at the restart. See {rel(self.log)}.")
        for path in (self.log, self.console):
            if path.is_file():
                os.replace(path, path.with_name("before-restart.log"))
        self.lines = []
        self._read_bytes = 0
        self.start()


# Scenarios: each scenario_<name>(server) is the command <name>. It runs on a fresh profile with the
# pinned mod set, after start() and before stop().


def scenario_smoke(server: Server) -> None:
    """The built jar loads beside the pinned mods and the server answers commands over RCON."""
    server.wait_log(DONE, BOOT_TIMEOUT)
    if server.find_log(OUR_MOD_LOADING) is None:
        raise TestpackError(f"no {OUR_MOD_LOADING!r} line in {rel(server.log)}")
    reply = server.rcon("list")
    if "players online" not in reply:
        raise TestpackError(f"unexpected reply to list: {reply!r}")


def reload_data(server: Server) -> None:
    """Run /reload and wait until it ends."""
    loads = server.count_log(RECIPES_LOADED)
    server.rcon("reload")
    # The data pack reload ends on the server thread after the command has replied, so its own log line marks the end.
    server.wait_log(RECIPES_LOADED, RELOAD_TIMEOUT, count=loads + 1)


def write_probe_pack(folder: pathlib.Path) -> pathlib.Path:
    """A world data pack whose Stoker file is the built-in one with PROBE_HANDLER. /reload enables a new world pack."""
    pack = folder / "world" / "datapacks" / PROBE_PACK
    role = json.loads(STOKER_FILE.read_text(encoding="utf-8"))
    role["handler"] = PROBE_HANDLER
    roles = pack / "data" / "cobblemon_dynamics" / "cobblemon_dynamics" / "roles"
    roles.mkdir(parents=True)
    meta = {"pack": {"pack_format": DATA_PACK_FORMAT, "description": "Cobblemon Dynamics reload probe"}}
    (pack / "pack.mcmeta").write_text(json.dumps(meta, indent=2) + "\n", encoding="utf-8", newline="\n")
    (roles / "stoker.json").write_text(json.dumps(role, indent=2) + "\n", encoding="utf-8", newline="\n")
    return pack


def scenario_reload(server: Server) -> None:
    """The claims command answers on the console (CD-UI-14), and /reload applies a changed role file (CD-CFG-08)."""
    server.wait_log(DONE, BOOT_TIMEOUT)
    reply = server.rcon("cobblemondynamics claims")
    if CLAIMS_EMPTY not in reply:
        raise TestpackError(f"unexpected reply to cobblemondynamics claims: {reply!r}")
    reload_data(server)
    if server.find_log(PROBE_APPLIED) is not None:
        raise TestpackError(f"the Stoker names {PROBE_HANDLER} before the probe pack exists")
    pack = write_probe_pack(server.folder)
    reload_data(server)
    skipped = server.find_log(SKIPPED_ROLE)
    if skipped is not None:
        raise TestpackError(f"a role file did not load: {skipped.strip()}")
    if server.find_log(PROBE_APPLIED) is None:
        raise TestpackError(f"CD-CFG-08: after /reload the role registry still uses the old Stoker file, not {rel(pack)}")
    say(f"CD-CFG-08: /reload applied the Stoker file of {rel(pack)}")


def has_key(table: dict, dotted: str) -> bool:
    for part in dotted.split("."):
        if not isinstance(table, dict) or part not in table:
            return False
        table = table[part]
    return True


def check_server_config(folder: pathlib.Path) -> None:
    """CD-CFG-01: after the first start, the server's config folder holds the server config with every key."""
    # Here, not at the top: super-linter's ruff sorts tomllib as third-party and newer ruff as standard library.
    import tomllib

    path = folder / SERVER_CONFIG
    if not path.is_file():
        raise TestpackError(f"CD-CFG-01: no {rel(path)} after the first start")
    try:
        config = tomllib.loads(path.read_text(encoding="utf-8"))
    except tomllib.TOMLDecodeError as error:
        raise TestpackError(f"CD-CFG-01: {rel(path)} is not TOML: {error}")
    missing = [key for key in SERVER_CONFIG_KEYS if not has_key(config, key)]
    if missing:
        raise TestpackError(f"CD-CFG-01: {rel(path)} lacks {', '.join(missing)}")
    say(f"CD-CFG-01: {rel(path)} holds all {len(SERVER_CONFIG_KEYS)} keys")


def coordinates(pos: tuple[int, int, int]) -> str:
    return " ".join(str(c) for c in pos)


def drive_direction(server: Server, pos: tuple[int, int, int]) -> int | None:
    """The saved turn direction of the block entity at pos, or None while it has none, as before its first tick."""
    match = re.search(BLOCK_DATA, server.rcon(f"data get block {coordinates(pos)} {DRIVE_DIRECTION}"))
    return int(match.group(1)) if match else None


def wait_drive(server: Server, block: str, pos: tuple[int, int, int], expected: int) -> None:
    """Wait until the block entity at pos reads the direction expected. Create attaches behaviours on the first tick."""
    deadline = time.monotonic() + DRIVE_TIMEOUT
    while (value := drive_direction(server, pos)) != expected:
        if time.monotonic() > deadline:
            raise TestpackError(f"the {block} at {coordinates(pos)} reads direction {value}, not {expected}")
        time.sleep(0.5)


def scenario_restart(server: Server) -> None:
    """A real restart: the server config file (CD-CFG-01), the turn direction that a wheel and a crank save with their block
    entities (CD-WHL-07, CD-CRK-08), and an empty claim registry after the restart (CD-CLM-07).

    Pasturing needs a player, which the test pack has not got, so the restart checks that need a worker (the claims, bindings,
    flips, beds and Work Stamina of CD-CLM-07, CD-LSH-07, CD-WHL-07, CD-CRK-08, CD-BED-14 and CD-STA-04) stay in the GameTests.
    """
    server.wait_log(DONE, BOOT_TIMEOUT)
    check_server_config(server.folder)  # before the first restart, so the file is the one that the first start wrote
    server.rcon("forceload add 0 0")
    for block, pos in DRIVES.items():
        reply = server.rcon(f"setblock {coordinates(pos)} {block}")
        if "Changed the block" not in reply:
            raise TestpackError(f"setblock {block} failed: {reply!r}")
    for block, pos in DRIVES.items():
        wait_drive(server, block, pos, 1)  # a new target turns forward
        server.rcon(f"data merge block {coordinates(pos)} {{{DRIVE_DIRECTION}:-1}}")
        wait_drive(server, block, pos, -1)
    server.restart()
    server.wait_log(DONE, BOOT_TIMEOUT)
    for block, pos in DRIVES.items():
        value = drive_direction(server, pos)
        if value != -1:
            raise TestpackError(f"CD-WHL-07, CD-CRK-08: the {block} turns {value} after the restart, -1 before it")
    say("CD-WHL-07, CD-CRK-08: the wheel and the crank kept direction -1 through the restart")
    reply = server.rcon("cobblemondynamics claims")
    if CLAIMS_EMPTY not in reply:
        raise TestpackError(f"CD-CLM-07: the registry is not empty after the restart: {reply!r}")
    error = server.find_log(r"/(ERROR|FATAL)\].*(cobblemon_?dynamics|codes\.honor)")
    if error is not None:
        raise TestpackError(f"the restarted server logged an error of ours: {error.strip()}")


def scenarios() -> dict[str, Callable[[Server], None]]:
    return {name.removeprefix("scenario_"): fn for name, fn in globals().items() if name.startswith("scenario_")}


def run_scenario(name: str) -> None:
    folder = install(name)
    with Server(folder) as server:
        server.start()
        scenarios()[name](server)
        code = server.stop()
    if code != 0:
        raise TestpackError(f"the server exited with code {code} after stop. See {rel(server.log)}.")


# Dependency-range demos


class Case(NamedTuple):
    """One dependency-range case.

    swap maps a mod key of mods.json to a demo_versions key to install instead, or to None to leave the
    mod out. neoforge is the demo_versions key of another NeoForge version. reject is the mod id and
    range that FML must reject, or None when the server must load.
    """

    name: str
    swap: dict[str, str | None]
    neoforge: str | None = None
    reject: tuple[str, str] | None = None


DEPS_CASES = [
    # The floor loads the whole set: the stamina_view attachment uses NeoForge's attachment sync (CD-PLAT-01).
    Case("neoforge-floor", {}, neoforge="neoforge_floor"),
    Case("neoforge-below-floor", {}, neoforge="neoforge_below_floor", reject=("neoforge", "[21.1.219,)")),
    Case("cobblemon-below-floor", {"cobblemon": "cobblemon_below_floor"}, reject=("cobblemon", "[1.8.4,1.9)")),
    Case("create-below-floor", {"create": "create_below_floor"}, reject=("create", "[6.0.10,6.1)")),
    Case("ae2-below-floor", {"ae2": "ae2_below_floor"}, reject=("ae2", "[19.2,20)")),
    Case("without-create-ae2", {"create": None, "ae2": None, "guideme": None}),
    Case("kff-floor", {}),  # the pinned set already uses kotlinforforge 5.10.0, the floor of [5.10,)
]


def run_case(case: Case) -> tuple[str, str]:
    demo = PINS["demo_versions"]
    overrides = {mod: demo[key] if key else None for mod, key in case.swap.items()}
    neoforge = demo[case.neoforge] if case.neoforge else None
    for pin in [*overrides.values(), neoforge]:
        if pin is not None and not pin.get("url"):
            return "SKIPPED", f"{pin_name(pin)} has no NeoForge {PINS['minecraft']} release"
    with Server(install(f"deps-{case.name}", overrides, neoforge)) as server:
        server.start()
        first = server.wait_log(f"{DONE}|{REJECTED}", BOOT_TIMEOUT)
        loaded = re.search(DONE, first) is not None
        if loaded:
            server.stop()
        else:
            server.wait_exit(STOP_TIMEOUT)  # FML ends the server itself after a rejection
    if case.reject is None:
        if not loaded:
            return "FAIL", f"FML rejected the set: {first.strip()}"
        if server.find_log(OUR_MOD_LOADING) is None:
            return "FAIL", f"server loaded without a {OUR_MOD_LOADING!r} line"
        return "PASS", f"loaded: {first.split(']: ', 1)[-1].strip()}"
    mod, version_range = case.reject
    if loaded:
        return "FAIL", f"the server loaded, but FML must reject {mod} {version_range}"
    expected = rf"Mod ID: '{re.escape(mod)}', Requested by: '{MOD_ID}', Expected range: '{re.escape(version_range)}'"
    line = server.find_log(expected)
    if line is None:
        return "FAIL", f"FML did not reject {mod} {version_range} for {MOD_ID}. See {rel(server.log)}."
    return "PASS", line.strip()


def deps_demo() -> bool:
    """Run every dependency-range case, print one CASE line each, and return True when none failed."""
    results = []
    for case in DEPS_CASES:
        say(f"deps case {case.name}")
        try:
            status, detail = run_case(case)
        except (TestpackError, OSError) as error:
            status, detail = "FAIL", str(error)
        say(f"  -> {status}")
        results.append((case.name, status, detail))
    for name, status, detail in results:
        say(f"CASE {name}: {status} {detail}")
    ran = [status for _, status, _ in results if status != "SKIPPED"]
    return bool(ran) and all(status == "PASS" for status in ran)


# Compatibility with Cobbleworkers and Cobblebase


class CompatCase(NamedTuple):
    """One neighbour mod beside the pinned set.

    mods holds the keys under "compat" in mods.json to install: the neighbour first, then the libraries
    it needs. The server logs every class it loads: present names a class of ours that must load, which
    shows that the log sees our classes, and absent holds the class name prefixes of the other mod's
    compat code, which must never load (CD-CMP-06, CD-CMP-07).
    """

    name: str
    mods: tuple[str, ...]
    present: str
    absent: tuple[str, ...]


OUR_PACKAGE = "codes.honor.cobblemondynamics."
COMPAT_CASES = [
    # Cobblebase 2.0.1 needs Cloth Config at run time, although its neoforge.mods.toml does not list it.
    CompatCase("compat_cobblebase", ("cobblebase", "cloth_config"), OUR_PACKAGE + "compat.CobblebaseHooks",
               (OUR_PACKAGE + "compat.Cobbleworkers",)),
    CompatCase("compat_cobbleworkers", ("cobbleworkers",), OUR_PACKAGE + "compat.CobbleworkersCompat",
               (OUR_PACKAGE + "compat.Cobblebase", OUR_PACKAGE + "mixin.compat.cobblebase.")),
]
CLASS_LOG = "classload.log"
# CobblebaseHooks logs this once when a Cobblebase hook did not apply (CD-CMP-05).
HOOKS_MISSING = r"Cobblebase hooks .* did not apply"
MIXIN_FAILURE = r"Mixin apply failed|InvalidInjectionException|InjectionError|cobblemon_dynamics\.mixins\.json.*(ERROR|failed)"


def loaded_classes(log: pathlib.Path) -> set[str]:
    """The class names in a -Xlog:class+load log: '[0.123s][info][class,load] a.b.C source: ...'."""
    if not log.is_file():
        raise TestpackError(f"no class load log at {rel(log)}")
    names = set()
    with log.open(encoding="utf-8", errors="replace") as f:
        for line in f:
            match = re.search(r"\[class,load\] (\S+)", line)
            if match:
                names.add(match.group(1))
    return names


def run_compat_case(case: CompatCase) -> tuple[str, str]:
    pins = {mod: PINS["compat"][mod] for mod in case.mods}
    pin = pins[case.mods[0]]
    for extra in pins.values():
        if not extra.get("url"):
            return "SKIPPED", f"{pin_name(extra)} has no NeoForge {PINS['minecraft']} release"
    folder = install(case.name, pins)
    problems = []
    with Server(folder) as server:
        server.start([f"-Xlog:class+load=info:file={CLASS_LOG}"])
        server.wait_log(DONE, BOOT_TIMEOUT)
        if server.find_log(OUR_MOD_LOADING) is None:
            problems.append(f"no {OUR_MOD_LOADING!r} line")
        for prefix in case.absent:
            error = server.find_log(rf"/(ERROR|FATAL)\].*{re.escape(prefix)}")
            if error is not None:
                problems.append(f"an error names {prefix}: {error.strip()}")
        for pattern in (HOOKS_MISSING, MIXIN_FAILURE):
            line = server.find_log(pattern)
            if line is not None:
                problems.append(line.strip())
        code = server.stop()
    if code != 0:
        problems.append(f"the server exited with code {code} after stop")
    classes = loaded_classes(folder / CLASS_LOG)
    if case.present not in classes:
        problems.append(f"{case.present} never loaded")
    for prefix in case.absent:
        loaded = sorted(name for name in classes if name.startswith(prefix))
        if loaded:
            problems.append(f"loaded {', '.join(loaded)}")
    if problems:
        return "FAIL", "; ".join(problems)
    return "PASS", f"{pin_name(pin)}: started and stopped, {case.present} loaded, none of {', '.join(case.absent)} loaded"


def compat_demo() -> bool:
    """Run each compat case, print one CASE line each, and return True when none failed."""
    results = []
    for case in COMPAT_CASES:
        say(f"compat case {case.name}")
        try:
            status, detail = run_compat_case(case)
        except (TestpackError, OSError) as error:
            status, detail = "FAIL", str(error)
        say(f"  -> {status}")
        results.append((case.name, status, detail))
    for name, status, detail in results:
        say(f"CASE {name}: {status} {detail}")
    ran = [status for _, status, _ in results if status != "SKIPPED"]
    return bool(ran) and all(status == "PASS" for status in ran)


# Client mods folder


def client() -> pathlib.Path:
    """Fill run/testpack/client/mods/ with the pinned mods and the built jar, for a manual play-test."""
    mods = PACK / "client" / "mods"
    clear(mods)
    mods.mkdir(parents=True)
    for pin in mod_set():
        shutil.copy2(fetch_pin(pin), mods / pin["filename"])
    jar = mod_jar()
    shutil.copy2(jar, mods / jar.name)
    return mods


def main() -> int:
    sys.stdout.reconfigure(errors="replace")
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("command", choices=["resolve", "deps", "compat", "client", *scenarios()])
    command = parser.parse_args().command
    try:
        if command == "resolve":
            resolve()
            return 0
        if command == "client":
            say(str(client()))
            say(f"Point the mods folder of a clean NeoForge {PINS['neoforge']['version']} launcher instance at "
                "this folder (never an existing modpack instance).")
            return 0
        if command == "deps":
            passed = deps_demo()
            say("DEPS PASS" if passed else "DEPS FAIL")
            return 0 if passed else 1
        if command == "compat":
            passed = compat_demo()
            say("COMPAT PASS" if passed else "COMPAT FAIL")
            return 0 if passed else 1
        run_scenario(command)
    except (TestpackError, OSError) as error:
        say(f"{command.upper()} FAIL: {error}")
        return 1
    say(f"{command.upper()} PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
