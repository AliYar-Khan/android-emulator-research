"""Rewrite a fingerprint document with sorted keys and a refreshed hash.

Normalization changes no observation data: it recomputes
``fingerprint_sha256`` over the document (volatile fields excluded, per the
canonicalization contract) and emits deterministic key order so two exports of
the same capture are byte-identical.

Output modes:

* default: pretty JSON, keys sorted (UTF-8 order via :func:`canonical_json`
  is used for the hash; the file itself uses ``json.dumps(sort_keys=True)``).
* ``--compact``: single-line canonical form, byte-identical to the hashed
  representation plus the volatile fields that the hash excludes.

Exit codes: 0 success, 2 usage/IO problems.
"""

from __future__ import annotations

import argparse
import json
import sys
from typing import Any

from .canonical import canonical_json, fingerprint_sha256

__all__ = ["normalize_document", "main"]


def normalize_document(doc: dict[str, Any]) -> dict[str, Any]:
    """Return a copy with a refreshed fingerprint_sha256."""
    out = dict(doc)
    out["fingerprint_sha256"] = fingerprint_sha256(doc)
    return out


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="python -m analysis.normalize",
        description="Re-canonicalize a fingerprint: sorted keys, refreshed fingerprint_sha256.",
    )
    parser.add_argument("file", help="fingerprint JSON file")
    parser.add_argument("-o", "--output", help="output path (default: stdout)")
    parser.add_argument(
        "--compact",
        action="store_true",
        help="emit the single-line canonical form instead of pretty JSON",
    )
    args = parser.parse_args(argv)

    try:
        with open(args.file, "r", encoding="utf-8") as fh:
            doc = json.load(fh)
    except (OSError, json.JSONDecodeError) as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 2
    if not isinstance(doc, dict):
        print("error: top-level JSON value must be an object", file=sys.stderr)
        return 2

    normalized = normalize_document(doc)
    if args.compact:
        text = canonical_json(normalized) + "\n"
    else:
        text = json.dumps(normalized, sort_keys=True, indent=2, ensure_ascii=False) + "\n"

    if args.output:
        try:
            with open(args.output, "w", encoding="utf-8") as fh:
                fh.write(text)
        except OSError as exc:
            print(f"error: {exc}", file=sys.stderr)
            return 2
    else:
        sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
