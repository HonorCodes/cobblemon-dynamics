#!/usr/bin/env python3
"""Writes a gate report: each MUST and MUST NOT row of the spec sheet that the gate holds, with its tests, result and commit.

The Phase 1 gate (the default) holds every row outside the TLK, MAG, FAN, TLW, QWK and ART areas, except the rows of
PHASE_2_ROWS, which are Phase 2 work in a Phase 1 area: the Quick Work keys CD-CFG-16 to CD-CFG-18, the INFO lines at load
CD-CMP-08 to CD-CMP-10, and the shared containers CD-CLM-10, CD-CLM-11, CD-LSH-28, CD-LSH-29 and CD-CFG-19. The Phase 2 gate holds every row, because CD-REL-14 needs every MUST
and MUST NOT row of the sheet to pass on one commit. The script reads the spec sheet, the JUnit XML reports of
`./gradlew build`, the GameTest sources with the log of each GameTest run, the deferred lists of the gate's plans, the
manual checklist, the test pack's README and the output of each test pack command. The Phase 2 gate also reads the output
of `py scripts/art.py check` and the play-test note of the Phase 1 report. The script rewrites only the generated blocks
of the gate's report, docs/testing/phase-N-gate.md, and keeps the text around them.

Usage: py scripts/gate_report.py [--phase 1] --gradle-log FILE --testpack-log FILE [--testpack-log FILE ...] [--allow-dirty]
       py scripts/gate_report.py --phase 2 --gradle-log FILE --testpack-log FILE [...] --art-log FILE [--allow-dirty]
       py scripts/gate_report.py --self-check
Exit code 0 when every automated row has a passing test, 1 when a row has not or an input disagrees with another, and 2
when an input is missing or unreadable.
"""
from __future__ import annotations

import argparse
import dataclasses
import datetime
import hashlib
import itertools
import json
import pathlib
import re
import subprocess
import sys
from collections import defaultdict
from collections.abc import Callable
from typing import NamedTuple
from xml.etree import ElementTree

ROOT = pathlib.Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs/plans/2026-11-09-v1-spec-sheet.md"
M5_PLAN = ROOT / "docs/plans/2026-11-09-phase-1-m5-ui-compat-gate.md"
M8_PLAN = ROOT / "docs/plans/2026-11-10-phase-2-m8-art-gate.md"
# The task of each plan whose end-of-phase pass wrote the tests of its deferred list.
DEFERRED_TASKS = {M5_PLAN: "M5 Task 9", M8_PLAN: "M8 Task 7"}
CHECKLIST = ROOT / "docs/testing/manual-checklist.md"
ART_SCRIPT = ROOT / "scripts/art.py"
ART_COMMAND = "py scripts/art.py check --references auto --release"
JUNIT = ROOT / "build/test-results/test"
BUILD_GRADLE = ROOT / "build.gradle"
TESTPACK = ROOT / "scripts/testpack/testpack.py"
TESTPACK_README = ROOT / "scripts/testpack/README.md"
PINS = ROOT / "scripts/testpack/mods.json"
CLIENT_MODS = ROOT / "run/testpack/client/mods"
BUILD_LIBS = ROOT / "build/libs"
PERF_TEST = ROOT / "src/gametest/kotlin/codes/honor/cobblemondynamics/test/PerfTests.kt"
PHASE_2_AREAS = {"TLK", "MAG", "FAN", "TLW", "QWK", "ART"}
# Rows outside the Phase 2 areas that are Phase 2 work, the same list as PHASE_2_ROWS of scripts/check_plan_coverage.py: the
# Quick Work keys (R63), the INFO lines at load (R65) and the shared containers of the logistics roles (R66).
PHASE_2_ROWS = {
    "CD-CFG-16", "CD-CFG-17", "CD-CFG-18", "CD-CMP-08", "CD-CMP-09", "CD-CMP-10",
    "CD-CLM-10", "CD-CLM-11", "CD-LSH-28", "CD-LSH-29", "CD-CFG-19",
}
LEVELS = ("MUST", "MUST NOT")
PASS = "pass"
FINISHED = "finished"
PACK_ORDER = ["smoke", "deps", "compat", "reload", "restart"]


class Run(NamedTuple):
    """A GameTest run: its Gradle task, its log, its source folders and the one namespace that it enables."""

    name: str
    title: str
    task: str
    log: pathlib.Path
    sources: tuple[str, ...]
    namespace: str


# The gameTestServer runs of build.gradle. CI runs the first three. The three claim runs run outside CI, in each pull
# request that touches the claims code and at each gate.
RUNS = (
    Run("full", "GameTests, full mod set", "runGameTestServer", ROOT / "run/gametest/logs/latest.log",
        ("src/gametest/kotlin", "src/gametest/kotlin-shared"), "cobblemon_dynamics"),
    Run("core", "GameTests without Create and AE2", "runGameTestServerCore", ROOT / "run/gametest-core/logs/latest.log",
        ("src/gametestCore/kotlin", "src/gametest/kotlin-shared"), "cobblemon_dynamics"),
    Run("perf", "GameTests, server time of 64 Working workers", "runGameTestServerPerf",
        ROOT / "run/gametest-perf/logs/latest.log", ("src/gametest/kotlin", "src/gametest/kotlin-shared"),
        "cobblemon_dynamics_perf"),
    Run("ftbchunks", "GameTests with FTB Chunks", "runGameTestServerFtbChunks", ROOT / "run/gametest-ftbchunks/logs/latest.log",
        ("src/gametestFtbChunks/kotlin", "src/gametest/kotlin-claims", "src/gametest/kotlin-shared"), "cobblemon_dynamics"),
    Run("opac", "GameTests with Open Parties and Claims", "runGameTestServerOpac", ROOT / "run/gametest-opac/logs/latest.log",
        ("src/gametestOpac/kotlin", "src/gametest/kotlin-claims", "src/gametest/kotlin-shared"), "cobblemon_dynamics"),
    Run("minecolonies", "GameTests with MineColonies", "runGameTestServerMineColonies",
        ROOT / "run/gametest-minecolonies/logs/latest.log",
        ("src/gametestMineColonies/kotlin", "src/gametest/kotlin-claims", "src/gametest/kotlin-shared"), "cobblemon_dynamics"),
)


def is_phase_2(row: str, area: str) -> bool:
    """Phase 2 work, as scripts/check_plan_coverage.py counts it: a row of a Phase 2 area, or a row of PHASE_2_ROWS."""
    return area in PHASE_2_AREAS or row in PHASE_2_ROWS


class Phase(NamedTuple):
    """One gate: the rows that its report holds, and the rows that it marks.

    holds takes a row id and its area. later maps each row whose last part comes after this gate to that milestone.
    record is the row that this report and its play-test note complete. earlier maps each row that an earlier gate
    completed to that gate's report. art_review maps each row that completes with the approver's art review to the case
    of `py scripts/art.py check` that reads the review record for the row.
    """

    number: int
    report: pathlib.Path
    holds: Callable[[str, str], bool]
    deferred_plans: tuple[pathlib.Path, ...]
    later: dict[str, str]
    record: str
    earlier: dict[str, pathlib.Path]
    art_review: dict[str, str]

    @property
    def checks_art(self) -> bool:
        """The gate of the art review rows also runs the art checks."""
        return bool(self.art_review)


# The plan index, "The art review rows": CD-ART-02, CD-ART-03, CD-ART-04 and CD-ART-10 complete when the review record
# approves every asset at its current source (cd-art-10). The release check of CD-ART-12 (cd-art-12) needs those approvals.
ART_REVIEW = {"CD-ART-02": "cd-art-10", "CD-ART-03": "cd-art-10", "CD-ART-04": "cd-art-10", "CD-ART-10": "cd-art-10",
              "CD-ART-12": "cd-art-12"}
PHASES = {
    # After the Phase 1 gate come the v1.0.0 tag (CD-REL-04), the Phase 2 gate (CD-REL-14) and the approver's pack
    # (CD-REL-15), by the plan index's special rows and M5 Task 9. The Phase 1 record is CD-REL-13 (M5 Task 9, Step 5).
    1: Phase(1, ROOT / "docs/testing/phase-1-gate.md", lambda row, area: not is_phase_2(row, area), (M5_PLAN,),
             {"CD-REL-04": "M9", "CD-REL-14": "M8", "CD-REL-15": "M9"}, "CD-REL-13", {}, {}),
    # CD-REL-14 needs every MUST and MUST NOT row of the sheet on one commit, so the Phase 2 report holds them all.
    2: Phase(2, ROOT / "docs/testing/phase-2-gate.md", lambda row, area: True, (M5_PLAN, M8_PLAN),
             {"CD-REL-04": "M9", "CD-REL-15": "M9"}, "CD-REL-14", {"CD-REL-13": ROOT / "docs/testing/phase-1-gate.md"},
             ART_REVIEW),
}

