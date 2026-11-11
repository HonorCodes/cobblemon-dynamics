#!/usr/bin/env python3
"""Checks that every spec row of a phase is cited by the milestone plan files of that phase.

Phase 1 (the default): every row outside the TLK, MAG, FAN, TLW and ART areas, cited anywhere in a Phase 1 milestone file.
Phase 2: every row of those five areas, and each Phase 1 row with a Phase 2 part (the split-row table of the Phase 2 plan
index), cited inside a task section of a Phase 2 milestone file. A task section runs from a "### Task" heading to the next
heading of level 1 to 3.
Usage: py scripts/check_plan_coverage.py [--phase {1,2}]
Exit code 0 when every row is cited, 1 otherwise.
"""
import argparse
import itertools
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs/plans/2026-11-09-v1-spec-sheet.md"
PLANS = {
    1: "2026-11-09-phase-1-m*.md",
    2: "2026-11-10-phase-2-m*.md",
}
PHASE_2_AREAS = {"TLK", "MAG", "FAN", "TLW", "ART"}
# Phase 1 rows whose last part is Phase 2 work: the split-row table of docs/plans/2026-11-10-phase-2-plan.md.
PHASE_2_PARTS = (
    "CD-LSH-16", "CD-LSH-17", "CD-LSH-18", "CD-LSH-19", "CD-LSH-20", "CD-SEC-11",
    "CD-BAL-05", "CD-SEC-10", "CD-BAL-04", "CD-REL-14",
)
ROW = re.compile(r"^\| (CD-([A-Z]+)-\d{2}) \|", re.MULTILINE)
CITE = re.compile(r"CD-[A-Z]+-\d{2}")
HEADING = re.compile(r"^#{1,3} ", re.MULTILINE)


def spec_rows(phase: int) -> list[str]:
    rows = [(m.group(1), m.group(2)) for m in ROW.finditer(SPEC.read_text(encoding="utf-8"))]
    if phase == 1:
        return [row for row, area in rows if area not in PHASE_2_AREAS]
    unknown = sorted(set(PHASE_2_PARTS) - {row for row, _ in rows})
    if unknown:
        raise SystemExit(f"PHASE_2_PARTS names rows that the spec sheet lacks: {', '.join(unknown)}")
    return [row for row, area in rows if area in PHASE_2_AREAS] + list(PHASE_2_PARTS)


def task_sections(text: str) -> str:
    """The text of every task section: from a '### Task' heading to the next heading of level 1 to 3."""
    starts = [m.start() for m in HEADING.finditer(text)] + [len(text)]
    return "\n".join(text[start:end] for start, end in itertools.pairwise(starts) if text.startswith("### Task ", start))


def cited(plan: pathlib.Path, phase: int) -> set[str]:
    text = plan.read_text(encoding="utf-8")
    return set(CITE.findall(text if phase == 1 else task_sections(text)))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--phase", type=int, choices=sorted(PLANS), default=1, help="the phase to check (default 1)")
    phase = parser.parse_args().phase
    rows = spec_rows(phase)
    plans = sorted((ROOT / "docs/plans").glob(PLANS[phase]))
    found = set()
    for plan in plans:
        found |= cited(plan, phase)
    missing = [row for row in rows if row not in found]
    print(f"phase {phase} rows: {len(rows)}, plan files: {len(plans)}, cited: {len(set(rows) & found)}")
    for row in missing:
        print(f"  missing: {row}")
    print(f"missing: {len(missing)}")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
