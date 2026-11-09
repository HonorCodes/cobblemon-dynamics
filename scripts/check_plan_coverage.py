"""Checks that every Phase 1 spec row is cited by a task in the Phase 1 milestone plan files.

Phase 1 rows are every row outside the TLK, MAG, FAN, TLW and ART areas.
Usage: py scripts/check_plan_coverage.py
Exit code 0 when every row is cited, 1 otherwise.
"""
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
SPEC = ROOT / "docs/plans/2026-11-09-v1-spec-sheet.md"
PLANS = sorted((ROOT / "docs/plans").glob("2026-11-09-phase-1-m*.md"))
PHASE_2_AREAS = {"TLK", "MAG", "FAN", "TLW", "ART"}
ROW = re.compile(r"^\| (CD-([A-Z]+)-\d{2}) \|", re.MULTILINE)
CITE = re.compile(r"CD-[A-Z]+-\d{2}")


def main() -> int:
    spec_rows = [m.group(1) for m in ROW.finditer(SPEC.read_text(encoding="utf-8")) if m.group(2) not in PHASE_2_AREAS]
    cited = set()
    for plan in PLANS:
        cited.update(CITE.findall(plan.read_text(encoding="utf-8")))
    missing = [r for r in spec_rows if r not in cited]
    print(f"phase 1 rows: {len(spec_rows)}, plan files: {len(PLANS)}, cited: {len(set(spec_rows) & cited)}")
    for row in missing:
        print(f"  missing: {row}")
    print(f"missing: {len(missing)}")
    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