ROW = re.compile(r"^\| (CD-([A-Z]+)-\d{2}) \|")
CELL = re.compile(r"(?<!\\)\|")
CITE = re.compile(r"\bCD-[A-Z]+-\d{2}\b")
PART = re.compile(r"\b(Unit|GameTest|Inspect|Demo|Analysis):")
AUTOMATED_PARTS = {"Unit", "GameTest", "Inspect", "Analysis"}
CHECK = re.compile(r"^(\d+)\. \*\*(CD-[A-Z]+-\d{2})\.?\*\*(?: \(([^)]*)\))?")
# Kotlin members at four spaces of indentation, the lines above a member, and the parts of a GameTest source.
DECLARATION = re.compile(
    r"^    (?:(?:private|internal|protected|public|override|open|abstract|data|inner|enum|const|lateinit|companion|inline)"
    r"\s+)*(fun|val|var|class|object|interface|init)\b"
)
PREAMBLE = re.compile(r"^    (?:/\*|@|//)")
FUN = re.compile(r"\bfun\s+(?:<[^>]*>\s*)?(?:[\w<>?]+\.)?(\w+)\s*\(")
TEST_ANNOTATION = re.compile(r"^\s*@GameTest(?:\(|\s*$)")
HOLDER = re.compile(r"^@GameTestHolder\(([^)]*)\)", re.MULTILINE)
CLASS = re.compile(r"^(?:[a-z]+ )*class (\w+)", re.MULTILINE)
CONSTANT = re.compile(r'\bconst val (\w+)\s*=\s*"([^"]*)"')
MESSAGE = re.compile(r'"((?:CD-[A-Z]+-\d{2}(?:, | and )?)+):')
CALL = re.compile(r"\b(\w+)\(")
# GameTestServer and LogTestReporter lines in the log of a run.
COMPLETE = re.compile(r"=+ (\d+) GAME TESTS COMPLETE IN (.+?) =+")
SOME_FAILED = re.compile(r"\]: (\d+) required tests failed")
LISTED = re.compile(r"\]:\s+- (\S+)\s*$")
FAILED_AT = re.compile(r"\]: (\S+) failed at -?\d+, -?\d+, -?\d+!")
CRASHED = "Game test server crashed"
PERF = re.compile(r"CD-PERF-01 mean ([\d.]+) ms, CD-PERF-02 p95 ([\d.]+) ms of Dynamics server time per tick, "
                  r"over (\d+) ticks with (\d+) Working workers")
PERF_LINES = re.compile(r"CD-PERF-05 (\d+) Dynamics log lines after the warm-up")
BUDGET = re.compile(r"const val (MEAN|P95)_BUDGET_MS = ([\d.]+)")
# Test pack and Gradle console output.
VERDICT = re.compile(r"^([A-Z_]+) (PASS|FAIL)\b:?\s*(.*)$", re.MULTILINE)
CASE_LINE = re.compile(r"^CASE ([\w-]+): (PASS|FAIL|SKIPPED)\b\s*(.*)$", re.MULTILINE)
TASK = re.compile(r"^> Task :([\w:-]+)(?: ([A-Z-]+))?\s*$", re.MULTILINE)
TASK_FAILED = re.compile(r"Execution failed for task ':([\w:-]+)'")
BUILD_RESULT = re.compile(r"^BUILD (SUCCESSFUL|FAILED)\b", re.MULTILINE)
# The art checks: the verdict line of their output, and the case names in scripts/art.py.
ART_VERDICT = re.compile(r"^ART CHECK (PASS|FAIL)\b", re.MULTILINE)
ART_CASE = re.compile(r'"(cd-art-\d{2})"')
# The date line of a gate report's play-test note.
NOTE_DATE = re.compile(r"^- Date: (\d{4}-\d{2}-\d{2})\b", re.MULTILINE)
BEGIN = "<!-- gate-report {}: written by scripts/gate_report.py; edits inside this block are lost -->"
END = "<!-- gate-report {} end -->"


class InputError(Exception):
    """An input that is missing or cannot be read. main() exits with code 2."""


class SpecRow(NamedTuple):
    id: str
    parts: frozenset[str]


class Check(NamedTuple):
    number: int
    row: str
    marks: str
    text: str


class Sheet(NamedTuple):
    """The MUST and MUST NOT rows that a gate holds, in sheet order, every row id of the sheet, and every area. whole is
    true when the gate holds every MUST and MUST NOT row of the sheet."""

    rows: dict[str, SpecRow]
    every: set[str]
    areas: set[str]
    whole: bool


class Ref(NamedTuple):
    """A test of a row. kind is unit, gametest, testpack, gradle, art or report. group is the test class, the test pack
    command, the Gradle task, the art case or the path of an earlier gate's report. name is the name of the test, a test
    pack case, the date of a report's play-test note, or empty."""

    kind: str
    group: str
    name: str = ""


class Member(NamedTuple):
    name: str
    test: bool
    doc: str
    body: str


class GameTest(NamedTuple):
    ref: Ref
    path: pathlib.Path
    namespace: str
    log_name: str
    rows: frozenset[str]


class RunLog(NamedTuple):
    status: str
    detail: str
    total: int
    failed: frozenset[str]
    duration: str


@dataclasses.dataclass
class Gate:
    """Everything that the report shows."""

    phase: Phase
    commit: str
    short: str
    commit_date: str
    subject: str
    rows: dict[str, SpecRow]
    checks: list[Check]
    unit_cites: dict[Ref, set[str]]
    tests: dict[Ref, GameTest]
    run_logs: dict[str, RunLog]
    results: dict[Ref, tuple[str, str]]
    pack_logs: dict[str, pathlib.Path]
    gradle_tasks: list[str]
    gradle_outcomes: dict[str, str]
    build_result: str
    row_refs: dict[str, set[Ref]]
    deferred_groups: list[tuple[str, list[str]]]
    problems: list[str]
    whole_sheet: bool = False
    art_log: pathlib.Path | None = None
    art_verdict: tuple[str, str] = ("not run", "")
    evaluated: dict[str, tuple[str, list, list[Check]]] = dataclasses.field(default_factory=dict)


def rel(path: pathlib.Path) -> str:
    path = path.resolve()
    return path.relative_to(ROOT).as_posix() if path.is_relative_to(ROOT) else str(path)


def read(path: pathlib.Path) -> str:
    try:
        return path.read_text(encoding="utf-8", errors="replace")
    except OSError as error:
        raise InputError(f"cannot read {rel(path)}: {error}") from error


def git(*args: str) -> str:
    try:
        return subprocess.run(["git", *args], cwd=ROOT, capture_output=True, text=True, check=True).stdout
    except (OSError, subprocess.CalledProcessError) as error:
        raise InputError(f"git {' '.join(args)} failed: {error}") from error


def sha512_of(path: pathlib.Path) -> str:
    digest = hashlib.sha512()
    with path.open("rb") as f:
        while chunk := f.read(1 << 20):
            digest.update(chunk)
    return digest.hexdigest()


# Spec sheet and manual checklist


def parse_spec(text: str, phase: Phase) -> Sheet:
    """The MUST and MUST NOT rows of the sheet text that the gate holds, with every row id and area of the sheet."""
    rows: dict[str, SpecRow] = {}
    every: set[str] = set()
    areas: set[str] = set()
    levels = 0
    for line in text.splitlines():
        match = ROW.match(line)
        if not match:
            continue
        cells = [cell.strip() for cell in CELL.split(line.strip())[1:-1]]
        if len(cells) != 8:
            raise InputError(f"{match.group(1)} has {len(cells)} cells in {rel(SPEC)}, not 8")
        every.add(match.group(1))
        areas.add(match.group(2))
        levels += cells[2] in LEVELS
        if phase.holds(match.group(1), match.group(2)) and cells[2] in LEVELS:
            rows[match.group(1)] = SpecRow(match.group(1), frozenset(PART.findall(cells[5])))
    if not rows:
        raise InputError(f"no Phase {phase.number} MUST or MUST NOT row in {rel(SPEC)}")
    return Sheet(rows, every, areas, len(rows) == levels)


def read_spec(phase: Phase) -> Sheet:
    return parse_spec(read(SPEC), phase)


def parse_checklist(text: str) -> list[Check]:
    """Each numbered check: its number, its row, the marks in parentheses after the row id, and its whole text."""
    checks: list[Check] = []
    current: list | None = None
    for line in text.splitlines():
        match = CHECK.match(line)
        if match:
            current = [int(match.group(1)), match.group(2), match.group(3) or "", line]
            checks.append(Check(*current))
        elif current is not None and line.startswith(" "):
            current[3] += "\n" + line
            checks[-1] = Check(*current)
        else:
            current = None
    return checks


# Unit tests


