#!/usr/bin/env python3
"""Checks that every spec row of a phase is cited by the milestone plan files of that phase.

Phase 1 (the default): every row outside the TLK, MAG, FAN, TLW, QWK and ART areas and outside PHASE_2_ROWS, cited anywhere in a
Phase 1 milestone file. PHASE_2_ROWS holds the Quick Work keys of the server config, the INFO lines at load and the shared
containers of the logistics roles.
Phase 2: every row of those six areas and of PHASE_2_ROWS, and each Phase 1 row with a Phase 2 part (the split-row table of the
Phase 2 plan index), cited inside a task section of a Phase 2 milestone file or of the Phase 2 plan index. A task section runs
from a "### Task" heading to the next heading of level 1 to 3.
Phase 3: every row of the LND area, which Phase 1 leaves out, and each earlier row with a Phase 3 part (the split-row table of
the Phase 3 plan), cited inside a task section of the Phase 3 plan.
Usage: py scripts/check_plan_coverage.py [--phase {1,2,3}]
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
    1: ("2026-11-09-phase-1-m*.md",),
    2: (
        "2026-11-10-phase-2-m*.md",
        "2026-11-10-phase-2-plan.md",
        "2026-11-11-phase-2-quick-work.md",
        "2026-11-11-phase-2-quick-work-claims.md",
    ),
}
PHASE_2_AREAS = {"TLK", "MAG", "FAN", "TLW", "QWK", "ART"}
# Rows outside the Phase 2 areas that are Phase 2 work as a whole: the Quick Work keys of the server config (R63), the INFO
# lines at load (R65), and the shared containers of the logistics roles (R66).
PHASE_2_ROWS = (
    "CD-CFG-16", "CD-CFG-17", "CD-CFG-18", "CD-CMP-08", "CD-CMP-09", "CD-CMP-10",
    "CD-CLM-10", "CD-CLM-11", "CD-LSH-28", "CD-LSH-29", "CD-CFG-19",
)
# Phase 1 rows whose last part is Phase 2 work: the split-row table of docs/plans/2026-11-10-phase-2-plan.md.
PHASE_2_PARTS = (
    "CD-LSH-16", "CD-LSH-17", "CD-LSH-18", "CD-LSH-19", "CD-LSH-20", "CD-SEC-11",
    "CD-BAL-05", "CD-SEC-10", "CD-BAL-04", "CD-REL-14", "CD-CFG-01",
)
# Phase 3: the land claims of pastures and MineColonies (R67), and the public release.
PLANS[3] = ("2026-11-11-phase-3-release.md",)
PHASE_3_AREAS = {"LND"}
# Earlier rows whose last part is Phase 3 work: the split-row table of docs/plans/2026-11-11-phase-3-release.md.
PHASE_3_PARTS = (
    "CD-REL-04", "CD-REL-05", "CD-REL-08", "CD-REL-09", "CD-REL-11", "CD-REL-12", "CD-REL-14", "CD-REL-15",
    "CD-QWK-26", "CD-QWK-27", "CD-QWK-28", "CD-QWK-29", "CD-QWK-30", "CD-QWK-31", "CD-QWK-32", "CD-QWK-33",
    "CD-CMP-08", "CD-CMP-09", "CD-CMP-10", "CD-STK-07",
    "CD-PLAT-06", "CD-UI-03", "CD-UI-09", "CD-CMP-01", "CD-CMP-03", "CD-CMP-04", "CD-CMP-05", "CD-REL-01",
)
ROW = re.compile(r"^\| (CD-([A-Z]+)-\d{2}) \|", re.MULTILINE)
CITE = re.compile(r"CD-[A-Z]+-\d{2}")
HEADING = re.compile(r"^#{1,3} ", re.MULTILINE)


def is_phase_2(row: str, area: str) -> bool:
    return area in PHASE_2_AREAS or row in PHASE_2_ROWS


def spec_rows(phase: int) -> list[str]:
    rows = [(m.group(1), m.group(2)) for m in ROW.finditer(SPEC.read_text(encoding="utf-8"))]
    if phase == 1:
        return [row for row, area in rows if not is_phase_2(row, area) and area not in PHASE_3_AREAS]
    if phase == 3:
        unknown = sorted(set(PHASE_3_PARTS) - {row for row, _ in rows})
        if unknown:
            raise SystemExit(f"PHASE_3_PARTS names rows that the spec sheet lacks: {', '.join(unknown)}")
        return [row for row, area in rows if area in PHASE_3_AREAS] + list(PHASE_3_PARTS)
    unknown = sorted(set(PHASE_2_PARTS + PHASE_2_ROWS) - {row for row, _ in rows})
    if unknown:
        raise SystemExit(f"PHASE_2_PARTS or PHASE_2_ROWS names rows that the spec sheet lacks: {', '.join(unknown)}")
    return [row for row, area in rows if is_phase_2(row, area)] + list(PHASE_2_PARTS)


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
    plans = sorted({plan for pattern in PLANS[phase] for plan in (ROOT / "docs/plans").glob(pattern)})
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
