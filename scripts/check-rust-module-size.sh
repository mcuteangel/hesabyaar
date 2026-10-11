#!/usr/bin/env bash
set -euo pipefail

# check-rust-module-size.sh - Read-only module-size gate for the Rust workspace.
# Usage: ./scripts/check-rust-module-size.sh
#
# A non-generated .rs production source file fails when it exceeds 800 lines
# or defines more than 25 top-level `fn` items, unless it is listed in the
# frozen baseline `rust/.module-size-baseline.json`. A baselined file fails only
# when it grows past its baselined value. The baseline never grows (same rule
# as `config/detekt/detekt-baseline.xml`): editing a file obligates shrinking it.
# Generated sources under any `generated/` directory and test/benchmark modules
# (`tests.rs`, `tests/`, `benches/`) are excluded (mirrors detekt's test exemption).

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUST_DIR="$REPO_ROOT/rust"
BASELINE="$RUST_DIR/.module-size-baseline.json"

MAX_LINES=800
MAX_FNS=25

if [[ ! -f "$BASELINE" ]]; then
    echo "❌ Module-size baseline '$BASELINE' not found."
    exit 1
fi

PYTHON_BIN=""
if command -v python3 >/dev/null 2>&1 && python3 --version >/dev/null 2>&1; then
    PYTHON_BIN="python3"
elif command -v python >/dev/null 2>&1 && python --version >/dev/null 2>&1; then
    PYTHON_BIN="python"
else
    echo "❌ Neither python3 nor python found."
    exit 1
fi

"$PYTHON_BIN" - "$RUST_DIR" "$BASELINE" "$MAX_LINES" "$MAX_FNS" <<'EOF'
import json
import os
import re
import sys

rust_dir, baseline_path, max_lines, max_fns = sys.argv[1], sys.argv[2], int(sys.argv[3]), int(sys.argv[4])

with open(baseline_path, encoding="utf-8") as fp:
    baseline = json.load(fp)

fn_re = re.compile(r"^(pub(\([^)]*\))?\s+)?(async\s+)?(const\s+)?fn\s+")
failures = []
checked = 0

for root, dirs, files in os.walk(rust_dir):
    # Skip generated sources (e.g. gitignored core_version.rs).
    dirs[:] = [d for d in dirs if d != "generated"]
    if "generated" in root.replace(os.sep, "/").split("/"):
        continue
    for name in sorted(files):
        if not name.endswith(".rs"):
            continue
        # Skip test and benchmark files (mirrors detekt's test exemptions).
        norm_root = root.replace(os.sep, "/")
        if name == "tests.rs" or "/tests" in norm_root or "/benches" in norm_root:
            continue
        path = os.path.join(root, name)
        rel = os.path.relpath(path, rust_dir).replace(os.sep, "/")
        with open(path, encoding="utf-8") as fp:
            lines = fp.readlines()
        line_count = len(lines)
        fn_count = sum(1 for line in lines if fn_re.match(line))
        checked += 1
        entry = baseline.get(rel)
        if entry is not None:
            if line_count > entry["lines"]:
                failures.append(
                    f"{rel}: {line_count} lines exceeds baselined {entry['lines']} (baseline never grows)"
                )
            if fn_count > entry["functions"]:
                failures.append(
                    f"{rel}: {fn_count} top-level fns exceeds baselined {entry['functions']} (baseline never grows)"
                )
        elif line_count > max_lines or fn_count > max_fns:
            failures.append(
                f"{rel}: {line_count} lines, {fn_count} top-level fns exceeds limit "
                f"({max_lines} lines / {max_fns} fns) and has no baseline entry"
            )

print(f"Checked {checked} Rust files against {baseline_path}")
if failures:
    print("❌ Rust module-size gate failed:")
    for failure in failures:
        print(f"  - {failure}")
    sys.exit(1)
print("=== Rust Module-Size Check: Complete (PASS) ===")
EOF