def read_junit() -> tuple[dict[Ref, tuple[str, str]], dict[Ref, set[str]]]:
    """Each unit test with its status and detail, and the rows that its name cites."""
    results: dict[Ref, tuple[str, str]] = {}
    cites: dict[Ref, set[str]] = {}
    paths = sorted(JUNIT.glob("TEST-*.xml"))
    if not paths:
        raise InputError(f"no JUnit XML report in {rel(JUNIT)}. Run ./gradlew build first.")
    for path in paths:
        try:
            # The build's own reports, so the standard library parser is enough.
            suite = ElementTree.parse(path).getroot()
        except (OSError, ElementTree.ParseError) as error:
            raise InputError(f"cannot parse {rel(path)}: {error}") from error
        for case in suite.iter("testcase"):
            name = case.get("name", "").removesuffix("()")
            ref = Ref("unit", case.get("classname", "").rsplit(".", 1)[-1], name)
            problem = next((child for child in case if child.tag in ("failure", "error", "skipped")), None)
            if problem is None:
                results[ref] = (PASS, "")
            else:
                message = (problem.get("message") or problem.tag).strip()
                results[ref] = ("skipped" if problem.tag == "skipped" else "fail", message.splitlines()[0][:200])
            cites[ref] = set(CITE.findall(name))
    return results, cites


# GameTests


def area_token(areas: set[str]) -> re.Pattern[str]:
    names = sorted((area.capitalize() for area in areas), key=len, reverse=True)
    return re.compile(rf"(?:({'|'.join(names)})(\d{{2}})|And(\d{{2}}))")


def name_rows(name: str, token: re.Pattern[str]) -> list[str]:
    """The rows that a GameTest name cites at its start: cdLsh05Lsh06Refusals and cdSta04And05Stamina cite two each."""
    if not name.startswith("cd"):
        return []
    rows: list[str] = []
    area, pos = "", 2
    while (match := token.match(name, pos)) is not None:
        area = match.group(1).upper() if match.group(1) else area
        if not area:
            break
        rows.append(f"CD-{area}-{match.group(2) or match.group(3)}")
        pos = match.end()
    return rows


def kotlin_functions(text: str) -> list[Member]:
    """The functions declared at four spaces of indentation. Each has the KDoc, annotations and comments above it, and the
    lines from its declaration up to the next member or the end of its class."""
    lines = text.splitlines()
    bounds: list[tuple[int, int | None, str]] = []
    pending = None
    for index, line in enumerate(lines):
        declaration = DECLARATION.match(line)
        if declaration:
            bounds.append((index if pending is None else pending, index, declaration.group(1)))
            pending = None
        elif PREAMBLE.match(line):
            pending = index if pending is None else pending
        elif line[:1] not in ("", " "):
            bounds.append((index, None, ""))
            pending = None
    bounds.append((len(lines), None, ""))
    functions = []
    for (first, declaration, keyword), (end, _, _) in itertools.pairwise(bounds):
        match = FUN.search(lines[declaration]) if declaration is not None and keyword == "fun" else None
        if match is None:
            continue
        preamble = lines[first:declaration]
        functions.append(Member(match.group(1), any(TEST_ANNOTATION.match(line) for line in preamble),
                                "\n".join(preamble), "\n".join(lines[declaration:end])))
    return functions


def message_rows(member: Member, helpers: dict[str, Member], seen: set[str]) -> set[str]:
    """The rows that the assertion messages of a function cite, with those of the helpers of its class that it calls."""
    rows = {row for match in MESSAGE.finditer(member.body) for row in CITE.findall(match.group(1))}
    for call in sorted(set(CALL.findall(member.body)) & (helpers.keys() - seen)):
        seen.add(call)
        rows |= message_rows(helpers[call], helpers, seen)
    return rows


def read_gametests(token: re.Pattern[str], every: set[str]) -> tuple[dict[Ref, GameTest], list[str]]:
    """Each GameTest with its namespace, its name in the logs and the rows that its name, KDoc or assertion messages cite."""
    constants: dict[str, str] = {}
    for path in sorted((ROOT / "src").rglob("*.kt")):
        for name, value in CONSTANT.findall(read(path)):
            constants.setdefault(name, value)
    tests: dict[Ref, GameTest] = {}
    problems = []
    folders = sorted({folder for run in RUNS for folder in run.sources})
    for path in sorted({path for folder in folders for path in (ROOT / folder).rglob("*.kt")}):
        text = read(path)
        functions = kotlin_functions(text)
        if not any(function.test for function in functions):
            continue
        holder = HOLDER.search(text)
        owner = CLASS.search(text, holder.end()) if holder else None
        if holder is None or owner is None:
            problems.append(f"{rel(path)} has GameTests but no @GameTestHolder class")
            continue
        argument = holder.group(1).strip()
        namespace = argument.strip('"') if argument.startswith('"') else constants.get(argument, argument)
        unprefixed = "@PrefixGameTestTemplate(false)" in text[:owner.start()]
        helpers = {function.name: function for function in functions if not function.test}
        for function in functions:
            if not function.test:
                continue
            rows = set(name_rows(function.name, token)) | set(CITE.findall(function.doc))
            rows |= message_rows(function, helpers, set())
            for row in sorted(rows - every):
                problems.append(f"GameTest {owner.group(1)}.{function.name} cites {row}, which the spec sheet lacks")
            prefixed = "@PrefixGameTestTemplate(true)" in function.doc or (
                not unprefixed and "@PrefixGameTestTemplate(false)" not in function.doc)
            log_name = f"{owner.group(1).lower()}.{function.name.lower()}" if prefixed else function.name.lower()
            ref = Ref("gametest", owner.group(1), function.name)
            if ref in tests:
                problems.append(f"two GameTests are named {owner.group(1)}.{function.name}")
            tests[ref] = GameTest(ref, path, namespace, log_name, frozenset(rows & every))
    if not tests:
        raise InputError("no GameTest in " + ", ".join(folders))
    return tests, problems


def run_tests(run: Run, tests: dict[Ref, GameTest]) -> list[GameTest]:
    return [test for test in tests.values() if test.namespace == run.namespace
            and any(test.path.is_relative_to(ROOT / folder) for folder in run.sources)]


def parse_run_log(text: str) -> RunLog:
    """The result of the GameTest run in a server log: its test count and the tests that failed."""
    complete = list(COMPLETE.finditer(text))
    if CRASHED in text or not complete:
        return RunLog("not run", "the log holds no finished run", 0, frozenset(), "")
    failed = set()
    listing = False
    for line in text.splitlines():
        if match := FAILED_AT.search(line):
            failed.add(match.group(1))
        if SOME_FAILED.search(line):
            listing = True
        elif listing and (match := LISTED.search(line)):
            failed.add(match.group(1))
        else:
            listing = False
    return RunLog(FINISHED, "", int(complete[-1].group(1)), frozenset(failed), complete[-1].group(2).strip())


def read_run(run: Run, tests: list[GameTest], commit_time: float) -> tuple[RunLog, dict[Ref, tuple[str, str]], list[str]]:
    """The log of a run, the status of each of its tests, and the problems that the log shows."""
    if not run.log.is_file():
        log = RunLog("not run", f"no {rel(run.log)}", 0, frozenset(), "")
    elif run.log.stat().st_mtime < commit_time:
        log = RunLog("stale", f"{rel(run.log)} is older than the commit", 0, frozenset(), "")
    else:
        log = parse_run_log(read(run.log))
    if log.status == FINISHED and log.total != len(tests):
        log = log._replace(status="unknown", detail=f"the log counts {log.total} tests, the sources hold {len(tests)}")
    problems = [f"{rel(run.log)} names a failed test {name}, which the sources of the {run.name} run lack"
                for name in sorted(log.failed - {test.log_name for test in tests})]
    results = {}
    for test in tests:
        if log.status != FINISHED:
            results[test.ref] = (log.status, f"{run.task}: {log.detail}")
        elif test.log_name in log.failed:
            results[test.ref] = ("fail", f"{run.task}: failed, see {rel(run.log)}")
        else:
            results[test.ref] = (PASS, "")
    return log, results, problems


# Test pack, Gradle and build.gradle


def read_testpack_shape() -> tuple[set[str], dict[str, str]]:
    """The test pack commands that check something, and the command of each case."""
    text = read(TESTPACK)
    commands = set(re.findall(r"^def scenario_(\w+)\(", text, re.MULTILINE)) | {"deps", "compat"}
    cases = {name: "deps" for name in re.findall(r'\bCase\("([\w-]+)"', text)}
    cases.update({name: "compat" for name in re.findall(r'\bCompatCase\("([\w-]+)"', text)})
    return commands, cases


