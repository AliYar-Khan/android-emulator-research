"""Structural diff of two fingerprint documents.

Volatile fields (``timestamp`` at any depth, ``fingerprint_sha256``) are
excluded by default so two captures of the same environment compare clean;
pass ``--keep-volatile`` to include them.

Differences are grouped by top-level section and reported as JSON-pointer-ish
paths (``build.data.status``, ``sensors[2].name``).

Exit codes (diff semantics): 0 identical, 1 differences found, 2 usage/IO
problems.
"""

from __future__ import annotations

import argparse
import json
import sys
from dataclasses import dataclass
from typing import Any

from .canonical import canonical_json, strip_volatile

__all__ = ["Difference", "diff_documents", "main"]

_MISSING = object()


@dataclass(frozen=True)
class Difference:
    """One leaf or structural difference between documents A and B."""

    path: str
    a: Any
    b: Any

    def format(self) -> str:
        return f"{self.path}: {_fmt(self.a)} -> {_fmt(self.b)}"


def _fmt(value: Any, limit: int = 120) -> str:
    if value is _MISSING:
        return "<missing>"
    text = canonical_json(value)
    if len(text) > limit:
        text = text[: limit - 3] + "..."
    return text


def _walk(a: Any, b: Any, path: str, out: list[Difference]) -> None:
    if isinstance(a, dict) and isinstance(b, dict):
        for key in sorted(set(a) | set(b)):
            sub = f"{path}.{key}" if path else str(key)
            if key not in a:
                out.append(Difference(sub, _MISSING, b[key]))
            elif key not in b:
                out.append(Difference(sub, a[key], _MISSING))
            else:
                _walk(a[key], b[key], sub, out)
    elif isinstance(a, list) and isinstance(b, list):
        for index in range(max(len(a), len(b))):
            sub = f"{path}[{index}]"
            if index >= len(a):
                out.append(Difference(sub, _MISSING, b[index]))
            elif index >= len(b):
                out.append(Difference(sub, a[index], _MISSING))
            else:
                _walk(a[index], b[index], sub, out)
    else:
        # Scalars, None, or mismatched types. Compare canonically so that
        # True != 1 and "3" != 3, matching hash semantics.
        if canonical_json(a) != canonical_json(b):
            out.append(Difference(path, a, b))


def diff_documents(
    a: dict[str, Any], b: dict[str, Any], keep_volatile: bool = False
) -> list[Difference]:
    """Return all structural differences from A to B, document order."""
    if not keep_volatile:
        a = strip_volatile(a)
        b = strip_volatile(b)
    out: list[Difference] = []
    _walk(a, b, "", out)
    return out


def _group(differences: list[Difference]) -> dict[str, list[Difference]]:
    groups: dict[str, list[Difference]] = {}
    for diff in differences:
        head = diff.path.split(".", 1)[0].split("[", 1)[0]
        groups.setdefault(head or "(document)", []).append(diff)
    return groups


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(
        prog="python -m analysis.diff",
        description="Structural diff of two fingerprint documents (volatile fields ignored).",
    )
    parser.add_argument("file_a", help="baseline fingerprint JSON")
    parser.add_argument("file_b", help="comparison fingerprint JSON")
    parser.add_argument(
        "--keep-volatile",
        action="store_true",
        help="include timestamps and fingerprint_sha256 in the comparison",
    )
    args = parser.parse_args(argv)

    docs = []
    for name in (args.file_a, args.file_b):
        try:
            with open(name, "r", encoding="utf-8") as fh:
                doc = json.load(fh)
        except (OSError, json.JSONDecodeError) as exc:
            print(f"error: {name}: {exc}", file=sys.stderr)
            return 2
        if not isinstance(doc, dict):
            print(f"error: {name}: top-level JSON value must be an object", file=sys.stderr)
            return 2
        docs.append(doc)

    differences = diff_documents(docs[0], docs[1], keep_volatile=args.keep_volatile)
    if not differences:
        print("No differences.")
        return 0

    groups = _group(differences)
    for section, items in groups.items():
        print(f"{section}: {len(items)} difference(s)")
        for item in items:
            print(f"  {item.format()}")
    print(f"Total: {len(differences)} difference(s) in {len(groups)} section(s)")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
