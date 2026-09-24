#!/usr/bin/env bash
# Validate and compare captured fingerprints.
#
# Usage: scripts/compare.sh [-o report.md] <fingerprint.json> <fingerprint.json> [more...]
#
# Runs analysis.validate on every file, analysis.diff on the first two (when
# exactly two are given), and analysis.report across all of them.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPORT_OUT=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -o|--output) REPORT_OUT="${2:?}"; shift 2 ;;
    -h|--help) sed 's/^# \{0,1\}//' "$0" | head -5; exit 2 ;;
    *) break ;;
  esac
done

if [[ $# -lt 2 ]]; then
  echo "usage: scripts/compare.sh [-o report.md] <fingerprint.json> <fingerprint.json> [more...]" >&2
  exit 2
fi

cd "$ROOT"

for file in "$@"; do
  python3 -m analysis.validate "$file"
done

if [[ $# -eq 2 ]]; then
  set +e
  python3 -m analysis.diff "$1" "$2"
  rc=$?
  set -e
  [[ $rc -le 1 ]] || exit "$rc"   # 1 = differences found (expected), >=2 = error
  echo
fi

if [[ -n "$REPORT_OUT" ]]; then
  python3 -m analysis.report "$@" -o "$REPORT_OUT"
  echo "Report: $REPORT_OUT"
else
  python3 -m analysis.report "$@"
fi