def read_testpack_logs(paths: list[pathlib.Path], commands: set[str],
                       commit_time: float) -> tuple[dict[Ref, tuple[str, str]], dict[str, pathlib.Path]]:
    """The verdict of each command and the result of each case, from the saved output of test pack commands."""
    results: dict[Ref, tuple[str, str]] = {}
    sources: dict[str, pathlib.Path] = {}
    for path in paths:
        text = read(path)
        verdicts = list(VERDICT.finditer(text))
        if not verdicts or verdicts[-1].group(1).lower() not in commands:
            raise InputError(f"{rel(path)} holds no verdict line of a test pack command, such as SMOKE PASS")
        verdict = verdicts[-1]
        command = verdict.group(1).lower()
        stale = path.stat().st_mtime < commit_time
        sources[command] = path
        if stale:
            results[Ref("testpack", command)] = ("stale", f"{rel(path)} is older than the commit")
        else:
            results[Ref("testpack", command)] = (PASS if verdict.group(2) == "PASS" else "fail", verdict.group(3).strip())
        for case in CASE_LINE.finditer(text):
            status = "stale" if stale else {"PASS": PASS, "FAIL": "fail", "SKIPPED": "skipped"}[case.group(2)]
            results[Ref("testpack", command, case.group(1))] = (status, case.group(3).strip()[:200])
    return results, sources


def read_gradle(paths: list[pathlib.Path], commit_time: float) -> tuple[dict[str, str], str]:
    """The outcome of each Gradle task in the console logs, and the last BUILD line."""
    outcomes: dict[str, str] = {}
    result = ""
    for path in paths:
        text = read(path)
        stale = path.stat().st_mtime < commit_time
        for match in TASK.finditer(text):
            outcomes[match.group(1)] = "stale" if stale else (match.group(2) or "EXECUTED")
        for task in TASK_FAILED.findall(text):
            outcomes[task] = "FAILED"
        found = BUILD_RESULT.findall(text)
        result = f"BUILD {found[-1]}" if found else "no BUILD line"
    return outcomes, result


def gradle_status(task: str, outcomes: dict[str, str]) -> tuple[str, str]:
    outcome = outcomes.get(task)
    if outcome in ("EXECUTED", "UP-TO-DATE", "FROM-CACHE"):
        return PASS, ""
    if outcome is None:
        return "not run", f"no '> Task :{task}' line in the Gradle log"
    return ("stale" if outcome == "stale" else "fail"), f"{task}: {outcome}"


def read_build_checks() -> dict[str, set[Ref]]:
    """row -> the check tasks that build.gradle registers under a comment that cites the row."""
    checks: dict[str, set[Ref]] = defaultdict(set)
    comment: list[str] = []
    for line in read(BUILD_GRADLE).splitlines():
        stripped = line.strip()
        if stripped.startswith("//"):
            comment.append(stripped)
            continue
        match = re.match(r"tasks\.register\('(\w+)'", stripped)
        if match:
            for row in CITE.findall(" ".join(comment)):
                checks[row].add(Ref("gradle", match.group(1)))
        comment = []
    return checks


def read_readme_refs(commands: set[str], cases: dict[str, str]) -> dict[str, set[Ref]]:
    """row -> test pack commands and cases: each line of the README's tables that names one and cites rows."""
    refs: dict[str, set[Ref]] = defaultdict(set)
    for line in read(TESTPACK_README).splitlines():
        match = re.match(r"^\| `([\w-]+)` \|", line)
        if not match:
            continue
        name = match.group(1)
        if name in commands:
            ref = Ref("testpack", name)
        elif name in cases:
            ref = Ref("testpack", cases[name], name)
        else:
            continue
        for row in CITE.findall(line):
            refs[row].add(ref)
    return refs


def checklist_refs(checks: list[Check], commands: set[str], cases: dict[str, str]) -> tuple[dict[str, set[Ref]], list[str]]:
    """row -> the test pack commands and cases that its scripted checklist checks expect to pass."""
    refs: dict[str, set[Ref]] = defaultdict(set)
    problems = []
    for check in checks:
        if "scripted" not in check.marks:
            continue
        for case in re.findall(r"`CASE ([\w-]+): PASS`", check.text):
            if case in cases:
                refs[check.row].add(Ref("testpack", cases[case], case))
            else:
                problems.append(f"checklist {check.number} expects the case {case}, which testpack.py lacks")
        for command in re.findall(r"`([A-Z]+) PASS`", check.text):
            if command.lower() in commands:
                refs[check.row].add(Ref("testpack", command.lower()))
            else:
                problems.append(f"checklist {check.number} expects {command} PASS, which no test pack command prints")
    return refs, problems


# The deferred lists of M5 Task 9 and M8 Task 7


def class_refs(refs: list[Ref], row: str, rows: list[str], cites) -> list[Ref]:
    """The tests of a class that cite the row, else those that cite another row of the same item, else all of them."""
    return [ref for ref in refs if row in cites(ref)] or [ref for ref in refs if cites(ref) & set(rows)] or refs


def deferred_items(text: str, plan: pathlib.Path) -> list[list[str]]:
    """The lines of a plan's deferred list: [group, ""] for each group line, then [group, text] for each of its rows."""
    start = text.find("\nDeferred rows (")
    end = text.find("\nThe same pass checked", start)
    if start < 0 or end < 0:
        raise InputError(f"no deferred list in {rel(plan)}")
    items: list[list[str]] = []
    for line in text[start:end].splitlines():
        if line.startswith("- "):
            items.append([line[2:].split(":")[0].split(",")[0].strip(), ""])
        elif line.startswith("  - ") and items:
            items.append([items[-1][0], line[4:]])
        elif line.startswith("    ") and items and items[-1][1]:
            items[-1][1] += " " + line.strip()
    return items


def read_deferred(phase: Phase, tests: dict[Ref, GameTest], unit_cites: dict[Ref, set[str]],
                  commands: set[str]) -> tuple[dict[str, set[Ref]], list[tuple[str, list[str]]], list[str]]:
    """The deferred refs of the lists of every plan of the gate, in the order of the plans (see deferred_refs)."""
    items = [item for plan in phase.deferred_plans for item in deferred_items(read(plan), plan)]
    return deferred_refs(items, tests, unit_cites, commands)


def deferred_refs(items: list[list[str]], tests: dict[Ref, GameTest], unit_cites: dict[Ref, set[str]],
                  commands: set[str]) -> tuple[dict[str, set[Ref]], list[tuple[str, list[str]]], list[str]]:
    """row -> the tests that the deferred lists name for it, the rows of each milestone part in order, and the test names
    that a list holds but no test has."""
    by_class: dict[str, list[Ref]] = defaultdict(list)
    by_method: dict[str, list[Ref]] = defaultdict(list)
    for ref in tests:
        by_class[ref.group].append(ref)
        by_method[ref.name].append(ref)
    unit_classes: dict[str, list[Ref]] = defaultdict(list)
    for ref in unit_cites:
        unit_classes[ref.group].append(ref)
    refs: dict[str, set[Ref]] = defaultdict(set)
    groups: dict[str, list[str]] = {}
    problems = []
    for group, body in items:
        bucket = groups.setdefault(group, [])
        if not body:
            continue
        head, _, tail = body.partition(": ")
        rows = list(dict.fromkeys(CITE.findall(head)))
        bucket += [row for row in rows if row not in bucket]
        named = {Ref("testpack", command) for command in re.findall(r"test pack's `(\w+)`", tail) if command in commands}
        classes = []
        for token in re.findall(r"`([^`]+)`", tail):
            owner, _, method = token.partition(".")
            if method and Ref("gametest", owner, method) in tests:
                named.add(Ref("gametest", owner, method))
            elif not method and token in by_class:
                classes.append((by_class[token], lambda ref: tests[ref].rows))
            elif not method and token in unit_classes:
                classes.append((unit_classes[token], lambda ref: unit_cites[ref]))
            elif not method and len(by_method.get(token, [])) == 1:
                named.update(by_method[token])
            elif re.fullmatch(r"[A-Z]\w*Tests?(\.\w+)?|cd[A-Z]\w+", token):
                problems.append(f"the deferred list names {token}, which no test has")
        for row in rows:
            refs[row] |= named
            for members, cites in classes:
                refs[row].update(class_refs(members, row, rows, cites))
    return refs, list(groups.items()), problems


# The art checks and the record of an earlier gate


def art_cases(text: str) -> set[str]:
    """The cases that `py scripts/art.py check` can print, from the text of scripts/art.py."""
    return set(ART_CASE.findall(text))


def parse_art_log(text: str, cases: set[str], source: str,
                  stale: bool) -> tuple[dict[Ref, tuple[str, str]], tuple[str, str]]:
    """The result of each art case and the verdict, from the output of `py scripts/art.py check`. A failed case's detail is
    its first problem. A case of cases that the output lacks has not run. source names the output in the details."""
    verdicts = ART_VERDICT.findall(text)
    if not verdicts:
        raise InputError(f"{source} holds no verdict line of py scripts/art.py check, such as ART CHECK PASS")
    found: dict[str, tuple[str, str]] = {}
    problems: dict[str, list[str]] = defaultdict(list)
    case = ""
    for line in text.splitlines():
        if match := CASE_LINE.match(line):
            case = match.group(1)
            found[case] = ({"PASS": PASS, "FAIL": "fail", "SKIPPED": "skipped"}[match.group(2)], match.group(3).strip())
        elif case and line.startswith("  "):
            problems[case].append(line.strip())
        else:
            case = ""
    results: dict[Ref, tuple[str, str]] = {}
    for name, (status, detail) in found.items():
        listed = problems.get(name, [])
        if listed:
            detail = listed[0] + (f", and {len(listed) - 1} more" if len(listed) > 1 else "")
        results[Ref("art", name)] = ("stale", f"{source} is older than the commit") if stale else (status, detail[:200])
    for name in sorted(cases - found.keys()):
        results[Ref("art", name)] = ("not run", f"{source} holds no CASE {name} line: run {ART_COMMAND}")
    if stale:
        return results, ("stale", f"{source} is older than the commit")
    return results, (PASS if verdicts[-1] == "PASS" else "fail", "")


def read_art_log(path: pathlib.Path, cases: set[str],
                 commit_time: float) -> tuple[dict[Ref, tuple[str, str]], tuple[str, str]]:
    text = read(path)
    return parse_art_log(text, cases, rel(path), path.stat().st_mtime < commit_time)


def note_date(text: str) -> str:
    """The date of a gate report's play-test note, or an empty string while the note has none."""
    start = text.find("\n## Play-test note")
    if start < 0:
        return ""
    end = text.find("\n## ", start + 1)
    match = NOTE_DATE.search(text, start, end if end >= 0 else len(text))
    return match.group(1) if match else ""


# Play-test client


def client_summary() -> str:
    """What run/testpack/client/mods/ holds, checked against mods.json and the jar in build/libs."""
    jars = sorted(CLIENT_MODS.glob("*.jar"))
    if not jars:
        return f"`{rel(CLIENT_MODS)}/` holds no jar. Run `py scripts/testpack/testpack.py client`."
    pins = {pin["filename"]: pin["sha512"] for pin in json.loads(read(PINS))["mods"].values()}
    built = {jar.name: jar for jar in BUILD_LIBS.glob("cobblemon_dynamics-*.jar")}
    problems = []
    for jar in jars:
        if jar.name in pins:
            if sha512_of(jar) != pins.pop(jar.name):
                problems.append(f"{jar.name} differs from its pin")
        elif jar.name in built:
            if sha512_of(jar) != sha512_of(built[jar.name]):
                problems.append(f"{jar.name} differs from build/libs/{jar.name}")
        else:
            problems.append(f"{jar.name} is neither a pin of mods.json nor the built jar")
    problems += [f"{name} is missing" for name in sorted(pins)]
    names = ", ".join(f"`{jar.name}`" for jar in jars)
    if problems:
        return f"`{rel(CLIENT_MODS)}/` holds {names}. Problems: {'; '.join(problems)}."
    return (f"`{rel(CLIENT_MODS)}/` holds {len(jars)} jars: {names}. Each pinned jar has its SHA-512 from `mods.json`, and "
            "the mod jar is the same file as in `build/libs/`.")


# Rows


def evaluate(row: SpecRow, refs: set[Ref], checks: list[Check], results: dict[Ref, tuple[str, str]], phase: Phase):
    """The result of a row, and each of its tests that did not pass with its status and detail."""
    scripted = any("scripted" in check.marks for check in checks)
    special = row.id in phase.later or row.id == phase.record or row.id in phase.earlier
    required = not special and bool(row.parts & AUTOMATED_PARTS or ("Demo" in row.parts and scripted))
    manual = any("scripted" not in check.marks for check in checks) or ("Demo" in row.parts and not scripted)
    play_test = row.id == phase.record or (row.id not in phase.later and row.id not in phase.earlier and manual)
    # The art case of a row in review fails until the approver approves every asset, so it waits and does not fail.
    review = Ref("art", phase.art_review[row.id]) if row.id in phase.art_review else None
    reviewing = review in refs and results.get(review, ("",))[0] == "fail"
    missing = [(ref, results.get(ref, ("not run", "no result"))) for ref in sorted(refs)]
    missing = [(ref, result) for ref, result in missing if result[0] != PASS and not (reviewing and ref == review)]
    if missing:
        return "fail", missing
    if required and not refs:
        return "no test", []
    parts = [PASS] if refs and not reviewing else []
    parts += ["art review"] if reviewing else []
    parts += ["play-test"] if play_test else []
    parts += [f"open ({phase.later[row.id]})"] if row.id in phase.later else []
    return (", ".join(parts) or "no test"), []


def collect(args: argparse.Namespace, phase: Phase) -> Gate:
    dirty = [line[3:].strip('"') for line in git("status", "--porcelain", "--untracked-files=no").splitlines()]
    dirty = [path for path in dirty if path != rel(phase.report)]
    if dirty and not args.allow_dirty:
        raise InputError(f"tracked files differ from HEAD ({', '.join(dirty)}). Commit them and run the checks again.")
    commit_time = float(git("show", "-s", "--format=%ct", "HEAD"))
    sheet = read_spec(phase)
    rows, every, areas = sheet.rows, sheet.every, sheet.areas
    checks = parse_checklist(read(CHECKLIST))
    unit_results, unit_cites = read_junit()
    tests, problems = read_gametests(area_token(areas), every)
    results: dict[Ref, tuple[str, str]] = dict(unit_results)
    run_logs = {}
    for run in RUNS:
        log, statuses, run_problems = read_run(run, run_tests(run, tests), commit_time)
        run_logs[run.name] = log
        results.update(statuses)
        problems += run_problems
    problems += [f"GameTest {ref.group}.{ref.name} is in no GameTest run" for ref in tests if ref not in results]
    commands, cases = read_testpack_shape()
    pack_results, pack_logs = read_testpack_logs(args.testpack_log, commands, commit_time)
    results.update(pack_results)
    problems += [f"no output of the test pack command {command}: pass it with --testpack-log"
                 for command in sorted(commands - pack_logs.keys())]
    outcomes, build_result = read_gradle(args.gradle_log, commit_time)
    gradle_refs = read_build_checks()
    tasks = sorted({ref.group for refs in gradle_refs.values() for ref in refs})
    results.update({Ref("gradle", task): gradle_status(task, outcomes) for task in tasks})
    row_refs: dict[str, set[Ref]] = defaultdict(set)
    for ref, cited in unit_cites.items():
        for row in cited:
            row_refs[row].add(ref)
    for test in tests.values():
        for row in test.rows:
            row_refs[row].add(test.ref)
    pack_refs, check_problems = checklist_refs(checks, commands, cases)
    listed, deferred_groups, deferred_problems = read_deferred(phase, tests, unit_cites, commands)
    problems += check_problems + deferred_problems
    for extra in (gradle_refs, read_readme_refs(commands, cases), pack_refs, listed):
        for row, refs in extra.items():
            row_refs[row] |= refs
    art_log, art_verdict = None, ("not run", "")
    if phase.checks_art:
        art = art_cases(read(ART_SCRIPT))
        if args.art_log is None:
            problems.append(f"no output of the art checks: run {ART_COMMAND} and pass its output with --art-log")
        else:
            art_results, art_verdict = read_art_log(args.art_log, art, commit_time)
            results.update(art_results)
            art |= {ref.group for ref in art_results}
            art_log = args.art_log
        for case in art:
            row_refs[case.upper()].add(Ref("art", case))
        for row, case in phase.art_review.items():
            row_refs[row].add(Ref("art", case))
    for row, report in phase.earlier.items():
        date = note_date(read(report))
        note = Ref("report", rel(report), date)
        results[note] = (PASS, "") if date else ("fail", "the note has no date")
        row_refs[row].add(note)
    gate = Gate(phase, git("rev-parse", "HEAD").strip(), git("rev-parse", "--short=7", "HEAD").strip(),
                git("show", "-s", "--format=%cs", "HEAD").strip(), git("show", "-s", "--format=%s", "HEAD").strip(),
                rows, checks, unit_cites, tests, run_logs, results, pack_logs, tasks, outcomes, build_result, row_refs,
                deferred_groups, problems, sheet.whole, art_log, art_verdict)
    for row in rows.values():
        row_checks = [check for check in checks if check.row == row.id]
        gate.evaluated[row.id] = (*evaluate(row, row_refs[row.id], row_checks, results, phase), row_checks)
    return gate


# The report


def failing_rows(gate: Gate) -> list[str]:
    return [row for row, (result, _, _) in gate.evaluated.items() if result in ("fail", "no test")]


def count(gate: Gate, refs) -> str:
    refs = list(refs)
    passed = sum(1 for ref in refs if gate.results.get(ref, ("",))[0] == PASS)
    return f"{passed} of {len(refs)} pass"


def scope(gate: Gate) -> str:
    """The qualifier of the report's rows: the phase, unless the gate holds every MUST and MUST NOT row of the sheet."""
    return "" if gate.whole_sheet else f"Phase {gate.phase.number} "


def listing(rows: list[str]) -> str:
    return f" ({', '.join(rows)})" if rows else ""


def totals(gate: Gate) -> str:
    results = [result for result, _, _ in gate.evaluated.values()]
    automated = [result for result in results if result.startswith(PASS) or result == "fail"]
    passing = [result for result in automated if result.startswith(PASS)]
    play_test = [result for result in results if "play-test" in result]
    only = [result for result in play_test if not result.startswith(PASS) and "art review" not in result]
    review = [row for row, (result, _, _) in gate.evaluated.items() if "art review" in result]
    later = [row for row, (result, _, _) in gate.evaluated.items() if "open" in result]
    waiting = f"{len(review)} rows wait for the approver's art review{listing(review)}. " if gate.phase.checks_art else ""
    return (f"The spec sheet has {len(results)} {scope(gate)}`MUST` and `MUST NOT` rows. {len(passing)} of the "
            f"{len(automated)} rows with automated tests pass. {len(play_test)} rows have a play-test part, and "
            f"{len(only)} of them have no automated test. {waiting}{len(later)} rows stay open after Phase "
            f"{gate.phase.number}{listing(later)}. {len(failing_rows(gate))} automated rows have no passing test.")


def art_summary(gate: Gate) -> str:
    """The result of the art checks: the verdict, the cases that pass, and the review cases that still fail."""
    if gate.art_log is None:
        return "No art log was given."
    status, detail = gate.art_verdict
    own = sorted(ref for ref in gate.results if ref.kind == "art")
    line = status + (f": {detail}" if detail and status != PASS else "") + (f", cases {count(gate, own)}" if own else "")
    review = sorted({case for case in gate.phase.art_review.values()
                     if gate.results.get(Ref("art", case), ("",))[0] == "fail"})
    if review:
        line += (f". {' and '.join(f'`{case}`' for case in review)} {'fails' if len(review) == 1 else 'fail'} until the "
                 "review record approves every asset.")
    return line


def render_summary(gate: Gate) -> str:
    today = datetime.datetime.now(datetime.UTC).date().isoformat()
    out = ["## Run summary", "",
           (f"`scripts/gate_report.py` wrote this block and the Rows section on {today} (UTC). Every check below ran on "
            f"commit `{gate.commit}` ({gate.commit_date}, \"{gate.subject}\")."), "",
           "| Check | Command | Result |", "| --- | --- | --- |"]
    units = [ref for ref in gate.unit_cites]
    out.append(f"| Unit tests | `./gradlew build` | {count(gate, units)}, in {len({ref.group for ref in units})} "
               "classes |")
    checks = ", ".join(f"`{task}` {gradle_status(task, gate.gradle_outcomes)[0]}" for task in gate.gradle_tasks)
    ending = f"The Gradle log ends with {gate.build_result}." if gate.build_result else "No Gradle log was given."
    out.append(f"| Jar and source checks | `./gradlew build` | {checks}. {ending} |")
    for run in RUNS:
        log = gate.run_logs[run.name]
        line = count(gate, (test.ref for test in run_tests(run, gate.tests)))
        if log.status == FINISHED:
            line += f". The log counts {log.total} tests and {len(log.failed)} failures, in {log.duration}."
        else:
            line += f". {log.detail}."
        out.append(f"| {run.title} | `./gradlew {run.task}` | {line} |")
    for command in sorted(gate.pack_logs, key=lambda name: (PACK_ORDER.index(name) if name in PACK_ORDER else 99, name)):
        status, detail = gate.results[Ref("testpack", command)]
        own = [ref for ref in gate.results if ref.kind == "testpack" and ref.group == command and ref.name]
        line = status + (f": {detail}" if detail and status != PASS else "")
        line += f", cases {count(gate, own)}" if own else ""
        out.append(f"| Test pack | `py scripts/testpack/testpack.py {command}` | {line} |")
    if gate.phase.checks_art:
        out.append(f"| Art checks | `{ART_COMMAND}` | {art_summary(gate)} |")
    out.append(f"| Play-test client | `py scripts/testpack/testpack.py client` | {client_summary()} |")
    out.append("")
    perf_log = read(RUNS[2].log) if RUNS[2].log.is_file() else ""
    perf = PERF.search(perf_log)
    lines = PERF_LINES.search(perf_log)
    budgets = dict(BUDGET.findall(read(PERF_TEST))) if PERF_TEST.is_file() else {}
    if perf:
        mean, p95, ticks, workers = perf.groups()
        budget = (f" The budgets are {budgets['MEAN']} ms for the mean and {budgets['P95']} ms for the p95."
                  if {"MEAN", "P95"} <= budgets.keys() else "")
        written = (f"Dynamics wrote {lines.group(1)} log lines after the warm-up (CD-PERF-05)." if lines
                   else "The log holds no CD-PERF-05 line.")
        out.append(f"The perf run measured the server time of Dynamics per tick, with {workers} Working workers over "
                   f"{int(ticks):,} ticks. The mean was {mean} ms (CD-PERF-01), and the p95 was {p95} ms (CD-PERF-02)."
                   f"{budget} {written}")
    else:
        out.append(f"The log of the perf run, `{rel(RUNS[2].log)}`, holds no CD-PERF-01 line.")
    out += ["", totals(gate)]
    if gate.problems:
        out += ["", "Problems in the inputs:", ""] + [f"- {problem}" for problem in gate.problems]
    return "\n".join(out)


def ref_labels(refs: set[Ref], checks: list[Check]) -> str:
    """The Tests cell of a row."""
    units: dict[str, int] = defaultdict(int)
    for ref in refs:
        if ref.kind == "unit":
            units[ref.group] += 1
    labels = [f"`{name}` ×{number}" if number > 1 else f"`{name}`" for name, number in sorted(units.items())]
    labels += [f"`{ref.group}.{ref.name}`" for ref in sorted(ref for ref in refs if ref.kind == "gametest")]
    pack: dict[str, list[str]] = defaultdict(list)
    for ref in sorted(ref for ref in refs if ref.kind == "testpack"):
        pack[ref.group] += [f"`{ref.name}`"] if ref.name else []
    labels += [f"test pack `{command}`" + (f" ({', '.join(names)})" if names else "") for command, names in pack.items()]
    labels += [f"Gradle `{ref.group}`" for ref in sorted(ref for ref in refs if ref.kind == "gradle")]
    labels += [f"art check `{ref.group}`" for ref in sorted(ref for ref in refs if ref.kind == "art")]
    labels += [f"the play-test note of `{ref.group}`" + (f", dated {ref.name}" if ref.name else "")
               for ref in sorted(ref for ref in refs if ref.kind == "report")]
    labels += [f"checklist {check.number}" for check in checks]
    return "; ".join(labels)


def ref_name(ref: Ref) -> str:
    if ref.kind == "unit":
        return f"unit test {ref.group}: {ref.name}"
    if ref.kind == "gametest":
        return f"GameTest {ref.group}.{ref.name}"
    if ref.kind == "testpack":
        return f"test pack {ref.group}" + (f", case {ref.name}" if ref.name else "")
    if ref.kind == "art":
        return f"art check {ref.group}"
    if ref.kind == "report":
        return f"the play-test note of {ref.group}"
    return f"Gradle task {ref.group}"


def render_rows(gate: Gate) -> str:
    phase = gate.phase
    tasks = [DEFERRED_TASKS[plan] for plan in phase.deferred_plans]
    legend_tests = (
        "- Tests: `Class` ×n is n unit tests of that class whose names cite the row. `Class.method` is a GameTest whose "
        f"name, KDoc or assertion messages cite the row, or which the deferred list of {' or '.join(tasks)} names for "
        "the row. Test pack names a command of `scripts/testpack/testpack.py`, with the cases that the test pack's README "
        "or the manual checklist gives for the row. Gradle names a check task of `./gradlew build`. ")
    if phase.checks_art:
        legend_tests += f"Art check names a case of `{ART_COMMAND}`. "
    if phase.earlier:
        legend_tests += "The play-test note of a report is the record of an earlier gate. "
    legend_tests += "Checklist n is check n of `docs/testing/manual-checklist.md`."
    legend_result = (
        "- Result: `pass` means that every test of the row passed. `play-test` means that the row's evidence is a "
        "Demo or a checklist line, which the approver checks in the play-test. `open (Mn)` means that the last part "
        f"of the row comes after the Phase {phase.number} gate, in milestone Mn. ")
    if phase.checks_art:
        legend_result += ("`art review` means that the art check of the row fails until the approver approves every "
                          "asset in the review record, `docs/testing/art-review.md`. ")
    legend_result += "The section \"Rows without a passing test\" lists each row with `fail` or `no test`."
    out = ["## Rows", "",
           (f"The table has one line for each {scope(gate)}`MUST` and `MUST NOT` row of the spec sheet, in the order of "
            "the sheet."), "", legend_tests, legend_result, "", "| Row | Tests | Result | Commit |",
           "| --- | --- | --- | --- |"]
    for row in gate.rows:
        result, _, checks = gate.evaluated[row]
        cell = ref_labels(gate.row_refs[row], checks)
        if row == phase.record:
            cell = "this report (the run on the commit) and the play-test note" + (f"; {cell}" if cell else "")
        elif not cell and row in phase.later:
            cell = f"after the Phase {phase.number} gate ({phase.later[row]})"
        out.append(f"| {row} | {cell or 'none'} | {result} | `{gate.short}` |")
    passes = (f"The end-of-phase pass of {tasks[0]} gave each row of its deferred list a test." if len(tasks) == 1 else
              f"The end-of-phase passes of {', '.join(tasks[:-1])} and {tasks[-1]} gave each row of their deferred lists "
              "a test.")
    out += ["", "### Deferred rows", "",
            (f"{passes} The table above names the tests of each row. This list gives the result of each row, by the "
             "task that deferred it:"), ""]
    for group, rows in gate.deferred_groups:
        listed = "; ".join(f"{row} {gate.evaluated[row][0] if row in gate.evaluated else f'is not a {scope(gate)}MUST row'}"
                           for row in rows)
        out.append(f"- {group}: {listed}.")
    cited = {ref for refs in gate.row_refs.values() for ref in refs}
    uncited = sorted(ref for ref, test in gate.tests.items() if not test.rows and ref not in cited)
    out += ["", "### Tests that cite no row", ""]
    if uncited:
        out.append("These GameTests cite no row of the spec sheet: " + ", ".join(
            f"`{ref.group}.{ref.name}` ({gate.results.get(ref, ('not run',))[0]})" for ref in uncited) + ".")
        out.append("")
    quiet = [ref for ref, rows in gate.unit_cites.items() if not rows]
    out.append(f"{len(quiet)} of the {len(gate.unit_cites)} unit tests cite no row id. They test the helpers that the "
               "tests of the rows use.")
    out += ["", "### Totals", "", totals(gate), "", "### Rows without a passing test", ""]
    failing = failing_rows(gate)
    if not failing and not gate.problems:
        out.append("None.")
    for row in failing:
        result, missing, _ = gate.evaluated[row]
        reasons = "; ".join(f"{ref_name(ref)}: {status}" + (f" ({detail})" if detail else "")
                            for ref, (status, detail) in missing)
        out.append(f"- {row}: {result}. " + (reasons or "No test cites the row."))
    out += [f"- Input problem: {problem}" for problem in gate.problems]
    return "\n".join(out)


def replace_block(text: str, name: str, content: str, report: pathlib.Path) -> str:
    begin, end = BEGIN.format(name), END.format(name)
    pattern = re.compile(re.escape(begin) + r".*?" + re.escape(end), re.DOTALL)
    if not pattern.search(text):
        raise InputError(f"{rel(report)} has no '{begin}' ... '{end}' block")
    return pattern.sub(lambda _: f"{begin}\n\n{content.strip()}\n\n{end}", text, count=1)


def write_report(args: argparse.Namespace) -> int:
    phase = PHASES[args.phase]
    gate = collect(args, phase)
    report = phase.report
    if report.is_file():
        text = read(report)
    else:
        text = (f"# Phase {phase.number} gate report\n\n{BEGIN.format('summary')}\n{END.format('summary')}\n\n"
                f"{BEGIN.format('rows')}\n{END.format('rows')}\n")
    text = replace_block(text, "summary", render_summary(gate), report)
    text = replace_block(text, "rows", render_rows(gate), report)
    report.write_text(text, encoding="utf-8", newline="\n")
    failing = failing_rows(gate)
    print(f"wrote {rel(report)} for commit {gate.short}: {totals(gate)}")
    for problem in gate.problems:
        print(f"  input problem: {problem}")
    for row in failing:
        print(f"  {row}: {gate.evaluated[row][0]}")
    return 1 if failing or gate.problems else 0


# Self-check on samples


def self_check() -> int:
    token = area_token({"LSH", "STA", "GEN", "MUS", "PERF", "SEC", "WMD"})
    assert name_rows("cdLsh05Lsh06RefusalsAndTheBoundMessage", token) == ["CD-LSH-05", "CD-LSH-06"]
    assert name_rows("cdSta04And05StaminaStartsFull", token) == ["CD-STA-04", "CD-STA-05"]
    assert name_rows("cdGen07Ae2RatioAtLv50", token) == ["CD-GEN-07"]
    assert name_rows("cdSec05Wmd03PlacerPayloads", token) == ["CD-SEC-05", "CD-WMD-03"]
    assert name_rows("smokePastureTethersAPokemon", token) == []
    source = (
        "@GameTestHolder(MOD_ID)\nclass MuseTests {\n    private fun scene(helper: Helper): Scene {\n"
        '        helper.assertTrue(ok, "CD-MUS-01: the bind failed")\n    }\n\n'
        "    /** The Muse adds 8 shelves (CD-MUS-03), and the bind works. */\n"
        '    @GameTest(template = "floor")\n    fun cdMus03EightShelves(helper: Helper) {\n        val s = scene(helper)\n'
        '        helper.assertTrue(x, "CD-WHL-07, CD-CRK-08: wrong")\n    }\n}\n')
    functions = kotlin_functions(source)
    assert [(f.name, f.test) for f in functions] == [("scene", False), ("cdMus03EightShelves", True)], functions
    helpers = {f.name: f for f in functions if not f.test}
    assert message_rows(functions[1], helpers, set()) == {"CD-MUS-01", "CD-WHL-07", "CD-CRK-08"}
    assert CITE.findall(functions[1].doc) == ["CD-MUS-03"]
    log = parse_run_log(
        "[t] [Server thread/ERROR] [LogTestReporter/]: cdmus03eightshelves failed at 1, -59, 2! Assertion\n"
        "[t] [Server thread/INFO] [GameTestServer/]: ========= 3 GAME TESTS COMPLETE IN 1.0 s ======\n"
        "[t] [Server thread/INFO] [GameTestServer/]: 1 required tests failed :(\n"
        "[t] [Server thread/INFO] [GameTestServer/]:    - cdmus03eightshelves\n")
    assert log.total == 3 and log.failed == {"cdmus03eightshelves"}, log
    assert parse_run_log("[t] ... 2 tests are now running").status == "not run"
    checks = parse_checklist("1. **CD-PLAT-01** (scripted). Setup: x.\n   Expected: `CASE neoforge-floor: PASS`.\n\n"
                             "6. **CD-UI-01.** Setup: a pasture.\n")
    assert [(c.number, c.row, c.marks) for c in checks] == [(1, "CD-PLAT-01", "scripted"), (6, "CD-UI-01", "")], checks
    assert "neoforge-floor" in checks[0].text
    first, second = PHASES[1], PHASES[2]
    demo = SpecRow("CD-UI-01", frozenset({"Demo"}))
    assert evaluate(demo, set(), checks[1:], {}, first) == ("play-test", [])
    unit = Ref("unit", "UiTest", "CD-UI-01 a button")
    assert evaluate(demo, {unit}, [], {unit: ("fail", "")}, first)[0] == "fail"
    assert evaluate(SpecRow("CD-X-01", frozenset({"GameTest"})), set(), [], {}, first) == ("no test", [])
    assert evaluate(SpecRow("CD-REL-04", frozenset({"Demo"})), {unit}, [], {unit: (PASS, "")}, first) == (
        "pass, open (M9)", [])

    # The rows of each gate (read_spec): Phase 1 holds no Phase 2 row, and Phase 2 holds every MUST and MUST NOT row.
    sheet = "".join(f"| {row} | r | {level} | s | w | {test}: t. | o | agreed |\n" for row, level, test in (
        ("CD-CFG-01", "MUST", "Unit"), ("CD-CFG-16", "MUST", "Unit"), ("CD-QWK-21", "MUST NOT", "GameTest"),
        ("CD-ART-11", "MAY", "Inspect")))
    held = parse_spec(sheet, first)
    assert list(held.rows) == ["CD-CFG-01"] and not held.whole, held
    held = parse_spec(sheet, second)
    assert list(held.rows) == ["CD-CFG-01", "CD-CFG-16", "CD-QWK-21"] and held.whole, held

    # The deferred lists of every plan of the gate (read_deferred).
    m5 = ("a\nDeferred rows (rule 6).\n\n- M1 Task 10:\n  - CD-FX-06: `EffectTests.cdFx06FocusIsNeverSaved`.\n\n"
          "The same pass checked b.\n")
    m8 = ("a\nDeferred rows (rule 6).\n\n- Quick Work Task 3:\n"
          "  - CD-QWK-04: `QuickWorkTests.cdQwk04ChestAndCauldronNeedTheLeash`.\n"
          "  - CD-QWK-21: `QuickWorkTests.cdQwk21BattleEndsTheOrder`.\n\nThe same pass checked b.\n")
    focus = Ref("gametest", "EffectTests", "cdFx06FocusIsNeverSaved")
    leash = Ref("gametest", "QuickWorkTests", "cdQwk04ChestAndCauldronNeedTheLeash")
    known = {ref: GameTest(ref, M8_PLAN, "cobblemon_dynamics", ref.name.lower(), frozenset()) for ref in (focus, leash)}
    listed, groups, unknown = deferred_refs(deferred_items(m5, M5_PLAN) + deferred_items(m8, M8_PLAN), known, {}, set())
    assert listed["CD-FX-06"] == {focus} and listed["CD-QWK-04"] == {leash} and not listed["CD-QWK-21"], listed
    assert groups == [("M1 Task 10", ["CD-FX-06"]), ("Quick Work Task 3", ["CD-QWK-04", "CD-QWK-21"])], groups
    assert unknown == ["the deferred list names QuickWorkTests.cdQwk21BattleEndsTheOrder, which no test has"], unknown
    assert input_error(lambda: deferred_items("a plan with no deferred list", M8_PLAN))

    # The output of the art checks (--art-log): a result for each case, a stale output, and an output with no verdict.
    art = art_cases('        "cd-art-05": check_logo(),\n        cases["cd-art-10"] = check_record(args.record)\n')
    assert art == {"cd-art-05", "cd-art-10"}, art
    output = ("CASE cd-art-05: PASS\nCASE cd-art-10: FAIL\n  art/a.pixels is pending\n  art/b.pixels is pending\n"
              "ART CHECK FAIL\n")
    found, verdict = parse_art_log(output, art | {"cd-art-09"}, "art.txt", stale=False)
    assert verdict == ("fail", "") and found[Ref("art", "cd-art-05")] == (PASS, ""), (verdict, found)
    assert found[Ref("art", "cd-art-10")] == ("fail", "art/a.pixels is pending, and 1 more"), found
    assert found[Ref("art", "cd-art-09")][0] == "not run", found
    found, verdict = parse_art_log(output, art, "art.txt", stale=True)
    assert {status for status, _ in found.values()} == {"stale"} and verdict[0] == "stale", (found, verdict)
    assert input_error(lambda: parse_art_log("CASE cd-art-05: PASS\n", art, "art.txt", stale=False))

    # The marks of the Phase 2 gate (evaluate): the art review rows, the earlier gate and the record.
    record = Ref("art", "cd-art-10")
    pending = {record: ("fail", "art/a.pixels is pending")}
    review = SpecRow("CD-ART-10", frozenset({"Inspect"}))
    assert evaluate(review, {record}, [], pending, second) == ("art review", [])
    assert evaluate(review, {record}, [], {record: (PASS, "")}, second) == ("pass", [])
    assert evaluate(review, {record}, [], {}, second)[0] == "fail", "an art case that did not run is no review"
    sheet_check = Check(55, "CD-ART-02", "", "55. **CD-ART-02.** Setup: the review spritesheet.")
    assert evaluate(SpecRow("CD-ART-02", frozenset({"Demo"})), {record}, [sheet_check], pending, second) == (
        "art review, play-test", [])
    sprite = Ref("unit", "WorkModeSpritesTest", "CD-ART-04 the button shows a sprite")
    marked = evaluate(SpecRow("CD-ART-04", frozenset({"Inspect", "Demo"})), {record, sprite}, [],
                      {**pending, sprite: ("fail", "")}, second)
    assert marked == ("fail", [(sprite, ("fail", ""))]), marked
    note = Ref("report", "docs/testing/phase-1-gate.md", "2026-11-10")
    phase_1_record = SpecRow("CD-REL-13", frozenset({"Inspect"}))
    assert evaluate(phase_1_record, {note}, [], {note: (PASS, "")}, second) == ("pass", [])
    assert evaluate(phase_1_record, {note}, [], {note: ("fail", "the note has no date")}, second)[0] == "fail"
    assert evaluate(phase_1_record, set(), [], {}, first) == ("play-test", [])
    phase_2_record = SpecRow("CD-REL-14", frozenset({"Inspect"}))
    assert evaluate(phase_2_record, set(), [], {}, second) == ("play-test", [])
    assert evaluate(phase_2_record, set(), [], {}, first) == ("open (M8)", [])
    assert note_date("# R\n\n## Play-test note\n\n- Date: 2026-11-10\n") == "2026-11-10"
    assert note_date("# R\n\n## Play-test note\n\n- Date: pending\n\n## Art review\n\n- Date: 2026-11-12\n") == ""

    # The run summary's art line, and the totals that name the phase (art_summary, totals).
    rows = {"CD-ART-10": ("art review", [], []), "CD-REL-04": ("open (M9)", [], []),
            "CD-UI-01": ("pass, play-test", [], [])}
    gate = sample_gate(second, rows, {record: ("fail", ""), Ref("art", "cd-art-05"): (PASS, "")}, whole=True)
    assert totals(gate) == (
        "The spec sheet has 3 `MUST` and `MUST NOT` rows. 1 of the 1 rows with automated tests pass. 1 rows have a "
        "play-test part, and 0 of them have no automated test. 1 rows wait for the approver's art review (CD-ART-10). "
        "1 rows stay open after Phase 2 (CD-REL-04). 0 automated rows have no passing test."), totals(gate)
    assert art_summary(gate) == "No art log was given."
    gate.art_log, gate.art_verdict = pathlib.Path("art.txt"), ("fail", "")
    assert art_summary(gate) == (
        "fail, cases 1 of 2 pass. `cd-art-10` fails until the review record approves every asset."), art_summary(gate)
    gate = sample_gate(first, {"CD-REL-04": ("open (M9)", [], [])}, {}, whole=False)
    assert totals(gate) == (
        "The spec sheet has 1 Phase 1 `MUST` and `MUST NOT` rows. 0 of the 0 rows with automated tests pass. 0 rows "
        "have a play-test part, and 0 of them have no automated test. 1 rows stay open after Phase 1 (CD-REL-04). 0 "
        "automated rows have no passing test."), totals(gate)

    # The options (main).
    args = arguments().parse_args(["--phase", "2", "--art-log", "art.txt"])
    assert (args.phase, args.art_log) == (2, pathlib.Path("art.txt")) and arguments().parse_args([]).phase == 1, args
    print("self-check passed")
    return 0


def input_error(call: Callable[[], object]) -> bool:
    """True when the call raises InputError."""
    try:
        call()
    except InputError:
        return True
    return False


def sample_gate(phase: Phase, evaluated: dict[str, tuple[str, list, list[Check]]], results: dict[Ref, tuple[str, str]],
                whole: bool) -> Gate:
    """A gate that holds only the results of its rows and tests, for the samples of the report text."""
    gate = Gate(phase=phase, commit="0" * 40, short="0" * 7, commit_date="2026-11-11", subject="a commit", rows={},
                checks=[], unit_cites={}, tests={}, run_logs={}, results=results, pack_logs={}, gradle_tasks=[],
                gradle_outcomes={}, build_result="", row_refs=defaultdict(set), deferred_groups=[], problems=[],
                whole_sheet=whole)
    gate.evaluated = evaluated
    return gate


def arguments() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--phase", type=int, choices=sorted(PHASES), default=1,
                        help="the gate whose report the script writes (default 1)")
    parser.add_argument("--gradle-log", action="append", default=[], type=pathlib.Path,
                        help="the console output of ./gradlew build and the GameTest runs, with --console=plain")
    parser.add_argument("--testpack-log", action="append", default=[], type=pathlib.Path,
                        help="the output of one test pack command, such as smoke; give one for each command")
    parser.add_argument("--art-log", type=pathlib.Path,
                        help=f"the output of {ART_COMMAND}, for the Phase 2 gate")
    parser.add_argument("--allow-dirty", action="store_true",
                        help="write the report although tracked files differ from the commit")
    parser.add_argument("--self-check", action="store_true", help="check the parsers on built-in samples")
    return parser


def main() -> int:
    parser = arguments()
    args = parser.parse_args()
    if args.self_check:
        return self_check()
    if args.art_log is not None and not PHASES[args.phase].checks_art:
        parser.error("--art-log belongs to the gate that runs the art checks: add --phase 2")
    try:
        return write_report(args)
    except InputError as error:
        print(f"gate_report: {error}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
